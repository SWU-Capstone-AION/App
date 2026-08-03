package com.example.aion_app.monitor.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * MediaPipe PoseLandmarker(LIVE_STREAM) 래퍼.
 * CameraX 의 ImageProxy 를 받아 회전/미러 보정 후 비동기 검출을 돌리고,
 * 결과를 [onResult] 로 넘긴다. GPU delegate 실패 시 CPU 로 자동 폴백.
 */
class PoseLandmarkerHelper(
    context: Context,
    private val onResult: (ResultBundle) -> Unit,
    private val onError: (String) -> Unit,
) {
    data class ResultBundle(
        val result: PoseLandmarkerResult,
        val inputImageWidth: Int,
        val inputImageHeight: Int,
        val inferenceTimeMs: Long,
    )

    private var poseLandmarker: PoseLandmarker? = null
    var delegateName: String = "-"
        private set

    init {
        try {
            poseLandmarker = create(context, Delegate.GPU)
            delegateName = "GPU"
        } catch (e: Throwable) {
            try {
                poseLandmarker = create(context, Delegate.CPU)
                delegateName = "CPU"
                onError("GPU delegate 실패 → CPU 전환")
            } catch (e2: Throwable) {
                onError("모델 로드 실패: ${e2.message}")
            }
        }
    }

    private fun create(context: Context, delegate: Delegate): PoseLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(delegate)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result: PoseLandmarkerResult, input: MPImage ->
                val inference = SystemClock.uptimeMillis() - result.timestampMs()
                onResult(ResultBundle(result, input.width, input.height, inference))
            }
            .setErrorListener { e -> onError(e.message ?: "pose error") }
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    /** CameraX 프레임 1장을 비동기 검출 큐에 넣는다. RGBA_8888 포맷 ImageProxy 필요. */
    fun detect(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val landmarker = poseLandmarker ?: run { imageProxy.close(); return }
        val frameTime = SystemClock.uptimeMillis()

        val bitmap = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmap.copyPixelsFromBuffer(it.planes[0].buffer) }

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
            }
        }
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        landmarker.detectAsync(BitmapImageBuilder(rotated).build(), frameTime)
    }

    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    companion object {
        const val MODEL_ASSET = "pose_landmarker_full.task"
    }
}
