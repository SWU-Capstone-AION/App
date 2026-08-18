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
import androidx.compose.ui.graphics.Brush
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
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.Light
import com.example.aion_app.ui.theme.LightHover
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.Dark
import com.example.aion_app.ui.theme.Red
import com.example.aion_app.ui.theme.Orange
import com.example.aion_app.ui.theme.Green
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.GrayDark
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

    // 날짜 이동용 offset (0 = 최신/오늘, 음수 = 과거). 미래(양수)는 막음.
    var dayOffset by remember { mutableStateOf(0) }
    var weekOffset by remember { mutableStateOf(0) }
    var monthOffset by remember { mutableStateOf(0) }

    // offset 이 바뀔 때만 재생성 (같은 offset 이면 항상 같은 데이터)
    val daily = remember(dayOffset) { dailyReportFor(dayOffset) }
    val weekly = remember(weekOffset) { weeklyReportFor(weekOffset) }
    val monthly = remember(monthOffset) { monthlyReportFor(monthOffset) }

    Scaffold(
        topBar = {
            AionTopBar(
                title = "상세 리포트",
                onBackClick = onBackClick,
                iconStartPadding = 8.dp,      // 아이콘 꼭짓점을 기간 탭 왼쪽(20dp)에 맞춤
                dividerColor = AionTextDark   // #2D3C4A
            )
        },
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
                // 날짜 네비게이터 (기간별 라벨 + 이전/다음 이동)
                when (period) {
                    ReportPeriod.DAILY -> DateNavigator(
                        label = daily.dateLabel,
                        onPrev = { dayOffset-- },
                        onNext = { if (dayOffset < 0) dayOffset++ },
                        nextEnabled = dayOffset < 0
                    )
                    ReportPeriod.WEEKLY -> DateNavigator(
                        label = weekly.dateLabel,
                        onPrev = { weekOffset-- },
                        onNext = { if (weekOffset < 0) weekOffset++ },
                        nextEnabled = weekOffset < 0
                    )
                    ReportPeriod.MONTHLY -> DateNavigator(
                        label = monthly.monthLabel,
                        onPrev = { monthOffset-- },
                        onNext = { if (monthOffset < 0) monthOffset++ },
                        nextEnabled = monthOffset < 0
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 학생 프로필 카드 (공통)
                StudentHeaderCard(student = report.student)

                Spacer(modifier = Modifier.height(24.dp))

                // 기간별 본문
                when (period) {
                    ReportPeriod.DAILY -> DailyContent(daily)
                    ReportPeriod.WEEKLY -> WeeklyContent(weekly)
                    ReportPeriod.MONTHLY -> MonthlyContent(monthly) { day ->
                        dayOffset = dayOffsetForCalendar(monthOffset, day).coerceAtMost(0)
                        period = ReportPeriod.DAILY
                    }
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
                            val datePart = when (period) {
                                ReportPeriod.DAILY -> daily.detailDateLabel
                                ReportPeriod.WEEKLY -> weekly.detailDateLabel.replace(" ", "")
                                ReportPeriod.MONTHLY -> monthly.detailDateLabel
                            }
                            saveSuccess = saveBitmapToGallery(
                                context = context,
                                bitmap = bitmap,
                                displayName = "AION_리포트_${report.student.name}_$datePart"
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
    RiskBarChartCard(title = "시간대별 평균 위험 점수", risks = daily.hourlyRisks)

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
    WeeklyHeatmapCard(title = "진할수록 위험점수가 높습니다.", cells = weekly.heatCells)

    Spacer(modifier = Modifier.height(24.dp))

    InsightSection(insights = weekly.insights)
}

@Composable
private fun MonthlyContent(monthly: MonthlyReport, onDayClick: (Int) -> Unit) {
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
    MonthCalendar(days = monthly.calendarDays, onDayClick = onDayClick)

    Spacer(modifier = Modifier.height(24.dp))

    SectionTitle(main = "상세 리포트", sub = monthly.detailDateLabel)
    Spacer(modifier = Modifier.height(12.dp))
    RiskBarChartCard(title = "시간대별 평균 위험 점수", risks = monthly.hourlyRisks)

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
            // 슬롯은 1/3 그대로 두고, 안쪽 선택 pill 만 좌우로 좁힌다.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Normal else Color.Transparent)
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
}

@Composable
private fun DateNavigator(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "이전", tint = AionTextDark)
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        IconButton(onClick = onNext, enabled = nextEnabled) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = "다음",
                // 색상 통일(#2D3C4A). 비활성만 같은 색 30% 로 흐리게.
                tint = if (nextEnabled) AionTextDark else AionTextDark.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun StudentHeaderCard(student: ReportStudent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LightActive, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 프로필 + 상태 점
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.BottomEnd   // 상태 점을 우측으로
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF0F1F3))
            )
            // 홈 화면 아이들 리스트의 상태 점과 같은 크기(12/6).
            // 안쪽 원을 padding 으로 만들면 바깥 크기를 바꿀 때마다 padding 도
            // 다시 계산해야 해서, 두 원의 크기를 각각 지정하는 방식으로 바꿨다.
            if (student.isActive) {
                Box(
                    modifier = Modifier.size(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Green.copy(alpha = 0.5f))   // 뒤쪽 원 #629F7D 50%
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Green)
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
            .height(20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LightHover)   // 배지 배경 #E8EFFC
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 시안: #6495ED 점 뒤에 #CFDEF9 원이 한 겹 더 있다 (지름 비율 약 1:2)
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(LightActive)   // 뒤쪽 원 #CFDEF9
                .padding(2.5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Normal)   // #6495ED
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "활동중",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Dark   // #4B70B2
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
            // 배경 그라데이션 #FFFFFF → #E8EFFC (위 → 아래).
            // 시안은 60% 지점에서 이미 #E8EFFC 로 포화되고 아래까지 유지된다.
            .background(
                Brush.verticalGradient(
                    0.0f to White,
                    0.6f to LightHover,
                    1.0f to LightHover
                )
            )
            .border(1.dp, LightActive, RoundedCornerShape(12.dp))
            .padding(vertical = 16.dp, horizontal = 14.dp)
    ) {
        Text(text = label, fontSize = 14.sp, color = GrayDark, fontWeight = FontWeight.SemiBold)
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.padding(vertical = 3.dp, horizontal = 8.dp)
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
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = sub,
                fontSize = 12.sp,
                color = GrayText,
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
            .border(1.dp, LightActive, RoundedCornerShape(16.dp))
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
            .border(1.dp, LightActive, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // 태그 칩
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(LightHover)   // #E8EFFC
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = insight.tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Dark   // #4B70B2
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

// 시간대별 평균 위험 점수 막대그래프 카드 (일간·월간 공용) — 막대 탭 시 값 표시
@Composable
private fun RiskBarChartCard(title: String, risks: List<HourlyRisk>) {
    var selectedHour by remember(risks) { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LightActive, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GrayText,
                modifier = Modifier.weight(1f)
            )
            val sel = selectedHour
            if (sel != null) {
                val score = risks.firstOrNull { it.hour == sel }?.score ?: 0
                SelectedValuePill(text = "${sel}\uc2dc \u00b7 ${score}\uc810", color = scoreColor(score))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        RiskBarChart(
            risks = risks,
            selectedHour = selectedHour,
            onBarClick = { hour -> selectedHour = if (selectedHour == hour) null else hour }
        )
    }
}

@Composable
private fun RiskBarChart(
    risks: List<HourlyRisk>,
    selectedHour: Int?,
    onBarClick: (Int) -> Unit
) {
// 시안 실측: 100 기준선 ~ 0 기준선 사이 131dp
    val chartHeight = 131.dp
    val startPad = 26.dp
    val axisLabel = 14.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight + axisLabel)
        ) {
            // ---------- 플롯 영역 (여기서 0f = 100 선, size.height = 0 선) ----------
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = axisLabel / 2)
            ) {
                // 위험/주의 점선 + 100/0 기준선 (전부 1dp)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dangerY = size.height * (1f - RiskThreshold.DANGER / 100f)
                    val cautionY = size.height * (1f - RiskThreshold.CAUTION / 100f)
                    val left = startPad.toPx()
                    val stroke = 1.dp.toPx()
                    val dash = PathEffect.dashPathEffect(
                        floatArrayOf(3.dp.toPx(), 3.dp.toPx())
                    )

                    // 100 / 0 기준선 — 선 두께의 절반만큼 안쪽으로 넣어 잘림 방지
                    drawLine(
                        color = Light,
                        start = Offset(left, stroke / 2),
                        end = Offset(size.width, stroke / 2),
                        strokeWidth = stroke
                    )
                    drawLine(
                        color = Light,
                        start = Offset(left, size.height - stroke / 2),
                        end = Offset(size.width, size.height - stroke / 2),
                        strokeWidth = stroke
                    )

                    drawLine(
                        color = Red.copy(alpha = 0.45f),
                        start = Offset(left, dangerY),
                        end = Offset(size.width, dangerY),
                        strokeWidth = stroke,
                        pathEffect = dash
                    )
                    drawLine(
                        color = Orange.copy(alpha = 0.4f),
                        start = Offset(left, cautionY),
                        end = Offset(size.width, cautionY),
                        strokeWidth = stroke,
                        pathEffect = dash
                    )
                }

                // 위험 / 주의 라벨
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = chartHeight * (1f - RiskThreshold.DANGER / 100f) - 10.dp)
                        .height(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\uc704\ud5d8", fontSize = 9.sp, color = Red)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = chartHeight * (1f - RiskThreshold.CAUTION / 100f) - 10.dp)
                        .height(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\uc8fc\uc758", fontSize = 9.sp, color = Orange)
                }

                // 막대 (탭 영역 = 세로 전체, 넓게)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = startPad, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    risks.forEach { risk ->
                        val isSelected = risk.hour == selectedHour
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .fillMaxHeight()
                                .clickable { onBarClick(risk.hour) },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .fillMaxHeight((risk.score / 100f).coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(barColor(risk.score, isSelected))
                            )
                        }
                    }
                }
            }
            // ---------- 플롯 영역 끝 ----------

            // 축 라벨은 바깥 Box 기준. 박스 중심이 각 기준선 위에 정확히 놓인다.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .height(axisLabel),
                contentAlignment = Alignment.Center
            ) {
                Text("100", fontSize = 10.sp, color = GrayText)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .height(axisLabel),
                contentAlignment = Alignment.Center
            ) {
                Text("0", fontSize = 10.sp, color = GrayText)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startPad, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            risks.forEach { risk ->
                val isSelected = risk.hour == selectedHour
                Text(
                    text = risk.hour.toString().padStart(2, '0'),
                    fontSize = 10.sp,
                    color = if (isSelected) TextPrimary else GrayText,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun barColor(score: Int, isSelected: Boolean): Color {
    if (isSelected) return Dark
    return when {
        score >= RiskThreshold.DANGER -> Normal
        score >= RiskThreshold.CAUTION -> Normal.copy(alpha = 0.6f)
        else -> Normal.copy(alpha = 0.35f)
    }
}

// \uc8fc\uac04 \ud788\ud2b8\ub9f5 \uce74\ub4dc — \uc140 \ud0ed \uc2dc \uc694\uc77c\u00b7\uc2dc\uac04\u00b7\ub808\ubca8 \ud45c\uc2dc
@Composable
private fun WeeklyHeatmapCard(title: String, cells: List<HeatCell>) {
    val dayLabels = listOf("\uc6d4", "\ud654", "\uc218", "\ubaa9", "\uae08")
    var selected by remember(cells) { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LightActive, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GrayText,
                modifier = Modifier.weight(1f)
            )
            val sel = selected
            if (sel != null) {
                val cell = cells.firstOrNull { it.dayIndex == sel.first && it.hour == sel.second }
                val level = intensityLevel(cell?.intensity ?: 0f)
                SelectedValuePill(
                    text = "${dayLabels[sel.first]} ${sel.second}\uc2dc \u00b7 ${levelText(level)}",
                    color = levelColor(level)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        WeeklyHeatmap(
            cells = cells,
            selected = selected,
            onCellClick = { d, h -> selected = if (selected == (d to h)) null else (d to h) }
        )
    }
}

@Composable
private fun WeeklyHeatmap(
    cells: List<HeatCell>,
    selected: Pair<Int, Int>?,
    onCellClick: (Int, Int) -> Unit
) {
    val days = listOf("\uc6d4", "\ud654", "\uc218", "\ubaa9", "\uae08")
    val hours = (8..15).toList()
    val gutter = 28.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(gutter))
            days.forEach { d ->
                Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = GrayText)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        hours.forEach { hour ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(hour.toString().padStart(2, '0'), modifier = Modifier.width(gutter), fontSize = 11.sp, color = GrayText)
                for (day in 0..4) {
                    val cell = cells.firstOrNull { it.dayIndex == day && it.hour == hour }
                    val intensity = cell?.intensity ?: 0f
                    val isSelected = selected == (day to hour)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(heatColor(intensity))
                            .then(
                                if (isSelected)
                                    Modifier.border(2.dp, Dark, RoundedCornerShape(6.dp))
                                else Modifier
                            )
                            .clickable { onCellClick(day, hour) }
                    )
                }
            }
        }
    }
}

