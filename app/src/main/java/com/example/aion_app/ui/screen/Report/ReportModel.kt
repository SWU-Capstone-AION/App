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

/**
 * 막대그래프 한 칸.
 * 일간은 시간대(8~15시), 월간은 요일(월~일)을 그린다.
 * score 가 null 이면 기록이 없는 칸 — 막대를 그리지 않는다(0점과 구분).
 */
data class ChartBar(
    val key: Int,               // 시간 또는 요일 번호 (선택 표시용)
    val axisLabel: String,      // 축에 쓸 짧은 글자 ("09", "월")
    val selectedLabel: String,  // 눌렀을 때 보여줄 글자 ("9시", "월요일")
    val score: Int?             // 0 ~ 100
)

// ---------- 일간 ----------

data class DailyReport(
    val dateLabel: String,        // "08.26 수"
    val detailDateLabel: String,  // "2026.08.26"
    val cautionCount: Int,
    val dangerCount: Int,
    val hourlyRisks: List<ChartBar>,
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
    val dateLabel: String,        // "08.24 월 - 08.28 금"
    val detailDateLabel: String,  // "2026.08.24 - 08.28"
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
    val monthLabel: String,       // "8월"
    val detailDateLabel: String,  // "2026.08"
    val cautionCount: Int,
    val dangerCount: Int,
    val calendarDays: List<CalendarDay>,
    // 월간은 서버가 시간대 값을 안 줘서 요일별 평균을 그린다
    val weekdayRisks: List<ChartBar>,
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

    /** "2026-08-24" → Calendar. 모양이 다르면 null. */
    fun parse(dateText: String?): Calendar? {
        val parts = dateText?.take(10)?.split("-") ?: return null
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return Calendar.getInstance().apply { clear(); set(y, m - 1, d) }
    }

    fun dailyLabel(cal: Calendar): String {
        val dow = WEEKDAY_KR[cal.get(Calendar.DAY_OF_WEEK) - 1]
        return "${p2(cal.get(Calendar.MONTH) + 1)}.${p2(cal.get(Calendar.DAY_OF_MONTH))} $dow"
    }

    fun dailyDetailLabel(cal: Calendar): String =
        "${cal.get(Calendar.YEAR)}.${p2(cal.get(Calendar.MONTH) + 1)}.${p2(cal.get(Calendar.DAY_OF_MONTH))}"

    fun weeklyLabel(monday: Calendar): String = weeklyLabel(monday, friday(monday))

    fun weeklyDetailLabel(monday: Calendar): String = weeklyDetailLabel(monday, friday(monday))

    /** 서버가 준 시작·끝 날짜로 만든다. 값이 없으면 null. */
    fun weeklyLabelFrom(startDate: String?, endDate: String?): String? {
        val start = parse(startDate) ?: return null
        val end = parse(endDate) ?: return null
        return weeklyLabel(start, end)
    }

    fun weeklyDetailLabelFrom(startDate: String?, endDate: String?): String? {
        val start = parse(startDate) ?: return null
        val end = parse(endDate) ?: return null
        return weeklyDetailLabel(start, end)
    }

    /** 올해면 "8월", 다른 해면 "2025년 12월" */
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

    private fun weeklyLabel(start: Calendar, end: Calendar): String {
        val startDow = WEEKDAY_KR[start.get(Calendar.DAY_OF_WEEK) - 1]
        val endDow = WEEKDAY_KR[end.get(Calendar.DAY_OF_WEEK) - 1]
        return "${p2(start.get(Calendar.MONTH) + 1)}.${p2(start.get(Calendar.DAY_OF_MONTH))} $startDow - " +
                "${p2(end.get(Calendar.MONTH) + 1)}.${p2(end.get(Calendar.DAY_OF_MONTH))} $endDow"
    }

    private fun weeklyDetailLabel(start: Calendar, end: Calendar): String =
        "${start.get(Calendar.YEAR)}.${p2(start.get(Calendar.MONTH) + 1)}.${p2(start.get(Calendar.DAY_OF_MONTH))} - " +
                "${p2(end.get(Calendar.MONTH) + 1)}.${p2(end.get(Calendar.DAY_OF_MONTH))}"

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
// 리포트 목록 학생 (미리보기 전용)
//
// 실제 목록은 ReportListViewModel 이 담당 아동을 불러온다.
// ============================================================

fun defaultReportStudents(): List<ReportStudent> = listOf(
    ReportStudent(id = "preview-1", name = "김지우", gender = "남", age = 9),
    ReportStudent(id = "preview-2", name = "이주미", gender = "여", age = 9),
    ReportStudent(id = "preview-3", name = "전소미", gender = "여", age = 9)
)

fun sampleStudentReport(studentId: String = "preview-2"): StudentReport {
    val student = defaultReportStudents().firstOrNull { it.id == studentId }
        ?: defaultReportStudents()[1]
    return StudentReport(student = student)
}

// ============================================================
// 미리보기(Preview) 전용 값 — 실제 화면에서는 안 쓰임
// ============================================================

private fun hourBar(hour: Int, score: Int?) = ChartBar(
    key = hour,
    axisLabel = hour.toString().padStart(2, '0'),
    selectedLabel = "${hour}시",
    score = score
)

fun previewDailyReport(): DailyReport = DailyReport(
    dateLabel = "08.26 수",
    detailDateLabel = "2026.08.26",
    cautionCount = 7,
    dangerCount = 5,
    hourlyRisks = listOf(
        hourBar(8, null), hourBar(9, 49), hourBar(10, 42), hourBar(11, null),
        hourBar(12, 50), hourBar(13, 47), hourBar(14, 57), hourBar(15, 56)
    ),
    insights = listOf(
        AiInsight("취약 시간대", "14~15시 집중 발생", "14~15시 사이의 위험점수가 다른 시간대보다 평균적으로 13% 높았습니다."),
        AiInsight("감지 성과", "몸 흔들기 7건", "가장 많이 감지된 행동이에요.")
    )
)

fun previewWeeklyReport(): WeeklyReport = WeeklyReport(
    dateLabel = "08.24 월 - 08.28 금",
    detailDateLabel = "2026.08.24 - 08.28",
    cautionCount = 20,
    dangerCount = 10,
    heatCells = (0..4).flatMap { d ->
        (8..15).map { h ->
            val score = when {
                d == 4 && h == 11 -> 58
                (d + h) % 3 == 0 -> null
                else -> 40 + (d * 3 + h) % 15
            }
            HeatCell(d, h, score)
        }
    },
    insights = listOf(
        AiInsight("감지 성과", "손 물기 12건", "가장 많이 감지된 행동이에요."),
        AiInsight("취약 시간대", "금요일 11~12시 집중 발생", "금요일 11~12시 사이의 위험점수가 다른 시간대보다 평균적으로 22% 높았습니다.")
    )
)

fun previewMonthlyReport(): MonthlyReport = MonthlyReport(
    monthLabel = "8월",
    detailDateLabel = "2026.08",
    cautionCount = 79,
    dangerCount = 37,
    calendarDays = buildMonthCalendar(
        2026, 8,
        mapOf(3 to RiskLevel.CAUTION, 5 to RiskLevel.DANGER, 12 to RiskLevel.DANGER, 26 to RiskLevel.DANGER)
    ),
    weekdayRisks = listOf("월", "화", "수", "목", "금", "토", "일").mapIndexed { i, name ->
        ChartBar(
            key = i,
            axisLabel = name,
            selectedLabel = "${name}요일",
            score = listOf(46, 49, 49, 45, 48, null, null)[i]
        )
    },
    insights = listOf(
        AiInsight("취약 요일", "수요일 집중 발생", "수요일의 위험 감지가 다른 요일보다 116% 많았습니다."),
        AiInsight("감지 성과", "팔 흔들기 44건", "가장 많이 감지된 행동이에요.")
    )
)