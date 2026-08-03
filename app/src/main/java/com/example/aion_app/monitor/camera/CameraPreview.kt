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
import com.example.aion_app.monitor.pose.PoseLandmarkerHelper
import java.util.concurrent.Executors

/**
 * 전면 카메라 프리뷰 + MediaPipe 포즈 검출.
 * ImageAnalysis 프레임을 PoseLandmarkerHelper 로 흘려보내고, 결과는 [onResult] 로 콜백.
 */
@Composable
fun PoseCameraView(
    modifier: Modifier = Modifier,
    onResult: (PoseLandmarkerHelper.ResultBundle) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)

    val executor = remember { Executors.newSingleThreadExecutor() }
    val helper = remember {
        PoseLandmarkerHelper(
            context = context,
            onResult = { currentOnResult(it) },
            onError = { currentOnError(it) },
        )
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisOutputImageFormat = ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
            setImageAnalysisAnalyzer(executor) { imageProxy ->
                helper.detect(imageProxy, isFrontCamera = true)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            helper.close()
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
