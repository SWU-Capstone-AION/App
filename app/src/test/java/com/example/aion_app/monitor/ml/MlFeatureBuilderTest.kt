package com.example.aion_app.monitor.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 학습모델 입력 만들기 확인 (안드로이드 기기 없이 실행)
 *   ./gradlew :app:testDebugUnitTest --tests "*MlFeatureBuilderTest*"
 */
class MlFeatureBuilderTest {

    private val columns = listOf(
        "nose", "left_eye", "right_eye", "left_ear", "right_ear",
        "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
        "left_wrist", "right_wrist", "left_hip", "right_hip",
    ).flatMap { listOf("${it}_x", "${it}_y") }

    private fun builder(mean: Float = 0f) = MlFeatureBuilder.fromColumns(
        columns, FloatArray(26) { mean }, FloatArray(26) { 1f },
    )

    /** fps 로 찍은 i번째 프레임의 시각 (ms, 올림) */
    private fun ms(i: Int, fps: Int) = (i * 1000L + fps - 1) / fps

    /** 가만히 서 있는 사람 1명 */
    private fun stillPerson() = FloatArray(66) { 0.5f }.also {
        it[11 * 2] = 0.6f; it[11 * 2 + 1] = 0.4f   // 왼어깨
        it[12 * 2] = 0.4f; it[12 * 2 + 1] = 0.4f   // 오른어깨
    }

    @Test
    fun `30fps 기준 2초가 차면 첫 판정, 이후 0_5초마다`() {
        val b = builder()
        val readyAt = mutableListOf<Int>()
        for (i in 0 until 150) {               // 30fps 로 5초
            val out = b.addFrame(ms(i, 30), stillPerson())
            if (out.isNotEmpty()) readyAt += i
        }
        assertEquals(listOf(59, 74, 89, 104, 119, 134, 149), readyAt)
    }

    @Test
    fun `카메라가 15fps 여도 30fps 로 맞춰서 같은 간격`() {
        val b = builder()
        var count = 0
        for (i in 0..75) {                     // 15fps 로 5초
            count += b.addFrame(ms(i, 15), stillPerson()).size
        }
        assertEquals(7, count)
    }

    @Test
    fun `가만히 있으면 속도는 0 이고 표준화 결과는 -mean`() {
        val b = builder(mean = 0.25f)
        var last: MlFeatureBuilder.Output? = null
        for (i in 0 until 60) b.addFrame(ms(i, 30), stillPerson()).forEach { last = it }
        val ready = last as MlFeatureBuilder.Output.Ready
        assertEquals(60 * 26, ready.input.size)
        assertTrue(ready.input.all { kotlin.math.abs(it + 0.25f) < 1e-5f })
    }

    @Test
    fun `사람이 절반 넘게 안 잡히면 건너뜀`() {
        val b = builder()
        var last: MlFeatureBuilder.Output? = null
        for (i in 0 until 60) {
            val pts = if (i < 5 || i % 2 == 0) stillPerson() else null
            b.addFrame(ms(i, 30), pts).forEach { last = it }
        }
        assertTrue(last is MlFeatureBuilder.Output.Skipped)
    }
}
