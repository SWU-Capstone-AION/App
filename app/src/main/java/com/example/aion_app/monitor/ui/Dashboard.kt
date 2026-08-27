package com.example.aion_app.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.monitor.pose.StereotypyDetector
import com.example.aion_app.monitor.ui.theme.Orbitron
import com.example.aion_app.monitor.ui.theme.ShareTechMono

private val Blue = Color(0xFF34C6FF)
private val Amber = Color(0xFFFFB020)
private val Red = Color(0xFFFF5A3C)
private val Ink = Color(0xFFCFE6FF)
private val InkDim = Color(0xFF7FA6CF)
private val PanelBg = Color(0xDB050B12)
private val BarBg = Color(0xFF0A1622)
private val Line = Color(0x4D34C6FF)
private val Mono = ShareTechMono
private val PanelShape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
private val ButtonShape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp)

private val LeftArmColor = Color(0xFF34C6FF)
private val RightArmColor = Color(0xFF3B6DFF)
private val HeadColor = Color(0xFF16D0C0)
private val BodyColor = Color(0xFFB47CFF)

private typealias Part = StereotypyDetector.Part

private fun durOf(state: StereotypyDetector.State?, part: Part) = state?.parts?.get(part)?.duration ?: 0.0
private fun alarmOf(state: StereotypyDetector.State?, part: Part) = state?.parts?.get(part)?.alarm ?: false
private fun armOf(state: StereotypyDetector.State?, part: Part) = state?.parts?.get(part)?.analysis
private fun totalOf(state: StereotypyDetector.State?, part: Part) = state?.activeTotals?.get(part) ?: 0.0
private fun anyActive(state: StereotypyDetector.State?) = state?.parts?.values?.any { it.analysis.active } == true

/** 전체 HUD 대시보드 (카메라/오버레이 위에 겹치는 반투명 레이어). */
@Composable
fun Dashboard(
    state: StereotypyDetector.State?,
    fps: Int,
    inferenceMs: Long?,
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(state, fps, inferenceMs, running, onStart, onStop, onBack)
        Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
            // 좌측 클러스터
            Column(
                modifier = Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TrendPanel(state)
                SessionPanel(state)
                AnalystPanel(state)
            }
            Spacer(modifier = Modifier.weight(1f))
            // 우측 클러스터
            Column(
                modifier = Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ArmGaugePanel(state)
                OperationPanel(state)
            }
        }
    }
}

@Composable
private fun TopBar(
    state: StereotypyDetector.State?,
    fps: Int,
    inferenceMs: Long?,
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val (statusText, statusColor) = when {
        !running -> "대기 중" to InkDim
        state?.anyAlarm == true -> "알람 발생" to Red
        anyActive(state) -> "활성 동작 감지" to Amber
        else -> "모니터링 중" to Blue
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xD1030810))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AION", color = Color(0xFFEAF6FF), fontSize = 26.sp, fontFamily = Orbitron, fontWeight = FontWeight.Black)
            Text("V23.1", color = InkDim, fontSize = 13.sp, fontFamily = Mono)
            Text("STEREOTYPY MONITORING", color = Blue, fontSize = 12.sp, fontFamily = Mono)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 아동 화면으로 돌아가기. 하단에 두면 눈에 안 띄어서 정지 버튼 옆에 붙였다.
            if (onBack != null) {
                PillButton("◀ 뒤로", Blue) { onBack() }
            }
            PillButton(if (running) "■ 정지" else "▶ 시작", if (running) Red else Blue) {
                if (running) onStop() else onStart()
            }
            Text(statusText, color = statusColor, fontFamily = Mono, fontSize = 14.sp)
            Text("│", color = Color(0xFF1C2F4A))
            Text(
                if (inferenceMs != null) "$fps fps · ${inferenceMs}ms" else "$fps fps",
                color = Blue, fontFamily = Mono, fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun PillButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(ButtonShape)
            .border(1.dp, color, ButtonShape)
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(text, color = color, fontFamily = Mono, fontSize = 14.sp)
    }
}

@Composable
private fun Panel(title: String, tag: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(PanelBg)
            .border(1.dp, Line, PanelShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("▸ $title", color = Color(0xFFEAF6FF), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(tag, color = InkDim, fontSize = 11.sp, fontFamily = Mono)
        }
        content()
    }
}

@Composable
private fun TrendPanel(state: StereotypyDetector.State?) {
    Panel("이상 행동 빈도 추이", "ANOMALY_TREND") {
        TimelineChart(
            timeline = state?.timeline ?: emptyList(),
            modifier = Modifier.fillMaxWidth().height(78.dp).background(Color(0x80010409)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Legend(LeftArmColor, "좌팔")
            Legend(RightArmColor, "우팔")
            Legend(HeadColor, "머리")
            Legend(BodyColor, "몸통")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.width(14.dp).height(3.dp).background(color))
        Text(label, color = InkDim, fontFamily = Mono, fontSize = 12.sp)
    }
}

@Composable
private fun SessionPanel(state: StereotypyDetector.State?) {
    Panel("세션 요약", "SESSION_SUMMARY") {
        InfoRow("세션 모니터링 시간", "SESSION_TIME", fmtHMS(state?.elapsedSec ?: 0.0), Blue)
        InfoRow("좌측 팔 활성 누계", "LEFT_ARM", fmtHMS(totalOf(state, Part.LEFT_ARM)), LeftArmColor)
        InfoRow("우측 팔 활성 누계", "RIGHT_ARM", fmtHMS(totalOf(state, Part.RIGHT_ARM)), RightArmColor)
        InfoRow("머리 활성 누계", "HEAD", fmtHMS(totalOf(state, Part.HEAD)), HeadColor)
        InfoRow("몸통 활성 누계", "BODY", fmtHMS(totalOf(state, Part.BODY)), BodyColor)
        InfoRow("경고 발생 수", "ALARM_COUNT", "${state?.alarmCount ?: 0}", Amber)
        InfoRow("최대 지속 활성 시간", "PEAK_STREAK", fmtHMS(state?.maxStreak ?: 0.0), Blue)
    }
}

@Composable
private fun InfoRow(label: String, tag: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, color = Ink, fontSize = 14.sp)
            Text(tag, color = InkDim, fontSize = 9.sp, fontFamily = Mono)
        }
        Text(value, color = valueColor, fontFamily = Mono, fontSize = 17.sp)
    }
}

