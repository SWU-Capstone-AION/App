package com.example.aion_app.ui.screen.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.aion_app.ui.component.AionBottomNavBar
import com.example.aion_app.ui.component.AionPrimaryButton
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.theme.BlueLight
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White
import kotlinx.coroutines.launch

@Composable
fun ReportDetailScreen(
    report: StudentReport = sampleStudentReport(),
    onBackClick: () -> Unit = {},
    onTabSelect: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureController = rememberCaptureController()

    var period by remember { mutableStateOf(ReportPeriod.DAILY) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(true) }

    Scaffold(
        topBar = { AionTopBar(title = "상세 리포트", onBackClick = onBackClick) },
        bottomBar = { AionBottomNavBar(selected = "report", onSelect = onTabSelect) },
        containerColor = White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 기간 탭 (일간/주간/월간)
            PeriodTabs(selected = period, onSelect = { period = it })

            Spacer(modifier = Modifier.height(16.dp))

            // ↓↓↓ 이미지로 저장할 영역 (날짜 + 프로필 + 그래프 + 인사이트) ↓↓↓
            Column(modifier = Modifier.capturable(captureController)) {
                // 날짜 네비게이터 (기간별 라벨)
                val dateLabel = when (period) {
                    ReportPeriod.DAILY -> report.daily.dateLabel
                    ReportPeriod.WEEKLY -> report.weekly.dateLabel
                    ReportPeriod.MONTHLY -> report.monthly.monthLabel
                }
                DateNavigator(label = dateLabel)

                Spacer(modifier = Modifier.height(16.dp))

                // 학생 프로필 카드 (공통)
                StudentHeaderCard(student = report.student)

                Spacer(modifier = Modifier.height(24.dp))

                // 기간별 본문
                when (period) {
                    ReportPeriod.DAILY -> DailyContent(report.daily)
                    ReportPeriod.WEEKLY -> WeeklyContent(report.weekly)
                    ReportPeriod.MONTHLY -> MonthlyContent(report.monthly)
                }
            }
            // ↑↑↑ 저장 영역 끝 ↑↑↑

            Spacer(modifier = Modifier.height(24.dp))

            // 이미지 다운로드
            AionPrimaryButton(
                text = "이미지 다운로드",
                onClick = {
                    val bitmap = captureController.toBitmap()
                    if (bitmap == null) {
                        saveSuccess = false
                        showSavedDialog = true
                    } else {
                        scope.launch {
                            saveSuccess = saveBitmapToGallery(
                                context = context,
                                bitmap = bitmap,
                                displayName = "AION_리포트_${report.student.name}_${report.daily.detailDateLabel}"
                            )
                            showSavedDialog = true
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSavedDialog) {
        ReportSavedDialog(
            success = saveSuccess,
            onConfirm = { showSavedDialog = false }
        )
    }
}

// ============================================================
// 기간별 본문
// ============================================================

@Composable
private fun DailyContent(daily: DailyReport) {
    SectionTitle(main = "오늘 요약")
    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("주의 감지", "${daily.cautionCount}", "건", Modifier.weight(1f))
        SummaryCard("위험 감지", "${daily.dangerCount}", "건", Modifier.weight(1f))
    }

    Spacer(modifier = Modifier.height(24.dp))

    SectionTitle(main = "상세 리포트", sub = daily.detailDateLabel)
    Spacer(modifier = Modifier.height(12.dp))
    ChartCard(title = "시간대별 평균 위험 점수") {
        RiskBarChart(risks = daily.hourlyRisks)
    }

    Spacer(modifier = Modifier.height(24.dp))

    InsightSection(insights = daily.insights)
}

@Composable
private fun WeeklyContent(weekly: WeeklyReport) {
    SectionTitle(main = "이번 주 요약")
    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("주의 감지", "${weekly.cautionCount}", "건", Modifier.weight(1f))
        SummaryCard("위험 감지", "${weekly.dangerCount}", "건", Modifier.weight(1f))
        SummaryCard("출석", "${weekly.attendance}", "/${weekly.attendanceTotal}", Modifier.weight(1f))
    }

    Spacer(modifier = Modifier.height(24.dp))

    SectionTitle(main = "상세 리포트", sub = weekly.detailDateLabel)
    Spacer(modifier = Modifier.height(12.dp))
    ChartCard(title = "진할수록 위험점수가 높습니다.") {
        WeeklyHeatmap(cells = weekly.heatCells)
    }

    Spacer(modifier = Modifier.height(24.dp))

    InsightSection(insights = weekly.insights)
}

@Composable
private fun MonthlyContent(monthly: MonthlyReport) {
    SectionTitle(main = "이번 달 요약")
    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("주의 감지", "${monthly.cautionCount}", "건", Modifier.weight(1f))
        SummaryCard("위험 감지", "${monthly.dangerCount}", "건", Modifier.weight(1f))
        SummaryCard("출석", "${monthly.attendance}", "/${monthly.attendanceTotal}", Modifier.weight(1f))
    }

    Spacer(modifier = Modifier.height(24.dp))

    SectionTitle(main = monthly.monthLabel)
    Spacer(modifier = Modifier.height(12.dp))
    MonthCalendar(days = monthly.calendarDays)

    Spacer(modifier = Modifier.height(24.dp))

    SectionTitle(main = "상세 리포트", sub = monthly.detailDateLabel)
    Spacer(modifier = Modifier.height(12.dp))
    ChartCard(title = "시간대별 평균 위험 점수") {
        RiskBarChart(risks = monthly.hourlyRisks)
    }

    Spacer(modifier = Modifier.height(24.dp))

    InsightSection(insights = monthly.insights)
}

// ============================================================
// 상단: 기간 탭 / 날짜 네비 / 프로필 카드
// ============================================================

@Composable
private fun PeriodTabs(
    selected: ReportPeriod,
    onSelect: (ReportPeriod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF1F3F6))
            .padding(4.dp)
    ) {
        ReportPeriod.values().forEach { p ->
            val isSelected = p == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) BluePrimary else Color.Transparent)
                    .clickable { onSelect(p) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = p.label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) White else GrayText
                )
            }
        }
    }
}

