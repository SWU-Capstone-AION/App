package com.example.aion_app.ui.screen.report

// ============================================================
// 분석 리포트 데이터 모델
// 위치: ui/screen/report/ReportModel.kt
// 홈(HomeModel)·알림(NotificationModel)과 동일한 패턴:
//   - enum / data class 로 모델 정의
//   - 화면에서 쓸 더미데이터를 이 파일의 빌더 함수로 제공
// ============================================================

// 리포트 기간 탭 (일간 / 주간 / 월간)
enum class ReportPeriod(val label: String) {
    DAILY("일간"),
    WEEKLY("주간"),
    MONTHLY("월간")
}

// 위험 레벨 (그래프·달력 점 색상용)
enum class RiskLevel {
    SAFE,       // 안전/안정 (초록 점)
    CAUTION,    // 주의 (주황 점)
    DANGER      // 위험 (빨강 점)
}

// 그래프 임계값 (막대 색·점선 위치 기준)
object RiskThreshold {
    const val CAUTION = 40   // 주의선
    const val DANGER = 70    // 위험선
}

// ---------- 공통 ----------

// 리포트 대상 학생 (목록 카드 + 상세 헤더 공용)
data class ReportStudent(
    val id: String,
    val name: String,
    val gender: String,     // "남" / "여"
    val age: Int,
    val grade: Int = 3,
    val classNum: Int = 4,
    val teacher: String = "박서연",
    val isActive: Boolean = true
)

// AI 인사이트 카드
data class AiInsight(
    val tag: String,         // "취약 시간대", "감지 성과", "취약 요일"
    val title: String,       // "12~13시 집중 발생"
    val description: String  // 본문 설명
)

// 시간대별 평균 위험 점수 (막대 그래프 한 칸)
data class HourlyRisk(
    val hour: Int,   // 9 ~ 15
    val score: Int   // 0 ~ 100
)

// ---------- 일간 ----------

data class DailyReport(
    val dateLabel: String,        // "05.25 월"
    val detailDateLabel: String,  // "2026.05.25"
    val cautionCount: Int,        // 주의 감지 건수
    val dangerCount: Int,         // 위험 감지 건수
    val hourlyRisks: List<HourlyRisk>,
    val insights: List<AiInsight>
)

// ---------- 주간 ----------

// 주간 히트맵 한 칸 (요일 × 시간)
data class HeatCell(
    val dayIndex: Int,   // 0=월 ... 4=금
    val hour: Int,       // 8 ~ 15
    val intensity: Float // 0f ~ 1f (진할수록 위험)
)

data class WeeklyReport(
    val dateLabel: String,        // "05.18 월 - 05.22 금"
    val detailDateLabel: String,  // "2026.05.25 - 05.29"
    val cautionCount: Int,
    val dangerCount: Int,
    val attendance: Int,          // 출석 (4)
    val attendanceTotal: Int,     // 전체 (5)
    val heatCells: List<HeatCell>,
    val insights: List<AiInsight>
)

// ---------- 월간 ----------

// 달력 한 칸
data class CalendarDay(
    val day: Int,             // 날짜 (1~31)
    val inMonth: Boolean,     // 이번 달이면 true, 다음/이전 달이면 false(회색)
    val dotLevel: RiskLevel?  // 날짜 밑 점 색 (없으면 null)
)

data class MonthlyReport(
    val monthLabel: String,       // "5월"
    val detailDateLabel: String,  // "2026.05.25"
    val cautionCount: Int,
    val dangerCount: Int,
    val attendance: Int,          // 28
    val attendanceTotal: Int,     // 30
    val calendarDays: List<CalendarDay>,
    val hourlyRisks: List<HourlyRisk>,
    val insights: List<AiInsight>
)

// ---------- 학생별 전체 리포트 묶음 ----------

data class StudentReport(
    val student: ReportStudent,
    val daily: DailyReport,
    val weekly: WeeklyReport,
    val monthly: MonthlyReport
)

// ============================================================
// 더미 데이터 (백엔드 연동 전 UI 테스트용)
// ============================================================

// 리포트 목록 화면의 학생들 (시안 page 1)
fun defaultReportStudents(): List<ReportStudent> = listOf(
    ReportStudent(id = "1", name = "김지우", gender = "남", age = 9),
    ReportStudent(id = "2", name = "이주미", gender = "여", age = 9),
    ReportStudent(id = "3", name = "전소미", gender = "여", age = 9)
)

// studentId 로 학생을 찾아 상세 리포트를 만들어 줌.
// (지금은 그래프/요약 수치는 동일한 샘플, 학생 정보만 바꿔서 반환)
fun sampleStudentReport(studentId: String = "2"): StudentReport {
    val student = defaultReportStudents().firstOrNull { it.id == studentId }
        ?: defaultReportStudents()[1]  // 기본값: 이주미 (시안 기준)
    return StudentReport(
        student = student,
        daily = sampleDaily(),
        weekly = sampleWeekly(),
        monthly = sampleMonthly()
    )
}

