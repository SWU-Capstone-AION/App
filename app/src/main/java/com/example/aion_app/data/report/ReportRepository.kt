package com.example.aion_app.data.report

import android.util.Log
import com.example.aion_app.data.network.ApiClient
import com.example.aion_app.ui.screen.report.AiInsight
import com.example.aion_app.ui.screen.report.CalendarDotRule
import com.example.aion_app.ui.screen.report.DailyReport
import com.example.aion_app.ui.screen.report.HeatCell
import com.example.aion_app.ui.screen.report.HourlyRisk
import com.example.aion_app.ui.screen.report.MonthlyReport
import com.example.aion_app.ui.screen.report.ReportDates
import com.example.aion_app.ui.screen.report.RiskLevel
import com.example.aion_app.ui.screen.report.RiskThreshold
import com.example.aion_app.ui.screen.report.WeeklyReport
import com.example.aion_app.ui.screen.report.buildMonthCalendar
import com.google.gson.JsonElement
import com.google.gson.JsonObject
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
private val WEEKDAY_NAMES = listOf("월", "화", "수", "목", "금")

private fun DailyReportDto.toDailyReport(day: Calendar): DailyReport {
    val byHour = hourly.orEmpty().associateBy { it.hour }
    val risks = HOURS.map { h -> HourlyRisk(hour = h, score = byHour[h]?.avg.toScore()) }

    val insights = buildList {
        val peak = peakHour.toPeak(primitiveIsHour = true, fallbackPercent = percentAboveAverage)
        val hour = peak?.hour
        if (hour != null) {
            val range = hourRange(hour)
            add(
                AiInsight(
                    tag = "취약 시간대",
                    title = "$range 집중 발생",
                    description = peakDescription(range, peak.percent)
                )
            )
        }
        topBehavior.toBehaviorLabel()?.let { add(behaviorInsight(it)) }
    }

    return DailyReport(
        dateLabel = ReportDates.dailyLabel(day),
        detailDateLabel = ReportDates.dailyDetailLabel(day),
        cautionCount = summary?.caution ?: 0,
        dangerCount = summary?.danger ?: 0,
        hourlyRisks = risks,
        insights = insights
    )
}

private fun WeeklyReportDto.toWeeklyReport(monday: Calendar): WeeklyReport {
    val byCell = heatmap.orEmpty().associateBy { it.weekday to it.hour }
    val cells = (0..4).flatMap { d ->
        HOURS.map { h -> HeatCell(dayIndex = d, hour = h, score = byCell[d to h]?.avg.toScore()) }
    }

    val insights = buildList {
        topBehavior.toBehaviorLabel()?.let { add(behaviorInsight(it)) }

        val peak = peakCell.toPeak(primitiveIsHour = false, fallbackPercent = percentAboveAverage)
        val dayName = peak?.weekday?.let { WEEKDAY_NAMES.getOrNull(it) }
        val hour = peak?.hour
        if (dayName != null && hour != null) {
            val range = "${dayName}요일 ${hourRange(hour)}"
            add(
                AiInsight(
                    tag = "취약 시간대",
                    title = "$range 집중 발생",
                    description = peakDescription(range, peak.percent)
                )
            )
        }
    }

    return WeeklyReport(
        dateLabel = ReportDates.weeklyLabel(monday),
        detailDateLabel = ReportDates.weeklyDetailLabel(monday),
        cautionCount = summary?.caution ?: 0,
        dangerCount = summary?.danger ?: 0,
        heatCells = cells,
        insights = insights
    )
}

private fun MonthlyReportDto.toMonthlyReport(month: Calendar): MonthlyReport {
    val year = month.get(Calendar.YEAR)
    val month1 = month.get(Calendar.MONTH) + 1

    // 날짜별 점 색
    val levels = HashMap<Int, RiskLevel?>()
    days.orEmpty().forEach { d ->
        val parts = d.date?.take(10)?.split("-") ?: return@forEach
        if (parts.size != 3) return@forEach
        if (parts[0].toIntOrNull() != year || parts[1].toIntOrNull() != month1) return@forEach
        val dayOfMonth = parts[2].toIntOrNull() ?: return@forEach
        levels[dayOfMonth] = d.toDotLevel()
    }

    // 월간 막대그래프는 서버가 hourly를 줄 때만 그린다
    val risks = hourly?.let { list ->
        val byHour = list.associateBy { it.hour }
        HOURS.map { h -> HourlyRisk(hour = h, score = byHour[h]?.avg.toScore()) }
    }.orEmpty()

    val insights = buildList {
        val peak = peakWeekday.toPeak(primitiveIsHour = false, fallbackPercent = percentAboveAverage)
        val dayName = peak?.weekday?.let { WEEKDAY_NAMES.getOrNull(it) }
        if (dayName != null) {
            // 취약 요일은 점수가 아니라 '감지 건수'로 고른 것이라 퍼센트를 붙이지 않는다
            add(
                AiInsight(
                    tag = "취약 요일",
                    title = "${dayName}요일 집중 발생",
                    description = "이번 달에는 ${dayName}요일에 위험 감지가 가장 많았습니다."
                )
            )
        }
        topBehavior.toBehaviorLabel()?.let { add(behaviorInsight(it)) }
    }

    return MonthlyReport(
        monthLabel = ReportDates.monthLabel(month),
        detailDateLabel = ReportDates.monthDetailLabel(month),
        cautionCount = summary?.caution ?: 0,
        dangerCount = summary?.danger ?: 0,
        calendarDays = buildMonthCalendar(year, month1, levels),
        hourlyRisks = risks,
        insights = insights
    )
}

