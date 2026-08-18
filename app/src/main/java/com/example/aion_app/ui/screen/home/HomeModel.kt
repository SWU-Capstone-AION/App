package com.example.aion_app.ui.screen.home

// 학생 활동 상태
enum class StudentStatus {
    ACTIVE,     // 활동중
    INACTIVE    // 비활동
}

// 학생 상태 레벨 (스트레스 지수 기반)
enum class StressLevel(val label: String) {
    STABLE("안정"),        // 초록색
    CAUTION("주의"),       // 주황색
    DANGER("위험"),        // 빨간색
    NO_DATA("데이터 없음")  // 회색
}

// 학생 데이터
data class Student(
    val id: String,
    val name: String,
    val gender: String,           // "남" 또는 "여"
    val age: Int,
    val status: StudentStatus,    // 활동중 / 비활동
    val stressScore: Int,         // 0 ~ 100
    val stressLevel: StressLevel, // 안정/주의/위험/데이터없음
    val heartRate: Int? = null    // 심박수 (비활동이면 null)
)

// 반 정보
data class ClassInfo(
    val grade: Int,       // 학년
    val classNum: Int,    // 반
    val date: String      // 날짜 (예: "2026.05.28(목)")
)

// 알림
data class HomeAlert(
    val message: String,  // "김지우 학생이 안정 상태에 도달했습니다."
    val timeText: String  // "1분 전"
)

// 오늘의 학급 현황
data class ClassStats(
    val activeCount: Int,      // 활동중인 학생 수
    val totalCount: Int,       // 전체 학생 수
    val cautionCount: Int,     // 주의 감지 횟수
    val dangerCount: Int       // 위험 감지 횟수
)