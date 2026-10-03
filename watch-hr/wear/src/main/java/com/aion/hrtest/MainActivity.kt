package com.aion.hrtest

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.wear.compose.material3.MaterialTheme

/** 화면은 상태 표시와 버튼만 한다. 측정·전송은 HeartRateService가 한다. 화면 디자인은 WatchScreens.kt */
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // 심박 권한만 필수. 알림 권한은 거부해도 서비스는 돈다 (알림만 안 보임)
        if (result[heartRatePermission()] == true) {
            HeartRateService.start(this)
        } else {
            Toast.makeText(this, "심박 측정 권한이 필요해요", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 디자인 확인용: adb shell am start -n com.aion.hrtest/.MainActivity --es demo DISCONNECTED
        val demo = intent.getStringExtra("demo")?.let { runCatching { WatchScreen.valueOf(it) }.getOrNull() }

        setContent {
            MaterialTheme {
                val running by HrServiceState.running.collectAsState()
                val worn by HrServiceState.worn.collectAsState()
                val connected by HrServiceState.connected.collectAsState()
                val bpm by HrServiceState.bpm.collectAsState()
                val target by TabletLink.chosen.collectAsState()
                val found by TabletLink.found.collectAsState()

                AionWatchApp(
                    screen = demo ?: screenOf(running, worn, connected),
                    bpm = if (demo != null) 82 else bpm,
                    onStart = { requestAndStart() },
                    onStop = { HeartRateService.stop(this) },
                    onReconnect = { HeartRateService.recheckConnection(this) },
                    onRetry = { HeartRateService.restartMeasuring(this) },
                    target = target,
                    found = found,
                    onChooseTarget = { TabletLink.choose(it) },
                )
            }
        }
    }

    // 화면이 보이는 동안 같은 Wi-Fi의 태블릿을 찾는다 (측정 중에는 서비스가 따로 붙잡고 있음)
    override fun onStart() {
        super.onStart()
        TabletLink.acquire(this)
    }

    override fun onStop() {
        TabletLink.release()
        super.onStop()
    }

    private fun requestAndStart() {
        val permissions = buildList {
            add(heartRatePermission())
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    /** Wear OS 6(API 36)부터는 BODY_SENSORS 대신 READ_HEART_RATE */
    private fun heartRatePermission(): String =
        if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE"
        else Manifest.permission.BODY_SENSORS
}
