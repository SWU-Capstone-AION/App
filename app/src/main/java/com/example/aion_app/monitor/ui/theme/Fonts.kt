@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.example.aion_app.monitor.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.aion_app.R

/** 디스플레이 폰트 (브랜드/로고). 가변폰트라 굵기별 variation 지정. */
val Orbitron = FontFamily(
    Font(
        R.font.orbitron,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        R.font.orbitron,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(FontVariation.weight(900)),
    ),
)

/** 모노스페이스 폰트 (태그/수치/HUD 텍스트). */
val ShareTechMono = FontFamily(Font(R.font.share_tech_mono))
