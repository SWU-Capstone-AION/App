package com.example.aion_app.ui.screen.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aion_app.ui.component.AionBottomNavBar
import com.example.aion_app.ui.component.AionPrimaryButton
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.home.ChildAvatar
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.Light
import com.example.aion_app.ui.theme.LightHover
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.Dark
import com.example.aion_app.ui.theme.Red
import com.example.aion_app.ui.theme.Orange
import com.example.aion_app.R
import com.example.aion_app.ui.theme.Green
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.GreyDarkActive
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White
import kotlinx.coroutines.launch

// 기록 없는 칸(avg = null) 색 — 0점(Light)과 구분되는 회색
private val EmptyCellColor = Color(0xFFF1F3F6)

@Composable
fun ReportDetailScreen(
    report: StudentReport = sampleStudentReport(),
    onBackClick: () -> Unit = {},
    onTabSelect: (String) -> Unit = {},
    viewModel: ReportDetailViewModel = viewModel()
) {
    LaunchedEffect(report.student.id) {
        viewModel.start(report.student.id)
    }

    ReportDetailContent(
        student = report.student,
        period = viewModel.period,
        dateLabel = viewModel.dateLabel,
        nextEnabled = viewModel.nextEnabled,
        fileDateLabel = viewModel.fileDateLabel,
        daily = viewModel.daily,
        weekly = viewModel.weekly,
        monthly = viewModel.monthly,
        onPeriodSelect = viewModel::selectPeriod,
        onPrev = viewModel::prev,
        onNext = viewModel::next,
        onCalendarDayClick = viewModel::openDayFromCalendar,
        onRetry = viewModel::retry,
        onBackClick = onBackClick,
        onTabSelect = onTabSelect
    )
}

