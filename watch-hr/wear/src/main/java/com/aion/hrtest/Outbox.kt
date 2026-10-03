package com.aion.hrtest

/** 못 보낸 값을 이만큼(약 10분치)까지 모아 둔다. 넘치면 가장 오래된 것부터 버린다 */
const val OUTBOX_CAPACITY = 600

/** 이 시간(5초) 동안 계속 못 보낼 때만 "연결 끊김"으로 본다 (블루투스 ↔ Wi-Fi 경로가 바뀌는 순간에 깜빡이지 않게) */
const val DISCONNECT_GRACE_MS = 5_000L

/**
 * 워치 → 태블릿 보낼 값 대기열.
 *
 * 실기기에서 폰 블루투스를 끄자 워치가 Wi-Fi 경로로 바꾸는 사이 16초 분량이 빠졌고,
 * 화면에 "연결 끊김"이 0.5초 깜빡였다. 그래서
 *  - 못 보낸 값은 버리지 않고 모아 뒀다가 연결이 돌아오면 순서대로 다시 보낸다
 *  - 실패가 [graceMs] 동안 이어질 때만 연결 끊김으로 본다 (측정값이 잠깐 안 오는 것만으로는 바뀌지 않음)
 * Android 의존성이 없어 단위 테스트로 검증한다.
 */
class Outbox(
    private val capacity: Int = OUTBOX_CAPACITY,
    private val graceMs: Long = DISCONNECT_GRACE_MS,
) {
    private val pending = ArrayDeque<HrSample>()

    /** 처음 실패한 시각. 한 번이라도 보내지면 null로 돌아간다 */
    private var failingSince: Long? = null

    /** 넘쳐서 버린 개수 (로그용) */
    var dropped = 0
        private set

    val size: Int get() = pending.size

    /** 센서가 준 값을 넣는다. 0 이하는 미착용 순간에 오는 의미 없는 값이라 넣지 않는다 */
    fun add(samples: List<HrSample>) {
        samples.filter { it.bpm > 0 }.forEach { pending.addLast(it) }
        while (pending.size > capacity) { pending.removeFirst(); dropped++ }
    }

    /**
     * 오래된 것부터 보낸다. 하나라도 실패하면 거기서 멈추고 나머지는 다음에 다시 보낸다 (순서 유지).
     * @return 이번에 보낸 개수
     */
    suspend fun flush(now: Long, send: suspend (HrSample) -> Boolean): Int {
        var sent = 0
        while (pending.isNotEmpty()) {
            if (send(pending.first())) {
                pending.removeFirst()
                sent++
                failingSince = null
            } else {
                if (failingSince == null) failingSince = now
                break
            }
        }
        return sent
    }

    /** 화면에 "연결됨"으로 보여도 되는지 */
    fun connected(now: Long): Boolean {
        val since = failingSince ?: return true
        return now - since < graceMs
    }

    fun clear() {
        pending.clear()
        failingSince = null
    }
}
