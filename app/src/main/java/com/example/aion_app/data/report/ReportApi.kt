package com.example.aion_app.data.report

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 리포트 API 3종.
 * 끝 슬래시 필수 (없으면 Django가 리다이렉트한다).
 */
interface ReportApi {

    @GET("api/reports/daily/")
    suspend fun getDaily(
        @Query("childId") childId: String,
        @Query("date") date: String,          // yyyy-MM-dd
    ): DailyReportDto

    /** 그 주 아무 날짜나 넣으면 월~금을 돌려준다. */
    @GET("api/reports/weekly/")
    suspend fun getWeekly(
        @Query("childId") childId: String,
        @Query("date") date: String,
    ): WeeklyReportDto

    @GET("api/reports/monthly/")
    suspend fun getMonthly(
        @Query("childId") childId: String,
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): MonthlyReportDto
}

// ============================================================
// 응답 모양 (2026-09 실제 응답 기준)
//
// avg 는 0~1 값이고, 기록 없는 칸은 avg: null / count: 0 으로 온다.
// weekday 는 0=월 ... 6=일.
// 서버가 값을 빼고 보내도 앱이 안 멈추도록 전부 null 을 허용한다.
// ============================================================

/** 덩어리(안정에서 벗어났다 돌아올 때까지) 기준 건수 */
data class ReportSummaryDto(
    val caution: Int? = null,
    val danger: Int? = null,
)

/** 시간대 한 칸 */
data class HourlyDto(
    val hour: Int? = null,
    val avg: Double? = null,
    val count: Int? = null,
)

/** 주간 히트맵 한 칸 */
data class HeatmapCellDto(
    val weekday: Int? = null,
    val hour: Int? = null,
    val avg: Double? = null,
    val count: Int? = null,
)

/** 월간 하루. level 은 서버가 정한 달력 점 색 (CAUTION / DANGER) */
data class DayDto(
    val date: String? = null,
    val level: String? = null,
    val caution: Int? = null,
    val danger: Int? = null,
)

/** 월간 요일별 값 */
data class WeekdayDto(
    val weekday: Int? = null,
    val avg: Double? = null,
    val count: Int? = null,
    val caution: Int? = null,
    val danger: Int? = null,
)

/** 가장 위험했던 지점 (일간=시간, 주간=요일+시간, 월간=요일) */
data class PeakDto(
    val hour: Int? = null,
    val weekday: Int? = null,
    val avg: Double? = null,
    val caution: Int? = null,
    val danger: Int? = null,
    // 일간·주간은 점수 기준, 월간은 위험 건수 기준
    val percentAboveAverage: Double? = null,
)

/** 가장 많이 감지된 행동. label 은 서버가 만든 한글 이름 */
data class BehaviorDto(
    val type: String? = null,
    val label: String? = null,
    val count: Int? = null,
)

data class DailyReportDto(
    val ok: Boolean? = null,
    val date: String? = null,
    val summary: ReportSummaryDto? = null,
    val hourly: List<HourlyDto>? = null,
    val peakHour: PeakDto? = null,
    val topBehavior: BehaviorDto? = null,
)

data class WeeklyReportDto(
    val ok: Boolean? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val summary: ReportSummaryDto? = null,
    val heatmap: List<HeatmapCellDto>? = null,
    val peakCell: PeakDto? = null,
    val topBehavior: BehaviorDto? = null,
)

data class MonthlyReportDto(
    val ok: Boolean? = null,
    val year: Int? = null,
    val month: Int? = null,
    val summary: ReportSummaryDto? = null,
    val days: List<DayDto>? = null,
    val weekdays: List<WeekdayDto>? = null,
    val peakWeekday: PeakDto? = null,
    val topBehavior: BehaviorDto? = null,
)