// 화면 그리기만 담당 (미리보기에서도 이걸 씀)
@Composable
private fun ReportDetailContent(
    student: ReportStudent,
    period: ReportPeriod,
    dateLabel: String,
    nextEnabled: Boolean,
    fileDateLabel: String,
    daily: ReportLoadState<DailyReport>,
    weekly: ReportLoadState<WeeklyReport>,
    monthly: ReportLoadState<MonthlyReport>,
    onPeriodSelect: (ReportPeriod) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCalendarDayClick: (Int) -> Unit,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
    onTabSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureController = rememberCaptureController()

    var showSavedDialog by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(true) }

    // 지금 탭의 값이 다 불러와졌는지 (불러오는 중엔 이미지 저장 막음)
    val loaded = when (period) {
        ReportPeriod.DAILY -> daily is ReportLoadState.Success
        ReportPeriod.WEEKLY -> weekly is ReportLoadState.Success
        ReportPeriod.MONTHLY -> monthly is ReportLoadState.Success
    }

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
            PeriodTabs(selected = period, onSelect = onPeriodSelect)

            Spacer(modifier = Modifier.height(16.dp))

            // ↓↓↓ 이미지로 저장할 영역 (날짜 + 프로필 + 그래프 + 인사이트) ↓↓↓
            Column(modifier = Modifier.capturable(captureController)) {
                DateNavigator(
                    label = dateLabel,
                    onPrev = onPrev,
                    onNext = onNext,
                    nextEnabled = nextEnabled
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 학생 프로필 카드 (공통)
                StudentHeaderCard(student = student)

                Spacer(modifier = Modifier.height(24.dp))

                // 기간별 본문 (불러오는 중 / 실패 / 성공)
                when (period) {
                    ReportPeriod.DAILY -> LoadStateBody(daily, onRetry) { DailyContent(it) }
                    ReportPeriod.WEEKLY -> LoadStateBody(weekly, onRetry) { WeeklyContent(it) }
                    ReportPeriod.MONTHLY -> LoadStateBody(monthly, onRetry) {
                        MonthlyContent(it, onDayClick = onCalendarDayClick)
                    }
                }
            }
            // ↑↑↑ 저장 영역 끝 ↑↑↑

            Spacer(modifier = Modifier.height(24.dp))

            // 이미지 다운로드
            AionPrimaryButton(
                text = "이미지 다운로드",
                onClick = {
                    val bitmap = if (loaded) captureController.toBitmap() else null
                    if (bitmap == null) {
                        saveSuccess = false
                        showSavedDialog = true
                    } else {
                        scope.launch {
                            saveSuccess = saveBitmapToGallery(
                                context = context,
                                bitmap = bitmap,
                                displayName = "AION_리포트_${student.name}_$fileDateLabel"
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
// 불러오기 상태별 본문
// ============================================================

@Composable
private fun <T> LoadStateBody(
    state: ReportLoadState<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit
) {
    when (state) {
        is ReportLoadState.Loading -> ReportLoading()
        is ReportLoadState.Error -> ReportError(message = state.message, onRetry = onRetry)
        is ReportLoadState.Success -> content(state.data)
    }
}

@Composable
private fun ReportLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Normal)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "리포트를 불러오는 중이에요", fontSize = 14.sp, color = GrayText)
        Spacer(modifier = Modifier.height(4.dp))
        // Render 서버가 잠들어 있으면 깨는 데 1분 가까이 걸린다
        Text(text = "처음 열 때는 1분 정도 걸릴 수 있어요", fontSize = 12.sp, color = GrayText)
    }
}

@Composable
private fun ReportError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            fontSize = 14.sp,
            color = GrayText,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) {
            Text(text = "다시 시도", color = Normal)
        }
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
    }

    Spacer(modifier = Modifier.height(24.dp))

    SectionTitle(main = monthly.monthLabel)
    Spacer(modifier = Modifier.height(12.dp))
    MonthCalendar(days = monthly.calendarDays, onDayClick = onDayClick)

    // 서버가 월간 시간대 값을 줄 때만 그래프 표시
    if (monthly.hourlyRisks.isNotEmpty()) {
        Spacer(modifier = Modifier.height(24.dp))

        SectionTitle(main = "상세 리포트", sub = monthly.detailDateLabel)
        Spacer(modifier = Modifier.height(12.dp))
        RiskBarChartCard(title = "시간대별 평균 위험 점수", risks = monthly.hourlyRisks)
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
            modifier = Modifier.size(48.dp)
        ) {
            // 홈 화면과 같은 컴포넌트를 써서 아이별 사진을 보여준다
            ChildAvatar(
                size = 48.dp,
                cornerRadius = 8.dp,
                childName = student.name
            )
            // 홈 화면 아이들 리스트의 상태 점과 같은 크기(8/4).
            // 원의 '중심'이 프로필 네모(48dp / 모서리 반경 8dp)의 둥근 모서리 곡선 위에 오도록 민다.
            // 보정 = 점지름/2 - r * (1 - 1/√2) = 4 - 8*0.2929 ≒ 1.66dp
            if (student.isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 1.66.dp, y = 1.66.dp)
                        .size(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Green.copy(alpha = 0.5f))   // 뒤쪽 원 #629F7D 50%
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
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

            // 학급 정보는 계정에 없어서, 있는 값만 붙인다 (지금은 담임 이름만)
            val classText = if (student.grade != null && student.classNum != null)
                "${student.grade}학년 ${student.classNum}반" else null
            val teacherText = student.teacher.takeIf { it.isNotBlank() }?.let { "담임 $it" }
            val subLine = listOfNotNull(classText, teacherText).joinToString(" · ")

            if (subLine.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subLine,
                    fontSize = 12.sp,
                    color = GrayText
                )
            }
        }

        if (student.isActive) {
            ActiveBadge()
        }
    }
}

// 홈 화면 StatusBadge 와 같은 에셋을 쓴다.
@Composable
private fun ActiveBadge() {
    Image(
        painter = painterResource(R.drawable.activity_chip),
        contentDescription = "활동중",
        modifier = Modifier.height(20.dp)
    )
}

