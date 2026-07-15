package com.example.aion_app.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import com.example.aion_app.ui.theme.BlueLight
import com.example.aion_app.ui.theme.BluePrimary
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.ui.draw.alpha
import com.example.aion_app.ui.theme.GrayBackground

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

@Composable
fun HomeScreen(
    classInfo: ClassInfo = ClassInfo(grade = 3, classNum = 4, date = "2026.05.28(목)"),
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
    onNotificationClick: () -> Unit = {},
    onAlertClick: () -> Unit = {},
    onStudentClick: (Student) -> Unit = {},
    onTabSelect: (String) -> Unit = {}

) {
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
        // 나중에 여기에 콘텐츠 채울 거예요
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 최근 알림 배너 (있을 때만 표시)
            if (recentAlert != null) {
                RecentAlertBanner(
                    alert = recentAlert,
                    onClick = onAlertClick
                )
            }

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

// 학생 카드 목록
            students.forEach { student ->
                StudentCard(
                    student = student,
                    onClick = { onStudentClick(student) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))

// 오늘의 학급 현황 섹션
            ClassStatsSection(
                stats = classStats,
                dateText = "2026.05.28"
            )

            Spacer(modifier = Modifier.height(24.dp))
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

        // 반 정보 (학년, 반 + 날짜)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "${classInfo.grade}학년 ${classInfo.classNum}반",
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
                .size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onNotificationClick) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "알림",
                    tint = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun RecentAlertBanner(
    alert: HomeAlert,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BlueLight)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽 파란 세로 바
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(64.dp)
                .background(BluePrimary)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 종 아이콘
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = GrayText,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 텍스트 영역
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NEW",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BluePrimary
                )
                Text(
                    text = " · ${alert.timeText}",
                    fontSize = 12.sp,
                    color = BluePrimary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = alert.message,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        // 오른쪽 화살표
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = GrayText,
            modifier = Modifier.padding(end = 12.dp)
        )
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
            .clip(RoundedCornerShape(16.dp))
            .background(GrayBackground.copy(alpha = 0.3f))
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${student.gender} · ${student.age}세",
                        fontSize = 12.sp,
                        color = GrayText
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
                HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "심박수",
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.MonitorHeart,
                        contentDescription = null,
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${student.heartRate} ",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "bpm",
                        fontSize = 12.sp,
                        color = GrayText
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileWithIndicator(isActive: Boolean) {
    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        // 프로필 자리 (기본 회색 원, 이미지가 있으면 여기 대체)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE0E0E0))
        )

        // 좌하단 상태 점 (활동중일 때만 초록)
        if (isActive) {
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
}

@Composable
private fun StatusBadge(isActive: Boolean) {
    val bgColor = if (isActive) BlueLight else Color(0xFFF5F5F5)
    val textColor = if (isActive) BluePrimary else GrayText
    val label = if (isActive) "활동중" else "비활동"
    val dotColor = if (isActive) BluePrimary else GrayText

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
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
            .clip(RoundedCornerShape(12.dp))
            .background(GrayBackground.copy(alpha = 0.3f))
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = GrayText
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
                text = suffix,
                fontSize = 13.sp,
                color = GrayText,
                modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
            )
        }
    }
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