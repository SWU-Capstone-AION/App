package com.example.aion_app.monitor.net

import android.util.Log
import com.example.aion_app.BuildConfig
import com.example.aion_app.monitor.pose.StereotypyDetector
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 규칙모델 판정 결과를 Django 서버로 보낸다.
 * 매 프레임 호출해도 되고, 내부에서 3초에 한 번만 실제로 전송한다.
 */
object DetectionSender {

    private val BASE = BuildConfig.SERVER_URL.trimEnd('/')
    private val ENDPOINT = "$BASE/api/detections/"

    private const val MIN_INTERVAL_MS = 3000L

    private var lastSentAt = 0L
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * 판정 결과를 그대로 넘기면 점수로 바꿔서 보낸다.
     * 4개 부위(좌팔·우팔·머리·몸통) 중 가장 오래 활성인 값을 기준으로 한다.
     * 점수 1.0 = 앱이 알람을 띄우는 시점(3.9초 누적).
     */
    fun send(state: StereotypyDetector.State) {
        val maxDuration = state.parts.values.maxOfOrNull { it.duration } ?: 0.0
        val score = (maxDuration / StereotypyDetector.DURATION_THRESHOLD).coerceIn(0.0, 1.0)
        send(score)
    }

    /**
     * 점수를 직접 넘기는 버전.
     * force = true 면 3초 제한을 무시하고 바로 보낸다 (감지 종료 시 0점 전송용).
     */
    fun send(score: Double, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSentAt < MIN_INTERVAL_MS) return
        lastSentAt = now

        val childUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.w("DetectionSender", "로그인 안 됨 — 전송 생략")
            return
        }

        val occurredAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            .format(Date(now))

        val body = JSONObject().apply {
            put("eventId", UUID.randomUUID().toString())
            put("childUid", childUid)
            put("occurredAt", occurredAt)
            put("score", score.coerceIn(0.0, 1.0))
        }.toString()

        scope.launch {
            try {
                val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 60_000   // Render 콜드스타트 대응
                    readTimeout = 60_000
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                Log.d("DetectionSender", "score=$score → ${conn.responseCode}")
                conn.disconnect()
            } catch (e: Exception) {
                // 실패해도 무시. 3초 뒤 최신 값이 다시 간다.
                Log.w("DetectionSender", "전송 실패: ${e.message}")
            }
        }
    }
}