package com.example.aion_app.ui.screen.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.Normal
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
            Text(
                text = "AION",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Normal
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "아이온",
                fontSize = 15.sp,
                color = GrayText
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
