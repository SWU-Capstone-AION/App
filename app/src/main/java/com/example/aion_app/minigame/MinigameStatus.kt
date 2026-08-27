package com.example.aion_app.minigame

/** 오래 진전이 없을 때 띄우는 안내 문구 단계 */
enum class Nudge { NONE, KEEP_GOING, HOW_TO_QUIT }

/**
 * 미니게임이 매 프레임 내놓는 공통 상태.
 * [PoseGameHost] 가 이것만 보고 진행바·안내 문구·완료 화면을 그린다.
 * 게임 고유의 내용(잡초 목록, 칠판 얼룩 등)은 각 게임의 스냅샷이 따로 들고 있다.
 */
interface MinigameStatus {
    /** 0f ~ 1f */
    val progress: Float
    /** 어깨가 잡히는가. 안 잡히면 아이가 자리에 없는 것으로 본다. */
    val poseVisible: Boolean
    val wrists: List<Vec2>
    val nudge: Nudge
    val cleared: Boolean
}
