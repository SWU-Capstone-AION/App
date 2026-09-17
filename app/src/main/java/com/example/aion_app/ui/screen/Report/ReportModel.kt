package com.example.aion_app.ui.screen.report

import java.util.Calendar
import java.util.Locale

// ============================================================
// 분석 리포트 데이터 모델
// 위치: ui/screen/report/ReportModel.kt
// ============================================================

// 리포트 기간 탭 (일간 / 주간 / 월간)
enum class ReportPeriod(val label: String) {
    DAILY("일간"),
    WEEKLY("주간"),
    MONTHLY("월간")
}

// 위험 레벨 (그래프·달력 점 색상용)
enum class RiskLevel {
    SAFE,       // 안정 (초록 점)
    CAUTION,    // 주의 (주황 점)
    DANGER      // 위험 (빨강 점)
}

// 그래프 임계값 (막대 색·점선 위치 기준)
object RiskThreshold {
    const val CAUTION = 40   // 주의선
    const val DANGER = 70    // 위험선
}

// 월간 달력 점 색 기준 (하루 건수)
// 건수를 "안정으로 돌아올 때까지 한 덩어리 = 1건"으로 세서 하루 1~3건 수준.
// 더미 다시 넣은 뒤 실제 분포 보고 백엔드와 다시 정할 값.
object CalendarDotRule {
    const val DANGER_MIN = 1
    const val CAUTION_MIN = 1
}

// 불러오기 상태 (불러오는 중 / 성공 / 실패)
sealed interface ReportLoadState<out T> {
    object Loading : ReportLoadState<Nothing>
    data class Success<T>(val data: T) : ReportLoadState<T>
    data class Error(val message: String) : ReportLoadState<Nothing>
}

// ---------- 공통 ----------

data class ReportStudent(
    val id: String,              // Firebase 아동 uid (리포트 API의 childId)
    val name: String,
    val gender: String,          // "남" / "여"
    val age: Int,
    val grade: Int? = null,      // 계정에 학급 정보가 없어서 지금은 비워둠
    val classNum: Int? = null,
    val teacher: String = "",
    val isActive: Boolean = true
)

data class AiInsight(
    val tag: String,         // "취약 시간대", "감지 성과", "취약 요일"
    val title: String,       // "12~13시 집중 발생"
    val description: String  // 본문 설명
)

// 시간대별 평균 위험 점수 (막대 그래프 한 칸)
data class HourlyRisk(
    val hour: Int,     // 8 ~ 15
    val score: Int?    // 0 ~ 100, 기록 없으면 null (0점과 구분)
)

// ---------- 일간 ----------

data class DailyReport(
    val dateLabel: String,        // "05.25 월"
    val detailDateLabel: String,  // "2026.05.25"
    val cautionCount: Int,
    val dangerCount: Int,
    val hourlyRisks: List<HourlyRisk>,
    val insights: List<AiInsight>
)

// ---------- 주간 ----------

// 주간 히트맵 한 칸 (요일 × 시간)
data class HeatCell(
    val dayIndex: Int,   // 0=월 ... 4=금
    val hour: Int,       // 8 ~ 15
    val score: Int?      // 0 ~ 100, 기록 없으면 null (회색 칸)
)

data class WeeklyReport(
    val dateLabel: String,        // "05.18 월 - 05.22 금"
    val detailDateLabel: String,  // "2026.05.18 - 05.22"
    val cautionCount: Int,
    val dangerCount: Int,
    val heatCells: List<HeatCell>,
    val insights: List<AiInsight>
)

// ---------- 월간 ----------

data class CalendarDay(
    val day: Int,             // 날짜 (1~31)
    val inMonth: Boolean,     // 이번 달이면 true, 이전/다음 달이면 false(회색)
    val dotLevel: RiskLevel?  // 날짜 밑 점 색 (없으면 null)
)

data class MonthlyReport(
    val monthLabel: String,       // "5월"
    val detailDateLabel: String,  // "2026.05"
    val cautionCount: Int,
    val dangerCount: Int,
    val calendarDays: List<CalendarDay>,
    val hourlyRisks: List<HourlyRisk>,   // 서버가 안 주면 빈 목록 (그래프 숨김)
    val insights: List<AiInsight>
)

