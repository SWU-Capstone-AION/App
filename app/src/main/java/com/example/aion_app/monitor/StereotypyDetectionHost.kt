package com.example.aion_app.monitor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
// 아동용 화면들은 시안대로만 보여야 하므로 화면은 그대로 두고
// 감지 파이프라인만 뒤에서 돌리기 위해 만든 래퍼다.
//
// ⚠ 이 컴포저블은 NavHost '바깥'에 붙는다. (AionNavHost.kt)
//   NavHost 안에 두면 화면을 옮길 때마다 컴포지션에서 빠져서 감지가 끊긴다.
//   바깥에 두면 아동용 화면끼리 이동하는 동안 카메라가 한 번도 재바인딩되지 않는다.
//
// 동작
//   1. 카메라 권한을 요청한다. 거부해도 화면은 정상 동작하고 감지만 꺼진다.
//   2. PoseCameraView 를 1dp 크기로 붙인다.
//      PoseCameraView 는 IMAGE_ANALYSIS 유즈케이스만 바인딩하므로(setEnabledUseCases)
//      PreviewView 는 화면에 아무것도 그리지 않는다. 크기는 형식상 필요할 뿐이다.
//   3. StereotypyDetector 결과의 anyAlarm 을 StereotypySignal 에 쓴다.
//
// ⚠ 카메라는 한 번에 한 곳만 쓸 수 있다.
//   미니게임 / 모니터링 화면은 직접 카메라를 잡으므로 enabled 조건에서 빠져 있다.
//   (Route.KIDS_DETECTION_ROUTES 참고)
//
// ⚠ 앱을 백그라운드로 내리면 감지는 멈춘다.
//   PoseCameraView 의 LifecycleCameraController 가 ON_STOP 에서 카메라를 놓기 때문.
//   앱 밖에서도 감지하려면 포그라운드 서비스(foregroundServiceType="camera")가 필요하다.
//
// ⚠ MinigameGate 는 여기서도 그대로 반영한다.
//   미니게임 중에는 팔 반복 동작이 판정에 걸리므로 검출을 멈춘다.
// ============================================================
@Composable
fun StereotypyDetectionHost(
    modifier: Modifier = Modifier,
    // false 로 두면 카메라를 아예 붙이지 않는다.
    // 아동용 화면에 있을 때만 true 가 되도록 라우트로 묶어서 넘긴다.
    enabled: Boolean = true,
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

    // 권한 요청은 감지 구간에 처음 들어왔을 때 한 번.
    // 스플래시/로그인 화면에서는 enabled 가 false 라 팝업이 뜨지 않는다.
    LaunchedEffect(enabled) {
        if (enabled && !hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    val detector = remember { StereotypyDetector() }

    val active = enabled && hasCameraPermission

    // 감지 구간을 벗어나면(모니터링 / 미니게임 / 교사 화면) 판정 상태를 초기화한다.
    // 다시 들어왔을 때 이전 판정이 남아 팝업이 바로 뜨는 걸 막는다.
    DisposableEffect(active) {
        onDispose {
            detector.reset()
            StereotypySignal.detected = false
        }
    }

    if (active) {
        PoseCameraView(
            modifier = modifier.size(1.dp),
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
                StereotypySignal.detected = state.anyAlarm

                // TODO: 교사폰 위험 알림(FCM) 전송 지점.
                //       state.alarmCount 가 늘어난 순간에만 한 번 보내야 중복 알림이 안 간다.
            },
            onError = { /* 모델 로드 실패 등. 아동 화면에는 노출하지 않는다 */ },
        )
    }
}