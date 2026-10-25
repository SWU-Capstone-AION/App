package com.example.aion_app.monitor.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 규칙모델 v3 확인 (안드로이드 기기 없이 실행)
 *   ./gradlew :app:testDebugUnitTest --tests "*RuleModelV3Test*"
 *
 * 가짜 사람(30fps, 1280×720)을 만들어 행동마다 10초씩 움직이고,
 * 마지막 판정에서 그 행동이 가장 높게 나오는지 본다.
 * HTML 대시보드(1024_aion_monitor_v3) 시험과 같은 움직임이다.
 */
class RuleModelV3Test {

    private val tau = 2 * PI
    private val rnd = java.util.Random(7)
    private fun n() = (rnd.nextDouble() - 0.5) * 0.0015

    private class Pose(val lms: List<RuleModelV3.Landmark>, val world: List<RuleModelV3.Landmark>, val hands: List<RuleModelV3.HandInput>)

    private fun frame(t: Double, act: String): Pose {
        val l = Array(33) { RuleModelV3.Landmark(0.5, 0.9, 0.0, 0.2) }
        var bx = 0.5; val by = 0.55; var sw = 0.2
        var nx = bx; var ny = by - 0.17; var roll = 0.0; var shy = 0.0
        if (act == "rock") sw *= 1 + 0.12 * sin(tau * t / 1.2)
        if (act == "sway") bx += 0.03 * sin(tau * t / 1.4)
        if (act == "nod") ny += 0.03 * sin(tau * t / 0.7)
        if (act == "tilt") roll = 0.25 * sin(tau * t / 1.0)
        if (act == "shrug") shy = -0.035 * sin(tau * t / 1.0)
        nx += bx - 0.5
        fun set(i: Int, x: Double, y: Double, v: Double = 0.95) { l[i] = RuleModelV3.Landmark(x + n(), y + n(), 0.0, v) }
        val er = 0.045; val c = cos(roll); val s = sin(roll)
        set(0, nx, ny); set(2, nx + 0.02, ny - 0.02); set(5, nx - 0.02, ny - 0.02); set(3, nx + 0.03, ny - 0.02); set(6, nx - 0.03, ny - 0.02)
        set(1, nx + 0.01, ny - 0.02); set(4, nx - 0.01, ny - 0.02); set(9, nx + 0.01, ny + 0.03); set(10, nx - 0.01, ny + 0.03)
        val ey = if (act == "nod") -0.7 * 0.03 * sin(tau * t / 0.7) else 0.0
        set(7, nx + er * c, ny + er * s + ey); set(8, nx - er * c, ny - er * s + ey)
        set(11, bx + sw / 2, by + shy); set(12, bx - sw / 2, by + shy); set(23, bx + 0.07, by + 0.28, 0.6); set(24, bx - 0.07, by + 0.28, 0.6)
        set(13, bx + sw / 2 + 0.03, by + 0.12); set(14, bx - sw / 2 - 0.03, by + 0.12)
        var lw = doubleArrayOf(bx + sw / 2 + 0.02, by + 0.22); var rw = doubleArrayOf(bx - sw / 2 - 0.02, by + 0.22)
        when (act) {
            "flap" -> { lw[1] += 0.05 * sin(tau * t / 0.4); rw[1] += 0.05 * sin(tau * t / 0.4) }
            "clap" -> { val g = 0.02 + 0.06 * (0.5 + 0.5 * sin(tau * t / 0.5)); lw = doubleArrayOf(bx + g, by + 0.12); rw = doubleArrayOf(bx - g, by + 0.12) }
            "earcover" -> { lw = doubleArrayOf(nx + 0.05, ny); rw = doubleArrayOf(nx - 0.05, ny) }
        }
        set(15, lw[0], lw[1]); set(16, rw[0], rw[1])
        set(17, lw[0] + 0.01, lw[1] + 0.03); set(19, lw[0] - 0.005, lw[1] + 0.035); set(21, lw[0] - 0.015, lw[1] + 0.02)
        set(18, rw[0] - 0.01, rw[1] + 0.03); set(20, rw[0] + 0.005, rw[1] + 0.035); set(22, rw[0] + 0.015, rw[1] + 0.02)
        val world = l.map { RuleModelV3.Landmark(it.x, it.y, 0.0) }.toMutableList()
        if (act == "rock") { val z = -0.06 * sin(tau * t / 1.2); for (i in listOf(11, 12)) world[i] = world[i].copy(z = z) }
        fun hand(w: DoubleArray, left: Boolean, curlAmp: Double, phase: Double): RuleModelV3.HandInput {
            val pts = ArrayList<RuleModelV3.Landmark>()
            pts += RuleModelV3.Landmark(w[0], w[1])
            val sg = if (left) 1.0 else -1.0
            val base = arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(0.012 * sg, -0.01), doubleArrayOf(0.004 * sg, -0.02),
                doubleArrayOf(-0.004 * sg, -0.02), doubleArrayOf(-0.011 * sg, -0.018))
            for (f in 0 until 5) {
                val cu = curlAmp * max(0.0, sin(tau * t / 0.35 + f * phase))
                var x = w[0] + base[f][0]; var y = w[1] + base[f][1]; var ang = -PI / 2
                for (j in 0 until 4) {
                    pts += RuleModelV3.Landmark(x, y, if (j > 0) -0.004 * sin(cu * j) else 0.0)
                    ang += cu * (if (j < 2) 1.0 else 0.5)
                    x += 0.012 * cos(ang) * 0.8; y += 0.012 * sin(ang)
                }
            }
            val w3 = pts.map { RuleModelV3.Landmark(it.x * 1.28, it.y * 0.72, it.z) }
            return RuleModelV3.HandInput(pts, w3, 0.95)
        }
        val fing = if (act == "wiggle") 1.0 to 1.2 else 0.0 to 0.0
        return Pose(l.toList(), world, listOf(hand(lw, true, fing.first, fing.second), hand(rw, false, 0.0, 0.0)))
    }

    /** act 를 10초 하고, 마지막 판정을 돌려준다 */
    private fun run(act: String, touch: Boolean = false, seconds: Int = 10, model: RuleModelV3 = RuleModelV3()): RuleModelV3.Tick {
        TouchTracker.reset()
        var last: RuleModelV3.Tick? = null
        for (i in 0 until seconds * 30) {
            val ms = 1_000_000L + i * 1000L / 30
            if (touch && i % 12 == 0) TouchTracker.onDown(ms)
            if (touch && i % 12 == 3) TouchTracker.onUp(ms)
            val p = frame(i / 30.0, act)
            val r = model.update(ms, p.lms, p.world, p.hands, 1280, 720) { TouchTracker.context(ms) }
            if (r.tick != null) last = r.tick
        }
        return last!!
    }

    @Test fun `가만히 있으면 근거 점수가 낮다`() {
        val t = run("still")
        assertTrue("r=${t.r}", (t.r ?: 0.0) < 0.1)
    }

    @Test fun `팔 퍼덕이기`() = assertTop("flap", "flap")
    @Test fun `몸 앞뒤 흔들기`() = assertTop("rock", "body_rock")
    @Test fun `몸 좌우 흔들기`() = assertTop("sway", "body_sway")
    @Test fun `끄덕이기`() = assertTop("nod", "head_nod")
    @Test fun `갸웃거리기`() = assertTop("tilt", "head_tilt")
    @Test fun `으쓱하기`() = assertTop("shrug", "shrug")
    @Test fun `손가락 꼼지락`() = assertTop("wiggle", "finger")
    @Test fun `박수`() = assertTop("clap", "clap")
    @Test fun `귀 막기`() = assertTop("earcover", "ear_cover")

    @Test fun `화면을 두드리는 중이면 퍼덕이기 점수가 80퍼센트 낮아진다`() {
        val t = run("flap", touch = true)
        val e = t.evidence["flap"]
        assertNotNull(e)
        assertTrue("raw=${e!!.rawScore}", e.rawScore > 0.8)
        assertEquals(1.0, e.functional, 1e-9)
        assertEquals(e.rawScore * 0.2, e.score, 1e-6)
    }

    @Test fun `소프트 보팅 - 4초 이어지면 위험`() {
        val m = RuleModelV3()
        val v = SoftVoting()
        var stage = 0; var fired = false
        TouchTracker.reset()
        for (i in 0 until 12 * 30) {
            val ms = 2_000_000L + i * 1000L / 30
            val p = frame(i / 30.0, "flap")
            val r = m.update(ms, p.lms, p.world, p.hands, 1280, 720)
            r.tick?.let { val f = v.fuse(it, 0.9); stage = f.stage; fired = fired || f.fireAlert }
        }
        assertEquals(2, stage)
        assertTrue(fired)
    }

    private fun assertTop(act: String, key: String) {
        val t = run(act)
        val top = t.evidence.values.sortedByDescending { it.score }.take(3).joinToString { "${it.key}=${"%.2f".format(it.score)}" }
        println("$act → $top")
        assertEquals("$act: $top", key, t.dominant)
        assertTrue("$act: $top", t.r!! >= 0.5)
    }
}