private fun sampleDaily() = DailyReport(
    dateLabel = "05.25 월",
    detailDateLabel = "2026.05.25",
    cautionCount = 4,
    dangerCount = 3,
    hourlyRisks = listOf(
        HourlyRisk(9, 4),
        HourlyRisk(10, 14),
        HourlyRisk(11, 24),
        HourlyRisk(12, 76),
        HourlyRisk(13, 94),
        HourlyRisk(14, 50),
        HourlyRisk(15, 80)
    ),
    insights = listOf(
        AiInsight(
            tag = "취약 시간대",
            title = "12~13시 집중 발생",
            description = "12~13시 사이의 위험점수가 다른 시간대보다 평균적으로 30% 높았습니다."
        ),
        AiInsight(
            tag = "감지 성과",
            title = "팔 흔들기 행동",
            description = "팔을 좌우로/앞뒤로 흔드는 행동을 가장 많이 감지했어요."
        )
    )
)

private fun sampleWeekly() = WeeklyReport(
    dateLabel = "05.18 월 - 05.22 금",
    detailDateLabel = "2026.05.25 - 05.29",
    cautionCount = 5,
    dangerCount = 4,
    attendance = 4,
    attendanceTotal = 5,
    heatCells = weeklyHeatCells(),
    insights = listOf(
        AiInsight(
            tag = "감지 성과",
            title = "팔 흔들기 행동",
            description = "팔을 좌우로/앞뒤로 흔드는 행동을 가장 많이 감지했어요."
        ),
        AiInsight(
            tag = "취약 시간대",
            title = "수요일 12~13시 집중 발생",
            description = "수요일 12~13시 사이의 위험점수가 다른 시간대보다 평균적으로 30% 높았습니다."
        )
    )
)

// 요일(0=월~4=금) × 시간(8~15) 40칸. 몇 군데만 강한 색, 나머지는 옅은 배경.
private fun weeklyHeatCells(): List<HeatCell> {
    val hotspots = mapOf(
        (1 to 10) to 0.90f,  // 화 10시
        (2 to 11) to 1.00f,  // 수 11시
        (4 to 11) to 0.85f,  // 금 11시
        (0 to 14) to 0.90f   // 월 14시
    )
    val cells = mutableListOf<HeatCell>()
    for (day in 0..4) {
        for (hour in 8..15) {
            val base = (((day * 3 + hour) % 4) * 0.12f) + 0.06f
            val intensity = hotspots[day to hour] ?: base
            cells.add(HeatCell(day, hour, intensity.coerceIn(0f, 1f)))
        }
    }
    return cells
}

private fun sampleMonthly() = MonthlyReport(
    monthLabel = "5월",
    detailDateLabel = "2026.05.25",
    cautionCount = 20,
    dangerCount = 15,
    attendance = 28,
    attendanceTotal = 30,
    calendarDays = mayCalendar(),
    hourlyRisks = listOf(
        HourlyRisk(9, 4),
        HourlyRisk(10, 14),
        HourlyRisk(11, 24),
        HourlyRisk(12, 76),
        HourlyRisk(13, 94),
        HourlyRisk(14, 50),
        HourlyRisk(15, 80)
    ),
    insights = listOf(
        AiInsight(
            tag = "취약 요일",
            title = "수요일 집중 발생",
            description = "이번 달에는 수요일의 위험 감지가 다른 요일보다 평균적으로 10% 높았습니다."
        ),
        AiInsight(
            tag = "감지 성과",
            title = "팔 흔들기 행동",
            description = "팔을 좌우로/앞뒤로 흔드는 행동을 가장 많이 감지했어요."
        )
    )
)

// 시안 그대로: 1일을 '월요일' 칸에 배치하고 1~30 채운 뒤, 다음 달 1~5를 회색으로 채움.
// (실제 데이터 연동 시에는 Calendar 로 요일 오프셋을 계산해서 앞쪽 빈칸을 넣어야 함)
private fun mayCalendar(): List<CalendarDay> {
    val dots = mapOf(
        1 to RiskLevel.DANGER,
        2 to RiskLevel.SAFE,
        3 to RiskLevel.SAFE,
        4 to RiskLevel.CAUTION,
        5 to RiskLevel.SAFE,
        8 to RiskLevel.DANGER,
        9 to RiskLevel.SAFE,
        10 to RiskLevel.SAFE,
        11 to RiskLevel.CAUTION,
        12 to RiskLevel.SAFE
    )
    val cells = mutableListOf<CalendarDay>()
    for (d in 1..30) {
        cells.add(CalendarDay(day = d, inMonth = true, dotLevel = dots[d]))
    }
    // 마지막 주 채우기 (다음 달 1~5, 회색)
    var next = 1
    while (cells.size % 7 != 0) {
        cells.add(CalendarDay(day = next, inMonth = false, dotLevel = null))
        next++
    }
    return cells
}
