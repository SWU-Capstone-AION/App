package com.example.aion_app.data.alert

import com.google.gson.annotations.SerializedName

/** GET /api/alerts/ 응답 */
data class AlertListResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("alerts") val alerts: List<AlertDto> = emptyList(),
)

/** 알림 한 건 */
data class AlertDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("childId") val childId: String = "",
    @SerializedName("childName") val childName: String = "",
    /** DANGER / CAUTION / STABLE */
    @SerializedName("level") val level: String = "",
    /** 짧은 라벨 — "위험" */
    @SerializedName("title") val title: String = "",
    /** 본문 */
    @SerializedName("body") val body: String = "",
    /** 감지된 시각 (ISO8601, +09:00) */
    @SerializedName("occurredAt") val occurredAt: String = "",
)

/** DELETE /api/alerts/{id}/ 응답 */
data class AlertDeleteResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("id") val id: Int = 0,
)