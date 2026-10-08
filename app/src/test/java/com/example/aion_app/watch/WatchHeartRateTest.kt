package com.example.aion_app.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 워치가 모아 뒀다가 한 번에 보낸 값도 "잰 시각" 기준으로 처리되는지 */
class WatchHeartRateTest {

    private val t0 = 1_000_000_000L

    @Before
    fun reset() = WatchHeartRate.resetBaseline()

    @Test
    fun `한꺼번에 받은 200초치 값도 잰 시각대로 기준선에 들어간다`() {
        // 실기기 상황: 연결이 끊긴 동안 모인 값이 1초 안에 한 번에 도착
        val arrived = t0 + 200_500
        for (i in 0 until 200) WatchHeartRate.onReceived(70, at = t0 + i * 1_000L, receivedAt = arrived)

        val b = WatchHeartRate.hrState.value.baseline
        // 마지막 값(199초)보다 90초 넘게 앞선 0~109초 값 110개가 보류를 넘겨 기준선에 들어간다
        // (받은 시각으로 처리하면 모두 같은 순간이라 0개였다)
        assertEquals(110, b.count)
        assertEquals(109_000L, b.spanMs)
    }

    @Test
    fun `늦게 온 옛날 값으로는 지금 위험도를 내지 않는다`() {
        val arrived = t0 + 300_000
        for (i in 0 until 200) WatchHeartRate.onReceived(70, at = t0 + i * 1_000L, receivedAt = arrived)
        // 마지막 값이 101초 전에 잰 것이므로 연결 끊김(20초)으로 보고 판단 보류
        assertNull(WatchHeartRate.hrState.value.risk(arrived))
    }

    @Test
    fun `잰 시각에 상동행동 중이었으면 늦게 와도 기준선에서 뺀다`() {
        // 50초 무렵 비전 AI가 상동행동을 봤다
        WatchHeartRate.onVision(1.0, now = t0 + 50_000)
        val arrived = t0 + 200_500
        for (i in 0 until 200) WatchHeartRate.onReceived(70, at = t0 + i * 1_000L, receivedAt = arrived)

        // 50초 전 대기 값은 버리고, 52초부터 2분은 회복 구간 → 172초 이후 값은 아직 보류 중
        assertEquals(0, WatchHeartRate.hrState.value.baseline.count)
        assertTrue(WatchHeartRate.pending.value > 0)
    }
}
