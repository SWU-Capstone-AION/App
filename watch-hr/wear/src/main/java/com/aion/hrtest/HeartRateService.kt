package com.aion.hrtest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AionHr"
private const val CHANNEL_ID = "aion_hr_measure"
private const val NOTIFICATION_ID = 1

/** 서비스 상태를 화면에 넘기기 위한 통로. 화면이 꺼지거나 다시 켜져도 서비스가 주인이다 */
object HrServiceState {
    internal val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    internal val _bpm = MutableStateFlow(0)
    val bpm: StateFlow<Int> = _bpm.asStateFlow()

    internal val _status = MutableStateFlow("대기")
    val status: StateFlow<String> = _status.asStateFlow()

    /** 받을 기기(태블릿)에 마지막으로 보내졌는지 */
    internal val _connected = MutableStateFlow(true)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** 손목에 차고 있는지 (워치가 "손목에서 빠짐"을 감지하면 false) */
    internal val _worn = MutableStateFlow(true)
    val worn: StateFlow<Boolean> = _worn.asStateFlow()
}

/**
 * 심박 측정·전송을 화면과 분리한 포그라운드 서비스.
 * 화면이 꺼지거나 다른 앱으로 가도 측정이 이어진다. 교사가 [측정 시작]을 누르면 켜지고 [중지]로 끈다.
 */
class HeartRateService : LifecycleService() {

    private lateinit var health: HealthServicesManager
    private lateinit var sender: HeartRateSender
    private var measureJob: Job? = null
    private var tickJob: Job? = null

    /** 못 보낸 값 대기열 + "연결 끊김" 판단. 센서 처리와 1초 타이머가 같이 건드리므로 잠금으로 순서를 지킨다 */
    private val outbox = Outbox()
    private val outboxLock = Mutex()

    private suspend fun flushOutbox() = outboxLock.withLock {
        val now = System.currentTimeMillis()
        val sent = outbox.flush(now) { sender.send(it) }
        HrServiceState._connected.value = outbox.connected(now)
        if (outbox.size > 0) Log.d(TAG, "대기 중 ${outbox.size}개 (이번에 ${sent}개 전송, 넘쳐서 버림 ${outbox.dropped}개)")
    }

    override fun onCreate() {
        super.onCreate()
        health = HealthServicesManager(this)
        sender = HeartRateSender(this)
        // 화면을 꺼도 태블릿(Wi-Fi) 연결을 붙잡아 둔다
        TabletLink.acquire(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "서비스 중지 요청")
                stopSelf()
                return START_NOT_STICKY
            }
            // [다시 연결]: 태블릿이 보이는지 바로 다시 확인 (측정은 그대로)
            ACTION_RECHECK -> lifecycleScope.launch {
                TabletLink.refresh()
                Log.d(TAG, "연결 다시 확인: 기기 보임=${sender.isConnected()}, 대기 ${outbox.size}개")
                flushOutbox()
            }
            // [다시 시도]: 센서 측정을 처음부터 다시 시작
            ACTION_RESTART -> {
                Log.d(TAG, "측정 다시 시작")
                measureJob?.cancel()
                measureJob = null
                HrServiceState._worn.value = true
                startMeasuring()
            }
            else -> startMeasuring()
        }
        // 시스템이 메모리 부족으로 죽여도 다시 살려 측정을 이어간다
        return START_STICKY
    }

    private fun startMeasuring() {
        goForeground()
        if (measureJob?.isActive == true) return

        HrServiceState._running.value = true
        // 1초마다 "연결 끊김" 판단을 갱신하고, 밀린 값이 있으면 5초마다 다시 보내 본다
        if (tickJob?.isActive != true) tickJob = lifecycleScope.launch {
            var tick = 0
            while (isActive) {
                delay(1_000)
                tick++
                if (outbox.size > 0 && tick % 5 == 0) flushOutbox()
                else HrServiceState._connected.value = outbox.connected(System.currentTimeMillis())
            }
        }
        measureJob = lifecycleScope.launch {
            val (exerciseHr, fiveSec) = runCatching { health.exerciseSupport() }.getOrDefault(false to false)
            if (!exerciseHr && !health.hasHeartRateCapability()) {
                HrServiceState._status.value = "심박 센서 없음"
                return@launch
            }
            HrServiceState._status.value = "측정 중"
            Log.d(TAG, "서비스 측정 시작 (운동 세션=$exerciseHr, 5초 배치=$fiveSec)")

            // 운동 세션을 못 쓰면 MeasureClient로 대신한다 (화면이 꺼지면 60초씩 묶여 온다)
            val source = if (exerciseHr) health.exerciseHeartRateFlow(fiveSec) else health.heartRateFlow()
            source.collect { msg ->
                when (msg) {
                    is HrMessage.Data -> {
                        // 미착용 순간에 오는 0 bpm은 화면에도, 전송에도 쓰지 않는다
                        val valid = msg.samples.filter { it.bpm > 0 }
                        if (valid.isNotEmpty()) {
                            HrServiceState._bpm.value = valid.last().bpm
                            HrServiceState._worn.value = true
                        }
                        // 대기열에 넣고 밀린 것부터 순서대로 보낸다 (못 보낸 건 남겨 뒀다가 다시)
                        outboxLock.withLock { outbox.add(msg.samples) }
                        flushOutbox()
                    }
                    is HrMessage.AvailabilityChanged -> {
                        HrServiceState._worn.value = msg.worn
                        HrServiceState._status.value = if (msg.worn) "측정 중" else "착용 확인"
                    }
                }
            }
        }
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "심박 측정", NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        // 아동이 볼 수 있으므로 알림 문구는 최소로 (설계서 7장: 무자극)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("AION")
            .setContentText("측정 중")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(openApp)

        // Wear OS: 시계 화면·최근 앱에 "진행 중" 표시를 띄워 앱이 살아 있음을 알린다
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(android.R.drawable.ic_menu_mylocation)
            .setTouchIntent(openApp)
            .setStatus(Status.Builder().addTemplate("측정 중").build())
            .build()
            .apply(applicationContext)

        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, builder.build(), type)
    }

    override fun onDestroy() {
        Log.d(TAG, "서비스 종료")
        measureJob?.cancel()   // heartRateFlow의 awaitClose에서 센서 콜백 해제
        tickJob?.cancel()
        if (outbox.size > 0) Log.w(TAG, "종료 시 못 보낸 값 ${outbox.size}개 버림")
        outbox.clear()
        TabletLink.release()
        HrServiceState._running.value = false
        HrServiceState._status.value = "대기"
        HrServiceState._bpm.value = 0
        HrServiceState._connected.value = true
        HrServiceState._worn.value = true
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.aion.hrtest.STOP"
        private const val ACTION_RECHECK = "com.aion.hrtest.RECHECK"
        private const val ACTION_RESTART = "com.aion.hrtest.RESTART"

        fun recheckConnection(context: Context) {
            context.startService(Intent(context, HeartRateService::class.java).setAction(ACTION_RECHECK))
        }

        fun restartMeasuring(context: Context) {
            context.startService(Intent(context, HeartRateService::class.java).setAction(ACTION_RESTART))
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, HeartRateService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, HeartRateService::class.java).setAction(ACTION_STOP))
        }
    }
}
