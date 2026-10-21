package com.example.aion_app.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathingCalmTrackerTest {

    /** 기준선 M 80, S 4 (판단 보류 해제). 위험도 = (bpm − 80) ÷ 8 */
    private val ready = Baseline(m = 80.0, s = 4.0, count = MIN_SAMPLES, spanMs = MIN_SPAN_MS)

    private fun state(bpm: Int?, at: Long?) = HrState(ready, latestBpm = bpm, latestAt = at)

    @Test
    fun `시작할 때 심박이 올라가 있으면 심박으로 끝낸다`() {
        val t = BreathingCalmTracker(state(90, 0), startAt = 0)   // 위험도 1.0
        assertTrue(t.usesHeartRate)
        assertEquals(1.0, t.startRisk!!, 1e-9)
    }

    @Test
    fun `처음부터 평소 범위면 정해진 횟수로 끝낸다`() {
        val t = BreathingCalmTracker(state(81, 0), startAt = 0)   // 위험도 0.125
        assertFalse(t.usesHeartRate)
    }

    @Test
    fun `기준선 준비 전이나 워치 끊김이면 정해진 횟수로 끝낸다`() {
        val notReady = HrState(Baseline(m = 80.0, s = 4.0, count = 10, spanMs = 10_000), 95, 0)
        assertFalse(BreathingCalmTracker(notReady, startAt = 0).usesHeartRate)
        assertNull(BreathingCalmTracker(notReady, startAt = 0).startRisk)

        // 마지막 값이 20초 넘게 지남 = 끊김
        assertFalse(BreathingCalmTracker(state(95, 0), startAt = HR_STALE_MS + 1).usesHeartRate)
        assertFalse(BreathingCalmTracker(null, startAt = 0).usesHeartRate)
    }

    @Test
    fun `평소 범위가 10초 이어져야 진정으로 본다`() {
        val t = BreathingCalmTracker(state(95, 0), startAt = 0)
        assertFalse(t.update(state(92, 1_000), 1_000))       // 아직 높음 (위험도 1.0)
        assertFalse(t.update(state(82, 5_000), 5_000))       // 평소 범위 시작 (0.25)
        assertFalse(t.update(state(81, 10_000), 10_000))     // 5초째
        assertTrue(t.update(state(81, 15_000), 15_000))      // 10초째 → 진정
    }

    @Test
    fun `중간에 다시 올라가면 처음부터 다시 잰다`() {
        val t = BreathingCalmTracker(state(95, 0), startAt = 0)
        t.update(state(82, 0), 0)
        assertFalse(t.update(state(86, 8_000), 8_000))      // 0.75 → 다시 올라감
        assertFalse(t.update(state(82, 12_000), 12_000))    // 다시 평소 범위
        assertFalse(t.update(state(82, 18_000), 18_000))    // 6초째
        assertTrue(t.update(state(82, 22_000), 22_000))     // 10초째
    }

    @Test
    fun `위험도 0_3 미만만 평소 범위다`() {
        // 위험도 0.3 = 80 + 0.3 × 8 = 82.4 bpm. 82(0.25)는 평소, 83(0.375)은 상승
        val calm = BreathingCalmTracker(state(95, 0), startAt = 0, holdMs = 0)
        assertTrue(calm.update(state(82, 0), 0))
        val raised = BreathingCalmTracker(state(95, 0), startAt = 0, holdMs = 0)
        assertFalse(raised.update(state(83, 0), 0))
    }

    @Test
    fun `워치가 끊기면 진정으로 보지 않고 심박 사용 불가를 알린다`() {
        val t = BreathingCalmTracker(state(95, 0), startAt = 0)
        t.update(state(81, 0), 0)
        // 마지막 값 시각 0에서 25초 지남 → 끊김
        assertFalse(t.update(state(81, 0), 25_000))
        assertFalse(t.heartRateAvailable(state(81, 0), 25_000))
        assertTrue(t.heartRateAvailable(state(81, 20_000), 25_000))
    }
}
