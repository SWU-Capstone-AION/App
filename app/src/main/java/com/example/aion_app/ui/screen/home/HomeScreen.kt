package com.example.aion_app.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.component.AionBottomNavBar
import com.example.aion_app.ui.theme.GreyDarkActive
import com.example.aion_app.ui.theme.GreyLight
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.Green
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.LightHover
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.Red
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

@Composable
fun HomeScreen(
    classInfo: ClassInfo = ClassInfo(teacherName = "박서연", date = "2026.05.28(목)"),
    recentAlert: HomeAlert? = HomeAlert(
        message = "김지우 학생이 안정 상태에 도달했습니다.",
        timeText = "1분 전"
    ),
    students: List<Student> = defaultStudents(),
    classStats: ClassStats = ClassStats(
        activeCount = 2,
        totalCount = 3,
        cautionCount = 7,
        dangerCount = 2
    ),
    // 위험 판정된 아동. null이 아니면 화면 위에 팝업이 뜬다.
    dangerAlert: Student? = null,
    onDangerAlertConfirm: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onAlertClick: () -> Unit = {},
    onStudentClick: (Student) -> Unit = {},
    // 아동 등록 바텀시트에서 고른 항목
    onSearchByIdClick: () -> Unit = {},
    onCreateChildAccountClick: () -> Unit = {},
    onTabSelect: (String) -> Unit = {}

) {
    // 등록하기 바텀시트 표시 여부
    var showRegisterSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HomeTopBar(
                classInfo = classInfo,
                onNotificationClick = onNotificationClick
            )
        },
        bottomBar = { AionBottomNavBar(selected = "home", onSelect = onTabSelect) },
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

            // 최근 알림 배너 (알림이 없어도 자리는 유지한다)
            RecentAlertBanner(
                alert = recentAlert,
                onClick = onAlertClick
            )

            Spacer(modifier = Modifier.height(24.dp))

// 우리반 아이들 섹션 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "우리반 아이들",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${students.size}명",
                    fontSize = 13.sp,
                    color = GrayText,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (students.isEmpty()) {
                // 아직 담당 아동을 연결하지 않은 상태
                EmptyChildrenCard(onRegisterClick = { showRegisterSheet = true })
            } else {
                // 학생 카드 목록
                students.forEach { student ->
                    StudentCard(
                        student = student,
                        onClick = { onStudentClick(student) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 아동을 더 연결할 수 있는 진입점
                AddChildButton(onClick = { showRegisterSheet = true })
            }

            Spacer(modifier = Modifier.height(16.dp))

// 오늘의 학급 현황 섹션
            ClassStatsSection(
                stats = classStats,
                dateText = classInfo.date.substringBefore("(")
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ===== 아동 등록하기 바텀시트 =====
    if (showRegisterSheet) {
        ChildRegisterBottomSheet(
            onDismiss = { showRegisterSheet = false },
            onSearchByIdClick = {
                showRegisterSheet = false
                onSearchByIdClick()
            },
            onCreateAccountClick = {
                showRegisterSheet = false
                onCreateChildAccountClick()
            }
        )
    }

    // ===== 위험 알림 팝업 =====
    // 화면 위에 덮어서 표시. 교사가 확인 버튼을 눌러야 닫힌다.
    if (dangerAlert != null) {
        DangerAlertDialog(
            student = dangerAlert,
            onConfirm = onDangerAlertConfirm
        )
    }
}

// ============================================
// 등록된 아동이 없을 때 보여주는 안내 카드
// ============================================
@Composable
private fun EmptyChildrenCard(onRegisterClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .border(
                width = 1.dp,
                color = LightActive,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "아직 등록된 아동이 없어요",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "실시간으로 아동의 상태를 파악하세요",
            fontSize = 12.sp,
            color = GrayText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Normal)
                .clickable(onClick = onRegisterClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+ 등록하기",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = White
            )
        }
    }
}

// ============================================
// 아동 추가 버튼 (목록이 있을 때 아래에 붙는다)
// ============================================
@Composable
private fun AddChildButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .border(
                width = 1.dp,
                color = LightActive,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+ 아동 추가",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Normal
        )
    }
}

