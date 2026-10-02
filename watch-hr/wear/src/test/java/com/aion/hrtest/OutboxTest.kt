package com.aion.hrtest

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxTest {

    private fun samples(vararg bpm: Int) = bpm.mapIndexed { i, b -> HrSample(bpm = b, at = i * 1000L) }

    @Test
    fun `연결되면 순서대로 모두 보낸다`() = runBlocking {
        val box = Outbox()
        val got = mutableListOf<Int>()
        box.add(samples(70, 71, 72))
        assertEquals(3, box.flush(0) { got += it.bpm; true })
        assertEquals(listOf(70, 71, 72), got)
        assertEquals(0, box.size)
    }

    @Test
    fun `못 보낸 값은 남겨 뒀다가 다시 보낸다`() = runBlocking {
        val box = Outbox()
        val got = mutableListOf<Int>()
        var online = false
        box.add(samples(70, 71))
        assertEquals(0, box.flush(0) { if (online) { got += it.bpm; true } else false })
        assertEquals(2, box.size)          // 경로가 바뀌는 사이 — 버리지 않음
        box.add(samples(72))
        online = true
        box.flush(16_000) { if (online) { got += it.bpm; true } else false }
        assertEquals(listOf(70, 71, 72), got)  // 순서 유지
    }

    @Test
    fun `5초 미만의 실패로는 연결 끊김으로 보지 않는다`() = runBlocking {
        val box = Outbox()   // 기본값 DISCONNECT_GRACE_MS = 5초
        box.add(samples(70))
        box.flush(1_000) { false }
        assertTrue(box.connected(1_500))     // 0.5초 뒤 — 깜빡이지 않음
        assertTrue(box.connected(5_999))
        assertFalse(box.connected(6_000))    // 실패가 5초 이어지면 연결 끊김
        box.flush(7_000) { true }
        assertTrue(box.connected(7_000))     // 한 번 보내지면 바로 회복
    }

    @Test
    fun `측정값이 안 오는 것만으로는 연결 끊김이 아니다`() {
        // 화면 꺼짐 중 15초 공백처럼 보낼 게 없을 때
        assertTrue(Outbox().connected(60_000))
    }

    @Test
    fun `0 bpm은 넣지 않는다`() {
        val box = Outbox()
        box.add(samples(72, 0, 73))
        assertEquals(2, box.size)
    }

    @Test
    fun `넘치면 가장 오래된 것부터 버린다`() = runBlocking {
        val box = Outbox(capacity = 3)
        box.add(samples(1, 2, 3, 4, 5).map { it.copy(bpm = it.bpm + 60) })
        assertEquals(3, box.size)
        assertEquals(2, box.dropped)
        val got = mutableListOf<Int>()
        box.flush(0) { got += it.bpm; true }
        assertEquals(listOf(63, 64, 65), got)
    }
}
