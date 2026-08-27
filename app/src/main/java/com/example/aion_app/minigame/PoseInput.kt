package com.example.aion_app.minigame

/** 화면 픽셀 좌표 (x 오른쪽 +, y 아래쪽 +) */
data class Vec2(val x: Float, val y: Float)

/**
 * 매 프레임 게임 엔진에 들어오는 입력. 화면 픽셀 좌표.
 *
 * [viewWidth]/[viewHeight] 는 게임 요소를 화면 안에 가둬 두는 데 쓴다.
 * 0 이면 제한 없이 몸 기준 좌표 그대로 놓는다(단위 테스트용).
 */
data class PoseInput(
    val leftShoulder: Vec2? = null,
    val rightShoulder: Vec2? = null,
    val leftWrist: Vec2? = null,
    val rightWrist: Vec2? = null,
    val viewWidth: Float = 0f,
    val viewHeight: Float = 0f,
)