// ---------- 학생별 리포트 ----------
// 기간별 값은 ViewModel이 서버에서 불러오므로 여기엔 학생 정보만 둔다.

data class StudentReport(
    val student: ReportStudent
)

// ============================================================
// 날짜 계산 & 라벨
// offset 0 = 오늘(이번 주/이번 달), 음수 = 과거
// ============================================================

object ReportDates {

    private val WEEKDAY_KR = arrayOf("일", "월", "화", "수", "목", "금", "토")
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun today(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun dayOf(offsetDays: Int): Calendar =
        today().apply { add(Calendar.DAY_OF_MONTH, offsetDays) }

    /** offset 주의 월요일 */
    fun mondayOf(offsetWeeks: Int): Calendar = today().apply {
        add(Calendar.DAY_OF_MONTH, offsetWeeks * 7)
        val daysFromMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7  // 월=0 ... 일=6
        add(Calendar.DAY_OF_MONTH, -daysFromMonday)
    }

    /** offset 달의 1일 */
    fun monthOf(offsetMonths: Int): Calendar = today().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, offsetMonths)
    }

    fun apiDate(cal: Calendar): String = String.format(
        Locale.US, "%04d-%02d-%02d",
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
    )

    fun dailyLabel(cal: Calendar): String {
        val dow = WEEKDAY_KR[cal.get(Calendar.DAY_OF_WEEK) - 1]
        return "${p2(cal.get(Calendar.MONTH) + 1)}.${p2(cal.get(Calendar.DAY_OF_MONTH))} $dow"
    }

    fun dailyDetailLabel(cal: Calendar): String =
        "${cal.get(Calendar.YEAR)}.${p2(cal.get(Calendar.MONTH) + 1)}.${p2(cal.get(Calendar.DAY_OF_MONTH))}"

    fun weeklyLabel(monday: Calendar): String {
        val fri = friday(monday)
        return "${p2(monday.get(Calendar.MONTH) + 1)}.${p2(monday.get(Calendar.DAY_OF_MONTH))} 월 - " +
                "${p2(fri.get(Calendar.MONTH) + 1)}.${p2(fri.get(Calendar.DAY_OF_MONTH))} 금"
    }

    fun weeklyDetailLabel(monday: Calendar): String {
        val fri = friday(monday)
        return "${monday.get(Calendar.YEAR)}.${p2(monday.get(Calendar.MONTH) + 1)}.${p2(monday.get(Calendar.DAY_OF_MONTH))} - " +
                "${p2(fri.get(Calendar.MONTH) + 1)}.${p2(fri.get(Calendar.DAY_OF_MONTH))}"
    }

    /** 올해면 "5월", 다른 해면 "2025년 12월" */
    fun monthLabel(cal: Calendar): String {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return if (year == today().get(Calendar.YEAR)) "${month}월" else "${year}년 ${month}월"
    }

    fun monthDetailLabel(cal: Calendar): String =
        "${cal.get(Calendar.YEAR)}.${p2(cal.get(Calendar.MONTH) + 1)}"

    /** 달력에서 누른 날짜 → 오늘 기준 일 offset */
    fun dayOffsetFor(year: Int, month1: Int, day: Int): Int {
        val target = today().apply { set(year, month1 - 1, day) }
        val diff = target.timeInMillis - today().timeInMillis
        return Math.round(diff.toDouble() / DAY_MS).toInt()
    }

    private fun friday(monday: Calendar): Calendar =
        (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 4) }

    private fun p2(n: Int) = n.toString().padStart(2, '0')
}

// 월요일 시작 달력 칸 목록 (앞뒤 빈칸은 이전/다음 달 날짜로 채움)
fun buildMonthCalendar(year: Int, month1: Int, levels: Map<Int, RiskLevel?>): List<CalendarDay> {
    val cal = Calendar.getInstance().apply {
        clear()
        set(year, month1 - 1, 1)
    }
    val leading = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7  // 1일이 월요일이면 0
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val prevMax = (cal.clone() as Calendar)
        .apply { add(Calendar.MONTH, -1) }
        .getActualMaximum(Calendar.DAY_OF_MONTH)

    val cells = mutableListOf<CalendarDay>()

    for (i in 0 until leading) {
        cells.add(CalendarDay(prevMax - leading + 1 + i, inMonth = false, dotLevel = null))
    }
    for (d in 1..daysInMonth) {
        cells.add(CalendarDay(d, inMonth = true, dotLevel = levels[d]))
    }
    var next = 1
    while (cells.size % 7 != 0) {
        cells.add(CalendarDay(next++, inMonth = false, dotLevel = null))
    }
    return cells
}

