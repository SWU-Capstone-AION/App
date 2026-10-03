package com.example.aion_app.watch

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

private const val TAG = "AionHr"

/** 워치 앱(watch-hr/wear)의 HR_PATH와 반드시 같아야 한다 */
const val HR_PATH = "/aion/hr"

/** 최근에 받은 값의 키를 기억한다 (워치 대기열 10분치 600개보다 넉넉하게). 블루투스·Wi-Fi 수신이 같이 쓴다 */
internal object RecentKeys {
    private const val MAX = 2_000
    private val keys = object : LinkedHashMap<String, Unit>(MAX, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > MAX
    }

    /** 처음 보는 키면 기억하고 true */
    @Synchronized
    fun firstTime(key: String): Boolean = keys.put(key, Unit) == null
}

/**
 * 갤럭시 워치가 보낸 심박을 받는다. 메시지: {"bpm":78,"at":1759...}
 *
 * 워치와 이 앱은 applicationId(com.example.aion_app)와 서명 키가 같아야 메시지가 온다.
 * 워치·태블릿은 Galaxy Wearable 앱으로 페어링돼 있어야 한다.
 */
class WatchHeartRateListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != HR_PATH) return

        val now = System.currentTimeMillis()
        val json = String(event.data, Charsets.UTF_8)

        runCatching {
            val obj = JSONObject(json)
            val bpm = obj.getInt("bpm")
            val at = obj.getLong("at")

            // 워치는 못 보낸 값을 다시 보낸다. 같은 워치·같은 측정 시각이면 이미 받은 값이라 버린다
            if (!RecentKeys.firstTime("${event.sourceNodeId}:$at")) return

            Log.d(TAG, "수신: bpm=$bpm, 지연=${now - at}ms")
            WatchHeartRate.onReceived(bpm, receivedAt = now)
        }.onFailure {
            Log.w(TAG, "파싱 실패: $json")
        }
    }
}