// ============================================================
// 요약 카드 / 섹션 타이틀 / 인사이트
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
        Text(text = label, fontSize = 14.sp, color = GreyDarkActive, fontWeight = FontWeight.SemiBold)
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
private fun InsightSection(insights: List<AiInsight>) {
    SectionTitle(main = "분석", sub = "AI 인사이트")
    Spacer(modifier = Modifier.height(12.dp))

    if (insights.isEmpty()) {
        EmptyInsightCard()
        return
    }

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

// 기록이 없어서 분석 문장을 만들 수 없을 때
@Composable
private fun EmptyInsightCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LightActive, RoundedCornerShape(16.dp))
            .padding(vertical = 24.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "아직 분석할 기록이 없어요.",
            fontSize = 13.sp,
            color = GrayText
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
                val score = risks.firstOrNull { it.hour == sel }?.score
                if (score == null) {
                    SelectedValuePill(text = "${sel}시 · 기록 없음", color = GrayText)
                } else {
                    SelectedValuePill(text = "${sel}시 · ${score}점", color = levelColor(scoreLevel(score)))
                }
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
                    Text(text = "위험", fontSize = 9.sp, color = Red)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = chartHeight * (1f - RiskThreshold.CAUTION / 100f) - 10.dp)
                        .height(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "주의", fontSize = 9.sp, color = Orange)
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
                            // 기록 없는 시간(null)은 막대를 그리지 않는다 (0점과 구분)
                            val score = risk.score
                            if (score != null) {
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .fillMaxHeight((score / 100f).coerceIn(0f, 1f))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(barColor(score, isSelected))
                                )
                            }
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

// 주간 히트맵 카드 — 셀 탭 시 요일·시간·레벨 표시
@Composable
private fun WeeklyHeatmapCard(title: String, cells: List<HeatCell>) {
    val dayLabels = listOf("월", "화", "수", "목", "금")
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
                val score = cells.firstOrNull { it.dayIndex == sel.first && it.hour == sel.second }?.score
                val where = "${dayLabels[sel.first]} ${sel.second}시"
                if (score == null) {
                    SelectedValuePill(text = "$where · 기록 없음", color = GrayText)
                } else {
                    val level = scoreLevel(score)
                    SelectedValuePill(text = "$where · ${levelText(level)}", color = levelColor(level))
                }
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
    val days = listOf("월", "화", "수", "목", "금")
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
                    val score = cells.firstOrNull { it.dayIndex == day && it.hour == hour }?.score
                    val isSelected = selected == (day to hour)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(heatColor(score))
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

// 점수가 높을수록 진하게. 기록 없는 칸은 회색.
private fun heatColor(score: Int?): Color =
    if (score == null) EmptyCellColor
    else lerp(Light, Dark, (score / 100f).coerceIn(0f, 1f))

// 월간 달력 — 이번 달 날짜 탭 시 해당 날짜의 일간 리포트로 이동
@Composable
private fun MonthCalendar(days: List<CalendarDay>, onDayClick: (Int) -> Unit) {
    val weekdays = listOf("월", "화", "수", "목", "금", "토", "일")

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

// ---------- 선택 값 표시 pill & 레벨 헬퍼 ----------

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

// 막대그래프와 히트맵이 같은 기준(주의 40 / 위험 70)을 쓴다
private fun scoreLevel(score: Int): RiskLevel = when {
    score >= RiskThreshold.DANGER -> RiskLevel.DANGER
    score >= RiskThreshold.CAUTION -> RiskLevel.CAUTION
    else -> RiskLevel.SAFE
}

private fun levelText(level: RiskLevel): String = when (level) {
    RiskLevel.DANGER -> "위험"
    RiskLevel.CAUTION -> "주의"
    RiskLevel.SAFE -> "안정"
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
        ReportDetailContent(
            student = defaultReportStudents()[1],
            period = ReportPeriod.DAILY,
            dateLabel = "05.25 월",
            nextEnabled = false,
            fileDateLabel = "2026.05.25",
            daily = ReportLoadState.Success(previewDailyReport()),
            weekly = ReportLoadState.Success(previewWeeklyReport()),
            monthly = ReportLoadState.Success(previewMonthlyReport()),
            onPeriodSelect = {},
            onPrev = {},
            onNext = {},
            onCalendarDayClick = {},
            onRetry = {},
            onBackClick = {},
            onTabSelect = {}
        )
    }
}