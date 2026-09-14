package com.example.aion_app.ui.screen.kids.reward

import androidx.annotation.DrawableRes
import com.example.aion_app.R

// ============================================================
// 구슬 (보상)
// ============================================================
// 호흡 가이드 · 잡초 뽑기 · 칠판 지우기를 마치면 상자를 하나 받고,
// 그 상자를 열면 여기 있는 5색 중 하나가 랜덤으로 나온다.
//
// 이미지는 drawable-xxhdpi 에 둔다 (아동용 에셋이 전부 거기에 있음).
//   marble_green / marble_blue / marble_cream / marble_pink / marble_purple
//
// 색을 추가하려면 여기에 한 줄만 늘리면 된다.
// 저장(RewardStore)은 색 이름을 그대로 키로 쓰기 때문에
// **이미 쓰고 있는 이름(GREEN 등)은 바꾸지 말 것** — 바꾸면 그 색 구슬이 0개가 된다.
enum class Marble(
    @DrawableRes val imageRes: Int,
    val label: String
) {
    GREEN(R.drawable.marble_green, "연두 구슬"),
    BLUE(R.drawable.marble_blue, "하늘 구슬"),
    CREAM(R.drawable.marble_cream, "크림 구슬"),
    PINK(R.drawable.marble_pink, "분홍 구슬"),
    PURPLE(R.drawable.marble_purple, "보라 구슬");

    companion object {
        /** 상자를 열 때 5색 중 하나를 랜덤으로 뽑는다 */
        fun random(): Marble = entries.random()
    }
}

// ============================================================
// 구슬을 어디서 받았는지
// ============================================================
// 지금은 호흡 가이드만 하루 상한이 걸려 있어서 구분이 필요하다.
// 나중에 "이번 주 잡초 뽑기 3번" 같은 기록을 낼 때도 쓸 수 있다.
enum class RewardSource {
    BREATHING,    // 호흡 가이드
    WEED,         // 잡초 뽑기
    BLACKBOARD    // 칠판 지우기
}