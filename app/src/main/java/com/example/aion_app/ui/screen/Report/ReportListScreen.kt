package com.example.aion_app.ui.screen.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.component.AionBottomNavBar
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

@Composable
fun ReportListScreen(
    students: List<ReportStudent> = defaultReportStudents(),
    onStudentClick: (ReportStudent) -> Unit = {},
    onTabSelect: (String) -> Unit = {}
) {
    Scaffold(
        topBar = { ReportListTopBar() },
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
            Spacer(modifier = Modifier.height(24.dp))

            students.forEach { student ->
                StudentReportCard(
                    student = student,
                    onClick = { onStudentClick(student) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// 목록 화면 상단바 (뒤로가기 없이 가운데 제목만 — 하단탭 루트 화면이라 AionTopBar 대신 별도 구성)
@Composable
private fun ReportListTopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(White)
            .height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "리포트",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,   // Pretendard Bold (AionTheme 안에서만 적용됨)
            color = TextPrimary
        )
    }
    // 하단 스트로크: 디자인 확정 색 #2D3C4A (= AionTextDark), 두께는 기존 0.5dp 유지
    HorizontalDivider(thickness = 0.5.dp, color = AionTextDark)
}

@Composable
private fun StudentReportCard(
    student: ReportStudent,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(White)
            .border(
                width = 1.dp,
                // 테두리: 디자인 확정 색 #CFDEF9 (= Blue Light :active 토큰과 동일 헥스)
                color = LightActive,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 프로필 자리 (회색 라운드 박스)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF0F1F3))
        )

        Spacer(modifier = Modifier.width(14.dp))

        // 이름
        Text(
            text = student.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,   // Pretendard Bold
            color = TextPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        // 성별 · 나이
        Text(
            text = "${student.gender} · ${student.age}세",
            fontSize = 12.sp,
            color = GrayText
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = GrayText
        )
    }
}

// 프리뷰도 AionTheme 으로 감싸야 Pretendard 가 적용된다.
// MaterialTheme 로 두면 시스템 기본 폰트로 떨어져서 볼드가 얇아 보임.
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ReportListScreenPreview() {
    AionTheme {
        ReportListScreen()
    }
}