package com.aion.hrtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateBaselineTest {

    /** 값을 [stepMs] 간격으로 넣는다 (기본 1초) */
    private fun baselineOf(values: List<Int>, stepMs: Long = 1000) =
        HeartRateBaseline().apply { values.forEachIndexed { i, v -> add(i * stepMs, v) } }.current()

    /** 판단 보류가 풀린 기준선 (테스트용) */
    private fun ready(m: Double, s: Double) = Baseline(m, s, count = MIN_SAMPLES, spanMs = MIN_SPAN_MS)

    @Test
    fun `M은 중앙값, S는 MAD x 1_4826`() {
        // 72~88 을 고르게: 중앙값 80, |x-80| = 8,4,0,4,8 → MAD 4 → S 5.93
        val values = List(150) { listOf(72, 76, 80, 84, 88)[it % 5] }
        val b = baselineOf(values)
        assertEquals(80.0, b.m, 1e-9)
        assertEquals(4 * 1.4826, b.s, 1e-9)
    }

    @Test
    fun `변동이 작으면 S는 하한 3`() {
        val b = baselineOf(List(150) { 85 })
        assertEquals(85.0, b.m, 1e-9)
        assertEquals(S_FLOOR, b.s, 1e-9)
    }

    @Test
    fun `개수가 부족하면 판단 보류로 위험도 0`() {
        // 3초 간격 59개 = 174초: 시간은 넘지만 개수 부족
        val b = baselineOf(List(MIN_SAMPLES - 1) { 85 }, stepMs = 3000)
        assertFalse(b.ready)
        assertEquals(0.0, b.risk(150), 1e-9)
    }

    @Test
    fun `시간이 부족하면 판단 보류`() {
        // 1초 간격 100개 = 99초: 개수는 넘지만 2분 미만
        assertFalse(baselineOf(List(100) { 85 }).ready)
    }

    @Test
    fun `화면 끔 속도(초당 0_65개)로도 2분이면 준비된다`() {
        // 실기기 화면 끔 실측: 3분 117개 → 약 1.54초에 1개. 2분이면 약 78개
        val b = baselineOf(List(80) { 85 }, stepMs = 1540)
        assertTrue(b.ready)
    }

    @Test
    fun `설계서 계산 예시 A~D`() {
        // 설계서 3장 표: (M, S, 현재) → 위험도
        fun risk(m: Double, s: Double, bpm: Int) = ready(m, s).risk(bpm)
        assertEquals(1.00, risk(72.0, 4.0, 80), 1e-9)   // A
        assertEquals(0.50, risk(96.0, 4.0, 100), 1e-9)  // B
        assertEquals(0.50, risk(85.0, 6.0, 91), 1e-9)   // C
        assertEquals(0.50, risk(85.0, 3.0, 88), 1e-9)   // D (S 2 → 하한 3)
        assertEquals(0.0, risk(85.0, 3.0, 70), 1e-9)    // 평소보다 낮으면 0
        assertEquals(1.0, risk(85.0, 3.0, 130), 1e-9)   // 상한 1
    }

    @Test
    fun `창은 개수가 아니라 최근 10분`() {
        // 화면 켬(1초) 5분은 70, 이어서 10분은 90 → 10분 창에는 90만 남는다
        val baseline = HeartRateBaseline()
        var t = 0L
        repeat(300) { baseline.add(t, 70); t += 1000 }
        repeat(600) { baseline.add(t, 90); t += 1000 }
        val b = baseline.current()
        assertEquals(90.0, b.m, 1e-9)
        assertTrue(b.spanMs <= BASELINE_WINDOW_MS)

        // 화면 끔(1.54초)이면 같은 10분에 약 390개 — 개수는 달라도 시간 창은 같다
        val off = HeartRateBaseline()
        repeat(900) { off.add(it * 1540L, 85) }
        assertTrue(off.current().count in 385..395)
    }

    @Test
    fun `공백 뒤에도 기준선이 통째로 사라지지 않는다`() {
        // 조용한 값 5분 → 1시간 공백(긴 행동, 다음 날 등) → 새 값 1개
        val baseline = HeartRateBaseline()
        repeat(300) { baseline.add(it * 1000L, 80) }
        baseline.add(300_000L + 3_600_000L, 82)
        val b = baseline.current()
        assertEquals(MIN_SAMPLES, b.count)   // 오래된 값은 밀려나도 최소 개수는 남김
        assertTrue(b.ready)                  // 판단 보류로 돌아가지 않음
        assertEquals(80.0, b.m, 1e-9)
    }

    @Test
    fun `필터 - 범위 밖과 급변은 제외`() {
        val f = HrFilter()
        assertEquals(HrFilter.Result.OUT_OF_RANGE, f.check(35, 0))
        assertEquals(HrFilter.Result.OUT_OF_RANGE, f.check(190, 1000))
        assertEquals(HrFilter.Result.OK, f.check(80, 2000))
        assertEquals(HrFilter.Result.JUMP, f.check(140, 3000))   // +60
        assertEquals(HrFilter.Result.OK, f.check(145, 4000))     // 새 기준점 140 대비 +5
        assertEquals(HrFilter.Result.OK, f.check(80, 60_000))    // 오래 비었으면 비교 안 함
    }

    @Test
    fun `필터 reset 후에는 이전 값과 비교하지 않는다`() {
        val f = HrFilter()
        assertEquals(HrFilter.Result.OK, f.check(145, 0))
        f.reset()
        assertEquals(HrFilter.Result.OK, f.check(79, 1000))
    }

    @Test
    fun `통합 판정 - Case A, B, 비전 단독`() {
        val ready = ready(80.0, 3.0)
        val now = 100_000L

        assertEquals(Verdict.DANGER, judge(HrState(ready, 86, now - 1000), now))     // 위험도 1.0
        assertEquals(Verdict.CAUTION, judge(HrState(ready, 81, now - 1000), now))    // 위험도 0.17
        assertEquals(Verdict.CAUTION, judge(HrState(ready, 81, now - 15_000), now))  // 화면 끔 15초 공백은 허용
        assertEquals(Verdict.VISION_ONLY, judge(HrState(ready, 86, now - 21_000), now)) // 20초 넘으면 연결 끊김
        assertEquals(Verdict.VISION_ONLY, judge(HrState(ready, null, null), now))       // 수신 없음
        val notReady = Baseline(m = 80.0, s = 3.0, count = 10, spanMs = 10_000)
        assertEquals(Verdict.VISION_ONLY, judge(HrState(notReady, 86, now), now))       // 샘플 부족
    }

    @Test
    fun `위험도 가용성`() {
        val ready = ready(80.0, 3.0)
        assertTrue(HrState(ready, 80, 0).available(HR_STALE_MS))
        assertNull(HrState(ready, 80, 0).risk(HR_STALE_MS + 1))
    }
}
