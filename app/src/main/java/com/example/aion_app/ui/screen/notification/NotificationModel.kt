package com.example.aion_app.ui.screen.notification

// 알림 유형
enum class NotificationType(val label: String) {
    DANGER("위험"),
    CAUTION("주의"),
    STABLE("안정")
}

// 알림 아이템
data class NotificationItem(
    val id: String,
    val type: NotificationType,         // 위험/주의/안정
    val message: String,                // 알림 내용
    val studentName: String,            // 관련 학생 이름 (필터용)
    val dateGroup: String,              // "오늘 · 5월 28일", "어제 · 5월 27일" 등
    val timeText: String                // "2분 전"
)

// 필터 옵션
sealed class NotificationFilter {
    object All : NotificationFilter()                       // 전체
    data class Student(val name: String) : NotificationFilter()  // 특정 학생
}