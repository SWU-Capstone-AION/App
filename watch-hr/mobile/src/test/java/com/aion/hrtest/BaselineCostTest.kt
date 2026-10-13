package com.aion.hrtest

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 레드팀 반려 사유 2 근거: 기준선 갱신 1회(창 안 값 중앙값 + MAD) 비용.
 * 화면 켬(초당 1개)일 때 10분 창이 가장 커서(600개) 그 조건으로 잰다.
 * PC JVM 기준이라 태블릿은 이보다 느리다 → 결과는 설계서에 "PC 측정값 × 보수 계수"로 적는다.
 */
class BaselineCostTest {

    @Test
    fun `600개 기준선 갱신 비용 측정`() {
        val rnd = Random(1)
        val baseline = HeartRateBaseline()
        var t = 0L
        fun step() { baseline.add(t, rnd.nextInt(70, 100)); t += 1000 }
        repeat(600) { step() }

        // 워밍업 (JIT)
        repeat(20_000) { step(); baseline.current() }

        val n = 50_000
        val t0 = System.nanoTime()
        repeat(n) { step(); baseline.current() }
        val perUpdateUs = (System.nanoTime() - t0) / 1000.0 / n

        // 아동 10명 × 1초 1회 갱신일 때 1초 중 차지하는 비율
        val tenChildrenPct = perUpdateUs * 10 / 1_000_000 * 100
        println("BENCH window=${baseline.count} perUpdateUs=%.1f tenChildrenPerSecPct=%.3f".format(perUpdateUs, tenChildrenPct))
        assertTrue(perUpdateUs < 5_000)
    }
}