// ============================================
// 위험 알림 팝업
// ============================================
// 놓치면 안 되는 알림이라 바깥을 눌러도 닫히지 않게 한다.
// (뒤로가기도 막아 두어 확인 버튼으로만 닫히도록)
@Composable
private fun DangerAlertDialog(
    student: Student,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = { /* 확인 버튼으로만 닫는다 */ }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(White),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Red,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "위험",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Red
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 이름 + 성별·나이
            Row(verticalAlignment = Alignment.Bottom) {
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
                    color = GrayText,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "즉시 아이의 상태를 확인하세요.",
                fontSize = 14.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 확인 버튼 — 카드 아래쪽에 꽉 차게
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Red)
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "확인",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    classInfo: ClassInfo,
    onNotificationClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(White)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 로고
        Image(
            painter = painterResource(id = R.drawable.aion_logo),
            contentDescription = "AION 로고",
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 교사 이름 + 날짜
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (classInfo.teacherName.isBlank()) "AION"
                else "${classInfo.teacherName} 선생님",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = classInfo.date,
                fontSize = 12.sp,
                color = GrayText
            )
        }

        // 알림 아이콘 (박스 안에)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GreyLight)
                .clickable(onClick = onNotificationClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.alarm_icon_blue),
                contentDescription = "알림",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun RecentAlertBanner(
    alert: HomeAlert?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(White, LightHover)
                )
            )
            // 알림이 없으면 눌러도 갈 곳이 없다
            .then(
                if (alert != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽 파란 세로 바
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(64.dp)
                .background(Normal)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 종 아이콘 (디자인팀 지정: 메인컬러 #6495ED 스트로크 버전)
        Image(
            painter = painterResource(R.drawable.alarm_icon_blue),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 텍스트 영역
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
        ) {
            if (alert != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "NEW",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Normal
                    )
                    Text(
                        text = " · ${alert.timeText}",
                        fontSize = 12.sp,
                        color = Normal
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = alert?.message ?: "알림 없음",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (alert != null) TextPrimary else GrayText
            )
        }

        // 오른쪽 화살표 (알림이 있을 때만)
        if (alert != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Normal,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(24.dp)
            )
        }
    }
}

@Composable
private fun StudentCard(
    student: Student,
    onClick: () -> Unit
) {
    val isActive = student.status == StudentStatus.ACTIVE
    val cardAlpha = if (isActive) 1f else 0.6f  // 비활동은 흐릿하게

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .border(
                width = 1.dp,
                color = LightActive,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
            .alpha(cardAlpha)
    ) {
        Column {
            // 상단: 프로필 + 이름/성별/나이 + 상태 뱃지
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 프로필 사진 (초록 점 인디케이터 포함)
                ProfileWithIndicator(isActive = isActive)

                Spacer(modifier = Modifier.width(12.dp))

                // 이름 + 성별·나이
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Bottom
                ) {
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
                        color = GrayText,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }

                // 활동 상태 뱃지
                StatusBadge(isActive = isActive)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 스트레스 지수 진행 바 + 숫자
            StressBar(
                score = student.stressScore,
                level = student.stressLevel,
                isActive = isActive
            )

            // 심박수 (활동중일 때만)
            if (isActive && student.heartRate != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = LightActive, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center  // ← 가운데 정렬
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Red,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "심박수",
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Image(
                        painter = painterResource(id = R.drawable.heart_graph),
                        contentDescription = null,
                        modifier = Modifier.size(60.dp, 24.dp)  // ← 크게
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${student.heartRate}",
                        fontSize = 22.sp,  // ← 크게
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "bpm",
                        fontSize = 13.sp,
                        color = GrayText
                    )
                }
            }
        }
    }
}

// 아이들 리스트 활동 상태 점 크기.
// 리포트 화면(ReportDetailScreen)의 상태 점과 같은 값을 쓴다.
private val IndicatorOuterSize = 8.dp
private val IndicatorInnerSize = 4.dp

// 프로필 네모 크기와 모서리 반경
private val ProfileBoxSize = 48.dp
private val ProfileCornerRadius = 8.dp

// 점의 중심을 둥근 모서리의 '곡선 위'(45° 지점)에 올리기 위한 보정값.
// 곡선의 45° 지점은 모서리 원 중심에서 r/√2 만큼 떨어져 있으므로,
// BottomEnd(= 네모 안쪽 끝)에 붙인 상태에서 아래만큼 더 밀어주면 된다.
//   보정 = 점지름/2 - r * (1 - 1/√2)
// 8dp 점 / 8dp 반경 기준으로 약 1.66dp.
private val IndicatorCornerOffset =
    IndicatorOuterSize / 2 - ProfileCornerRadius * 0.2929f

@Composable
private fun ProfileWithIndicator(isActive: Boolean) {
    Box(
        modifier = Modifier.size(ProfileBoxSize)
    ) {
        // 프로필 자리
        Box(
            modifier = Modifier
                .size(ProfileBoxSize)
                .clip(RoundedCornerShape(ProfileCornerRadius))
                .background(Color(0xFFE0E0E0))
        )

        // 우하단 활동 인디케이터 (활동/비활동 모두 표시, 색만 다름)
        val indicatorColor = if (isActive) Green else GrayText

        // 상단 알림 배너의 알림 아이콘(20dp)보다 확실히 작게.
        // 리포트 StudentHeaderCard 의 상태 점도 같은 값(8/4)으로 맞춰뒀다.
        //
        // 원의 '중심'이 프로필 네모의 둥근 모서리 곡선 위에 오도록 배치한다.
        // (Box 는 기본적으로 자식을 자르지 않으므로 튀어나온 부분도 그대로 그려진다)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = IndicatorCornerOffset, y = IndicatorCornerOffset)
                .size(IndicatorOuterSize),
            contentAlignment = Alignment.Center
        ) {
            // 뒷 원 (큰, 반투명)
            Box(
                modifier = Modifier
                    .size(IndicatorOuterSize)
                    .clip(CircleShape)
                    .background(indicatorColor.copy(alpha = 0.5f))
            )
            // 앞 원 (작은, 진한)
            Box(
                modifier = Modifier
                    .size(IndicatorInnerSize)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
        }
    }
}

