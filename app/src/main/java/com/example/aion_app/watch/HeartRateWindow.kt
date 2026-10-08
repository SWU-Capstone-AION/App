package com.example.aion_app.watch

import kotlin.math.roundToInt

/*
 * 심박 데이터는 1초마다 들어오지만, 화면 숫자와 서버 전송값은 HEART_RATE_DISPLAY_MS(5초)에 한 번만 바뀐다.
 * 5초 동안 들어온 값들의 평균을 써서, 순간적으로 튀는 값 대신 안정된 숫자가 5초마다 갱신된다.
 * 20초(HEART_RATE_STALE_MS) 넘게 새 값이 없으면 null("--")로 바꾼다.
 */

/** 화면 숫자가 바뀌는 간격 */
const val HEART_RATE_DISPLAY_MS = 5_000L

/** 마지막 값이 이보다 오래되면 "연결 확인"으로 본다 (태블릿의 연결 끊김 기준과 같음) */
const val HEART_RATE_STALE_MS = 20_000L

/**
 * 1초마다 들어오는 심박을 모았다가, 5초마다 보여줄 숫자 하나를 만든다.
 * Android 의존성이 없어 단위 테스트로 검증한다.
 */
class HeartRateWindow(
    private val windowMs: Long = HEART_RATE_DISPLAY_MS,
    private val staleMs: Long = HEART_RATE_STALE_MS,
) {
    private val samples = ArrayDeque<Pair<Long, Int>>()   // (측정 시각, bpm)
    private var shown: Int? = null
    private var lastSampleAt: Long? = null

    /** 새 심박 1개. 40~180 bpm 밖의 값은 센서 오류로 보고 버린다 */
    fun add(bpm: Int, at: Long) {
        if (bpm !in 40..180) return
        samples.addLast(at to bpm)
        lastSampleAt = maxOf(lastSampleAt ?: at, at)
    }

    /**
     * 5초마다 부른다. 최근 5초 동안 들어온 값의 평균으로 화면 숫자를 바꾼다.
     * 5초 동안 새 값이 없으면 이전 숫자를 그대로 두고, 20초 넘게 없으면 null("--").
     */
    fun tick(now: Long): Int? {
        while (samples.isNotEmpty() && now - samples.first().first > windowMs) samples.removeFirst()
        if (samples.isNotEmpty()) shown = samples.map { it.second }.average().roundToInt()
        val last = lastSampleAt
        if (last == null || now - last > staleMs) shown = null
        return shown
    }
}
