package com.example.aion_app.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.monitor.pose.StereotypyDetector

private val Blue = Color(0xFF34C6FF)
private val Amber = Color(0xFFFFB020)
private val Red = Color(0xFFFF5A3C)
private val Panel = Color(0xCC02070E)
private val BarBg = Color(0xFF0A1622)

/** 4단계 검증용 최소 판독 UI: 좌/우 누적활성 게이지 + 세션 요약. */
@Composable
fun DetectorReadout(state: StereotypyDetector.State?, modifier: Modifier = Modifier) {
    if (state == null) return
    Column(
        modifier = modifier
            .background(Panel)
            .padding(14.dp)
            .width(280.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArmBar("좌측 팔", state.leftDuration, state.left.active, state.leftAlarm)
        ArmBar("우측 팔", state.rightDuration, state.right.active, state.rightAlarm)
        Text(
            text = "경고 ${state.alarmCount}회 · 세션 ${fmtHMS(state.elapsedSec)}",
            color = Blue,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ArmBar(label: String, dur: Double, active: Boolean, alarm: Boolean) {
    val frac = (dur / StereotypyDetector.GLOBAL_WINDOW_S).coerceIn(0.0, 1.0).toFloat()
    val color = when {
        alarm -> Red
        dur > StereotypyDetector.DURATION_THRESHOLD * 0.7 -> Amber
        else -> Blue
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = label + if (active) " ●" else "",
                color = if (active) color else Color(0xFFCDE4FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "%.1fs / %.1fs".format(dur, StereotypyDetector.GLOBAL_WINDOW_S),
                color = Color(0xFFCDE4FF),
                fontSize = 13.sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(BarBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(22.dp)
                    .background(color)
            )
        }
    }
}

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

private fun fmtHMS(sec: Double): String {
    val s = sec.toInt().coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