private fun heatColor(intensity: Float): Color =
    lerp(Light, Dark, intensity.coerceIn(0f, 1f))

// \uc6d4\uac04 \ub2ec\ub825 — \uc774\ubc88 \ub2ec \ub0a0\uc9dc \ud0ed \uc2dc \ud574\ub2f9 \ub0a0\uc9dc\uc758 \uc77c\uac04 \ub9ac\ud3ec\ud2b8\ub85c \uc774\ub3d9
@Composable
private fun MonthCalendar(days: List<CalendarDay>, onDayClick: (Int) -> Unit) {
    val weekdays = listOf("\uc6d4", "\ud654", "\uc218", "\ubaa9", "\uae08", "\ud1a0", "\uc77c")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { w ->
                Text(w, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = GrayText)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (day.inMonth) Modifier.clickable { onDayClick(day.day) }
                                else Modifier
                            )
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
                repeat(7 - week.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ---------- \uc120\ud0dd \uac12 \ud45c\uc2dc pill & \ub808\ubca8 \ud5ec\ud37c ----------

@Composable
private fun SelectedValuePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= RiskThreshold.DANGER -> Red
    score >= RiskThreshold.CAUTION -> Orange
    else -> Green
}

private fun intensityLevel(intensity: Float): RiskLevel = when {
    intensity >= 0.66f -> RiskLevel.DANGER
    intensity >= 0.33f -> RiskLevel.CAUTION
    else -> RiskLevel.SAFE
}

private fun levelText(level: RiskLevel): String = when (level) {
    RiskLevel.DANGER -> "\uc704\ud5d8"
    RiskLevel.CAUTION -> "\uc8fc\uc758"
    RiskLevel.SAFE -> "\uc548\uc804"
}

private fun levelColor(level: RiskLevel): Color = when (level) {
    RiskLevel.DANGER -> Red
    RiskLevel.CAUTION -> Orange
    RiskLevel.SAFE -> Green
}

private fun dotColor(level: RiskLevel?): Color = when (level) {
    RiskLevel.DANGER -> Red
    RiskLevel.CAUTION -> Orange
    RiskLevel.SAFE -> Green
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
                        .background(Normal)
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
    AionTheme {
        ReportDetailScreen()
    }
}