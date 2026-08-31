package com.example.aion_app.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.aion_app.monitor.audio.AlarmSound
import com.example.aion_app.monitor.camera.PoseCameraView
import com.example.aion_app.monitor.pose.MinigameGate
import com.example.aion_app.monitor.pose.PoseIndex
import com.example.aion_app.monitor.pose.PoseLandmarkerHelper
import com.example.aion_app.monitor.pose.StereotypyDetector
import com.example.aion_app.monitor.ui.AlarmBanner
import com.example.aion_app.monitor.ui.Dashboard
import com.example.aion_app.monitor.ui.PoseOverlay

// ============================================================
// 상동행동 모니터링(인식) 화면
// ============================================================
// 아동용 홈(KidsHomeScreen) 좌상단 '모니터링' 버튼으로 들어온다.
//
// 이 화면은 '인식 결과를 보여주는 화면'만 담당한다.
//   카메라 프리뷰 + 스켈레톤 오버레이 + 판정 대시보드(HUD)
//
// 아동이 평소에 보는 화면(구체 / 진정 팝업 / 호흡)은 KidsHomeScreen 하나로 합쳤다.
// 예전에는 여기서도 ChildScreen 으로 같은 화면을 한 번 더 그려서 아동 홈이 두 개였는데,
// 그쪽은 삭제했다. 홈 뒤에서 도는 감지는 StereotypyDetectionHost 가 맡는다.
//
// ⚠ 카메라는 한 번에 한 곳만 쓸 수 있다.
//   홈 → 모니터링 이동 시 홈의 StereotypyDetectionHost 가 먼저 카메라를 놓아준다.
//   (AionNavHost 에서 enabled 를 현재 라우트로 묶어 순서를 보장한다)
@Composable
fun StereotypyMonitorScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
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

            PoseOverlay(
                result = bundle?.result,
                imageWidth = bundle?.inputImageWidth ?: 0,
                imageHeight = bundle?.inputImageHeight ?: 0,
                modifier = Modifier.fillMaxSize(),
            )

            // 대시보드 상단 '뒤로'로 아동 홈에 돌아간다.
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
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
            )

            AlarmBanner(
                show = running && detState?.anyAlarm == true,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
            )

            // 모델 로드 실패 등은 하단에 조용히 표시만 한다.
            if (status.isNotBlank()) {
                Text(
                    text = status,
                    color = Color(0xFFFF5A3C),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                )
            }
        } else {
            // 권한이 없으면 인식 자체가 불가능하다. 홈으로 돌아갈 길만 남긴다.
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("카메라 권한이 필요합니다", color = Color.White, fontSize = 16.sp)

                Box(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF34C6FF))
                        .clickable { onBack() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "돌아가기",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}