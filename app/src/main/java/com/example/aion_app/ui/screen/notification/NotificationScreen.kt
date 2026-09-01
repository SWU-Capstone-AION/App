package com.example.aion_app.ui.screen.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.component.AionBottomNavBar
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.GrayBackground
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.Green
import com.example.aion_app.ui.theme.Orange
import com.example.aion_app.ui.theme.Red
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    // 필터 칩에 쓸 (childId, 이름) 목록.
    // 동명이인이 있을 수 있어 매칭은 id로 하고 화면에는 이름만 보여준다.
    children: List<Pair<String, String>> = emptyList(),
    notifications: List<NotificationItem> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onBackClick: () -> Unit = {},
    onDelete: (NotificationItem) -> Unit = {},
    onTabSelect: (String) -> Unit = {}     // 하단탭 이동 콜백 (이 화면만 빠져 있었음)
) {
    // 현재 선택된 필터
    var selectedFilter: NotificationFilter by remember {
        mutableStateOf(NotificationFilter.All)
    }

    Scaffold(
        topBar = {
            // 칩 위 스트로크: 디자인 확정 색 #2D3C4A
            AionTopBar(
                title = "알림센터",
                onBackClick = onBackClick,
                iconStartPadding = 8.dp,  // 아이콘 꼭짓점을 칩 왼쪽(20dp)에 맞춤
                dividerColor = AionTextDark
            )
        },
        bottomBar = { AionBottomNavBar(selected = "home", onSelect = onTabSelect) },
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
                children = children,
                selectedFilter = selectedFilter,
                onFilterSelect = { selectedFilter = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Normal,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorMessage,
                            fontSize = 14.sp,
                            color = GrayText,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }

                else -> {
                    // 알림 목록 (날짜별 그룹핑 + 세로 스크롤)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                    ) {
                        val filteredNotifications = when (val f = selectedFilter) {
                            is NotificationFilter.All -> notifications
                            is NotificationFilter.Student ->
                                notifications.filter { it.childId == f.childId }
                        }

                        if (filteredNotifications.isEmpty()) {
                            Text(
                                text = "받은 알림이 없어요.",
                                fontSize = 14.sp,
                                color = GrayText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 60.dp)
                            )
                        }

                        // 날짜별로 그룹핑 (원본 순서 유지)
                        val grouped = filteredNotifications.groupBy { it.dateGroup }

                        grouped.forEach { (dateGroup, items) ->
                            DateGroupHeader(dateText = dateGroup)
                            Spacer(modifier = Modifier.height(8.dp))

                            items.forEach { notification ->
                                key(notification.id) {
                                    SwipeableNotificationCard(
                                        item = notification,
                                        onDelete = { onDelete(notification) }
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
    }
}

// 프리뷰도 AionTheme 로 감싸야 Pretendard 가 적용된 상태로 보인다.
@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7")
@Composable
fun NotificationScreenPreview() {
    AionTheme {
        NotificationScreen(
            children = listOf("c1" to "김지우", "c2" to "이주미"),
            notifications = previewNotifications()
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7", name = "연결 실패")
@Composable
fun NotificationScreenErrorPreview() {
    AionTheme {
        NotificationScreen(
            errorMessage = "알림을 불러올 수 없어요.\n네트워크 연결을 확인해 주세요."
        )
    }
}

@Composable
private fun FilterChipRow(
    children: List<Pair<String, String>>,
    selectedFilter: NotificationFilter,
    onFilterSelect: (NotificationFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        FilterChip(
            label = "전체",
            isSelected = selectedFilter is NotificationFilter.All,
            onClick = { onFilterSelect(NotificationFilter.All) }
        )

        children.forEach { (childId, name) ->
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                label = name,
                isSelected = selectedFilter is NotificationFilter.Student
                        && selectedFilter.childId == childId,
                onClick = { onFilterSelect(NotificationFilter.Student(childId)) }
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
            .background(if (isSelected) Normal else GrayBackground)
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
        positionalThreshold = { totalDistance -> totalDistance * 0.3f }
    )

    // confirmValueChange 안에서 곧바로 리스트를 수정하면 스와이프 애니메이션 도중
    // 아이템이 사라져 컴포지션이 흔들릴 수 있어, 상태가 확정된 뒤에 삭제한다.
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Red)   // 삭제 배경
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
    // 위험 #C05C47(Red) / 주의 #CC8D42(Orange) / 안정 #629F7D(Green)
    val (barColor, iconBgColor, iconRes) = when (item.type) {
        NotificationType.DANGER -> NotificationStyle(
            barColor = Red,
            iconBgColor = Red.copy(alpha = 0.14f),
            iconRes = R.drawable.noti_danger
        )
        NotificationType.CAUTION -> NotificationStyle(
            barColor = Orange,
            iconBgColor = Orange.copy(alpha = 0.14f),
            iconRes = R.drawable.noti_caution
        )
        NotificationType.STABLE -> NotificationStyle(
            barColor = Green,
            iconBgColor = Green.copy(alpha = 0.14f),
            iconRes = R.drawable.noti_stable
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
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
        // 72.dp 고정이면 문구가 두 줄로 넘어갈 때 바가 카드보다 짧아져서,
        // 카드 높이를 따라가도록 변경 (한 줄일 때 보이는 모습은 동일)
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
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
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(22.dp)
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

// 알림 스타일 묶음 (구조 분해용)
private data class NotificationStyle(
    val barColor: Color,
    val iconBgColor: Color,
    val iconRes: Int
)

// 프리뷰 전용 더미 데이터
private fun previewNotifications(): List<NotificationItem> = listOf(
    NotificationItem(
        id = "4",
        type = NotificationType.STABLE,
        message = "김지우 학생이 정상 범위로 돌아왔어요.",
        studentName = "김지우",
        childId = "c1",
        dateGroup = "오늘 · 8월 27일",
        timeText = "방금 전"
    ),
    NotificationItem(
        id = "1",
        type = NotificationType.DANGER,
        message = "즉시 상태를 확인하세요.",
        studentName = "김지우",
        childId = "c1",
        dateGroup = "오늘 · 8월 27일",
        timeText = "5분 전"
    ),
    NotificationItem(
        id = "6",
        type = NotificationType.CAUTION,
        message = "이주미 학생의 위험점수가 상승 중이에요.",
        studentName = "이주미",
        childId = "c2",
        dateGroup = "오늘 · 8월 27일",
        timeText = "30분 전"
    ),
)