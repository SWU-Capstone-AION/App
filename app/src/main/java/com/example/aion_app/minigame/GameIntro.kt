package com.example.aion_app.minigame

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 게임 시작 전 안내창의 한 단계. 그림 한 장 + 짧은 문구 한두 줄.
 *
 * 세 단계로 쓰는 것을 기준으로 안내창 크기를 잡았다 (예: 손 대기 → 쥐기 → 올리기).
 * 그림은 ui.screen.kids.tutorial.TutorialArt 의 게임 단계 장면을 쓴다.
 */
class GameIntroStep(
    val caption: String,
    val art: @Composable (Modifier) -> Unit,
)
