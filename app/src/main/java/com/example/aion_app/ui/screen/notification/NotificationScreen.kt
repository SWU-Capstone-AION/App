package com.example.aion_app.ui.screen.notification

import androidx.compose.runtime.key

import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.toMutableStateList

import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.GrayBackground
import com.example.aion_app.ui.theme.TextPrimary

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.component.AionBottomNavBar
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    studentNames: List<String> = listOf("김지우", "이주미"),
    notifications: List<NotificationItem> = defaultNotifications(),
    onBackClick: () -> Unit = {}
) {
    // 현재 선택된 필터
    var selectedFilter: NotificationFilter by remember {
        mutableStateOf(NotificationFilter.All)
    }

    // 삭제 가능하도록 state로 관리
    val notificationList = remember { notifications.toMutableStateList() }

    Scaffold(
        topBar = {
            AionTopBar(
                title = "알림센터",
                onBackClick = onBackClick
            )
        },
        bottomBar = { AionBottomNavBar(selected = "home") },
        containerColor = White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 필터 칩 목록 (가로 스크롤)
            FilterChipRow(
                studentNames = studentNames,
                selectedFilter = selectedFilter,
                onFilterSelect = { selectedFilter = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 알림 목록 (날짜별 그룹핑 + 세로 스크롤)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // 선택된 필터에 따라 알림 필터링
                val filteredNotifications = when (val f = selectedFilter) {
                    is NotificationFilter.All -> notificationList
                    is NotificationFilter.Student -> notificationList.filter { it.studentName == f.name }
                }

                // 날짜별로 그룹핑 (원본 순서 유지)
                val grouped = filteredNotifications.groupBy { it.dateGroup }

                grouped.forEach { (dateGroup, items) ->
                    // 날짜 그룹 헤더
                    DateGroupHeader(dateText = dateGroup)
                    Spacer(modifier = Modifier.height(8.dp))

                    // 해당 날짜의 알림 카드들
                    items.forEach { notification ->
                        key(notification.id) {  // ← 감쌈
                            SwipeableNotificationCard(
                                item = notification,
                                onDelete = { notificationList.remove(notification) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun NotificationScreenPreview() {
    MaterialTheme {
        NotificationScreen()
    }
}

@Composable
private fun FilterChipRow(
    studentNames: List<String>,
    selectedFilter: NotificationFilter,
    onFilterSelect: (NotificationFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // 전체 칩
        FilterChip(
            label = "전체",
            isSelected = selectedFilter is NotificationFilter.All,
            onClick = { onFilterSelect(NotificationFilter.All) }
        )

        // 학생별 칩
        studentNames.forEach { name ->
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                label = name,
                isSelected = selectedFilter is NotificationFilter.Student
                        && selectedFilter.name == name,
                onClick = { onFilterSelect(NotificationFilter.Student(name)) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BluePrimary else GrayBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) White else TextPrimary
        )
    }
}

@Composable
private fun DateGroupHeader(dateText: String) {
    Text(
        text = dateText,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = GrayText,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNotificationCard(
    item: NotificationItem,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.3f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,  // 왼→오는 비활성
        enableDismissFromEndToStart = true,   // 오→왼(왼쪽으로 스와이프)만 활성
        backgroundContent = {
            // 스와이프 시 뒤에 나타나는 빨간 배경
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE53935))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "삭제",
                        tint = White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "삭제",
                        color = White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) {
        NotificationCard(item = item)
    }
}

@Composable
private fun NotificationCard(item: NotificationItem) {
    val (barColor, iconBgColor, iconColor, icon) = when (item.type) {
        NotificationType.DANGER -> NotificationStyle(
            barColor = Color(0xFFE53935),         // 빨강
            iconBgColor = Color(0xFFFDECEA),      // 연한 빨강
            iconColor = Color(0xFFE53935),
            icon = Icons.Filled.PriorityHigh
        )
        NotificationType.CAUTION -> NotificationStyle(
            barColor = Color(0xFFFF9800),         // 주황
            iconBgColor = Color(0xFFFFF3E0),      // 연한 주황
            iconColor = Color(0xFFFF9800),
            icon = Icons.Filled.PriorityHigh
        )
        NotificationType.STABLE -> NotificationStyle(
            barColor = Color(0xFF4CAF50),         // 초록
            iconBgColor = Color(0xFFE8F5E9),      // 연한 초록
            iconColor = Color(0xFF4CAF50),
            icon = Icons.Filled.Check
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .border(
                width = 1.dp,
                color = Color(0xFFEEEEEE),
                shape = RoundedCornerShape(12.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽 색상 세로 바
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(72.dp)
                .background(barColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 아이콘 박스
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 텍스트 영역
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.type.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = item.timeText,
                    fontSize = 11.sp,
                    color = GrayText,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.message,
                fontSize = 13.sp,
                color = GrayText,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// 알림 스타일을 묶어서 반환하기 위한 헬퍼 (구조 분해용)
private data class NotificationStyle(
    val barColor: Color,
    val iconBgColor: Color,
    val iconColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

// 테스트용 더미 데이터
private fun defaultNotifications(): List<NotificationItem> = listOf(
    NotificationItem(
        id = "1",
        type = NotificationType.DANGER,
        message = "즉시 상태를 확인하세요.",
        studentName = "김지우",
        dateGroup = "오늘 · 5월 28일",
        timeText = "2분 전"
    ),
    NotificationItem(
        id = "2",
        type = NotificationType.CAUTION,
        message = "김지우 학생의 위험점수가 상승 중이에요.",
        studentName = "김지우",
        dateGroup = "오늘 · 5월 28일",
        timeText = "2분 전"
    ),
    NotificationItem(
        id = "3",
        type = NotificationType.CAUTION,
        message = "김지우 학생의 위험점수가 상승 중이에요.",
        studentName = "김지우",
        dateGroup = "오늘 · 5월 28일",
        timeText = "2분 전"
    ),
    NotificationItem(
        id = "4",
        type = NotificationType.STABLE,
        message = "김지우 학생이 정상 범위로 돌아왔어요.",
        studentName = "김지우",
        dateGroup = "오늘 · 5월 28일",
        timeText = "2분 전"
    ),
    NotificationItem(
        id = "5",
        type = NotificationType.STABLE,
        message = "이주미 학생이 정상 범위로 돌아왔어요.",
        studentName = "이주미",
        dateGroup = "오늘 · 5월 28일",
        timeText = "2분 전"
    ),
    NotificationItem(
        id = "6",
        type = NotificationType.CAUTION,
        message = "이주미 학생의 위험점수가 상승 중이에요.",
        studentName = "이주미",
        dateGroup = "오늘 · 5월 28일",
        timeText = "2분 전"
    ),
    NotificationItem(
        id = "7",
        type = NotificationType.STABLE,
        message = "김지우 학생이 정상 범위로 돌아왔어요.",
        studentName = "김지우",
        dateGroup = "어제 · 5월 27일",
        timeText = "1일 전"
    ),
    NotificationItem(
        id = "8",
        type = NotificationType.DANGER,
        message = "즉시 상태를 확인하세요.",
        studentName = "이주미",
        dateGroup = "어제 · 5월 27일",
        timeText = "1일 전"
    )
)