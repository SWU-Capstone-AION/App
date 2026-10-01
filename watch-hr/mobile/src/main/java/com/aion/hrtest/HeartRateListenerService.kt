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