// 활동중 / 비활동 배지.
// 배경·점·글자가 모두 포함된 디자인팀 에셋을 통째로 쓴다.
// 높이만 지정하면 가로는 원본 비율대로 따라온다.
private val StatusBadgeHeight = 24.dp

@Composable
private fun StatusBadge(isActive: Boolean) {
    Image(
        painter = painterResource(
            if (isActive) R.drawable.activity_chip else R.drawable.nonactivity_chip
        ),
        contentDescription = if (isActive) "활동중" else "비활동",
        modifier = Modifier.height(StatusBadgeHeight)
    )
}

@Composable
private fun StressBar(
    score: Int,
    level: StressLevel,
    isActive: Boolean
) {
    val barColor = when {
        !isActive -> Color(0xFFBDBDBD)
        level == StressLevel.STABLE -> Color(0xFF4CAF50)
        level == StressLevel.CAUTION -> Color(0xFFFF9800)
        level == StressLevel.DANGER -> Color(0xFFE53935)
        else -> Color(0xFFBDBDBD)
    }
    val textColor = if (isActive) barColor else GrayText

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 진행 바
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFE0E0E0))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (score / 100f).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 숫자와 라벨
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = score.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = level.label,
                fontSize = 11.sp,
                color = textColor
            )
        }
    }
}

@Composable
private fun ClassStatsSection(
    stats: ClassStats,
    dateText: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 섹션 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "오늘의 학급 현황",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = dateText,
                fontSize = 12.sp,
                color = GrayText,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3개 통계 카드
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsCard(
                label = "활동",
                value = "${stats.activeCount}",
                suffix = "/${stats.totalCount}",
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                label = "주의 감지",
                value = "${stats.cautionCount}",
                suffix = "건",
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                label = "위험 감지",
                value = "${stats.dangerCount}",
                suffix = "건",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatsCard(
    label: String,
    value: String,
    suffix: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(White, LightHover)
                )
            )
            .border(
                width = 1.dp,
                color = LightActive,
                shape = RoundedCornerShape(10.dp)
            )
            // 아래 치수는 리포트(일간~월간)의 SummaryCard 와 동일하게 맞춘 값이다.
            // 세로 여백·라벨·간격·숫자 크기가 모두 같아야 두 카드 높이가 일치한다.
            .padding(vertical = 16.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = GreyDarkActive
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
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

/** "2026.08.21(목)" 형태로 오늘 날짜를 만든다 */
fun todayText(): String {
    val format = java.text.SimpleDateFormat("yyyy.MM.dd(E)", java.util.Locale.KOREA)
    return format.format(java.util.Date())
}

// 테스트용 더미 데이터
private fun defaultStudents(): List<Student> = listOf(
    Student(
        id = "1",
        name = "김지우",
        gender = "남",
        age = 9,
        status = StudentStatus.ACTIVE,
        stressScore = 68,
        stressLevel = StressLevel.CAUTION,
        heartRate = 110
    ),
    Student(
        id = "2",
        name = "이주미",
        gender = "여",
        age = 9,
        status = StudentStatus.ACTIVE,
        stressScore = 21,
        stressLevel = StressLevel.STABLE,
        heartRate = 78
    ),
    Student(
        id = "3",
        name = "주우시",
        gender = "남",
        age = 9,
        status = StudentStatus.INACTIVE,
        stressScore = 0,
        stressLevel = StressLevel.NO_DATA,
        heartRate = null
    )
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen()
    }
}

// 등록된 아동이 없는 상태
@Preview(showBackground = true, showSystemUi = true, name = "아동 없음")
@Composable
fun HomeScreenEmptyPreview() {
    MaterialTheme {
        HomeScreen(
            recentAlert = null,
            students = emptyList(),
            classStats = ClassStats(0, 0, 0, 0)
        )
    }
}

// 위험 알림 팝업이 뜬 상태
@Preview(showBackground = true, showSystemUi = true, name = "위험 알림")
@Composable
fun HomeScreenDangerAlertPreview() {
    MaterialTheme {
        HomeScreen(dangerAlert = defaultStudents().first())
    }
}