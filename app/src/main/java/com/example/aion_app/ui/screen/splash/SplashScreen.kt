package com.example.aion_app.ui.screen.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aion_app.R
import com.example.aion_app.ui.theme.White
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinish: () -> Unit = {}) {
    // 1.5초 후 다음 화면으로 이동
    LaunchedEffect(Unit) {
        delay(1500)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 무한대 심볼 로고
            Image(
                painter = painterResource(R.drawable.logo_symbol),
                contentDescription = "AION 로고",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(140.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            // AION 워드마크
            Image(
                painter = painterResource(R.drawable.logo_text),
                contentDescription = "AION",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(160.dp)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SplashScreenPreview() {
    MaterialTheme {
        SplashScreen()
    }
}