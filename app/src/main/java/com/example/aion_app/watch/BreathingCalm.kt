package com.example.aion_app.watch

/*
 * 심호흡 가이드를 심박으로 끝내기.
 *
 * 호흡을 시작할 때 심박이 올라가 있었다면, 심박이 평소 범위로 내려왔을 때 호흡을 끝내고 홈으로 돌아간다.
 * 심박이 "내려가는 것"을 확인할 수 없으면(워치 끊김, 기준선 준비 전, 처음부터 평소 범위) 기존처럼 정해진 횟수만 한다.
 * Android 의존성이 없어 단위 테스트로 검증한다.
 */

/** 위험도가 이 값보다 낮으면 평소 범위로 본다. 위험도 구간: 초록(평소) < 0.3 ≤ 노랑(상승) < 0.7 ≤ 빨강(높음) */
const val CALM_RISK = 0.3

/** 평소 범위가 이 시간 이상 이어져야 진정으로 본다. 위험도에 쓰는 심박이 5초 평균이라 두 번 연속을 요구한다 */
const val CALM_HOLD_MS = 10_000L

class BreathingCalmTracker(
    startState: HrState?,
    startAt: Long,
    private val holdMs: Long = CALM_HOLD_MS,
) {
    /** 시작할 때 위험도 (기준선 준비 전·워치 끊김이면 null) */
    val startRisk: Double? = startState?.risk(startAt)

    /** 심박으로 끝낼지. 시작할 때 심박이 올라가 있었을 때만 true */
    val usesHeartRate: Boolean = startRisk?.let { it >= CALM_RISK } ?: false

    private var calmSince: Long? = null

    /**
     * 지금 심박 상태를 넣는다. 평소 범위가 [holdMs] 이상 이어졌으면 true.
     * 위험도를 쓸 수 없거나(끊김) 다시 올라가면 처음부터 다시 잰다.
     */
    fun update(state: HrState?, now: Long): Boolean {
        val risk = state?.risk(now)
        if (risk == null || risk >= CALM_RISK) {
            calmSince = null
            return false
        }
        val since = calmSince ?: now.also { calmSince = it }
        return now - since >= holdMs
    }

    /** 지금 심박을 쓸 수 있는지. 호흡 중 워치가 끊기면 정해진 횟수 규칙으로 돌아가는 데 쓴다 */
    fun heartRateAvailable(state: HrState?, now: Long): Boolean = state?.risk(now) != null
}
