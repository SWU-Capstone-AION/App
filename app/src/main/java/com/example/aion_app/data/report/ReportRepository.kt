package com.example.aion_app.data.report

import android.util.Log
import com.example.aion_app.data.network.ApiClient
import com.example.aion_app.ui.screen.report.AiInsight
import com.example.aion_app.ui.screen.report.ChartBar
import com.example.aion_app.ui.screen.report.DailyReport
import com.example.aion_app.ui.screen.report.HeatCell
import com.example.aion_app.ui.screen.report.MonthlyReport
import com.example.aion_app.ui.screen.report.ReportDates
import com.example.aion_app.ui.screen.report.RiskLevel
import com.example.aion_app.ui.screen.report.WeeklyReport
import com.example.aion_app.ui.screen.report.buildMonthCalendar
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * 리포트 조회 + 서버 숫자를 화면 값으로 바꾸기.
 * 분석 문장은 서버가 아니라 여기서 만든다 (서버는 숫자만 준다).
 */
class ReportRepository(
    private val api: ReportApi = ApiClient.create<ReportApi>()
) {

    suspend fun getDaily(childId: String, day: Calendar): Result<DailyReport> = runCatching {
        val dto = api.getDaily(childId, ReportDates.apiDate(day))
        if (dto.ok == false) throw IllegalStateException("일간 리포트 응답 오류")
        dto.toDailyReport(day)
    }.onFailure { Log.e(TAG, "일간 리포트 조회 실패", it) }

    suspend fun getWeekly(childId: String, monday: Calendar): Result<WeeklyReport> = runCatching {
        val dto = api.getWeekly(childId, ReportDates.apiDate(monday))
        if (dto.ok == false) throw IllegalStateException("주간 리포트 응답 오류")
        dto.toWeeklyReport(monday)
    }.onFailure { Log.e(TAG, "주간 리포트 조회 실패", it) }

    suspend fun getMonthly(childId: String, month: Calendar): Result<MonthlyReport> = runCatching {
        val year = month.get(Calendar.YEAR)
        val month1 = month.get(Calendar.MONTH) + 1
        val dto = api.getMonthly(childId, year, month1)
        if (dto.ok == false) throw IllegalStateException("월간 리포트 응답 오류")
        dto.toMonthlyReport(month)
    }.onFailure { Log.e(TAG, "월간 리포트 조회 실패", it) }

    private companion object {
        const val TAG = "AION_REPORT"
    }
}

// ============================================================
// 서버 값 → 화면 값
// ============================================================

private val HOURS = 8..15
private val WEEKDAY_NAMES = listOf("월", "화", "수", "목", "금", "토", "일")

private fun DailyReportDto.toDailyReport(day: Calendar): DailyReport {
    val byHour = hourly.orEmpty().associateBy { it.hour }
    val bars = HOURS.map { h ->
        ChartBar(
            key = h,
            axisLabel = h.toString().padStart(2, '0'),
            selectedLabel = "${h}시",
            score = byHour[h]?.avg.toScore()
        )
    }

    val insights = buildList {
        val hour = peakHour?.hour
        if (hour != null) {
            val range = hourRange(hour)
            add(
                AiInsight(
                    tag = "취약 시간대",
                    title = "$range 집중 발생",
                    description = scoreAboveText(
                        where = "$range 사이의 위험점수",
                        percent = peakHour.percentAboveAverage.toPercent(),
                        compared = "다른 시간대"
                    )
                )
            )
        }
        topBehavior.toInsight()?.let { add(it) }
    }

    return DailyReport(
        dateLabel = ReportDates.dailyLabel(day),
        detailDateLabel = ReportDates.dailyDetailLabel(day),
        cautionCount = summary?.caution ?: 0,
        dangerCount = summary?.danger ?: 0,
        hourlyRisks = bars,
        insights = insights
    )
}

private fun WeeklyReportDto.toWeeklyReport(monday: Calendar): WeeklyReport {
    val byCell = heatmap.orEmpty().associateBy { it.weekday to it.hour }
    val cells = (0..4).flatMap { d ->
        HOURS.map { h -> HeatCell(dayIndex = d, hour = h, score = byCell[d to h]?.avg.toScore()) }
    }

    val insights = buildList {
        topBehavior.toInsight()?.let { add(it) }

        val dayName = peakCell?.weekday?.let { WEEKDAY_NAMES.getOrNull(it) }
        val hour = peakCell?.hour
        if (dayName != null && hour != null) {
            val where = "${dayName}요일 ${hourRange(hour)}"
            add(
                AiInsight(
                    tag = "취약 시간대",
                    title = "$where 집중 발생",
                    description = scoreAboveText(
                        where = "$where 사이의 위험점수",
                        percent = peakCell.percentAboveAverage.toPercent(),
                        compared = "다른 시간대"
                    )
                )
            )
        }
    }

    return WeeklyReport(
        // 서버가 그 주의 월~금 날짜를 같이 주므로 그대로 쓴다
        dateLabel = ReportDates.weeklyLabelFrom(startDate, endDate)
            ?: ReportDates.weeklyLabel(monday),
        detailDateLabel = ReportDates.weeklyDetailLabelFrom(startDate, endDate)
            ?: ReportDates.weeklyDetailLabel(monday),
        cautionCount = summary?.caution ?: 0,
        dangerCount = summary?.danger ?: 0,
        heatCells = cells,
        insights = insights
    )
}

