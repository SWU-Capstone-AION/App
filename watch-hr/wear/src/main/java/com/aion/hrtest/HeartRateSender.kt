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

    /** 이 워치의 id. Wi-Fi로 보낼 때 같이 보내서, 받는 쪽이 블루투스로 온 값과 같은 기준으로 중복을 거른다 */
    private var localNodeId: String? = null

    private suspend fun nodeId(): String =
        localNodeId ?: runCatching { nodeClient.localNode.await().id }.getOrDefault("watch")
            .also { localNodeId = it }

    /** 받을 기기가 연결돼 있는지. 화면의 "연결 끊김" 판단에 쓴다 */
    suspend fun isConnected(): Boolean =
        if (TabletLink.chosen.value != null) TabletLink.reachable()
        else runCatching { nodeClient.connectedNodes.await().isNotEmpty() }.getOrDefault(false)

    /**
     * 심박수 한 건을 보낸다. 보내지면 true.
     * 태블릿을 골랐으면 Wi-Fi로 그 태블릿에, 아니면 블루투스로 연결된 기기(휴대폰) 전부에 보낸다.
     * 페이로드는 JSON 문자열 그대로 — 테스트라 라이브러리 없이 수동 조립
     */
    suspend fun send(sample: HrSample): Boolean {
        // 태블릿을 골라 두었으면 Wi-Fi로만 보낸다 (휴대폰으로도 보내면 두 기기가 같은 아이 심박을 받게 됨)
        if (TabletLink.chosen.value != null) return TabletLink.send(nodeId(), sample)

        val json = """{"bpm":${sample.bpm},"at":${sample.at}}"""
        val bytes = json.toByteArray(Charsets.UTF_8)

        val nodes = runCatching { nodeClient.connectedNodes.await() }
            .getOrElse {
                Log.w(TAG, "연결된 기기 조회 실패: ${it.message}")
                return false
            }

        if (nodes.isEmpty()) {
            Log.w(TAG, "연결된 기기 없음 — 페어링 확인 필요")
            return false
        }

        var sent = false
        nodes.forEach { node ->
            runCatching {
                messageClient.sendMessage(node.id, HR_PATH, bytes).await()
                Log.d(TAG, "전송 성공 → ${node.displayName}: $json")
                sent = true
            }.onFailure {
                Log.w(TAG, "전송 실패: ${it.message}")
            }
        }
        return sent
    }
}
