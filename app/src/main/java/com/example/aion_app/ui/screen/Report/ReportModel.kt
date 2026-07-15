package com.example.aion_app.ui.screen.report

import java.util.Calendar
import kotlin.random.Random

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
    val intensity: Float // 0f ~ 1f (진할수록 위험)
)

data class WeeklyReport(
    val dateLabel: String,        // "05.18 월 - 05.22 금"
    val detailDateLabel: String,  // "2026.05.18 - 05.22"
    val cautionCount: Int,
    val dangerCount: Int,
    val attendance: Int,          // 출석 (4)
    val attendanceTotal: Int,     // 전체 (5)
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
//
// 날짜 이동을 위해, "기준일(2026.05.25)로부터 offset"을 받아
// 해당 날짜/주/월의 리포트를 생성한다.
// offset 을 시드로 쓰므로 같은 날짜는 항상 같은 값이 나온다.
// offset = 0 이 최신(오늘), 음수로 갈수록 과거.
// ============================================================

// 리포트 목록 화면의 학생들 (시안 page 1)
fun defaultReportStudents(): List<ReportStudent> = listOf(
    ReportStudent(id = "1", name = "김지우", gender = "남", age = 9),
    ReportStudent(id = "2", name = "이주미", gender = "여", age = 9),
    ReportStudent(id = "3", name = "전소미", gender = "여", age = 9)
)

// studentId 로 학생을 찾아 오늘(offset 0) 기준 상세 리포트를 만들어 줌.
fun sampleStudentReport(studentId: String = "2"): StudentReport {
    val student = defaultReportStudents().firstOrNull { it.id == studentId }
        ?: defaultReportStudents()[1]  // 기본값: 이주미 (시안 기준)
    return StudentReport(
        student = student,
        daily = dailyReportFor(0),
        weekly = weeklyReportFor(0),
        monthly = monthlyReportFor(0)
    )
}

// ---------- 기준일 & 포맷 헬퍼 ----------

private const val BASE_YEAR = 2026
private const val BASE_MONTH = 5   // 1-based
private const val BASE_DAY = 25

private fun baseCalendar(): Calendar =
    Calendar.getInstance().apply {
        clear()
        set(BASE_YEAR, BASE_MONTH - 1, BASE_DAY)
    }

// Calendar.DAY_OF_WEEK(1=일 ... 7=토) → 한글 요일
private val WEEKDAY_KR = arrayOf("일", "월", "화", "수", "목", "금", "토")

private fun p2(n: Int) = n.toString().padStart(2, '0')

// 정오~오후에 높아지는 기본 패턴 + 시드 노이즈
private fun hourlyPattern(rnd: Random): List<HourlyRisk> = (9..15).map { h ->
    val base = when (h) {
        9 -> 8; 10 -> 18; 11 -> 30; 12 -> 72; 13 -> 88; 14 -> 55; else -> 78
    }
    HourlyRisk(hour = h, score = (base + rnd.nextInt(-15, 21)).coerceIn(0, 100))
}

// ---------- 일간 ----------

fun dailyReportFor(offsetDays: Int): DailyReport {
    val cal = baseCalendar().apply { add(Calendar.DAY_OF_MONTH, offsetDays) }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val dow = WEEKDAY_KR[cal.get(Calendar.DAY_OF_WEEK) - 1]

    val rnd = Random(offsetDays * 31 + 7)
    val hourly = hourlyPattern(rnd)
    val peak = hourly.maxByOrNull { it.score }?.hour ?: 13

    return DailyReport(
        dateLabel = "${p2(month)}.${p2(day)} $dow",
        detailDateLabel = "$year.${p2(month)}.${p2(day)}",
        cautionCount = 2 + rnd.nextInt(4),   // 2~5
        dangerCount = 1 + rnd.nextInt(4),    // 1~4
        hourlyRisks = hourly,
        insights = listOf(
            AiInsight(
                tag = "취약 시간대",
                title = "${peak}~${peak + 1}시 집중 발생",
                description = "${peak}~${peak + 1}시 사이의 위험점수가 다른 시간대보다 평균적으로 높았습니다."
            ),
            AiInsight(
                tag = "감지 성과",
                title = "팔 흔들기 행동",
                description = "팔을 좌우로/앞뒤로 흔드는 행동을 가장 많이 감지했어요."
            )
        )
    )
}

// ---------- 주간 ----------

fun weeklyReportFor(offsetWeeks: Int): WeeklyReport {
    val cal = baseCalendar().apply { add(Calendar.DAY_OF_MONTH, offsetWeeks * 7) }
    // 해당 주의 '월요일'로 이동 (locale 무관하게 직접 계산)
    val daysFromMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7  // 월=0 ... 일=6
    cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)

    val monMonth = cal.get(Calendar.MONTH) + 1
    val monDay = cal.get(Calendar.DAY_OF_MONTH)
    val year = cal.get(Calendar.YEAR)

    val fri = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 4) }
    val friMonth = fri.get(Calendar.MONTH) + 1
    val friDay = fri.get(Calendar.DAY_OF_MONTH)

    val rnd = Random(offsetWeeks * 131 + 17)

    // 히트맵: 몇 칸만 강한 색, 나머지는 옅게
    val hotspots = HashSet<Pair<Int, Int>>()
    repeat(3 + rnd.nextInt(2)) {
        hotspots.add(rnd.nextInt(5) to (8 + rnd.nextInt(8)))
    }
    val cells = mutableListOf<HeatCell>()
    for (dayIdx in 0..4) {
        for (hour in 8..15) {
            val intensity = if ((dayIdx to hour) in hotspots) {
                0.8f + rnd.nextFloat() * 0.2f
            } else {
                rnd.nextFloat() * 0.3f
            }
            cells.add(HeatCell(dayIdx, hour, intensity))
        }
    }

    return WeeklyReport(
        dateLabel = "${p2(monMonth)}.${p2(monDay)} 월 - ${p2(friMonth)}.${p2(friDay)} 금",
        detailDateLabel = "$year.${p2(monMonth)}.${p2(monDay)} - ${p2(friMonth)}.${p2(friDay)}",
        cautionCount = 4 + rnd.nextInt(4),   // 4~7
        dangerCount = 3 + rnd.nextInt(4),    // 3~6
        attendance = 3 + rnd.nextInt(3),     // 3~5
        attendanceTotal = 5,
        heatCells = cells,
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
}

