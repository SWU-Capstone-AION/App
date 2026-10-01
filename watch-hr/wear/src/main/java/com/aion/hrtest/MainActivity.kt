package com.aion.hrtest

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/** 화면은 시작/중지와 표시만 한다. 측정·전송은 HeartRateService가 한다 */
class MainActivity : ComponentActivity() {

    private val permissionDenied = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // 심박 권한만 필수. 알림 권한은 거부해도 서비스는 돈다 (알림만 안 보임)
        if (result[heartRatePermission()] == true) {
            permissionDenied.value = false
            HeartRateService.start(this)
        } else {
            permissionDenied.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val running by HrServiceState.running.collectAsState()
                val bpm by HrServiceState.bpm.collectAsState()
                val status by HrServiceState.status.collectAsState()
                val denied by permissionDenied

                Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (running && bpm > 0) "$bpm" else "--",
                            fontSize = 40.sp
                        )
                        Text(
                            text = if (denied) "권한 거부됨" else status,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        if (running) {
                            Button(onClick = { HeartRateService.stop(this@MainActivity) }) {
                                Text("중지")
                            }
                        } else {
                            Button(onClick = { requestAndStart() }) {
                                Text("측정 시작")
                            }
                        }
                    }
                }
            }
        }
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