@Composable
private fun DateNavigator(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { /* TODO: 이전 기간 */ }) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "이전", tint = GrayText)
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        IconButton(onClick = { /* TODO: 다음 기간 */ }) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "다음", tint = GrayText)
        }
    }
}

@Composable
private fun StudentHeaderCard(student: ReportStudent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE8EDF3), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 프로필 + 상태 점
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF0F1F3))
            )
            if (student.isActive) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(White)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = student.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${student.gender} · ${student.age}세",
                    fontSize = 12.sp,
                    color = GrayText
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${student.grade}학년 ${student.classNum}반 · 담임 ${student.teacher}",
                fontSize = 12.sp,
                color = GrayText
            )
        }

        if (student.isActive) {
            ActiveBadge()
        }
    }
}

@Composable
private fun ActiveBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BlueLight)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(BluePrimary)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "활동중",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = BluePrimary
        )
    }
}

// ============================================================
// 요약 카드 / 섹션 타이틀 / 차트 카드 / 인사이트
// ============================================================

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    suffix: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEEF3FB))
            .padding(vertical = 16.dp, horizontal = 14.dp)
    ) {
        Text(text = label, fontSize = 12.sp, color = GrayText)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = " $suffix",
                fontSize = 13.sp,
                color = GrayText,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(main: String, sub: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = main,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        if (sub != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = sub,
                fontSize = 12.sp,
                color = GrayText,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE8EDF3), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = GrayText
        )
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun InsightSection(insights: List<AiInsight>) {
    SectionTitle(main = "분석", sub = "AI 인사이트")
    Spacer(modifier = Modifier.height(12.dp))
    insights.forEachIndexed { index, insight ->
        InsightCard(insight)
        if (index != insights.lastIndex) {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InsightCard(insight: AiInsight) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE8EDF3), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // 태그 칩
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BlueLight)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = insight.tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = BluePrimary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = insight.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = insight.description,
            fontSize = 12.sp,
            color = GrayText,
            lineHeight = 18.sp
        )
    }
}

// ============================================================
// 차트: 막대그래프 / 히트맵 / 달력
// ============================================================

