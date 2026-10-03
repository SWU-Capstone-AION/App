package com.aion.hrtest

/*
 * 설계서 5장: 비전 AI 1차 신호 + 심박 위험도 통합 판정.
 * 비전 AI가 "상동행동 N초 지속"을 알린 직후에만 호출한다. 심박만으로는 판정하지 않는다.
 */

/** Case A 경계 — 잠정값. 공개 데이터셋/실측으로 확정 (설계서 미결 #4) */
const val HR_RISK_HIGH = 0.7

/**
 * 마지막 유효값이 이보다 오래되면 워치 연결 끊김으로 본다.
 * 실기기 화면 끔 3분 실측: 최장 공백 15.4초. 10초면 5.7%, 15초면 0.2%, 20초면 0% 시간 동안 심박을 못 쓴다.
 */
const val HR_STALE_MS = 20_000L

enum class Verdict(val label: String) {
    /** Case A: 행동 + 심박 상승 → 교사 알림 + AR 안정 훈련 */
    DANGER("위험 · 교사 알림 + AR 실행"),
    /** Case B: 행동은 감지됐지만 심박이 평온 → 알림 유예, 기록만 */
    CAUTION("주의 · 알림 유예 (단순 움직임 가능성)"),
    /** 심박 판단 불가(연결 끊김·미착용·샘플 부족) → 비전 AI 단독 */
    VISION_ONLY("위험 · 비전 AI 단독 판정 (심박 없음)"),
}

data class HrState(
    val baseline: Baseline,
    val latestBpm: Int?,
    val latestAt: Long?,
) {
    fun available(now: Long): Boolean =
        baseline.ready && latestBpm != null && latestAt != null && now - latestAt <= HR_STALE_MS

    fun risk(now: Long): Double? =
        if (available(now)) baseline.risk(latestBpm!!) else null
}

fun judge(hr: HrState, now: Long): Verdict {
    val risk = hr.risk(now) ?: return Verdict.VISION_ONLY
    return if (risk >= HR_RISK_HIGH) Verdict.DANGER else Verdict.CAUTION
}
