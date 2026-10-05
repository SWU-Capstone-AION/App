package com.example.aion_app.watch

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "AionHr"

/** 비전 AI가 마지막으로 반복 동작을 본 뒤 이 시간 안이면 "행동 중"으로 본다 (심박은 1초, 영상은 프레임마다 들어와서 시점이 어긋난다) */
private const val BEHAVIOR_HOLD_MS = 2_000L

/** 행동 시각을 이만큼 남겨 둔다 (워치 대기열 최대 10분보다 넉넉하게) */
private const val BEHAVIOR_KEEP_MS = 15 * 60_000L

/** 이 점수(= 반복 동작 약 2초 누적, 알람 3.9초의 절반) 이상이면 상동행동 구간으로 본다 */
private const val BEHAVIOR_SCORE = 0.5

/**
 * 워치에서 받은 심박을 앱 안에서 모아 두는 곳.
 *
 *  - displayBpm: 5초 평균. 화면 숫자와 서버 전송값 (5초마다 갱신, 20초 끊기면 null)
 *  - hrState:    개인 기준선 M·S와 마지막 유효값. 심박 위험도 계산용
 *
 * 기록은 메모리에만 둔다. 앱을 다시 켜면 기준선을 처음부터 다시 모은다 (약 3분 30초 뒤 위험도 사용 가능).
 */
object WatchHeartRate {

    private val filter = HrFilter()
    private val gate = QuietGate()
    private val baseline = HeartRateBaseline()
    private val window = HeartRateWindow()
    private val recent = RecentAverage()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    private val _displayBpm = MutableStateFlow<Int?>(null)
    val displayBpm: StateFlow<Int?> = _displayBpm.asStateFlow()

    private val _hrState = MutableStateFlow(HrState(baseline.current(), latestBpm = null, latestAt = null))
    val hrState: StateFlow<HrState> = _hrState.asStateFlow()

    /** 보류 중(90초 대기, 아직 기준선에 안 들어간) 값 개수. 화면 확인용 */
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    /** 비전 AI가 상동행동으로 본 시각들 (최근 15분). 워치가 늦게 보낸 값도 "잰 시각"에 행동 중이었는지 알 수 있게 남겨 둔다 */
    private val behaviorTimes = ArrayDeque<Long>()

    /**
     * 비전 AI 점수를 매 프레임 넘긴다. 상동행동으로 볼 만한 구간은 기준선에 넣지 않는다.
     * 순간적인 움직임 신호(부위별 active)는 숨쉬기·고개 흔들림에도 켜져서, 실기기에서 1~2분마다 걸려
     * 기준선이 하나도 쌓이지 않았다. 그래서 반복 동작이 [BEHAVIOR_SCORE] 이상 누적됐을 때만 행동으로 본다.
     */
    @Synchronized
    fun onVision(score: Double, now: Long = System.currentTimeMillis()) {
        if (score < BEHAVIOR_SCORE) return
        // 프레임마다 오므로 0.5초에 하나만 남긴다
        if (behaviorTimes.isEmpty() || now - behaviorTimes.last() >= 500) behaviorTimes.addLast(now)
        while (behaviorTimes.isNotEmpty() && now - behaviorTimes.first() > BEHAVIOR_KEEP_MS) behaviorTimes.removeFirst()
    }

    /** 그 시각(앞뒤 2초)에 비전 AI가 상동행동을 보고 있었는지 */
    private fun behaviorAt(t: Long): Boolean =
        behaviorTimes.any { it in (t - BEHAVIOR_HOLD_MS)..(t + BEHAVIOR_HOLD_MS) }

