package com.example.aion_app.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietGateTest {

    @Test
    fun `보류 시간을 채운 값만 기준선에 들어간다`() {
        val g = QuietGate(holdMs = 90_000, cooldownMs = 120_000)
        for (s in 0 until 90) {
            assertTrue(g.offer(80, s * 1000L, behaviorActive = false, valid = true).commit.isEmpty())
        }
        // 90초가 지나면 0초에 받은 값부터 하나씩 나온다
        assertEquals(listOf(0L to 80), g.offer(81, 90_000, false, true).commit)
    }

    @Test
    fun `상동행동이 감지되면 직전 대기 값을 버린다`() {
        val g = QuietGate(holdMs = 90_000, cooldownMs = 120_000)
        for (s in 0 until 60) g.offer(95, s * 1000L, false, true)   // 직전 상승 중 (비전은 아직 모름)
        val r = g.offer(110, 60_000, behaviorActive = true, valid = true)
        assertEquals(0L..59_000L, r.discarded)
        assertFalse(r.quietNow)
        assertEquals(0, g.pendingCount)
    }

    @Test
    fun `행동이 끝난 뒤 쿨다운 동안은 받지 않는다`() {
        val g = QuietGate(holdMs = 90_000, cooldownMs = 120_000)
        g.offer(110, 0, behaviorActive = true, valid = true)
        val during = g.offer(100, 60_000, behaviorActive = false, valid = true)
        assertFalse(during.quietNow)
        assertEquals(0, g.pendingCount)
        val after = g.offer(85, 120_000, behaviorActive = false, valid = true)
        assertTrue(after.quietNow)
        assertEquals(1, g.pendingCount)
    }

    @Test
    fun `필터에 걸린 값은 대기열에도 넣지 않는다`() {
        val g = QuietGate()
        g.offer(190, 0, behaviorActive = false, valid = false)
        assertEquals(0, g.pendingCount)
    }
}