// ---------- 월간 ----------

fun monthlyReportFor(offsetMonths: Int): MonthlyReport {
    val cal = baseCalendar().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, offsetMonths)
    }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1

    val rnd = Random(year * 100 + month)

    return MonthlyReport(
        monthLabel = "${month}월",
        detailDateLabel = "$year.${p2(month)}",
        cautionCount = 15 + rnd.nextInt(10),  // 15~24
        dangerCount = 10 + rnd.nextInt(10),   // 10~19
        attendance = 26 + rnd.nextInt(5),     // 26~30
        attendanceTotal = 30,
        calendarDays = buildMonthCalendar(year, month, rnd),
        hourlyRisks = hourlyPattern(Random(offsetMonths * 53 + 3)),
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
}

// 실제 요일 오프셋(월요일 시작)을 계산해 달력 셀 목록을 만든다.
private fun buildMonthCalendar(year: Int, month1: Int, rnd: Random): List<CalendarDay> {
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

    // 앞쪽: 이전 달 말일들 (회색)
    for (i in 0 until leading) {
        cells.add(CalendarDay(prevMax - leading + 1 + i, inMonth = false, dotLevel = null))
    }
    // 이번 달
    val levels = listOf(RiskLevel.SAFE, RiskLevel.CAUTION, RiskLevel.DANGER)
    for (d in 1..daysInMonth) {
        val level = if (rnd.nextInt(100) < 45) levels[rnd.nextInt(levels.size)] else null
        cells.add(CalendarDay(d, inMonth = true, dotLevel = level))
    }
    // 뒤쪽: 다음 달 초 (회색) — 마지막 주 7칸 채우기
    var next = 1
    while (cells.size % 7 != 0) {
        cells.add(CalendarDay(next++, inMonth = false, dotLevel = null))
    }
    return cells
}

// 월간 달력에서 특정 날짜(day)를 탭했을 때 → 기준일 대비 '일 offset'으로 변환
// (offsetMonths = 현재 보고 있는 달의 offset, day = 탭한 날짜)
fun dayOffsetForCalendar(offsetMonths: Int, day: Int): Int {
    val base = baseCalendar()
    val target = baseCalendar().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, offsetMonths)
        set(Calendar.DAY_OF_MONTH, day)
    }
    val dayMs = 1000L * 60 * 60 * 24
    return Math.round((target.timeInMillis - base.timeInMillis).toDouble() / dayMs).toInt()
}
