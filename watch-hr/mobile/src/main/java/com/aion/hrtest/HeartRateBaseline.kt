package com.aion.hrtest

import kotlin.math.abs

/*
 * 4단계: 개인 기준선과 심박 위험도.
 * 설계서 3장 공식 그대로:
 *   M = median(버퍼)
 *   S = max(MAD(버퍼) × 1.4826, 3)
 *   위험도 = clamp((현재 − M) ÷ (2 × S), 0, 1)
 * Android 의존성이 없어 단위 테스트로 바로 검증할 수 있다.
 */

/*
 * 기준선 창은 "개수"가 아니라 "시간"으로 잡는다 (실기기 측정 반영).
 * 갤럭시 워치7 실측: 화면 켬 초당 약 1.0개, 화면 끔 초당 약 0.65개(5초 묶음, 최대 15초 공백).
 * 개수로 잡으면 화면 상태에 따라 600개가 10분이 되기도 15분이 되기도 한다.
 */

/** 기준선 창: 가장 최근 조용한 값으로부터 10분. 벽시계가 아니라 마지막 값 기준이라, 상동행동 중(새 값 없음)에는 지워지지 않는다 */
const val BASELINE_WINDOW_MS = 10 * 60_000L

/** 창 안 최대 개수 (안전 상한: 초당 2개 × 10분) */
const val BASELINE_MAX = 1_200

/** 판단 보류 해제 조건 ①: 최소 개수. 화면 끔 2분 ≈ 78개에서 공백 여유를 둔 값 */
const val MIN_SAMPLES = 60

/** 판단 보류 해제 조건 ②: 첫 값~마지막 값이 최소 2분은 걸쳐 있어야 한다 */
const val MIN_SPAN_MS = 120_000L

/** S 하한: 개인 내 일별 SD 평균 3.03 bpm (Quer 2020) */
const val S_FLOOR = 3.0

/** MAD → 정규분포 SD 환산 계수 */
private const val MAD_TO_SD = 1.4826

data class Baseline(val m: Double, val s: Double, val count: Int, val spanMs: Long = 0) {
    val ready: Boolean get() = count >= MIN_SAMPLES && spanMs >= MIN_SPAN_MS

    /** 기준선이 준비되지 않았으면 0 (판단 보류) */
    fun risk(bpm: Int): Double {
        if (!ready) return 0.0
        return ((bpm - m) / (2 * s)).coerceIn(0.0, 1.0)
    }
}

class HeartRateBaseline(
    private val windowMs: Long = BASELINE_WINDOW_MS,
    private val maxSize: Int = BASELINE_MAX,
) {
    /** (받은 시각, bpm) — 시간순 */
    private val buffer = ArrayDeque<Pair<Long, Int>>()

    /**
     * 조용한 구간의, 필터를 통과하고 보류 시간을 넘긴 값만 넣는다.
     * 10분보다 오래된 값은 밀어내되, 최소 [MIN_SAMPLES]개는 남긴다.
     * 긴 상동행동이나 다음 날 재시작처럼 공백이 생겨도 기준선이 통째로 사라져 판단 보류로 돌아가지 않고,
     * 새 값이 쌓이면서 차례로 교체된다.
     */
    @Synchronized
    fun add(at: Long, bpm: Int) {
        buffer.addLast(at to bpm)
        while (buffer.size > maxSize ||
            (buffer.size > MIN_SAMPLES && at - buffer.first().first > windowMs)
        ) buffer.removeFirst()
    }

    @Synchronized
    fun clear() = buffer.clear()

    val count: Int @Synchronized get() = buffer.size

    @Synchronized
    fun current(): Baseline {
        if (buffer.isEmpty()) return Baseline(m = 0.0, s = S_FLOOR, count = 0)
        val values = buffer.map { it.second.toDouble() }
        val m = median(values)
        val mad = median(values.map { abs(it - m) })
        return Baseline(
            m = m, s = maxOf(mad * MAD_TO_SD, S_FLOOR), count = values.size,
            spanMs = buffer.last().first - buffer.first().first
        )
    }

    companion object {
        fun median(xs: List<Double>): Double {
            val sorted = xs.sorted()
            val n = sorted.size
            return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        }
    }
}

