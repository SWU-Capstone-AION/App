package com.example.aion_app.data.alert

import retrofit2.http.GET

/** 서버 상태 확인용. 응답 내용은 쓰지 않고 서버를 깨우는 데만 쓴다. */
interface HealthApi {
    @GET("api/health/")
    suspend fun check(): Map<String, Any>
}