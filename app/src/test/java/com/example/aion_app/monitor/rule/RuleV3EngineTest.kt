package com.example.aion_app.monitor.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * 규칙모델 v3 엔진 단위 테스트 (가짜 움직임).
 * HTML 원본과 같은 입력을 넣었을 때 결과가 소수점 6자리까지 같다는 것은 따로 확인했다.
 * 여기서는 행동별로 '잡혀야 할 것이 잡히는지'만 본다.
 */
class RuleV3EngineTest {

    private val w = 1280
    private val h = 720

    /** 앉아 있는 사람 하나. leftWristDy 로 왼손을 위아래로 움직인다 */
    private fun frame(leftWristDy: Double = 0.0, rock: Double = 0.0): FrameInput {
        val cx = 0.5; val cy = 0.55; val k = 1 + 0.12 * rock; val sw = 0.22 * k
        val p = MutableList(33) { Pt(cx, 0.97, 0.0, 0.1) }
        fun put(i: Int, x: Double, y: Double, v: Double = 0.98) { p[i] = Pt(x, y, 0.0, v) }
        put(0, cx, cy - 0.20 * k)
        put(1, cx + 0.02 * k, cy - 0.235 * k); put(2, cx + 0.035 * k, cy - 0.235 * k); put(3, cx + 0.05 * k, cy - 0.235 * k)
        put(4, cx - 0.02 * k, cy - 0.235 * k); put(5, cx - 0.035 * k, cy - 0.235 * k); put(6, cx - 0.05 * k, cy - 0.235 * k)
        put(7, cx + 0.075 * k, cy - 0.22 * k); put(8, cx - 0.075 * k, cy - 0.22 * k)
        put(9, cx + 0.02 * k, cy - 0.17 * k); put(10, cx - 0.02 * k, cy - 0.17 * k)
        put(11, cx + sw / 2, cy); put(12, cx - sw / 2, cy)
        val lwx = cx + sw / 2 + 0.05; val lwy = cy + 0.18 - 0.10 * leftWristDy
        val rwx = cx - sw / 2 - 0.05; val rwy = cy + 0.18
        put(13, (cx + sw / 2 + lwx) / 2 + 0.03, (cy + lwy) / 2 + 0.03)
        put(14, (cx - sw / 2 + rwx) / 2 - 0.03, (cy + rwy) / 2 + 0.03)
        put(15, lwx, lwy); put(16, rwx, rwy)
        put(17, lwx + 0.01, lwy + 0.04); put(18, rwx - 0.01, rwy + 0.04)
        put(19, lwx - 0.01, lwy + 0.045); put(20, rwx + 0.01, rwy + 0.045)
        put(21, lwx - 0.02, lwy + 0.02); put(22, rwx + 0.02, rwy + 0.02)
        put(23, cx + 0.08, cy + 0.33, 0.6); put(24, cx - 0.08, cy + 0.33, 0.6)
        val world = p.map { Pt((it.x - 0.5) * 1.6, (it.y - 0.6) * 1.2, 0.0) }.toMutableList()
        world[11] = world[11].copy(z = -0.08 * rock - 0.02); world[12] = world[12].copy(z = -0.08 * rock - 0.02)
        return FrameInput(p, world, emptyList(), w, h)
    }

    private fun run(seconds: Double, engine: RuleV3Engine, start: Long, motion: (Double) -> FrameInput): TickState? {
        var last: TickState? = null
        val n = (seconds * 30).toInt()
        for (i in 0 until n) {
            val t = i / 30.0
            last = engine.process(start + Math.round(t * 1000), motion(t), null).tick
        }
        return last
    }

    @Test
    fun still_person_is_safe() {
        val tick = run(10.0, RuleV3Engine(), 100_000) { frame() }!!
        assertEquals(0, tick.stage)
        assertTrue((tick.pRuleRaw ?: 0.0) < 0.2)
    }

    @Test
    fun left_hand_flapping_is_detected_and_alarms_after_4s() {
        val tick = run(12.0, RuleV3Engine(), 100_000) { t -> frame(leftWristDy = sin(2 * PI * t / 0.45)) }!!
        assertEquals("flap", tick.ev.dominant)
        assertEquals(SIDE_L, tick.ev.beh.getValue("flap").side)
        assertEquals(2, tick.stage)
        assertTrue(tick.inEpisode)
    }

    @Test
    fun body_rocking_is_detected() {
        val tick = run(12.0, RuleV3Engine(), 100_000) { t -> frame(rock = sin(2 * PI * t / 1.2)) }!!
        assertEquals("body_rock", tick.ev.dominant)
        assertTrue(tick.d > 0.5)
    }

