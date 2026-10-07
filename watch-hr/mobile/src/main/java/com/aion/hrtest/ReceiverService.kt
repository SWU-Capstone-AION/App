package com.aion.hrtest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AionHr"
private const val CHANNEL_ID = "aion_hr_receive"
private const val NOTIFICATION_ID = 2

/**
 * 수업 세션 동안 태블릿 앱을 "실행 중"으로 유지하는 포그라운드 서비스.
 *
 * 수신 자체는 HeartRateListenerService가 한다. 이 서비스는 아무것도 처리하지 않고 프로세스를 붙잡아 두기만 한다.
 * 삼성 기기는 백그라운드 앱을 배터리 절약을 위해 잠깐 얼리는데(Freecess),
 * 얼어 있는 동안 워치에서 온 메시지는 처리되지 못하고 버려진다 (실기기 3분 측정에서 7건 손실 확인).
 * 포그라운드 서비스가 떠 있는 앱은 얼리지 않는다.
 */
class ReceiverService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "수신 서비스 중지")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        // 저장소를 미리 띄워 DB 복원을 끝내 둔다 (첫 메시지가 왔을 때 기다리지 않도록)
        HrRepository.get(this)
        // PC 주소가 설정돼 있으면 PC 자동 저장도 함께 돈다 (앱이 백그라운드여도 이 서비스가 프로세스를 살려 둠)
        PcUploader.get(this).start()
        _running.value = true
        Log.d(TAG, "수신 서비스 시작")
        return START_STICKY
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "심박 수신", NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("AION")
            .setContentText("워치 심박 수신 중")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .build()

        // 워치(연결된 기기)와 통신하는 용도
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    override fun onDestroy() {
        _running.value = false
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.aion.hrtest.RECEIVE_STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** 화면(앞에 떠 있을 때)에서만 부른다. 안드로이드는 백그라운드에서 포그라운드 서비스를 새로 띄우지 못하게 막는다 */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ReceiverService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ReceiverService::class.java).setAction(ACTION_STOP))
        }
    }
}
