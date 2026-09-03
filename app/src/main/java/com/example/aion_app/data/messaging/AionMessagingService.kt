package com.example.aion_app.data.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.aion_app.MainActivity
import com.example.aion_app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 메시지 수신 및 토큰 갱신 처리.
 *
 * 앱이 화면에 보이는 중이면 시스템 알림 대신 앱 안 팝업으로 알린다.
 * 그 외에는 시스템 알림을 띄우고, 누르면 앱이 열리면서 같은 팝업이 뜬다.
 *
 * 팝업을 띄우는 알림은 두 가지다.
 *   DANGER — 상동행동이 위험 단계로 판정됨
 *   HELP   — 아동이 태블릿에서 도움을 요청함
 * 주의(CAUTION)·안정(STABLE)은 급하지 않아 알림센터에만 쌓인다.
 */
class AionMessagingService : FirebaseMessagingService() {

    /**
     * 토큰이 갱신될 때 서버(Firestore)에 다시 저장한다.
     *
     * 토큰은 앱 재설치, 데이터 삭제, 장기 미사용 등으로 바뀔 수 있다.
     * 갱신을 반영하지 않으면 저장된 토큰이 죽어서 알림이 오지 않는다.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "토큰 갱신: $token")

        // 로그인 상태가 아니면 저장할 곳이 없다.
        // 다음 로그인 때 새 토큰이 저장되므로 그냥 넘어간다.
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .update("fcmToken", token)
            .addOnFailureListener { e -> Log.w(TAG, "토큰 갱신 저장 실패", e) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "메시지 수신: ${message.data}")

        val alert = message.data.toPopupAlert()

        if (alert != null) {
            // 화면을 보고 있으면 팝업으로 충분하다. 시스템 알림까지 띄우면 중복이다.
            AlertBus.push(alert)
            if (AlertBus.isAppForeground) return

            val (title, body) = when (alert.kind) {
                AlertKind.DANGER -> "위험" to "${alert.childName} 학생의 상태를 즉시 확인하세요."
                AlertKind.HELP -> "도움 요청" to "${alert.childName} 학생이 도움을 요청했어요."
            }
            showNotification(title = title, body = body, alert = alert)
            return
        }

        // 팝업 대상이 아닌 메시지 (주의·안정, 콘솔 테스트 발송 등)
        val title = message.notification?.title ?: message.data["title"] ?: "AION"
        val body = message.notification?.body ?: message.data["body"] ?: return
        showNotification(title, body, alert = null)
    }

    /** data 페이로드를 팝업 알림으로 해석한다. 대상이 아니면 null. */
    private fun Map<String, String>.toPopupAlert(): DangerAlert? {
        val kind = when (this["level"]) {
            LEVEL_DANGER -> AlertKind.DANGER
            LEVEL_HELP -> AlertKind.HELP
            else -> return null
        }

        val childId = this["childId"] ?: return null
        val childName = this["childName"] ?: return null

        return DangerAlert(
            kind = kind,
            childId = childId,
            childName = childName,
            gender = this["gender"].orEmpty(),
            age = this["age"]?.toIntOrNull() ?: 0,
        )
    }

    private fun showNotification(title: String, body: String, alert: DangerAlert?) {
        createChannelIfNeeded()

        // 알림을 누르면 앱이 열린다.
        // 팝업 대상이면 내용을 함께 실어 보내 앱에서 팝업을 띄우게 한다.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (alert != null) {
                putExtra(EXTRA_KIND, alert.kind.name)
                putExtra(EXTRA_CHILD_ID, alert.childId)
                putExtra(EXTRA_CHILD_NAME, alert.childName)
                putExtra(EXTRA_GENDER, alert.gender)
                putExtra(EXTRA_AGE, alert.age)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // 권한이 없으면 표시되지 않는다. 앱에서 권한을 먼저 받아야 한다.
        try {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "알림 권한이 없어 표시하지 못함", e)
        }
    }

    /**
     * 알림 채널 생성.
     *
     * 중요도를 HIGH로 두어야 화면 상단에 배너로 뜬다.
     * 낮으면 알림창에만 조용히 쌓여서 교사가 놓칠 수 있다.
     */
    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "상동행동 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "아동에게 상동행동이 감지되면 알려줍니다."
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "FCM_TOKEN"
        private const val LEVEL_DANGER = "DANGER"
        private const val LEVEL_HELP = "HELP"

        const val CHANNEL_ID = "aion_alert"   // 매니페스트의 default_notification_channel_id 와 같아야 함

        const val EXTRA_KIND = "kind"
        const val EXTRA_CHILD_ID = "childId"
        const val EXTRA_CHILD_NAME = "childName"
        const val EXTRA_GENDER = "gender"
        const val EXTRA_AGE = "age"
    }
}