// ============================================================
// 리포트 목록 학생 (임시)
//
// 목록 화면을 Firestore 담당 아동 목록과 연결하기 전까지 쓰는 값.
// 서버 테스트가 되도록 김지우·이주미 id는 실제 테스트 계정 uid로 넣어둠.
// ============================================================

fun defaultReportStudents(): List<ReportStudent> = listOf(
    ReportStudent(id = "sKG2RTBpZqhi8yNE7EQyW9Mvnc22", name = "김지우", gender = "남", age = 9),
    ReportStudent(id = "ymLs9qwh8kcY5Q5WKnY1ui4Nacw2", name = "이주미", gender = "여", age = 9),
    ReportStudent(id = "3", name = "전소미", gender = "여", age = 9)
)

fun sampleStudentReport(studentId: String = "ymLs9qwh8kcY5Q5WKnY1ui4Nacw2"): StudentReport {
    val student = defaultReportStudents().firstOrNull { it.id == studentId }
        ?: defaultReportStudents()[1]
    return StudentReport(student = student)
}

// ============================================================
// 미리보기(Preview) 전용 값 — 실제 화면에서는 안 쓰임
// ============================================================

fun previewDailyReport(): DailyReport = DailyReport(
    dateLabel = "05.25 월",
    detailDateLabel = "2026.05.25",
    cautionCount = 2,
    dangerCount = 1,
    hourlyRisks = listOf(
        8 to null, 9 to 12, 10 to 25, 11 to 38, 12 to 64, 13 to 82, 14 to 51, 15 to null
    ).map { HourlyRisk(it.first, it.second) },
    insights = listOf(
        AiInsight("취약 시간대", "13~14시 집중 발생", "13~14시 사이의 위험점수가 다른 시간대보다 평균적으로 16% 높았습니다."),
        AiInsight("감지 성과", "팔 흔들기 행동", "팔을 좌우로/앞뒤로 흔드는 행동을 가장 많이 감지했어요.")
    )
)

fun previewWeeklyReport(): WeeklyReport = WeeklyReport(
    dateLabel = "05.18 월 - 05.22 금",
    detailDateLabel = "2026.05.18 - 05.22",
    cautionCount = 3,
    dangerCount = 2,
    heatCells = (0..4).flatMap { d ->
        (8..15).map { h ->
            val score = when {
                d == 2 && h == 12 -> 85
                (d + h) % 4 == 0 -> null
                else -> (d * 7 + h * 3) % 40
            }
            HeatCell(d, h, score)
        }
    },
    insights = listOf(
        AiInsight("감지 성과", "팔 흔들기 행동", "팔을 좌우로/앞뒤로 흔드는 행동을 가장 많이 감지했어요."),
        AiInsight("취약 시간대", "수요일 12~13시 집중 발생", "수요일 12~13시 사이의 위험점수가 다른 시간대보다 평균적으로 30% 높았습니다.")
    )
)

fun previewMonthlyReport(): MonthlyReport = MonthlyReport(
    monthLabel = "5월",
    detailDateLabel = "2026.05",
    cautionCount = 8,
    dangerCount = 5,
    calendarDays = buildMonthCalendar(
        2026, 5,
        mapOf(4 to RiskLevel.SAFE, 6 to RiskLevel.CAUTION, 13 to RiskLevel.DANGER, 20 to RiskLevel.SAFE)
    ),
    hourlyRisks = previewDailyReport().hourlyRisks,
    insights = listOf(
        AiInsight("취약 요일", "수요일 집중 발생", "이번 달에는 수요일에 위험 감지가 가장 많았습니다."),
        AiInsight("감지 성과", "팔 흔들기 행동", "팔을 좌우로/앞뒤로 흔드는 행동을 가장 많이 감지했어요.")
    )
)