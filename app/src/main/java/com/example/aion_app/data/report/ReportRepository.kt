package com.example.aion_app.data.report

import android.util.Log
import com.example.aion_app.data.network.ApiClient
import com.example.aion_app.ui.screen.report.AiInsight
import com.example.aion_app.ui.screen.report.BehaviorCount
import com.example.aion_app.ui.screen.report.ChartBar
import com.example.aion_app.ui.screen.report.DailyReport
import com.example.aion_app.ui.screen.report.HeatCell
import com.example.aion_app.ui.screen.report.MonthlyReport
import com.example.aion_app.ui.screen.report.ReportDates
import com.example.aion_app.ui.screen.report.RiskLevel
import com.example.aion_app.ui.screen.report.WeeklyReport
import com.example.aion_app.ui.screen.report.buildMonthCalendar
import java.text.Collator
import java.util.Calendar
import java.util.Locale
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
        behaviorInsight(behaviors, topBehavior)?.let { add(it) }
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
        behaviorInsight(behaviors, topBehavior)?.let { add(it) }

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
        // 취약 요일은 '위험 감지 건수'가 가장 많은 요일이다 (달력 빨간 점과 같은 기준).
        // 위 막대그래프(평균 점수)와 1등 요일이 다를 수 있어서,
        // 제목·문장에 '위험 감지 건수' 기준임을 드러내고 % 대신 실제 건수를 보여준다.
        val peakDay = peakWeekday?.weekday
        val dayName = peakDay?.let { WEEKDAY_NAMES.getOrNull(it) }
        val dangerCount = peakWeekday?.danger ?: byWeekday[peakDay]?.danger
        if (dayName != null && dangerCount != null && dangerCount > 0) {
            // 그래프에서 평균 점수가 가장 높은 요일 (기록 없는 요일은 뺀다)
            val topAvgDay = bars.filter { it.score != null }.maxByOrNull { it.score!! }?.key
            val topAvgName = topAvgDay?.let { WEEKDAY_NAMES.getOrNull(it) }

            // 1등 요일이 서로 다를 때만 그래프 기준 문장을 덧붙인다
            val countText = "${dayName}요일에 위험 감지가 ${dangerCount}건으로 가장 많았어요."
            val description = if (topAvgName != null && topAvgDay != peakDay)
                "$countText 평균 위험 점수는 ${topAvgName}요일이 가장 높았어요."
            else
                countText

            add(
                AiInsight(
                    tag = "취약 요일",
                    title = "${dayName}요일 위험 감지 최다",
                    description = description
                )
            )
        }
        behaviorInsight(behaviors, topBehavior)?.let { add(it) }
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

private val KOREAN = Collator.getInstance(Locale.KOREAN)

/**
 * 행동 카드. 감지된 행동을 전부 보여준다.
 * 행동 이름: 앱이 아는 코드(규칙모델 v3 의 behaviorType·part)는 앱 이름표를 먼저 쓰고,
 * 앱이 모르는 코드일 때만 서버 label 을 쓴다.
 * 정렬: 건수 많은 순 → 건수가 같으면 이름 가나다순 (서버도 같은 순서로 주지만 이름을 바꿨으니 다시 맞춘다).
 * 이름이 같은 행동(예: left_arm·right_arm → 팔 흔들기)은 한 줄로 합친다.
 * behaviors 를 안 주는 예전 서버면 topBehavior 1개만 쓴다.
 */
private fun behaviorInsight(behaviors: List<BehaviorDto>?, top: BehaviorDto?): AiInsight? {
    val source = behaviors?.takeIf { it.isNotEmpty() } ?: listOfNotNull(top)
    val rows = source
        .mapNotNull { b ->
            val name = b.type.toBehaviorName()
                ?: b.label?.takeIf { it.isNotBlank() }
                ?: b.type?.takeIf { it != "unknown" }
                ?: return@mapNotNull null
            name to (b.count ?: 0)
        }
        .groupBy({ it.first }, { it.second })
        .map { (name, counts) -> BehaviorCount(name, counts.sum()) }
        .filter { it.count > 0 }
        .sortedWith(compareByDescending<BehaviorCount> { it.count }.thenBy(KOREAN) { it.name })
    if (rows.isEmpty()) return null

    return AiInsight(
        tag = "AI 추정 행동",
        title = "${rows[0].name} ${rows[0].count}건",
        description = "가장 많이 감지된 행동이에요.",
        behaviors = rows
    )
}

/** 앱이 아는 코드 → 리포트 이름. 모르는 코드면 null (그때는 서버 label 을 쓴다) */
private fun String?.toBehaviorName(): String? {
    if (this == null || this == "" || this == "unknown") return null
    // 규칙모델 v3 행동 유형 (DetectionSender 의 behaviorType) — 이름은 RuleV3Config 의 typeLabel
    V3_TYPE_LABEL[this]?.let { return it }
    return when (this) {
        // 부위(part) 코드 — 예전 서버가 행동 대신 부위를 돌려줄 때를 대비
        "left_arm", "right_arm", "arm" -> "팔 흔들기"
        "left_hand", "right_hand", "hand" -> "손 동작"
        "both_hands" -> "박수"
        "head", "head_shaking", "head_banging" -> "머리 흔들기"
        "shoulder" -> "어깨 으쓱"
        "body", "rocking" -> "몸 흔들기"
        "hand_biting" -> "손 물기"
        else -> null
    }
}

/** behaviorType 코드 → 리포트 이름 (예: arm_flapping → 팔 퍼덕이기) */
private val V3_TYPE_LABEL: Map<String, String> =
    com.example.aion_app.monitor.rule.BEHAVIORS.associate { it.typeCode to it.typeLabel }
