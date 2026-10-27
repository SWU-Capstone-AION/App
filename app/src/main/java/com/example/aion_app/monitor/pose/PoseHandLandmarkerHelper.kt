package com.example.aion_app.monitor.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.example.aion_app.monitor.rule.FrameInput
import com.example.aion_app.monitor.rule.HandRaw
import com.example.aion_app.monitor.rule.Pt
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * 규칙모델 v3 용 포즈(33) + 손(21 × 2) 검출기.
 *
 * HTML 원본(1024_aion_monitor_v3)과 같게
 *  - 포즈와 손을 '같은 프레임'에서 함께 돌린다 (VIDEO 모드, 분석 스레드에서 차례로 실행)
 *  - 이미지를 좌우 반전하지 않는다. 그래야 MediaPipe 의 왼쪽/오른쪽이 아동 본인의 왼쪽/오른쪽과 같다.
 *    화면에 그릴 때만 반전한다 (HTML 도 CSS scaleX(-1) 로 표시만 뒤집음)
 *
 * 기존 [PoseLandmarkerHelper] 는 미니게임이 그대로 쓰므로 건드리지 않았다.
 *
 * 분석이 밀리면 CameraX(KEEP_ONLY_LATEST)가 알아서 프레임을 버린다.
 * 태블릿에서 fps 가 20 아래로 나오면 [withHands] = false 로 손 모델을 끌 수 있다.
 *
 * fps 올리기: 손 모델은 [handEvery] 프레임에 한 번만 돌리고, 그 사이 프레임은 직전 손 결과를 쓴다.
 *   실기기에서 손 모델을 켜면 17fps → 8~9fps 로 떨어졌다 (포즈보다 손 모델이 무겁다).
 *   handEvery = 1 이면 예전과 같이 매 프레임 돌린다.
 */
class PoseHandLandmarkerHelper(
    context: Context,
    private val withHands: Boolean = true,
    private val handEvery: Int = 2,
    private val onResult: (ResultBundle) -> Unit,
    private val onError: (String) -> Unit,
) {
    data class ResultBundle(
        val pose: PoseLandmarkerResult,
        val hands: HandLandmarkerResult?,
        val inputImageWidth: Int,
        val inputImageHeight: Int,
        val inferenceTimeMs: Long,
        /** SystemClock.uptimeMillis() 기준. 터치 기록과 같은 시계 */
        val timestampMs: Long,
        /** 이 프레임에서 손 모델을 새로 돌렸는지 (false = 직전 손 결과를 다시 씀) */
        val handFresh: Boolean = true,
        /** 실제로 쓰는 가속 (GPU / CPU) */
        val delegate: String = "-",
    ) {
        /** 규칙모델 v3 입력으로 바꾼다. 사람이 안 잡혔으면 null */
        fun toFrameInput(): FrameInput? {
            val lms = pose.landmarks().firstOrNull()?.takeIf { it.size >= 33 } ?: return null
            val world = pose.worldLandmarks().firstOrNull()?.takeIf { it.size >= 33 }
            val handList = ArrayList<HandRaw>()
            if (hands != null) {
                val hn = hands.handedness()
                hands.landmarks().forEachIndexed { i, lm ->
                    if (lm.size < 21) return@forEachIndexed
                    handList.add(
                        HandRaw(
                            norm = lm.map { Pt(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) },
                            world = hands.worldLandmarks().getOrNull(i)?.takeIf { it.size >= 21 }
                                ?.map { Pt(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) },
                            score = hn.getOrNull(i)?.firstOrNull()?.score()?.toDouble() ?: 0.8,
                        )
                    )
                }
            }
            return FrameInput(
                pose = lms.map {
                    Pt(it.x().toDouble(), it.y().toDouble(), it.z().toDouble(), it.visibility().orElse(1f).toDouble())
                },
                poseWorld = world?.map { Pt(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) },
                hands = handList,
                width = inputImageWidth,
                height = inputImageHeight,
            )
        }
    }

    private var poseLandmarker: PoseLandmarker? = null
    private var handLandmarker: HandLandmarker? = null

    // VIDEO 모드도 타임스탬프가 반드시 이전보다 커야 한다 (같은 ms 면 1ms 민다)
    private var lastTimestampMs = 0L

    // 손 모델을 건너뛴 프레임에서 다시 쓸 직전 결과
    private var frameNo = 0L
    private var lastHandResult: HandLandmarkerResult? = null

    var delegateName: String = "-"
        private set

    init {
        try {
            create(context, Delegate.GPU)
            delegateName = "GPU"
        } catch (e: Throwable) {
            close()
            try {
                create(context, Delegate.CPU)
                delegateName = "CPU"
                onError("GPU delegate 실패 → CPU 전환")
            } catch (e2: Throwable) {
                onError("모델 로드 실패: ${e2.message}")
            }
        }
    }

    private fun create(context: Context, delegate: Delegate) {
        poseLandmarker = PoseLandmarker.createFromOptions(
            context,
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(POSE_ASSET).setDelegate(delegate).build())
                .setRunningMode(RunningMode.VIDEO)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
        )
        if (withHands) {
            handLandmarker = HandLandmarker.createFromOptions(
                context,
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(HAND_ASSET).setDelegate(delegate).build())
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(2)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()
            )
        }
    }

    /**
     * CameraX 프레임 1장을 검출한다. RGBA_8888 포맷 ImageProxy 필요.
     * 분석 스레드에서 돌아가므로 예외가 새면 앱이 죽는다 → 전체를 감싼다.
     */
    fun detect(imageProxy: ImageProxy) {
        val pose = poseLandmarker ?: run { imageProxy.close(); return }

        try {
            // 회전 정보는 close() 전에 읽어 둔다
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            // toBitmap() 은 rowStride 패딩을 알아서 처리한다 (camera-core 1.3+)
            val bitmap = imageProxy.use { it.toBitmap() }
            val rotated = if (rotationDegrees == 0) bitmap else Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true
            )

            val now = SystemClock.uptimeMillis()
            val timestamp = if (now <= lastTimestampMs) lastTimestampMs + 1 else now
            lastTimestampMs = timestamp

            val image = BitmapImageBuilder(rotated).build()
            val poseResult = pose.detectForVideo(image, timestamp)
            val hl = handLandmarker
            val fresh = hl != null && (handEvery <= 1 || frameNo % handEvery == 0L || lastHandResult == null)
            val handResult = if (fresh) hl!!.detectForVideo(image, timestamp).also { lastHandResult = it } else lastHandResult
            frameNo++
            onResult(
                ResultBundle(
                    pose = poseResult,
                    hands = handResult,
                    inputImageWidth = rotated.width,
                    inputImageHeight = rotated.height,
                    inferenceTimeMs = SystemClock.uptimeMillis() - now,
                    timestampMs = timestamp,
                    handFresh = fresh,
                    delegate = delegateName,
                )
            )
        } catch (t: Throwable) {
            // 프레임 하나 실패로 앱이 죽지 않도록 삼킨다. 다음 프레임에서 다시 시도된다.
            onError("프레임 처리 실패: ${t.message}")
        } finally {
            runCatching { imageProxy.close() }
        }
    }

    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
        handLandmarker?.close()
        handLandmarker = null
        lastTimestampMs = 0L
        lastHandResult = null
        frameNo = 0L
    }

    companion object {
        const val POSE_ASSET = "pose_landmarker_full.task"
        const val HAND_ASSET = "hand_landmarker.task"
    }
}
