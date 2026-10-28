package com.example.aion_app.minigame

/** 화면 픽셀 좌표 (x 오른쪽 +, y 아래쪽 +) */
data class Vec2(val x: Float, val y: Float)

/**
 * 손 하나의 상태.
 *
 * [pos] 는 손목이 아니라 **손바닥 한가운데**다. 아이는 손바닥으로 잡는다고 느끼는데
 * 손목을 기준으로 잡으면 "손은 닿았는데 안 잡힌다"가 된다.
 *
 * [grip] 은 0(편 손) ~ 1(쥔 주먹). 손가락 관절이 손목 쪽으로 모일수록 1에 가까워진다.
 * 매 프레임 바뀌므로 화면 표시와 잡기 판정에 그대로 쓸 수 있다.
 */
data class HandPose(
    val pos: Vec2,
    val grip: Float = 0f,
) {
    /** 잡는 동작으로 볼 만큼 쥐었는가 */
    val closed: Boolean get() = grip >= GRIP_CLOSED

    companion object {
        /** 이 이상 쥐면 '잡았다'로 본다 */
        const val GRIP_CLOSED = 0.55f

        /** 이 아래로 펴면 '놓았다'로 본다. 사이 구간은 직전 상태를 유지해 깜빡임을 막는다. */
        const val GRIP_OPEN = 0.35f
    }
}

/**
 * 매 프레임 게임 엔진에 들어오는 입력. 화면 픽셀 좌표.
 *
 * [viewWidth]/[viewHeight] 는 게임 요소를 화면 안에 가둬 두는 데 쓴다.
 * 0 이면 제한 없이 몸 기준 좌표 그대로 놓는다(단위 테스트용).
 */
data class PoseInput(
    val leftShoulder: Vec2? = null,
    val rightShoulder: Vec2? = null,
    val leftHand: HandPose? = null,
    val rightHand: HandPose? = null,
    val viewWidth: Float = 0f,
    val viewHeight: Float = 0f,
) {
    /** 잡힌 손만 모아서 준다. 한 손만 들었으면 하나. */
    val hands: List<HandPose> get() = listOfNotNull(leftHand, rightHand)
}
