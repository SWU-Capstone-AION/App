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
     *
     * part = 가장 오래 움직인 부위 (left_arm / right_arm / head / body).
     * 모든 부위가 0이면 part는 보내지 않는다.
     */
    fun send(state: StereotypyDetector.State) {
        val top = state.parts.maxByOrNull { it.value.duration }
        val maxDuration = top?.value?.duration ?: 0.0
        val score = (maxDuration / StereotypyDetector.DURATION_THRESHOLD).coerceIn(0.0, 1.0)

        // 좌우는 합치지 않고 원래 값 그대로 보낸다 (한글 묶음은 서버가 처리)
        // StereotypyDetector에서 전면 카메라 좌우 반전을 이미 바로잡았으므로
        // LEFT_ARM = 아동 본인의 왼팔
        val part = if (maxDuration > 0.0) top?.key?.toServerValue() else null

        send(score, part = part)
    }

    /**
     * 점수를 직접 넘기는 버전.
     * force = true 면 3초 제한을 무시하고 바로 보낸다 (감지 종료 시 0점 전송용).
     * part = null 이면 part 필드를 아예 빼고 보낸다.
     */
    fun send(score: Double, force: Boolean = false, part: String? = null) {
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
            if (part != null) put("part", part)
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
                Log.d("DetectionSender", "score=$score part=$part → ${conn.responseCode}")
                conn.disconnect()
            } catch (e: Exception) {
                // 실패해도 무시. 3초 뒤 최신 값이 다시 간다.
                Log.w("DetectionSender", "전송 실패: ${e.message}")
            }
        }
    }

    /** LEFT_ARM → "left_arm" 처럼 서버가 받는 값으로 바꾼다. */
    private fun StereotypyDetector.Part.toServerValue(): String = when (this) {
        StereotypyDetector.Part.LEFT_ARM -> "left_arm"
        StereotypyDetector.Part.RIGHT_ARM -> "right_arm"
        StereotypyDetector.Part.HEAD -> "head"
        StereotypyDetector.Part.BODY -> "body"
    }
}