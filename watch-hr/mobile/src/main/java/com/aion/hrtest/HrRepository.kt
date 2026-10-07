package com.aion.hrtest

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AionHr"

/** 버퍼 크기: 약 10분 (1초 간격 가정). 실측 간격을 재면 10분 ÷ 간격으로 바꾼다 */
const val BUFFER_SIZE = 600

/** DB 보관 기간 */
private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000

/** 크기가 고정된 링버퍼. 가득 차면 가장 오래된 값부터 밀려난다 */
class RingBuffer<T>(val capacity: Int) {
    private val items = ArrayDeque<T>(capacity)

    @Synchronized
    fun add(item: T) {
        if (items.size == capacity) items.removeFirst()
        items.addLast(item)
    }

    @Synchronized
    fun snapshot(): List<T> = items.toList()

    @Synchronized
    fun clear() = items.clear()

    val size: Int @Synchronized get() = items.size
}

/**
 * 태블릿 쪽 심박 저장소. 받은 값을 세 곳에 동시에 둔다.
 *  1. latest  — 가장 최근 값 (종합 판정이 즉시 읽음)
 *  2. buffer  — 최근 600개 (4단계 기준선 M, S 계산용)
 *  3. Room DB — 영구 기록 (재시작 복원, 서버 전송, 리포트)
 */
class HrRepository private constructor(context: Context) {

    private val dao = HrDatabase.get(context).hrDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _latest = MutableStateFlow<HrReceived?>(null)
    val latest: StateFlow<HrReceived?> = _latest.asStateFlow()

    val buffer = RingBuffer<HrReceived>(BUFFER_SIZE)

    /** 화면 갱신용: 버퍼가 바뀔 때마다 크기를 흘려보낸다 */
    private val _bufferSize = MutableStateFlow(0)
    val bufferSize: StateFlow<Int> = _bufferSize.asStateFlow()

    val dbCount = dao.countFlow()
    fun recent(n: Int) = dao.recentFlow(n)

    // ---- 4단계: 기준선 · 위험도 ----

    private val filter = HrFilter()
    private val gate = QuietGate()
    private val baseline = HeartRateBaseline()

    /** DB 쓰기는 한 줄로 세운다: 기록(insert) 뒤에 quiet 되돌리기(update)가 오도록 */
    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** 보류 중(아직 기준선에 안 들어간) 값 개수 */
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    /** 비전 AI가 지금 상동행동을 보고 있는지. 테스트 앱에서는 화면 토글로 흉내 낸다 */
    val behaviorActive = MutableStateFlow(false)

    private val _hrState = MutableStateFlow(HrState(baseline.current(), latestBpm = null, latestAt = null))
    val hrState: StateFlow<HrState> = _hrState.asStateFlow()

    /** 필터에 걸러진 개수 (범위 밖, 급변) */
    private val _rejected = MutableStateFlow(0)
    val rejected: StateFlow<Int> = _rejected.asStateFlow()

    init {
        // 앱이 다시 켜지면 DB의 최근 값으로 버퍼를 채워 기준선이 처음부터 다시 쌓이지 않게 한다
        scope.launch {
            dao.deleteBefore(System.currentTimeMillis() - RETENTION_MS)
            val restored = dao.latest(BUFFER_SIZE)
            restored.forEach { buffer.add(it.toReceived()) }
            _bufferSize.value = buffer.size
            _latest.compareAndSet(null, restored.lastOrNull()?.toReceived())

            val quiet = dao.latestQuiet(BASELINE_MAX)
            // 새로 받은 값과 섞이지 않게 onReceived와 같은 잠금 안에서 채운다 (기준선은 시간순이어야 한다)
            synchronized(this@HrRepository) {
                quiet.forEach { baseline.add(it.receivedAt, it.bpm) }
                // 복원 값은 과거 값이라 latestAt은 비워 둔다 → 새 값이 올 때까지 "연결 끊김"으로 판단
                _hrState.value = HrState(baseline.current(), latestBpm = null, latestAt = null)
            }
            Log.d(TAG, "버퍼 복원: ${restored.size}개, 기준선 복원: ${baseline.count}개 (DB 확정값 ${quiet.size}개 중 최근 10분)")
        }
    }

