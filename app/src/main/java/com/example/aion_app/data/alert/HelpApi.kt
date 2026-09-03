package com.example.aion_app.data.alert

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

/** POST /api/help/ 요청 */
data class HelpRequest(
    @SerializedName("childId") val childId: String,
)

/** POST /api/help/ 응답 */
data class HelpResponse(
    @SerializedName("ok") val ok: Boolean = false,
)

interface HelpApi {
    // 끝 슬래시 필수 (Django 리다이렉트 시 POST가 GET으로 바뀐다)
    @POST("api/help/")
    suspend fun requestHelp(@Body body: HelpRequest): HelpResponse
}