private fun MonthlyReportDto.toMonthlyReport(month: Calendar): MonthlyReport {
    val year = this.year ?: month.get(Calendar.YEAR)
    val month1 = this.month ?: (month.get(Calendar.MONTH) + 1)

    // 날짜별 점 색 — 서버가 정한 level 을 그대로 쓴다
    val levels = HashMap<Int, RiskLevel?>()
    days.orEmpty().forEach { d ->
        val parts = d.date?.take(10)?.split("-") ?: return@forEach
        if (parts.size != 3) return@forEach
        if (parts[0].toIntOrNull() != year || parts[1].toIntOrNull() != month1) return@forEach
        val dayOfMonth = parts[2].toIntOrNull() ?: return@forEach
        levels[dayOfMonth] = d.level.toRiskLevel()
    }

    // 월간은 시간대 값 대신 요일별 평균이 온다
    val byWeekday = weekdays.orEmpty().associateBy { it.weekday }
    val bars = WEEKDAY_NAMES.indices.map { w ->
        ChartBar(
            key = w,
            axisLabel = WEEKDAY_NAMES[w],
            selectedLabel = "${WEEKDAY_NAMES[w]}요일",
            score = byWeekday[w]?.avg.toScore()
        )
    }

    val insights = buildList {
        val dayName = peakWeekday?.weekday?.let { WEEKDAY_NAMES.getOrNull(it) }
        if (dayName != null) {
            // 월간 퍼센트는 점수가 아니라 '위험 건수' 기준이다
            val percent = peakWeekday.percentAboveAverage.toPercent()
            add(
                AiInsight(
                    tag = "취약 요일",
                    title = "${dayName}요일 집중 발생",
                    description = if (percent != null && percent > 0)
                        "${dayName}요일의 위험 감지가 다른 요일보다 $percent% 많았습니다."
                    else
                        "${dayName}요일에 위험 감지가 가장 많았습니다."
                )
            )
        }
        topBehavior.toInsight()?.let { add(it) }
    }

    return MonthlyReport(
        monthLabel = ReportDates.monthLabel(month),
        detailDateLabel = ReportDates.monthDetailLabel(month),
        cautionCount = summary?.caution ?: 0,
        dangerCount = summary?.danger ?: 0,
        calendarDays = buildMonthCalendar(year, month1, levels),
        weekdayRisks = bars,
        insights = insights
    )
}

// ---------- 값 바꾸기 ----------

/** 서버 avg(0~1) → 화면 점수(0~100). 기록 없는 칸은 null 그대로. */
private fun Double?.toScore(): Int? =
    this?.let { (it * 100).roundToInt().coerceIn(0, 100) }

private fun Double?.toPercent(): Int? = this?.roundToInt()

private fun String?.toRiskLevel(): RiskLevel? = when (this?.uppercase()) {
    "DANGER" -> RiskLevel.DANGER
    "CAUTION" -> RiskLevel.CAUTION
    "STABLE", "SAFE" -> RiskLevel.SAFE
    else -> null
}

// ---------- 문장 ----------

private fun hourRange(hour: Int) = "$hour~${hour + 1}시"

private fun scoreAboveText(where: String, percent: Int?, compared: String): String =
    if (percent != null && percent > 0)
        "${where}가 ${compared}보다 평균적으로 $percent% 높았습니다."
    else
        "${where}가 ${compared}보다 높았습니다."

/** 서버가 한글 이름(label)을 주므로 그대로 쓰고, 없을 때만 코드값을 바꾼다. */
private fun BehaviorDto?.toInsight(): AiInsight? {
    if (this == null) return null
    val name = label?.takeIf { it.isNotBlank() } ?: type.toBehaviorName() ?: return null

    return AiInsight(
        tag = "감지 성과",
        title = if (count != null && count > 0) "$name ${count}건" else "$name 행동",
        description = "가장 많이 감지된 행동이에요."
    )
}

private fun String?.toBehaviorName(): String? = when (this) {
    null, "", "unknown" -> null
    "left_arm", "right_arm", "arm", "arm_flapping" -> "팔 흔들기"
    "head", "head_shaking", "head_banging" -> "머리 흔들기"
    "body", "rocking", "body_rocking" -> "몸 흔들기"
    "hand_biting" -> "손 물기"
    else -> this
}