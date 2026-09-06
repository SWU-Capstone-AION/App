package com.example.aion_app.data.alert

import com.google.gson.annotations.SerializedName

/** GET /api/children/states/ 응답 */
data class ChildStateListResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("children") val children: List<ChildStateDto> = emptyList(),
)

/** 아동 한 명의 현재 상태 */
data class ChildStateDto(
    @SerializedName("childId") val childId: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("gender") val gender: String = "",
    @SerializedName("age") val age: Int = 0,
    /** 위험 점수 0~100 */
    @SerializedName("score") val score: Int = 0,
    /** DANGER / CAUTION / STABLE. 아직 데이터가 없으면 null */
    @SerializedName("level") val level: String? = null,
    /** 지금 태블릿에서 모니터링 중인지 */
    @SerializedName("active") val active: Boolean = false,
    @SerializedName("updatedAt") val updatedAt: String? = null,
)