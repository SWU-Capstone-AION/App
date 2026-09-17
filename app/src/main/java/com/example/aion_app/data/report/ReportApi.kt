package com.example.aion_app.data.report

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
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
// 응답 모양
//
// 실제 응답 예시를 받기 전이라 안쪽 이름은 인수인계 문서 기준으로 잡았다.
// 이름이 조금 달라도 앱이 멈추지 않도록 전부 null 허용,
// 모양이 정해지지 않은 peak·topBehavior는 JsonElement로 받아서 직접 읽는다.
// ============================================================

data class ReportSummaryDto(
    @SerializedName(value = "caution", alternate = ["cautionCount"])
    val caution: Int? = null,
    @SerializedName(value = "danger", alternate = ["dangerCount"])
    val danger: Int? = null,
)

/** 시간대 한 칸. 기록 없는 시간은 avg = null, count = 0 */
data class HourlyDto(
    val hour: Int? = null,
    val avg: Double? = null,
    val count: Int? = null,
)

/** 주간 히트맵 한 칸. weekday 0=월 ... 4=금 */
data class HeatmapCellDto(
    @SerializedName(value = "weekday", alternate = ["dayIndex"])
    val weekday: Int? = null,
    val hour: Int? = null,
    val avg: Double? = null,
    val count: Int? = null,
)

/** 월간 하루. date = yyyy-MM-dd */
data class DayDto(
    val date: String? = null,
    val avg: Double? = null,
    val count: Int? = null,
    @SerializedName(value = "caution", alternate = ["cautionCount"])
    val caution: Int? = null,
    @SerializedName(value = "danger", alternate = ["dangerCount"])
    val danger: Int? = null,
)

data class DailyReportDto(
    val ok: Boolean? = null,
    val summary: ReportSummaryDto? = null,
    val hourly: List<HourlyDto>? = null,
    val peakHour: JsonElement? = null,
    val percentAboveAverage: Double? = null,
    val topBehavior: JsonElement? = null,
)

data class WeeklyReportDto(
    val ok: Boolean? = null,
    val summary: ReportSummaryDto? = null,
    val heatmap: List<HeatmapCellDto>? = null,
    val peakCell: JsonElement? = null,
    val percentAboveAverage: Double? = null,
    val topBehavior: JsonElement? = null,
)

data class MonthlyReportDto(
    val ok: Boolean? = null,
    val summary: ReportSummaryDto? = null,
    val days: List<DayDto>? = null,
    val hourly: List<HourlyDto>? = null,
    val peakWeekday: JsonElement? = null,
    val percentAboveAverage: Double? = null,
    val topBehavior: JsonElement? = null,
)
