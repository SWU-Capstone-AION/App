package com.example.aion_app.minigame.weed

import com.example.aion_app.monitor.pose.PoseLandmarkerHelper
import kotlin.math.max

/**
 * 기존 PoseLandmarkerHelper.ResultBundle을 게임이 쓰는 화면 좌표로 변환한다.
 * 헬퍼는 손대지 않는다 — 여기서만 변환한다.
 *
 * 헬퍼가 이미 전면 카메라 미러링을 처리하고 있으므로 여기서 다시 뒤집지 않는다.
 */
object PoseAdapter {

    private const val LEFT_SHOULDER = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_WRIST = 15
    private const val RIGHT_WRIST = 16

    private const val VISIBILITY_THRESHOLD = 0.5f

    /**
     * PreviewView가 FILL_CENTER로 영상을 잘라 표시하기 때문에,
     * 정규화 좌표에 그냥 화면 크기를 곱하면 어긋난다.
     * 크롭된 영역 기준으로 변환해야 손목 위치가 실제 손과 맞는다.
     */
    fun toGameInput(
        bundle: PoseLandmarkerHelper.ResultBundle,
        viewWidth: Float,
        viewHeight: Float,
    ): PoseInput {
        val imageW = bundle.inputImageWidth
        val imageH = bundle.inputImageHeight
        if (imageW <= 0 || imageH <= 0 || viewWidth <= 0f || viewHeight <= 0f) {
            return PoseInput()
        }

        val landmarks = bundle.result.landmarks().firstOrNull() ?: return PoseInput()

        val scale = max(viewWidth / imageW, viewHeight / imageH)
        val offsetX = (viewWidth - imageW * scale) / 2f
        val offsetY = (viewHeight - imageH * scale) / 2f

        fun at(index: Int): Vec2? {
            val point = landmarks.getOrNull(index) ?: return null
            if (point.visibility().orElse(0f) < VISIBILITY_THRESHOLD) return null
            return Vec2(
                point.x() * imageW * scale + offsetX,
                point.y() * imageH * scale + offsetY,
            )
        }

        return PoseInput(
            leftShoulder = at(LEFT_SHOULDER),
            rightShoulder = at(RIGHT_SHOULDER),
            leftWrist = at(LEFT_WRIST),
            rightWrist = at(RIGHT_WRIST),
            viewWidth = viewWidth,
            viewHeight = viewHeight,
        )
    }
}