    /** 서비스(바인더 스레드)와 화면 버튼(메인 스레드)에서 동시에 불릴 수 있다 */
    @Synchronized
    fun onReceived(hr: HrReceived, sourceNode: String) {
        _latest.value = hr
        buffer.add(hr)
        _bufferSize.value = buffer.size

        val result = filter.check(hr.bpm, hr.receivedAt)
        val valid = result == HrFilter.Result.OK
        if (!valid) _rejected.value += 1
        // 기준선에는 조용한 구간의 유효한 값만, 보류 시간을 넘긴 뒤에 넣는다. 위험도 계산은 항상 한다
        val gated = gate.offer(hr.bpm, hr.receivedAt, behaviorActive.value, valid)
        val quiet = gated.quietNow
        gated.commit.forEach { (at, bpm) -> baseline.add(at, bpm) }
        if (gated.commit.isNotEmpty()) {
            val from = gated.commit.first().first
            val to = gated.commit.last().first
            dbScope.launch { dao.markQuiet(from, to) }
        }
        gated.discarded?.let { range ->
            Log.d(TAG, "상동행동 감지 → 직전 대기 값 폐기 (${(range.last - range.first) / 1000 + 1}초 분량)")
        }
        _pending.value = gate.pendingCount

        val prev = _hrState.value
        _hrState.value = HrState(
            baseline = baseline.current(),
            latestBpm = if (valid) hr.bpm else prev.latestBpm,
            latestAt = if (valid) hr.receivedAt else prev.latestAt,
        )
        val b = _hrState.value.baseline
        Log.d(
            TAG,
            "기준선: bpm=${hr.bpm} $result quiet=$quiet M=%.1f S=%.1f n=${b.count} 위험도=%.2f"
                .format(b.m, b.s, _hrState.value.risk(hr.receivedAt) ?: 0.0)
        )

        dbScope.launch {
            dao.insert(
                HrRecord(
                    bpm = hr.bpm, at = hr.at, receivedAt = hr.receivedAt, sourceNode = sourceNode,
                    // 아직 보류 중이라 기준선 확정 전. 확정되면 markQuiet로 바꾼다
                    valid = valid, quiet = false
                )
            )
        }
    }

    /** 새 세션(다른 아동, 착용 위치 변경 등)에서 기준선만 다시 모은다. DB 기록은 그대로 */
    @Synchronized
    fun resetBaseline() {
        baseline.clear()
        filter.reset()
        gate.reset()
        _pending.value = 0
        _rejected.value = 0
        _hrState.value = _hrState.value.copy(baseline = baseline.current())
        Log.d(TAG, "기준선 초기화")
    }

    /** 비전 AI가 1차 위험 신호를 냈을 때 호출 */
    fun onVisionAlert(now: Long = System.currentTimeMillis()): Verdict {
        val verdict = judge(_hrState.value, now)
        Log.d(TAG, "통합 판정: $verdict (위험도=${_hrState.value.risk(now)})")
        return verdict
    }

    /** 최근 버퍼 기준 도착 간격 중앙값 (ms). 버퍼 600 / 최소 120 을 확정하는 데 쓴다 */
    fun medianGapMs(): Long? {
        val times = buffer.snapshot().map { it.receivedAt }
        if (times.size < 3) return null
        val gaps = times.zipWithNext { a, b -> b - a }.sorted()
        return gaps[gaps.size / 2]
    }

    @Synchronized
    fun clearAll() {
        buffer.clear()
        _bufferSize.value = 0
        _latest.value = null
        resetBaseline()
        _hrState.value = HrState(baseline.current(), latestBpm = null, latestAt = null)
        dbScope.launch { dao.clear() }
    }

    private fun HrRecord.toReceived() = HrReceived(bpm = bpm, at = at, receivedAt = receivedAt)

    companion object {
        @Volatile private var instance: HrRepository? = null

        fun get(context: Context): HrRepository =
            instance ?: synchronized(this) {
                instance ?: HrRepository(context.applicationContext).also { instance = it }
            }
    }
}
