package com.example.aion_app.minigame

/** 오래 진전이 없을 때 띄우는 안내 문구 단계 */
enum class Nudge { NONE, KEEP_GOING, HOW_TO_QUIT }

/** 안내 문구 시점. 1분: 조금만 더, 2분: 그만두는 법 */
const val NUDGE_KEEP_GOING_MS = 60_000L
const val NUDGE_HOW_TO_QUIT_MS = 120_000L

/**
 * 이 시간 동안 아무 동작이 없으면 게임을 끝내고 홈으로 돌아간다.
 * 아이가 자리를 떠났거나 하기 싫은데 끝내는 법을 모르는 경우다. 안내 없이 바로 돌아간다.
 * 그만두는 법 안내(2분)를 먼저 1분 동안 보여준 뒤에 끝나도록 둘을 떨어뜨려 두었다.
 */
const val GAME_IDLE_EXIT_MS = 180_000L

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
    /** 화면에 보이는 손. 게임마다 다른 그림(손바닥·지우개)으로 그린다. */
    val hands: List<HandView>
    val nudge: Nudge
    val cleared: Boolean
    /**
     * 마지막으로 게임 동작(잡초 잡기·뽑기, 칠판 지우기)을 한 시각(ms, System.currentTimeMillis 기준). 없으면 0.
     * 3분 무동작 종료에 쓴다. 엔진의 무반응 시간은 아이가 화면에 안 보이면 멈추기 때문에(안내 문구용),
     * 종료 판단은 이 시각부터 실제 시계로 잰다. 그래야 아이가 자리를 떠나도 3분 뒤 홈으로 돌아간다.
     */
    val lastActiveAt: Long
}

/**
 * 화면에 그릴 손 하나.
 *
 * [grip] 과 [holding] 이 매 프레임 바뀌므로, 게임은 이것만 보고 손 그림을
 * 편 손 → 쥔 손으로 실시간으로 바꿀 수 있다.
 *
 * @param pos 손바닥 중심(픽셀)
 * @param size 손 그림 크기(픽셀). 어깨폭 기준이라 아이가 가까이 오면 함께 커진다
 * @param grip 0 = 편 손, 1 = 쥔 주먹
 * @param holding 지금 무언가를 잡고 있는가(잡초를 쥔 상태)
 */
data class HandView(
    val pos: Vec2,
    val size: Float,
    val grip: Float = 0f,
    val holding: Boolean = false,
)
