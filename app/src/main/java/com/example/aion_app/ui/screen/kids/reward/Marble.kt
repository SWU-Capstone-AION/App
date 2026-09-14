package com.example.aion_app.ui.kids.reward

import androidx.annotation.DrawableRes
import com.example.aion_app.R

/**
 * 구슬 5색.
 * imageRes = drawable 이미지, label = 아이에게 보여줄 이름
 */
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
        /** 박스를 열 때 5색 중 하나를 랜덤으로 뽑는다 */
        fun random(): Marble = values().random()

        /** 저장된 글자("GREEN")를 다시 구슬로 바꿀 때 사용 */
        fun fromName(name: String): Marble? = values().find { it.name == name }
    }
}

/**
 * 구슬을 어디서 받았는지 구분하는 값.
 * 나중에 "이번 주 잡초 뽑기 3번" 같은 통계를 낼 때 쓸 수 있다.
 */
enum class RewardSource {
    BREATHING,    // 호흡 가이드
    WEED,         // 잡초 뽑기
    BLACKBOARD    // 칠판 닦기
}