@Composable
private fun ArmGaugePanel(state: StereotypyDetector.State?) {
    Panel("활성 게이지", "ACTIVITY_GAUGE") {
        Gauge("좌측 팔", "L.ARM", durOf(state, Part.LEFT_ARM), alarmOf(state, Part.LEFT_ARM))
        Gauge("우측 팔", "R.ARM", durOf(state, Part.RIGHT_ARM), alarmOf(state, Part.RIGHT_ARM))
        Gauge("머리", "HEAD", durOf(state, Part.HEAD), alarmOf(state, Part.HEAD))
        Gauge("몸통", "BODY", durOf(state, Part.BODY), alarmOf(state, Part.BODY))
    }
}

@Composable
private fun Gauge(label: String, tag: String, dur: Double, alarm: Boolean) {
    val frac = (dur / StereotypyDetector.GLOBAL_WINDOW_S).coerceIn(0.0, 1.0).toFloat()
    val color = when {
        alarm -> Red
        dur > StereotypyDetector.DURATION_THRESHOLD * 0.7 -> Amber
        else -> Blue
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Ink, fontSize = 15.sp)
            Text(tag, color = color, fontFamily = Mono, fontSize = 12.sp)
        }
        Box(modifier = Modifier.fillMaxWidth().height(22.dp).background(BarBg).border(1.dp, Line)) {
            Box(modifier = Modifier.fillMaxWidth(frac).height(22.dp).background(color))
            Text(
                "%.1fs / %.1fs".format(dur, StereotypyDetector.GLOBAL_WINDOW_S),
                color = Color(0xFFEAF6FF), fontFamily = Mono, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun OperationPanel(state: StereotypyDetector.State?) {
    val (actText, actColor) = when {
        state?.anyAlarm == true -> "상동 행동 탐지됨" to Red
        anyActive(state) -> "반복 동작 관찰" to Amber
        else -> "정상 범위" to Blue
    }
    Panel("운영 기록", "OPERATION_LOG") {
        WristGraph(
            wristTrace = state?.wristTrace ?: emptyList(),
            modifier = Modifier.fillMaxWidth().height(78.dp).background(Color(0x80010409)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(Color(0xFF34C6FF), "왼팔 (L)")
            Legend(Color(0xFF3B6DFF), "오른팔 (R)")
        }
        InfoRow("포즈", "POSE", state?.poseText ?: "자세 분석 중…", Ink)
        InfoRow("활동", "ACTIVITY", actText, actColor)
        InfoRow("바이오-피드백 · 심박수", "HEART_RATE", "${state?.heartRate ?: 75} /bpm", Blue)
    }
}

@Composable
private fun AnalystPanel(state: StereotypyDetector.State?) {
    Panel("정밀 분석", "ANALYST_VIEW") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CriteriaBlock("좌팔", armOf(state, Part.LEFT_ARM), Modifier.weight(1f))
            CriteriaBlock("우팔", armOf(state, Part.RIGHT_ARM), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CriteriaBlock("머리", armOf(state, Part.HEAD), Modifier.weight(1f))
            CriteriaBlock("몸통", armOf(state, Part.BODY), Modifier.weight(1f))
        }
    }
}

@Composable
private fun CriteriaBlock(title: String, arm: StereotypyDetector.Arm?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = InkDim, fontFamily = Mono, fontSize = 10.sp)
        CriteriaRow("박자", arm?.intervalOk, arm?.let { if (it.interval > 0) "%.2f".format(it.interval) else "—" })
        CriteriaRow("중심편차", arm?.centerDevOk, arm?.let { "%.3f".format(it.centerDev) })
        CriteriaRow("진폭", arm?.amplitudeOk, arm?.let { "%.0f".format(it.amplitude) })
    }
}

@Composable
private fun CriteriaRow(name: String, ok: Boolean?, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val mark = if (ok == null) "·" else if (ok) "✓" else "✗"
            val markColor = if (ok == null) InkDim else if (ok) Blue else Red
            Text(mark, color = markColor, fontFamily = Mono, fontSize = 12.sp)
            Text(name, color = Ink, fontSize = 12.sp)
        }
        Text(value ?: "—", color = InkDim, fontFamily = Mono, fontSize = 12.sp)
    }
}

private fun fmtHMS(sec: Double): String {
    val s = sec.toInt().coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
