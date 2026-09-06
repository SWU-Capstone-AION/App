package com.example.aion_app.data.alert

import com.example.aion_app.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 알림 목록 조회·삭제, 아동의 도움 요청.
 *
 * 서버는 백엔드 담당 노트북에서 돌기 때문에, 꺼져 있거나 다른 와이파이면
 * 연결이 실패한다. 화면에서 그 상황을 안내할 수 있도록 실패를 그대로 돌려준다.
 */
class AlertRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    private val retrofit: Retrofit by lazy {
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

    private val api: AlertApi by lazy { retrofit.create(AlertApi::class.java) }
    private val helpApi: HelpApi by lazy { retrofit.create(HelpApi::class.java) }
    private val healthApi: HealthApi by lazy { retrofit.create(HealthApi::class.java) }
    private val childStateApi: ChildStateApi by lazy { retrofit.create(ChildStateApi::class.java) }

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

    /**
     * 서버를 미리 깨워둔다.
     *
     * Render 무료 플랜은 15분 유휴 후 잠들고 깨는 데 50초 넘게 걸린다.
     * 앱을 켤 때 한 번 던져두면 사용자가 실제로 기능을 쓸 때는 이미 깨어 있다.
     * 응답을 기다릴 필요도, 실패를 알릴 필요도 없다.
     */
    suspend fun warmUp() {
        runCatching { healthApi.check() }
            .onFailure { android.util.Log.d("AION_API", "warm-up 실패(무시): ${it.message}") }
    }

    /** 담당 아동들의 현재 상태. 홈 화면에서 주기적으로 부른다. */
    suspend fun getChildStates(): Result<List<ChildStateDto>> = runCatching {
        val teacherUid = auth.currentUser?.uid
            ?: throw IllegalStateException("로그인이 필요합니다.")

        val response = childStateApi.getChildStates(teacherId = teacherUid)
        if (!response.ok) throw IllegalStateException("아동 상태를 불러오지 못했습니다.")

        response.children
    }.onFailure { error ->
        android.util.Log.e("AION_API", "아동 상태 조회 실패", error)
    }

    /**
     * 아동이 도움을 요청한다. 담당 교사에게 알림이 간다.
     *
     * 실패해도 아이에게 오류를 보여주지 않는다.
     * (버튼을 눌렀는데 에러 창이 뜨면 오히려 더 불안해질 수 있다)
     */
    suspend fun requestHelp(): Result<Unit> = runCatching {
        val childUid = auth.currentUser?.uid
            ?: throw IllegalStateException("로그인이 필요합니다.")

        android.util.Log.d("AION_API", "도움 요청: childId=$childUid")

        val response = helpApi.requestHelp(HelpRequest(childId = childUid))
        if (!response.ok) throw IllegalStateException("도움 요청에 실패했습니다.")
        Unit
    }.onFailure { error ->
        android.util.Log.e("AION_API", "도움 요청 실패", error)
    }
}

/** Retrofit의 baseUrl은 반드시 슬래시로 끝나야 한다. */
private fun String.ensureTrailingSlash(): String =
    if (endsWith("/")) this else "$this/"