    /**
     * 수신 서비스(바인더 스레드)에서 불린다.
     *
     * 시간 계산은 모두 워치가 "잰 시각"(at) 기준이다.
     * 워치는 연결이 끊긴 동안의 값을 모아 뒀다가 한 번에 보내는데, 실기기에서 3분치 191개가 1초 안에 들어왔다.
     * "받은 시각"으로 처리하면 그 값들이 모두 같은 순간에 잰 것처럼 돼서 기준선의 2분 조건·90초 보류가 어긋났다.
     * @param at 워치가 잰 시각. 워치 시계가 앞서 있으면(받은 시각보다 5초 넘게 미래) 받은 시각을 쓴다
     */
    @Synchronized
    fun onReceived(bpm: Int, at: Long, receivedAt: Long = System.currentTimeMillis()) {
        val t = if (at > receivedAt + 5_000) receivedAt else at
        window.add(bpm, t)
        startTicker()

        val check = filter.check(bpm, t)
        val valid = check == HrFilter.Result.OK
        val behavior = behaviorAt(t)
        // 기준선에는 조용한 구간의 유효한 값만, 보류 시간(90초)을 넘긴 뒤에 넣는다
        val gated = gate.offer(bpm, t, behavior, valid)
        gated.commit.forEach { (time, b) -> baseline.add(time, b) }
        _pending.value = gate.pendingCount

        if (valid) recent.add(t, bpm)
        val prev = _hrState.value
        _hrState.value = HrState(
            baseline = baseline.current(),
            latestBpm = if (valid) recent.average() else prev.latestBpm,
            // 늦게 온 옛날 값이면 "지금" 위험도로 쓰지 않도록 잰 시각을 둔다 (20초 넘으면 판단 보류)
            latestAt = if (valid) t else prev.latestAt,
        )
        val b = _hrState.value.baseline
        val risk = _hrState.value.risk(receivedAt)
        // 계산 근거 기록 (탭 파일 → PC 자동 저장)
        HrRecordLog.calc(listOf(
            HrRecordLog.time(receivedAt), HrRecordLog.time(t), bpm,
            when (check) {
                HrFilter.Result.OK -> "정상"
                HrFilter.Result.OUT_OF_RANGE -> "범위밖(40~180)"
                HrFilter.Result.JUMP -> "급변(5초에 30 넘게)"
            },
            behavior,
            when {
                behavior -> "제외: 상동행동 중"
                !gated.quietNow -> "제외: 행동 뒤 회복 2분"
                !valid -> "제외: 이상치"
                else -> "보류: 90초 뒤 기준선"
            },
            gated.commit.takeIf { it.isNotEmpty() }
                ?.let { "${it.size}개 ${HrRecordLog.time(it.first().first)}~${HrRecordLog.time(it.last().first)}" },
            gated.discarded?.let { "${HrRecordLog.time(it.first)}~${HrRecordLog.time(it.last)}" },
            b.count, b.spanMs / 1000.0, if (b.count > 0) b.m else null, if (b.count > 0) b.s else null, b.ready,
            _hrState.value.latestBpm, risk,
        ))
        Log.d(TAG, "심박 $bpm (5초 평균 ${_hrState.value.latestBpm}) valid=$valid 행동=$behavior M=%.1f S=%.1f n=${b.count} 위험도=${_hrState.value.risk(receivedAt)}"
            .format(b.m, b.s))
    }

    /** 다른 아동이 쓰거나 워치를 다시 찰 때 기준선만 다시 모은다 */
    @Synchronized
    fun resetBaseline() {
        baseline.clear()
        filter.reset()
        gate.reset()
        recent.clear()
        behaviorTimes.clear()
        _pending.value = 0
        _hrState.value = _hrState.value.copy(baseline = baseline.current())
    }

    /** 서버로 보낼 값. 5초 평균이 없으면(20초 넘게 끊김) null */
    fun forUpload(now: Long = System.currentTimeMillis()): Upload? {
        val bpm = _displayBpm.value ?: return null
        val state = _hrState.value
        return Upload(heartRate = bpm, heartRateAt = state.latestAt ?: now, hrRisk = state.risk(now))
    }

    data class Upload(
        /** 5초 평균 bpm */
        val heartRate: Int,
        /** 마지막으로 받은 유효값의 시각 */
        val heartRateAt: Long,
        /** 심박 위험도 0~1. 기준선 준비 전·연결 끊김이면 null */
        val hrRisk: Double?,
    )

    /** 첫 값이 들어오면 5초마다 화면 숫자를 갱신한다 */
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(HEART_RATE_DISPLAY_MS)
                _displayBpm.value = synchronized(this@WatchHeartRate) { window.tick(System.currentTimeMillis()) }
            }
        }
    }
}
