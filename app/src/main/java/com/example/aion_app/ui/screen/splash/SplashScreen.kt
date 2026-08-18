package com.example.aion_app.ui.screen.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.aion_app.R
import com.example.aion_app.ui.theme.AionTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinish: () -> Unit = {}) {
    // 1.5초 후 다음 화면으로 이동
    LaunchedEffect(Unit) {
        delay(1500)
        onFinish()
    }

    // 배경과 로고가 한 장에 들어 있는 에셋이라 이미지 하나로 화면 전체를 그린다.
    // Crop 이라 기기 비율이 달라도 여백 없이 꽉 차고, 대신 가장자리가 조금 잘릴 수 있다.
    // 로고 크기·위치를 코드로 조절할 수 없으므로 조정이 필요하면 에셋을 다시 받아야 한다.
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.splash_background_logo),
            contentDescription = "AION",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SplashScreenPreview() {
    AionTheme {
        SplashScreen()
    }
}