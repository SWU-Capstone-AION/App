package com.example.aion_app.minigame

import com.example.aion_app.monitor.pose.PoseLandmarkerHelper
import kotlin.math.hypot
import kotlin.math.max

/**
 * 기존 PoseLandmarkerHelper.ResultBundle 을 게임이 쓰는 화면 좌표로 변환한다.
 * 헬퍼는 손대지 않는다 — 여기서만 변환한다.
 *
 * 헬퍼가 이미 전면 카메라 미러링을 처리하고 있으므로 여기서 다시 뒤집지 않는다.
 *
 * 손은 손목이 아니라 **손바닥 중심**으로 준다. 포즈 모델이 주는 손가락 관절
 * (검지·새끼·엄지)과 손목 사이를 보간하면 손바닥 한가운데가 나온다.
 * 같은 점들로 **쥔 정도**도 함께 계산한다.
 */
object PoseAdapter {

    private const val LEFT_SHOULDER = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_WRIST = 15
    private const val RIGHT_WRIST = 16
    private const val LEFT_PINKY = 17
    private const val RIGHT_PINKY = 18
    private const val LEFT_INDEX = 19
    private const val RIGHT_INDEX = 20
    private const val LEFT_THUMB = 21
    private const val RIGHT_THUMB = 22

    private const val VISIBILITY_THRESHOLD = 0.5f

    /** 손가락 관절은 어깨·손목보다 흐릿하게 잡힌다. 같은 기준을 쓰면 손 모양을 거의 못 읽는다. */
    private const val FINGER_VISIBILITY_THRESHOLD = 0.3f

    /**
     * 손목에서 손가락 관절 쪽으로 이만큼 간 지점을 손바닥 중심으로 본다.
     * 0 이면 손목, 1 이면 손가락 관절. 0.6 이면 손바닥 한가운데쯤이다.
     */
    private const val PALM_RATIO = 0.6f

    /**
     * 손을 폈을 때와 쥐었을 때의 손 크기(어깨폭 S 배수).
     * 주먹을 쥐면 손가락 관절이 손목 쪽으로 모여 이 값이 줄어든다.
     * **기기에서 손 모양이 잘 안 잡히면 가장 먼저 만질 값이다.**
     */
    private const val OPEN_EXTENT_IN_S = 0.26f
    private const val CLOSED_EXTENT_IN_S = 0.15f

    /**
     * PreviewView 가 FILL_CENTER 로 영상을 잘라 표시하기 때문에,
     * 정규화 좌표에 그냥 화면 크기를 곱하면 어긋난다.
     * 크롭된 영역 기준으로 변환해야 손 위치가 실제 손과 맞는다.
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

        val landmarks = bundle.result.landmarks().firstOrNull()
            ?: return PoseInput(viewWidth = viewWidth, viewHeight = viewHeight)

        val scale = max(viewWidth / imageW, viewHeight / imageH)
        val offsetX = (viewWidth - imageW * scale) / 2f
        val offsetY = (viewHeight - imageH * scale) / 2f

        fun at(index: Int, minVisibility: Float = VISIBILITY_THRESHOLD): Vec2? {
            val point = landmarks.getOrNull(index) ?: return null
            if (point.visibility().orElse(0f) < minVisibility) return null
            return Vec2(
                point.x() * imageW * scale + offsetX,
                point.y() * imageH * scale + offsetY,
            )
        }

        fun finger(index: Int): Vec2? = at(index, FINGER_VISIBILITY_THRESHOLD)

        val leftShoulder = at(LEFT_SHOULDER)
        val rightShoulder = at(RIGHT_SHOULDER)

        // 손 크기를 어깨폭으로 정규화해야 아이가 가까이 오든 멀어지든 같은 값이 나온다.
        val shoulderWidth = if (leftShoulder != null && rightShoulder != null) {
            hypot(leftShoulder.x - rightShoulder.x, leftShoulder.y - rightShoulder.y)
        } else {
            0f
        }

        return PoseInput(
            leftShoulder = leftShoulder,
            rightShoulder = rightShoulder,
            leftHand = hand(
                wrist = at(LEFT_WRIST),
                index = finger(LEFT_INDEX),
                pinky = finger(LEFT_PINKY),
                thumb = finger(LEFT_THUMB),
                shoulderWidth = shoulderWidth,
            ),
            rightHand = hand(
                wrist = at(RIGHT_WRIST),
                index = finger(RIGHT_INDEX),
                pinky = finger(RIGHT_PINKY),
                thumb = finger(RIGHT_THUMB),
                shoulderWidth = shoulderWidth,
            ),
            viewWidth = viewWidth,
            viewHeight = viewHeight,
        )
    }

    /**
     * 손목과 손가락 관절로 손바닥 중심과 쥔 정도를 만든다.
     * 손가락이 안 잡히면 손목을 그대로 쓰고 쥔 정도는 0(편 손)으로 둔다 —
     * 모르는 것을 '쥐었다'로 넘기면 아이가 의도하지 않은 잡기가 일어난다.
     */
    private fun hand(
        wrist: Vec2?,
        index: Vec2?,
        pinky: Vec2?,
        thumb: Vec2?,
        shoulderWidth: Float,
    ): HandPose? {
        if (wrist == null) return null

        val knuckles = listOfNotNull(index, pinky)
        if (knuckles.isEmpty() || shoulderWidth <= 1f) return HandPose(wrist, grip = 0f)

        val knuckleMid = Vec2(
            knuckles.map { it.x }.average().toFloat(),
            knuckles.map { it.y }.average().toFloat(),
        )
        val palm = Vec2(
            wrist.x + (knuckleMid.x - wrist.x) * PALM_RATIO,
            wrist.y + (knuckleMid.y - wrist.y) * PALM_RATIO,
        )

        return HandPose(palm, grip = grip(wrist, index, pinky, thumb, shoulderWidth))
    }

    /**
     * 손이 얼마나 쥐어졌는지 0~1 로 환산한다.
     *
     * 두 가지를 섞는다. 손목에서 손가락까지의 **길이**(주먹을 쥐면 짧아진다)와
     * 검지~새끼 사이의 **벌어짐**(주먹을 쥐면 모인다). 하나만 보면 손을 비스듬히 들었을 때
     * 쉽게 흔들린다.
     */
    private fun grip(
        wrist: Vec2,
        index: Vec2?,
        pinky: Vec2?,
        thumb: Vec2?,
        shoulderWidth: Float,
    ): Float {
        val fingers = listOfNotNull(index, pinky, thumb)
        if (fingers.isEmpty()) return 0f

        val length = fingers.map { hypot(it.x - wrist.x, it.y - wrist.y) }.average().toFloat()
        val spread = if (index != null && pinky != null) {
            hypot(index.x - pinky.x, index.y - pinky.y)
        } else {
            length    // 한쪽만 보이면 길이로만 판단한다
        }

        val extent = (length * 0.6f + spread * 0.4f) / shoulderWidth
        val openness = (extent - CLOSED_EXTENT_IN_S) / (OPEN_EXTENT_IN_S - CLOSED_EXTENT_IN_S)
        return (1f - openness).coerceIn(0f, 1f)
    }
}