// ---------- 점수 ----------

/**
 * 서버 avg → 화면 점수(0~100).
 * 인수인계 문서에 0.71 같은 0~1 값과 90 같은 0~100 값이 섞여 있어서
 * 1 이하면 0~1로 보고 100을 곱한다. 응답 예시 받으면 한쪽으로 정리할 것.
 */
private fun Double?.toScore(): Int? {
    if (this == null) return null
    val score = if (this <= 1.0) this * 100 else this
    return score.roundToInt().coerceIn(0, 100)
}

private fun DayDto.toDotLevel(): RiskLevel? {
    // 건수가 오면 건수 기준
    if (danger != null || caution != null) {
        return when {
            (danger ?: 0) >= CalendarDotRule.DANGER_MIN -> RiskLevel.DANGER
            (caution ?: 0) >= CalendarDotRule.CAUTION_MIN -> RiskLevel.CAUTION
            avg != null || (count ?: 0) > 0 -> RiskLevel.SAFE
            else -> null
        }
    }
    // 건수가 없으면 평균 점수 기준
    val score = avg.toScore() ?: return null
    return when {
        score >= RiskThreshold.DANGER -> RiskLevel.DANGER
        score >= RiskThreshold.CAUTION -> RiskLevel.CAUTION
        else -> RiskLevel.SAFE
    }
}

// ---------- 문장 ----------

private fun hourRange(hour: Int) = "$hour~${hour + 1}시"

private fun peakDescription(range: String, percent: Int?): String =
    if (percent != null && percent > 0)
        "$range 사이의 위험점수가 다른 시간대보다 평균적으로 $percent% 높았습니다."
    else
        "$range 사이의 위험점수가 다른 시간대보다 높았습니다."

private fun behaviorInsight(label: String) = AiInsight(
    tag = "감지 성과",
    title = "$label 행동",
    description = when (label) {
        "팔 흔들기" -> "팔을 좌우로/앞뒤로 흔드는 행동을 가장 많이 감지했어요."
        "머리 흔들기" -> "머리를 반복해서 흔드는 행동을 가장 많이 감지했어요."
        "몸 흔들기" -> "몸을 앞뒤나 좌우로 흔드는 행동을 가장 많이 감지했어요."
        else -> "'$label' 행동을 가장 많이 감지했어요."
    }
)

// ---------- 모양이 정해지지 않은 값 읽기 ----------

private class Peak(val weekday: Int?, val hour: Int?, val percent: Int?)

/** 숫자 하나로 오든 {hour, weekday, percentAboveAverage} 묶음으로 오든 읽는다. */
private fun JsonElement?.toPeak(primitiveIsHour: Boolean, fallbackPercent: Double?): Peak? {
    if (this == null || isJsonNull) return null
    val fallback = fallbackPercent?.roundToInt()

    if (isJsonPrimitive && asJsonPrimitive.isNumber) {
        val value = asInt
        return if (primitiveIsHour) Peak(null, value, fallback) else Peak(value, null, fallback)
    }
    if (!isJsonObject) return null

    val o = asJsonObject
    return Peak(
        weekday = o.intOrNull("weekday"),
        hour = o.intOrNull("hour"),
        percent = o.doubleOrNull("percentAboveAverage")?.roundToInt() ?: fallback
    )
}

/** 글자로 오든 묶음으로 오든 행동 이름을 꺼내고, 코드 값이면 한글로 바꾼다. */
private fun JsonElement?.toBehaviorLabel(): String? {
    if (this == null || isJsonNull) return null

    val raw = when {
        isJsonPrimitive && asJsonPrimitive.isString -> asString
        isJsonObject -> listOf("label", "name", "behavior", "type", "behaviorType", "behavior_type", "part")
            .firstNotNullOfOrNull { asJsonObject.stringOrNull(it) }
        else -> null
    }?.trim() ?: return null

    return when (raw) {
        "", "unknown" -> null
        "left_arm", "right_arm", "arm", "arm_flapping" -> "팔 흔들기"
        "head", "head_shaking" -> "머리 흔들기"
        "body", "body_rocking" -> "몸 흔들기"
        else -> raw
    }
}

private fun JsonObject.intOrNull(key: String): Int? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

private fun JsonObject.doubleOrNull(key: String): Double? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble

private fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString