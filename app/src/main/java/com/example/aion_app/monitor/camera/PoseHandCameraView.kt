package com.example.aion_app.monitor.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.aion_app.monitor.pose.PoseHandLandmarkerHelper
import java.util.concurrent.Executors

/**
 * 전면 카메라 + MediaPipe 포즈·손 검출 (규칙모델 v3 용).
 * [PoseCameraView] 와 같은 구조이고, 검출기만 [PoseHandLandmarkerHelper] 로 바꿨다.
 *
 * ⚠ 프리뷰 화면은 좌우 반전되어 보이지만(전면 카메라), 검출은 반전하지 않은 원본으로 한다.
 *   그 위에 관절을 그릴 때는 x 를 뒤집어야 한다 (RuleV3Overlay 참고).
 *
 * @param withHands false 면 손 모델을 끈다 (fps 가 20 아래로 떨어지는 기기용).
 * @param handEvery 손 모델을 몇 프레임에 한 번 돌릴지 (1 = 매 프레임). 둘 다 처음 붙을 때 값으로 고정된다.
 */
@Composable
fun PoseHandCameraView(
    modifier: Modifier = Modifier,
    withHands: Boolean = true,
    handEvery: Int = DEFAULT_HAND_EVERY,
    onResult: (PoseHandLandmarkerHelper.ResultBundle) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)

    val executor = remember { Executors.newSingleThreadExecutor() }
    val helper = remember {
        PoseHandLandmarkerHelper(
            context = context,
            withHands = withHands,
            handEvery = handEvery,
            onResult = { currentOnResult(it) },
            onError = { currentOnError(it) },
        )
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisOutputImageFormat = ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
            setImageAnalysisAnalyzer(executor) { imageProxy -> helper.detect(imageProxy) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            // 분석 스레드에서 돌던 검출이 끝난 뒤에 닫히도록 같은 스레드에 넣고 종료한다
            executor.execute { helper.close() }
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                controller.bindToLifecycle(lifecycleOwner)
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
    )
}

/** 손 모델을 몇 프레임에 한 번 돌릴지 기본값 (아동 화면 뒤 감지와 모니터링 화면이 같이 쓴다) */
const val DEFAULT_HAND_EVERY = 2
