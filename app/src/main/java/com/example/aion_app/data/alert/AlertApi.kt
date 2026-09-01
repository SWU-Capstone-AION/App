package com.example.aion_app.data.alert

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Django 알림 API.
 *
 * 주의: 경로 끝의 슬래시를 빼면 안 된다.
 * Django가 슬래시 없는 주소를 리다이렉트하는데, 그 과정에서 DELETE가
 * GET으로 바뀌어 삭제가 조용히 실패한다.
 */
interface AlertApi {

    @GET("api/alerts/")
    suspend fun getAlerts(
        @Query("teacherId") teacherId: String,
        @Query("limit") limit: Int = 50,
    ): AlertListResponse

    @DELETE("api/alerts/{id}/")
    suspend fun deleteAlert(@Path("id") id: Int): AlertDeleteResponse
}