/** 이 시간 동안 대기시켰다가, 그 사이 상동행동이 감지되지 않은 값만 기준선에 넣는다 (직전 상승 60초 + 감지 지연 10초를 덮도록) */
const val QUIET_HOLD_MS = 90_000L

/** 상동행동이 끝난 뒤 이 시간 동안은 회복 구간으로 보고 기준선에 넣지 않는다 */
const val QUIET_COOLDOWN_MS = 120_000L

/**
 * 설계서 4장 ③ 조용한 구간 가드 (레드팀 반려 사유 1 대응).
 * 비전 AI는 행동이 시작된 뒤에야 감지하므로, 감지 직전 값은 "조용함"으로 들어오지만 실제로는 상승 중이다.
 *  - 보류: 값을 [holdMs] 동안 대기열에 두고, 그 사이 감지되면 대기열을 통째로 버린다
 *  - 쿨다운: 행동이 끝난 뒤 [cooldownMs] 동안은 회복 중으로 보고 받지 않는다
 */
class QuietGate(
    private val holdMs: Long = QUIET_HOLD_MS,
    private val cooldownMs: Long = QUIET_COOLDOWN_MS,
) {
    private val pending = ArrayDeque<Pair<Long, Int>>()
    private var lastBehaviorAt: Long? = null

    data class Result(
        /** 지금 기준선에 넣을 (받은 시각, bpm) — 보류 시간을 채운 것. DB에 quiet 표시를 하는 데도 쓴다 */
        val commit: List<Pair<Long, Int>>,
        /** 상동행동 감지로 버린 대기 값의 시각 범위 (로그용) */
        val discarded: LongRange?,
        /** 지금 받은 값이 조용한 구간 후보인지 (행동 중·쿨다운이면 false) */
        val quietNow: Boolean,
    )

    fun offer(bpm: Int, at: Long, behaviorActive: Boolean, valid: Boolean): Result {
        if (behaviorActive) {
            lastBehaviorAt = at
            val dropped = if (pending.isEmpty()) null else pending.first().first..pending.last().first
            pending.clear()
            return Result(emptyList(), dropped, quietNow = false)
        }
        val last = lastBehaviorAt
        if (last != null && at - last < cooldownMs) return Result(emptyList(), null, quietNow = false)

        if (valid) pending.addLast(at to bpm)
        val commit = mutableListOf<Pair<Long, Int>>()
        while (pending.isNotEmpty() && at - pending.first().first >= holdMs) {
            commit += pending.removeFirst()
        }
        return Result(commit, null, quietNow = true)
    }

    fun reset() {
        pending.clear()
        lastBehaviorAt = null
    }

    val pendingCount: Int get() = pending.size
}

/** 설계서 4장 ②: 기준선에 넣기 전에 버릴 값 */
class HrFilter(
    private val min: Int = 40,
    private val max: Int = 180,
    /** "급변" 기준 — 잠정값. 실기기 착용 데이터로 확정 (설계서 미결 #2) */
    private val maxJumpBpm: Int = 30,
    /** 이보다 오래 비었으면 직전 값과 비교하지 않는다 */
    private val jumpWindowMs: Long = 5_000,
) {
    private var lastBpm: Int? = null
    private var lastAt: Long = 0

    enum class Result { OK, OUT_OF_RANGE, JUMP }

    /** 새 세션에서 이전 세션의 마지막 값과 비교하지 않도록 */
    fun reset() {
        lastBpm = null
        lastAt = 0
    }

    fun check(bpm: Int, at: Long): Result {
        if (bpm !in min..max) return Result.OUT_OF_RANGE
        val prev = lastBpm
        val recent = prev != null && at - lastAt <= jumpWindowMs
        // 급변 값은 버리되 기준점은 갱신해서, 실제로 바뀐 심박이 계속 버려지지 않게 한다
        lastBpm = bpm
        lastAt = at
        if (recent && abs(bpm - prev!!) > maxJumpBpm) return Result.JUMP
        return Result.OK
    }
}
