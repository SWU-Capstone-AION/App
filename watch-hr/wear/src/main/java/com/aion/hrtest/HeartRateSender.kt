package com.aion.hrtest

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

private const val TAG = "AionHr"
const val HR_PATH = "/aion/hr"

class HeartRateSender(private val context: Context) {

    private val nodeClient by lazy { Wearable.getNodeClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }

    /**
     * 심박수 한 건을 연결된 기기 전부에 보낸다.
     * 페이로드는 JSON 문자열 그대로 — 테스트라 라이브러리 없이 수동 조립
     */
    suspend fun send(sample: HrSample) {
        val json = """{"bpm":${sample.bpm},"at":${sample.at}}"""
        val bytes = json.toByteArray(Charsets.UTF_8)

        val nodes = runCatching { nodeClient.connectedNodes.await() }
            .getOrElse {
                Log.w(TAG, "연결된 기기 조회 실패: ${it.message}")
                return
            }

        if (nodes.isEmpty()) {
            Log.w(TAG, "연결된 기기 없음 — 페어링 확인 필요")
            return
        }

        nodes.forEach { node ->
            runCatching {
                messageClient.sendMessage(node.id, HR_PATH, bytes).await()
                Log.d(TAG, "전송 성공 → ${node.displayName}: $json")
            }.onFailure {
                Log.w(TAG, "전송 실패: ${it.message}")
            }
        }
    }
}
