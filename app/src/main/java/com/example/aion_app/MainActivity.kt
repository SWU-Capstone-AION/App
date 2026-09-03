package com.example.aion_app

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.aion_app.navigation.AionNavHost
import com.example.aion_app.ui.theme.AionTheme

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.aion_app.data.messaging.AionMessagingService

import android.content.Intent
import com.example.aion_app.data.messaging.AlertBus
import com.example.aion_app.data.messaging.DangerAlert
import com.example.aion_app.data.messaging.AlertKind

// ============================================
// 기기 판별 기준
// ============================================
// 안드로이드 표준인 sw600dp 를 그대로 쓴다.
// smallestScreenWidthDp 는 '화면의 짧은 변' 이라 기기를 어떻게 돌려도 값이 바뀌지 않는다.
// → 회전 고정을 걸어둔 뒤에 읽어도 결과가 흔들리지 않는다.
const val TabletSmallestWidthDp = 600

fun isTabletDevice(context: Context): Boolean =
    context.resources.configuration.smallestScreenWidthDp >= TabletSmallestWidthDp

class MainActivity : ComponentActivity() {

    // 권한 결과는 따로 처리하지 않는다.
    // 거부해도 앱은 정상 동작하고, 알림만 표시되지 않는다.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()
        requestNotificationPermission()

        // 알림을 눌러서 앱이 열린 경우
        handleAlertIntent(intent)

        // 시스템바(상태바/내비게이션바) 여백은 AionNavHost 에서 처리한다.
        // 스플래시만 풀블리드로 둬야 해서 현재 라우트를 아는 쪽에 두는 게 맞다.
        setContent {
            AionTheme {
                AionNavHost()
            }
        }
    }

    /**
     * 알림 채널을 앱 시작 시 미리 만들어 둔다.
     *
     * 앱이 백그라운드일 때는 시스템이 알림을 직접 띄우는데,
     * 그 시점에 채널이 없으면 알림이 조용히 무시된다.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(AionMessagingService.CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            AionMessagingService.CHANNEL_ID,
            "상동행동 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "아동에게 상동행동이 감지되면 알려줍니다."
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** 안드로이드 13부터는 알림 권한을 사용자에게 받아야 한다. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 앱이 이미 떠 있는 상태에서 알림을 누르면 onCreate 대신 여기로 온다
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAlertIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AlertBus.isAppForeground = true
    }

    override fun onStop() {
        super.onStop()
        AlertBus.isAppForeground = false
    }

    /** 알림에 실려 온 알림 정보를 팝업으로 넘긴다. */
    private fun handleAlertIntent(intent: Intent?) {
        val childId = intent?.getStringExtra(AionMessagingService.EXTRA_CHILD_ID) ?: return
        val childName = intent.getStringExtra(AionMessagingService.EXTRA_CHILD_NAME) ?: return
        val kind = runCatching {
            AlertKind.valueOf(intent.getStringExtra(AionMessagingService.EXTRA_KIND).orEmpty())
        }.getOrDefault(AlertKind.DANGER)

        AlertBus.push(
            DangerAlert(
                kind = kind,
                childId = childId,
                childName = childName,
                gender = intent.getStringExtra(AionMessagingService.EXTRA_GENDER).orEmpty(),
                age = intent.getIntExtra(AionMessagingService.EXTRA_AGE, 0),
            )
        )
    }

}