// 시간대별 평균 위험 점수 막대그래프 (일간·월간 공용)
@Composable
private fun RiskBarChart(risks: List<HourlyRisk>) {
    val chartHeight = 170.dp
    val startPad = 26.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            // 위험/주의 점선
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dangerY = size.height * (1f - RiskThreshold.DANGER / 100f)
                val cautionY = size.height * (1f - RiskThreshold.CAUTION / 100f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                val left = startPad.toPx()
                drawLine(
                    color = Color(0xFFE0A9A9),
                    start = Offset(left, dangerY),
                    end = Offset(size.width, dangerY),
                    strokeWidth = 2f,
                    pathEffect = dash
                )
                drawLine(
                    color = Color(0xFFCBD5E6),
                    start = Offset(left, cautionY),
                    end = Offset(size.width, cautionY),
                    strokeWidth = 2f,
                    pathEffect = dash
                )
            }

            // Y축 라벨
            Text(
                text = "100",
                fontSize = 10.sp,
                color = GrayText,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Text(
                text = "0",
                fontSize = 10.sp,
                color = GrayText,
                modifier = Modifier.align(Alignment.BottomStart)
            )
            // 임계선 라벨
            Text(
                text = "위험",
                fontSize = 9.sp,
                color = Color(0xFFE57373),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = chartHeight * (1f - RiskThreshold.DANGER / 100f) - 6.dp)
            )
            Text(
                text = "주의",
                fontSize = 9.sp,
                color = Color(0xFF90A4C4),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = chartHeight * (1f - RiskThreshold.CAUTION / 100f) - 6.dp)
            )

            // 막대
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = startPad, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                risks.forEach { risk ->
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .fillMaxHeight((risk.score / 100f).coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor(risk.score))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // X축 시간 라벨
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startPad, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            risks.forEach { risk ->
                Text(
                    text = risk.hour.toString().padStart(2, '0'),
                    fontSize = 10.sp,
                    color = GrayText,
                    modifier = Modifier.width(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun barColor(score: Int): Color = when {
    score >= RiskThreshold.DANGER -> Color(0xFF6C90DC)
    score >= RiskThreshold.CAUTION -> Color(0xFF9FBDEA)
    else -> Color(0xFFC7D8F0)
}

// 주간 히트맵 (요일 × 시간)
@Composable
private fun WeeklyHeatmap(cells: List<HeatCell>) {
    val days = listOf("월", "화", "수", "목", "금")
    val hours = (8..15).toList()
    val gutter = 28.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        // 요일 헤더
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(gutter))
            days.forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = GrayText
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        hours.forEach { hour ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = hour.toString().padStart(2, '0'),
                    modifier = Modifier.width(gutter),
                    fontSize = 11.sp,
                    color = GrayText
                )
                for (day in 0..4) {
                    val cell = cells.firstOrNull { it.dayIndex == day && it.hour == hour }
                    val intensity = cell?.intensity ?: 0f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(heatColor(intensity))
                    )
                }
            }
        }
    }
}

private fun heatColor(intensity: Float): Color =
    lerp(Color(0xFFEFF3FA), Color(0xFF4A79D0), intensity.coerceIn(0f, 1f))

// 월간 달력
@Composable
private fun MonthCalendar(days: List<CalendarDay>) {
    val weekdays = listOf("월", "화", "수", "목", "금", "토", "일")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { w ->
                Text(
                    text = w,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = GrayText
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = day.day.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (day.inMonth) TextPrimary else Color(0xFFCCCCCC)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(dotColor(day.dotLevel))
                        )
                    }
                }
                // 마지막 주가 7칸이 안 되면 빈칸으로 채워 정렬 유지
                repeat(7 - week.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun dotColor(level: RiskLevel?): Color = when (level) {
    RiskLevel.DANGER -> Color(0xFFE53935)
    RiskLevel.CAUTION -> Color(0xFFFF9800)
    RiskLevel.SAFE -> Color(0xFF4CAF50)
    null -> Color.Transparent
}

// ============================================================
// 저장 완료 다이얼로그 (시안 page 6)
// ============================================================

@Composable
private fun ReportSavedDialog(success: Boolean, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onConfirm) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = White
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (success) "리포트 이미지가 저장되었습니다."
                        else "저장에 실패했어요. 잠시 후 다시 시도해 주세요.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                        .background(BluePrimary)
                        .clickable(onClick = onConfirm)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "확인",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ReportDetailScreenPreview() {
    MaterialTheme {
        ReportDetailScreen()
    }
}
