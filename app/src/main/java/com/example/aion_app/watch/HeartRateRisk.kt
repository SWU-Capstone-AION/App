package com.example.aion_app.watch

/*
 * 심박 위험도를 쓸 수 있는 상태인지 판단한다.
 *
 * 역할 나누기 (B방식)
 *  - 태블릿(여기): 개인 기준선 M·S와 심박 위험도(0~1)를 계산해 서버로 보낸다.
 *  - 서버: 비전 AI 점수와 심박 위험도를 합쳐 위험/주의를 정하고 교사 알림을 보낸다.
 *    서버 판정 규칙: 상동행동 감지 + hrRisk ≥ 0.7 → 위험, hrRisk < 0.7 → 주의, hrRisk 없음 → 비전 AI 단독.
 */

/**
 * 마지막 유효값이 이보다 오래되면 워치 연결 끊김으로 본다.
 * 실기기 화면 끔 3분 실측: 최장 공백 15.4초. 10초면 5.7%, 15초면 0.2%, 20초면 0% 시간 동안 심박을 못 쓴다.
 */
const val HR_STALE_MS = 20_000L

/** 위험도를 계산할 심박: 최근 이 시간 동안 받은 유효값의 평균 */
const val RISK_AVG_MS = 5_000L

/**
 * 최근 5초 유효값 평균.
 * 1초 값 하나로 위험도를 내면, 실기기에서 쉬는 중에도 심박이 107~125로 오르내려 위험도가 몇 초 사이에
 * 0 ↔ 0.9로 흔들렸다. 교사 홈 숫자와 같은 5초 단위로 맞춰 흔들림을 줄인다.
 * Android 의존성이 없어 단위 테스트로 검증한다.
 */
class RecentAverage(private val windowMs: Long = RISK_AVG_MS) {
    private val samples = ArrayDeque<Pair<Long, Int>>()

    fun add(at: Long, bpm: Int) {
        samples.addLast(at to bpm)
        while (samples.isNotEmpty() && at - samples.first().first >= windowMs) samples.removeFirst()
    }

    /** 값이 없으면 null */
    fun average(): Int? =
        if (samples.isEmpty()) null else Math.round(samples.map { it.second }.average()).toInt()

    fun clear() = samples.clear()
}

data class HrState(
    val baseline: Baseline,
    /** 위험도 계산에 쓰는 심박 = 최근 5초 유효값 평균 ([RecentAverage]) */
    val latestBpm: Int?,
    val latestAt: Long?,
) {
    fun available(now: Long): Boolean =
        baseline.ready && latestBpm != null && latestAt != null && now - latestAt <= HR_STALE_MS

    /** 기준선이 준비되지 않았거나 연결이 끊겼으면 null (서버는 비전 AI 단독으로 판정) */
    fun risk(now: Long): Double? =
        if (available(now)) baseline.risk(latestBpm!!) else null
}
