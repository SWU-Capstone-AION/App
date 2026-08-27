package com.example.aion_app.monitor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.aion_app.monitor.camera.PoseCameraView
import com.example.aion_app.monitor.pose.MinigameGate
import com.example.aion_app.monitor.pose.PoseIndex
import com.example.aion_app.monitor.pose.StereotypyDetector

// ============================================================
// 아동 화면 뒤에서 상동행동을 감지하는 호스트
// ============================================================
// StereotypyMonitorScreen 은 '개발/시연용 화면'이라 카메라 프리뷰와 대시보드를 같이 보여준다.
// 아동용 홈은 시안대로 구체만 보여야 하므로 화면은 그대로 두고
// 감지 파이프라인만 뒤에서 돌리기 위해 만든 래퍼다.
//
// 동작
//   1. 카메라 권한을 요청한다. 거부해도 화면은 정상 동작하고 감지만 꺼진다.
//   2. PoseCameraView 를 1dp 크기로 붙인다.
//      PoseCameraView 는 IMAGE_ANALYSIS 유즈케이스만 바인딩하므로(setEnabledUseCases)
//      PreviewView 는 화면에 아무것도 그리지 않는다. 크기는 형식상 필요할 뿐이다.
//   3. StereotypyDetector 결과의 anyAlarm 을 content 로 넘긴다.
//
// ⚠ 카메라는 한 번에 한 곳만 쓸 수 있다.
//   미니게임·모니터링 화면으로 이동하면 이 컴포저블이 컴포지션에서 빠지면서
//   DisposableEffect 가 카메라를 놓아준다. (라우트가 갈리므로 자동으로 정리됨)
//
// ⚠ MinigameGate 는 여기서도 그대로 반영한다.
//   미니게임 중에는 팔 반복 동작이 판정에 걸리므로 검출을 멈춘다.
// ============================================================
@Composable
fun StereotypyDetectionHost(
    modifier: Modifier = Modifier,
    // false 로 두면 카메라를 아예 붙이지 않는다. (프리뷰 / 에뮬레이터 확인용)
    enabled: Boolean = true,
    content: @Composable (stereotypyDetected: Boolean) -> Unit
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

    LaunchedEffect(enabled) {
        if (enabled && !hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    val detector = remember { StereotypyDetector() }
    var alarm by remember { mutableStateOf(false) }

    // 화면을 벗어나면 다음에 들어왔을 때 이전 판정이 남아 있지 않도록 초기화
    DisposableEffect(Unit) {
        onDispose {
            detector.reset()
            alarm = false
        }
    }

    val active = enabled && hasCameraPermission

    Box(modifier = modifier.fillMaxSize()) {
        content(alarm && active)

        if (active) {
            PoseCameraView(
                modifier = Modifier.align(Alignment.TopStart).size(1.dp),
                onResult = { bundle ->
                    // 미니게임 중에는 판정을 멈춘다 (잡초 뽑기의 팔 상하 반복이 조건에 걸림)
                    detector.setPaused(MinigameGate.active)

                    val lm = bundle.result.landmarks().firstOrNull()
                    fun point(i: Int): StereotypyDetector.Wrist? = lm?.getOrNull(i)?.let {
                        StereotypyDetector.Wrist(
                            xNorm = it.x().toDouble(),
                            yNorm = it.y().toDouble(),
                            visibility = it.visibility().orElse(1f).toDouble(),
                        )
                    }

                    val state = detector.update(
                        timestampMs = bundle.result.timestampMs(),
                        nose = point(PoseIndex.NOSE),
                        leftShoulder = point(PoseIndex.LEFT_SHOULDER),
                        rightShoulder = point(PoseIndex.RIGHT_SHOULDER),
                        leftHip = point(PoseIndex.LEFT_HIP),
                        rightHip = point(PoseIndex.RIGHT_HIP),
                        leftWrist = point(PoseIndex.LEFT_WRIST),
                        rightWrist = point(PoseIndex.RIGHT_WRIST),
                    )
                    alarm = state.anyAlarm

                    // TODO: 교사폰 위험 알림(FCM) 전송 지점.
                    //       state.alarmCount 가 늘어난 순간에만 한 번 보내야 중복 알림이 안 간다.
                },
                onError = { /* 모델 로드 실패 등. 아동 화면에는 노출하지 않는다 */ },
            )
        }
    }
}