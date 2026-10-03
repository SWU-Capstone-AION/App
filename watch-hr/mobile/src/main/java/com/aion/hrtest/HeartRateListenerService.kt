package com.aion.hrtest

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

private const val TAG = "AionHr"

/** wear 모듈의 HR_PATH와 반드시 같아야 한다 (모듈이 달라 공유 안 됨) */
const val HR_PATH = "/aion/hr"

data class HrReceived(
    val bpm: Int,
    /** 워치가 잰 시각 */
    val at: Long,
    /** 태블릿이 받은 시각 */
    val receivedAt: Long
) {
    /** 워치→태블릿 지연 (밀리초) */
    val delayMs: Long get() = receivedAt - at
}

/** 최근에 받은 값의 키를 기억한다 (워치 대기열 10분치 600개보다 넉넉하게) */
object RecentKeys {
    private const val MAX = 2_000
    private val keys = object : LinkedHashMap<String, Unit>(MAX, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > MAX
    }

    /** 처음 보는 키면 기억하고 true */
    @Synchronized
    fun firstTime(key: String): Boolean = keys.put(key, Unit) == null
}

class HeartRateListenerService : WearableListenerService() {

    private var lastReceivedAt = 0L

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != HR_PATH) return

        val now = System.currentTimeMillis()
        val json = String(event.data, Charsets.UTF_8)

        runCatching {
            val obj = JSONObject(json)
            val bpm = obj.getInt("bpm")
            val at = obj.getLong("at")

            // 워치는 못 보낸 값을 다시 보낸다. 같은 워치·같은 측정 시각이면 이미 받은 값이라 버린다
            if (!RecentKeys.firstTime("${event.sourceNodeId}:$at")) {
                Log.d(TAG, "중복 수신 무시: bpm=$bpm, at=$at")
                return
            }

            // ★ 도착 간격 측정 로그
            val gap = if (lastReceivedAt == 0L) 0 else now - lastReceivedAt
            lastReceivedAt = now

            Log.d(TAG, "수신: bpm=$bpm, 간격=${gap}ms, 지연=${now - at}ms")

            HrRepository.get(this).onReceived(
                HrReceived(bpm = bpm, at = at, receivedAt = now),
                sourceNode = event.sourceNodeId
            )
        }.onFailure {
            Log.w(TAG, "파싱 실패: $json")
        }
    }
}
