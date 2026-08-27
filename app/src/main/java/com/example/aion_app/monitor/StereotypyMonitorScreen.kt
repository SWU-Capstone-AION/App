package com.example.aion_app.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.aion_app.monitor.audio.AlarmSound
import com.example.aion_app.monitor.camera.PoseCameraView
import com.example.aion_app.monitor.pose.MinigameGate
import com.example.aion_app.monitor.pose.PoseIndex
import com.example.aion_app.monitor.pose.PoseLandmarkerHelper
import com.example.aion_app.monitor.pose.StereotypyDetector
import com.example.aion_app.monitor.ui.AlarmBanner
import com.example.aion_app.monitor.ui.ChildScreen
import com.example.aion_app.monitor.ui.Dashboard
import com.example.aion_app.monitor.ui.PoseOverlay

/**
 * 메인 앱에 삽입되는 상동행동 모니터링 화면 진입점.
 * 기본은 아동 화면(구/호흡)이며, 우상단 프로필로 선생님 대시보드로 전환한다.
 * 검출(팔/머리/몸통)은 두 화면 모두에서 백그라운드로 계속 동작.
 */
@Composable
fun StereotypyMonitorScreen(
    modifier: Modifier = Modifier,
    onWeedGame: (() -> Unit)? = null,
    onBoardGame: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    var bundle by remember { mutableStateOf<PoseLandmarkerHelper.ResultBundle?>(null) }
    var fps by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    val fpsTimes = remember { ArrayDeque<Long>() }
    val detector = remember { StereotypyDetector() }
    var detState by remember { mutableStateOf<StereotypyDetector.State?>(null) }
    var running by remember { mutableStateOf(true) }
    val alarmSound = remember { AlarmSound() }
    val lastAlarmCount = remember { intArrayOf(0) }
    var teacherMode by remember { mutableStateOf(false) } // false=아동 화면, true=선생님 대시보드

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            PoseCameraView(
                modifier = Modifier.fillMaxSize(),
                onResult = { b ->
                    bundle = b
                    val now = SystemClock.uptimeMillis()
                    fpsTimes.addLast(now)
                    while (fpsTimes.isNotEmpty() && fpsTimes.first() < now - 1000) {
                        fpsTimes.removeFirst()
                    }
                    fps = fpsTimes.size

                    // 미니게임 중에는 판정을 멈춘다.
                    // 잡초 뽑기의 팔 상하 반복이 상동행동 조건에 그대로 걸려 알림이 나가기 때문.
                    detector.setPaused(MinigameGate.active)

                    if (running) {
                        val lm = b.result.landmarks().firstOrNull()
                        fun w(i: Int): StereotypyDetector.Wrist? = lm?.getOrNull(i)?.let {
                            StereotypyDetector.Wrist(
                                xNorm = it.x().toDouble(),
                                yNorm = it.y().toDouble(),
                                visibility = it.visibility().orElse(1f).toDouble(),
                            )
                        }
                        val st = detector.update(
                            timestampMs = b.result.timestampMs(),
                            nose = w(PoseIndex.NOSE),
                            leftShoulder = w(PoseIndex.LEFT_SHOULDER),
                            rightShoulder = w(PoseIndex.RIGHT_SHOULDER),
                            leftHip = w(PoseIndex.LEFT_HIP),
                            rightHip = w(PoseIndex.RIGHT_HIP),
                            leftWrist = w(PoseIndex.LEFT_WRIST),
                            rightWrist = w(PoseIndex.RIGHT_WRIST),
                        )
                        detState = st
                        if (st.alarmCount > lastAlarmCount[0]) {
                            lastAlarmCount[0] = st.alarmCount
                            alarmSound.beep()
                        }
                    }
                },
                onError = { status = it },
            )
            if (teacherMode) {
                PoseOverlay(
                    result = bundle?.result,
                    imageWidth = bundle?.inputImageWidth ?: 0,
                    imageHeight = bundle?.inputImageHeight ?: 0,
                    modifier = Modifier.fillMaxSize(),
                )
                Dashboard(
                    state = detState,
                    fps = fps,
                    inferenceMs = bundle?.inferenceTimeMs,
                    running = running,
                    onStart = {
                        detector.reset()
                        detState = null
                        lastAlarmCount[0] = 0
                        running = true
                    },
                    onStop = { running = false },
                    modifier = Modifier.fillMaxSize(),
                )
                AlarmBanner(
                    show = running && detState?.anyAlarm == true,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC02070E))
                        .clickable { teacherMode = false }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("◀ 아동 화면", color = Color(0xFF34C6FF))
                }
            } else {
                ChildScreen(
                    alarm = running && detState?.anyAlarm == true,
                    points = 20,
                    onOpenTeacher = { teacherMode = true },
                    onHelp = {
                        Toast.makeText(context, "선생님께 도움을 요청했어요", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxSize(),
                    onWeedGame = onWeedGame,
                    onBoardGame = onBoardGame,
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("카메라 권한이 필요합니다", color = Color.White)
            }
        }
    }
}
