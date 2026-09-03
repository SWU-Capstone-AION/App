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

// ============================================================
// 스플래시
// ============================================================
// 배경과 로고가 한 장에 들어 있는 에셋이라 이미지 하나로 화면 전체를 그린다.
// 로고 크기·위치를 코드로 조절할 수 없으므로 조정이 필요하면 에셋을 다시 받아야 한다.
//
// 아동용(태블릿)과 교사용(폰)은 에셋 비율 자체가 다르다.
//   교사용 splash_background_logo        512 x 1140  (세로)
//   아동용 splash_background_kids_logo  1860 x 1164  (가로, 시안 930x582 의 2배)
// 폰 에셋을 태블릿에 Crop 으로 깔면 좌우가 잘려 로고가 화면 밖으로 나간다.
// 그래서 진입 기기에 맞는 에셋을 골라 쓴다.
//
// isKids 판별은 AionNavHost 의 isTabletDevice() 결과를 그대로 받는다.
// (스플래시가 스스로 판단하면 다음 화면 분기 기준과 갈릴 수 있다)
@Composable
fun SplashScreen(
    isKids: Boolean = false,
    onFinish: () -> Unit = {}
) {
    // 1.5초 후 다음 화면으로 이동
    LaunchedEffect(Unit) {
        delay(1500)
        onFinish()
    }

    // Crop 이라 기기 비율이 달라도 여백 없이 꽉 차고, 대신 가장자리가 조금 잘릴 수 있다.
    // 아동용 에셋은 시안과 같은 1.598:1 이라 태블릿에서 잘림이 거의 없다.
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(
                if (isKids) R.drawable.splash_background_kids_logo
                else R.drawable.splash_background_logo
            ),            contentDescription = "AION",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "교사용 (폰)")
@Composable
private fun SplashScreenPreview() {
    AionTheme { SplashScreen() }
}

@Preview(showBackground = true, device = "spec:width=1204dp,height=753dp,dpi=340", name = "아동용 (태블릿)")
@Composable
private fun SplashScreenKidsPreview() {
    AionTheme { SplashScreen(isKids = true) }
}