    @Test
    fun five_waves_alarm_on_default_sensitivity_even_with_low_dl_score() {
        // 실기기 시험: 학습모델 0.2, 팔 5번 흔들기(0.6초 간격) → 기본(민감)에서는 위험까지 가야 한다
        val engine = RuleV3Engine()
        var alarm = false
        for (i in 0 until 12 * 30) {
            val t = i / 30.0
            val ph = t - 3.0
            val mv = if (ph in 0.0..3.0) sin(2 * PI * ph / 0.6) else 0.0
            val tick = engine.process(100_000 + Math.round(t * 1000), frame(leftWristDy = mv), 0.2).tick
            if (tick?.stage == 2) alarm = true
        }
        assertTrue(alarm)
    }

    @Test
    fun two_moves_do_not_alarm_on_default_sensitivity() {
        val engine = RuleV3Engine()
        var alarm = false
        for (i in 0 until 12 * 30) {
            val t = i / 30.0
            val ph = t - 3.0
            val mv = if (ph in 0.0..1.2) sin(2 * PI * ph / 0.6) else 0.0
            if (engine.process(100_000 + Math.round(t * 1000), frame(leftWristDy = mv), 0.2).tick?.stage == 2) alarm = true
        }
        assertTrue(!alarm)
    }

    @Test
    fun part_follows_the_behavior_that_built_up_duration() {
        // 왼팔 퍼덕 → 위험이 될 때와, 멈춘 뒤 지속 시간이 줄어드는 동안 모두 left_arm
        val engine = RuleV3Engine()
        val parts = HashSet<String?>()
        var sawAlarmWithOne = false
        for (i in 0 until 16 * 30) {
            val t = i / 30.0
            val ph = t - 3.0
            val mv = if (ph in 0.0..5.0) sin(2 * PI * ph / 0.5) else 0.0
            val o = engine.process(100_000 + Math.round(t * 1000), frame(leftWristDy = mv), 0.2)
            val tick = o.tick ?: continue
            if (o.ticked && tick.d > 0) parts.add(tick.part)
            if (tick.alert.newEpisode && tick.d >= 1.0) sawAlarmWithOne = true
        }
        assertEquals(setOf<String?>("left_arm"), parts)
        assertTrue(sawAlarmWithOne)
    }

    @Test
    fun server_part_mapping() {
        fun ev(key: String, side: String?, inst: Map<String, Double> = emptyMap()): Evidence {
            fun e(k: String, sd: String?, raw: Double) = BehEvidence(raw, raw, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, sd, k, k.substringBefore("_"), subtype = "")
            return Evidence(0.9, key, mapOf(key to e(key, side, 0.9)), null, inst.mapValues { (k, v) -> e(k, k.substringAfterLast("_"), v) })
        }
        assertEquals("left_arm", serverPart("flap", ev("flap", SIDE_L)))
        assertEquals("right_arm", serverPart("eye_cover", ev("eye_cover", SIDE_R)))
        assertEquals("head", serverPart("head_nod", ev("head_nod", null)))
        assertEquals("body", serverPart("body_rock", ev("body_rock", null)))
        assertEquals("body", serverPart("shrug", ev("shrug", null)))
        assertEquals("body", serverPart("jump", ev("jump", null)))
        // 박수(양손): 손·팔 점수가 높은 쪽 팔
        assertEquals("left_arm", serverPart("clap", ev("clap", null, mapOf("flap_L" to 0.4, "wave_R" to 0.2))))
        assertEquals("right_arm", serverPart("clap", ev("clap", null, mapOf("flap_L" to 0.1, "wave_R" to 0.3))))
    }

    @Test
    fun tapping_the_screen_lowers_hand_scores() {
        val engine = RuleV3Engine()
        val start = 100_000L
        // 4초 동안 퍼덕이고, 마지막 2초는 화면을 계속 누르고 있다
        run(4.0, engine, start) { t -> frame(leftWristDy = sin(2 * PI * t / 0.45)) }
        engine.touchDown(start + 4000)
        val tick = run(2.0, engine, start + 4000) { t -> frame(leftWristDy = sin(2 * PI * (t + 4) / 0.45)) }!!
        val flap = tick.ev.beh.getValue("flap")
        // 마지막 판정(5.5초)까지 1.5초 눌렀으므로 f = 1.5 / 2 = 0.75
        assertEquals(0.75, tick.touch.f, 0.02)
        // 점수 × (1 − 0.8 × f)
        assertEquals(flap.rawScore * (1 - 0.8 * tick.touch.f), flap.score, 1e-9)
        assertTrue(flap.score < flap.rawScore * 0.5)
    }
}
