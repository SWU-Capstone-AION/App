package com.example.aion_app.data.network

import com.example.aion_app.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 서버 연결 공용 설정.
 * 알림·아동 상태·도움 요청·리포트가 모두 이 Retrofit 하나를 같이 쓴다.
 */
object ApiClient {

    val retrofit: Retrofit by lazy {
        val client = OkHttpClient.Builder()
            // Render 무료 플랜은 15분 유휴 후 잠들고, 깨는 데 50초 넘게 걸린다.
            // 첫 요청이 그 시간을 기다릴 수 있도록 넉넉히 잡는다.
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.SERVER_URL.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    inline fun <reified T> create(): T = retrofit.create(T::class.java)
}

/** Retrofit의 baseUrl은 반드시 슬래시로 끝나야 한다. */
private fun String.ensureTrailingSlash(): String =
    if (endsWith("/")) this else "$this/"