package com.example.aion_app.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Red = Color(0xFFFF5A3C)

/** 상단 알람 배너 */
@Composable
fun AlarmBanner(show: Boolean, modifier: Modifier = Modifier) {
    if (!show) return
    Box(
        modifier = modifier
            .background(Red)
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Text(
            text = "⚠  상동행동입니다. 안정을 취해주세요.",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
