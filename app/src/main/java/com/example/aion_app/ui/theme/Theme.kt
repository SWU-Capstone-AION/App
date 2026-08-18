package com.example.aion_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    background = White,
    surface = White,
    onPrimary = White,
    onBackground = TextPrimary
)

@Composable
fun AionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AionTypography
    ) {
        // 핵심: 화면들의 Text 대부분이 fontFamily 를 직접 지정하지 않으므로,
        // 기본 TextStyle 의 fontFamily 를 Pretendard 로 깔아주면 앱 전체에 적용된다.
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Pretendard),
            content = content
        )
    }
}
