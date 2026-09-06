package com.example.aion_app.data.alert

import retrofit2.http.GET
import retrofit2.http.Query

/** 교사 홈에 띄울 담당 아동들의 현재 상태 */
interface ChildStateApi {
    @GET("api/children/states/")
    suspend fun getChildStates(
        @Query("teacherId") teacherId: String,
    ): ChildStateListResponse
}