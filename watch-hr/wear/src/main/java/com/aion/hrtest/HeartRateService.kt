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
}

/**
 * 심박 측정·전송을 화면과 분리한 포그라운드 서비스.
 * 화면이 꺼지거나 다른 앱으로 가도 측정이 이어진다. 교사가 [측정 시작]을 누르면 켜지고 [중지]로 끈다.
 */
class HeartRateService : LifecycleService() {

    private lateinit var health: HealthServicesManager
    private lateinit var sender: HeartRateSender
    private var measureJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        health = HealthServicesManager(this)
        sender = HeartRateSender(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "서비스 중지 요청")
                stopSelf()
                return START_NOT_STICKY
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
                        HrServiceState._bpm.value = msg.samples.last().bpm
                        // 여러 개 묶여 오면 전부 보낸다
                        msg.samples.forEach { sender.send(it) }
                    }
                    is HrMessage.AvailabilityChanged -> {
                        HrServiceState._status.value = if (msg.available) "측정 중" else "착용 확인"
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
        HrServiceState._running.value = false
        HrServiceState._status.value = "대기"
        HrServiceState._bpm.value = 0
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.aion.hrtest.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, HeartRateService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, HeartRateService::class.java).setAction(ACTION_STOP))
        }
    }
}
