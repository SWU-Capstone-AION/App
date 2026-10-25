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
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.atomic.AtomicReference

/**
 * MediaPipe PoseLandmarker(LIVE_STREAM) 래퍼.
 * CameraX 의 ImageProxy 를 받아 회전/미러 보정 후 비동기 검출을 돌리고,
 * 결과를 [onResult] 로 넘긴다. GPU delegate 실패 시 CPU 로 자동 폴백.
 *
 * 규칙모델 v3 를 위해 손 모델(HandLandmarker, 손마다 21개 관절)도 같은 프레임에 돌린다.
 *  - [enableHands] = true 일 때만 켠다. assets/hand_landmarker.task 가 없거나 false 면 손 모델은 꺼지고
 *    ResultBundle.hands 는 null (규칙모델은 포즈의 손 관절 3개로 대신한다)
 *  - 손 결과는 따로 도착하므로, 포즈 결과가 나올 때 '가장 최근 손 결과'(100ms 안)를 같이 넘긴다
 */
class PoseLandmarkerHelper(
    context: Context,
    private val onResult: (ResultBundle) -> Unit,
    private val onError: (String) -> Unit,
    // 미니게임처럼 손가락이 필요 없는 곳은 기본값(false)으로 둬서 계산을 아낀다
    enableHands: Boolean = false,
) {
    data class ResultBundle(
        val result: PoseLandmarkerResult,
        val inputImageWidth: Int,
        val inputImageHeight: Int,
        val inferenceTimeMs: Long,
        /** 같은 시점(±100ms)의 손 모델 결과. 손 모델이 꺼져 있으면 null */
        val hands: HandLandmarkerResult? = null,
    )

    private var poseLandmarker: PoseLandmarker? = null
    private var handLandmarker: HandLandmarker? = null
    private val latestHands = AtomicReference<HandLandmarkerResult?>(null)

    // 마지막으로 detectAsync 에 넣은 타임스탬프.
    //
    // LIVE_STREAM 모드는 타임스탬프가 반드시 이전보다 커야 한다.
    // uptimeMillis() 는 ms 단위라 추론이 빠를 때 두 프레임이 같은 값을 받을 수 있고,
    // 그러면 MediaPipe 가 IllegalArgumentException 을 던져 앱이 죽는다.
    private var lastTimestampMs = 0L

    var delegateName: String = "-"
        private set

    /** 손 모델이 켜져 있는지 (화면 표시용) */
    val handsEnabled: Boolean get() = handLandmarker != null

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
        if (enableHands) {
            val hasAsset = runCatching { context.assets.list("")?.contains(HAND_MODEL_ASSET) == true }.getOrDefault(false)
            if (!hasAsset) {
                onError("손 모델 없음(assets/$HAND_MODEL_ASSET) → 포즈 손 관절로 대신")
            } else {
                handLandmarker = runCatching { createHands(context, Delegate.GPU) }
                    .recoverCatching { createHands(context, Delegate.CPU) }
                    .onFailure { onError("손 모델 로드 실패: ${it.message}") }
                    .getOrNull()
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
                // 같은 시점(±100ms)의 손 결과만 붙인다. 오래된 손 결과는 버린다.
                val hands = latestHands.get()?.takeIf { kotlin.math.abs(it.timestampMs() - result.timestampMs()) <= 100 }
                onResult(ResultBundle(result, input.width, input.height, inference, hands))
            }
            .setErrorListener { e -> onError(e.message ?: "pose error") }
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    private fun createHands(context: Context, delegate: Delegate): HandLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath(HAND_MODEL_ASSET)
            .setDelegate(delegate)
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result: HandLandmarkerResult, _: MPImage -> latestHands.set(result) }
            .setErrorListener { e -> onError(e.message ?: "hand error") }
            .build()
        return HandLandmarker.createFromOptions(context, options)
    }

    /**
     * CameraX 프레임 1장을 비동기 검출 큐에 넣는다. RGBA_8888 포맷 ImageProxy 필요.
     *
     * ⚠ 이 메서드는 ImageAnalysis 의 분석 스레드에서 돌아간다.
     *   여기서 예외가 새어 나가면 잡아줄 곳이 없어서 앱이 그대로 종료된다.
     *   그래서 프레임 하나가 잘못돼도 그 프레임만 버리고 넘어가도록 전체를 감쌌다.
     */
    fun detect(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val landmarker = poseLandmarker ?: run { imageProxy.close(); return }

        try {
            // ⚠ 회전/크기 정보는 close() 전에 미리 읽어 둔다.
            //   닫힌 ImageProxy 에서 읽으면 "Image is already closed" 로 죽는다.
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val width = imageProxy.width
            val height = imageProxy.height

            // ⚠ 직접 Bitmap 을 만들어 copyPixelsFromBuffer 하면 안 된다.
            //   ImageAnalysis 버퍼는 rowStride 가 width*4 보다 클 수 있어서(패딩)
            //   "Buffer not large enough for pixels" 로 죽는다.
            //   특히 Preview 를 같이 바인딩하면 해상도가 바뀌면서 패딩이 생기기 쉽다.
            //   toBitmap() 은 stride 를 알아서 처리해 준다. (camera-core 1.3+)
            val bitmap = imageProxy.use { it.toBitmap() }

            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
                if (isFrontCamera) postScale(-1f, 1f, width / 2f, height / 2f)
            }
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            // 같은 ms 에 두 프레임이 들어오면 뒤엣것을 1ms 밀어 단조 증가를 보장한다.
            val now = SystemClock.uptimeMillis()
            val timestamp = if (now <= lastTimestampMs) lastTimestampMs + 1 else now
            lastTimestampMs = timestamp

            val image = BitmapImageBuilder(rotated).build()
            // 손을 먼저 넣어야 포즈 결과가 나올 때 같은 프레임의 손 결과가 있을 가능성이 높다
            handLandmarker?.let { runCatching { it.detectAsync(image, timestamp) } }
            landmarker.detectAsync(image, timestamp)
        } catch (t: Throwable) {
            // 프레임 하나 실패로 앱이 죽지 않도록 삼킨다. 다음 프레임에서 다시 시도된다.
            onError("프레임 처리 실패: ${t.message}")
        } finally {
            // use 로 이미 닫혔으면 두 번 닫아도 무해하다.
            runCatching { imageProxy.close() }
        }
    }

    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
        handLandmarker?.close()
        handLandmarker = null
        latestHands.set(null)
        lastTimestampMs = 0L
    }

    companion object {
        const val MODEL_ASSET = "pose_landmarker_full.task"

        /** 손 모델 파일. 없으면 손 모델 없이 돌아간다.
         *  받는 곳: https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task */
        const val HAND_MODEL_ASSET = "hand_landmarker.task"
    }
}
