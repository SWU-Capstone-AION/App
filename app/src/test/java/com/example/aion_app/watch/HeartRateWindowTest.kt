package com.example.aion_app.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateWindowTest {

    @Test
    fun `1초마다 들어와도 숫자는 5초마다 평균으로 한 번만 바뀐다`() {
        val w = HeartRateWindow()
        // 0~4초: 70, 72, 74, 76, 78 → 5초에 평균 74
        listOf(70, 72, 74, 76, 78).forEachIndexed { i, b -> w.add(b, i * 1000L + 500) }
        assertEquals(74, w.tick(5_000))
        // 5~9초: 80~84 → 10초에 평균 82 (이전 5초 값은 섞이지 않음)
        listOf(80, 81, 82, 83, 84).forEachIndexed { i, b -> w.add(b, 5_000L + i * 1000 + 500) }
        assertEquals(82, w.tick(10_000))
    }

    @Test
    fun `5초 동안 새 값이 없으면 이전 숫자를 유지한다`() {
        val w = HeartRateWindow()
        w.add(75, 4_000)
        assertEquals(75, w.tick(5_000))
        assertEquals(75, w.tick(10_000))   // 화면 꺼짐 중 잠깐 공백
    }

    @Test
    fun `20초 넘게 값이 없으면 null(--)`() {
        val w = HeartRateWindow()
        w.add(75, 0)
        assertEquals(75, w.tick(5_000))
        assertEquals(75, w.tick(20_000))
        assertNull(w.tick(25_000))
    }

    @Test
    fun `40~180 밖의 값은 평균에 넣지 않는다`() {
        val w = HeartRateWindow()
        w.add(0, 1_000)
        w.add(80, 2_000)
        w.add(250, 3_000)
        assertEquals(80, w.tick(5_000))
    }

    @Test
    fun `값이 한 번도 없으면 null`() {
        assertNull(HeartRateWindow().tick(5_000))
    }
}
