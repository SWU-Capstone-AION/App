package com.example.aion_app.data.alert

import com.example.aion_app.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 알림 목록 조회·삭제.
 *
 * 서버는 백엔드 담당 노트북에서 돌기 때문에, 꺼져 있거나 다른 와이파이면
 * 연결이 실패한다. 화면에서 그 상황을 안내할 수 있도록 실패를 그대로 돌려준다.
 */
class AlertRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    private val api: AlertApi by lazy {
        val client = OkHttpClient.Builder()
            // 서버가 꺼져 있을 때 오래 기다리지 않도록 짧게 잡는다
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.SERVER_URL.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AlertApi::class.java)
    }

    /** 현재 로그인한 교사의 알림 목록. 최신순. */
    suspend fun getAlerts(limit: Int = 50): Result<List<AlertDto>> = runCatching {
        val teacherUid = auth.currentUser?.uid
            ?: throw IllegalStateException("로그인이 필요합니다.")

        android.util.Log.d("AION_API", "요청: ${BuildConfig.SERVER_URL} / teacherId=$teacherUid")

        val response = api.getAlerts(teacherId = teacherUid, limit = limit)
        if (!response.ok) throw IllegalStateException("알림을 불러오지 못했습니다.")

        response.alerts
    }.onFailure { error ->
        android.util.Log.e("AION_API", "알림 조회 실패", error)
    }

    /** 알림 한 건 삭제. 서버에서도 감춰진다. */
    suspend fun deleteAlert(id: Int): Result<Unit> = runCatching {
        val response = api.deleteAlert(id)
        if (!response.ok) throw IllegalStateException("삭제하지 못했습니다.")
        Unit
    }
}

/** Retrofit의 baseUrl은 반드시 슬래시로 끝나야 한다. */
private fun String.ensureTrailingSlash(): String =
    if (endsWith("/")) this else "$this/"