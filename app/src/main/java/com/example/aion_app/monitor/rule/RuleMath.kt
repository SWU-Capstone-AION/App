package com.example.aion_app.monitor.rule

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ============================================================
// 규칙모델 v3 계산 도구 (HTML 대시보드 1024_aion_monitor_v3 의 CORE 와 같은 식)
// 안드로이드 의존이 없어서 JVM 단위 테스트로 그대로 확인할 수 있다.
// ============================================================
internal object RuleMath {

    fun mean(a: List<Double>): Double = if (a.isEmpty()) 0.0 else a.sum() / a.size
    fun mean(a: DoubleArray): Double = if (a.isEmpty()) 0.0 else a.sum() / a.size

    fun variance(a: List<Double>): Double {
        val m = mean(a)
        return a.sumOf { (it - m) * (it - m) } / a.size
    }

    fun median(a: List<Double>): Double {
        if (a.isEmpty()) return 0.0
        val s = a.sorted()
        val n = s.size
        return if (n % 2 == 1) s[(n - 1) / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }

    fun percentile(a: List<Double>, q: Double): Double {
        if (a.isEmpty()) return 0.0
        val s = a.sorted()
        return s[floor(q * (s.size - 1)).toInt()]
    }

    /** 위아래 5%를 뺀 폭 (튀는 값 무시) */
    fun pRange(a: List<Double>): Double {
        if (a.size < 2) return 0.0
        val s = a.sorted()
        return s[ceil(0.95 * (s.size - 1)).toInt()] - s[floor(0.05 * (s.size - 1)).toInt()]
    }
    fun pRange(a: DoubleArray): Double = pRange(a.toList())

    fun smoothN(a: DoubleArray, n: Int): DoubleArray {
        if (n < 1) return a.copyOf()
        return DoubleArray(a.size) { i ->
            var s = 0.0; var c = 0
            for (j in max(0, i - n)..min(a.size - 1, i + n)) { s += a[j]; c++ }
            s / c
        }
    }

    fun highpass(a: DoubleArray, n: Int): DoubleArray {
        if (n < 1) return a.copyOf()
        val base = smoothN(a, n)
        return DoubleArray(a.size) { a[it] - base[it] }
    }

    /** 비어 있는 칸(NaN)을 앞뒤 값으로 직선 보간 */
    fun fillGaps(a: DoubleArray): DoubleArray {
        val out = a.copyOf()
        var last = -1
        for (i in out.indices) {
            if (!out[i].isFinite()) continue
            if (last == -1) for (j in 0 until i) out[j] = out[i]
            else for (j in last + 1 until i) out[j] = out[last] + (out[i] - out[last]) * (j - last) / (i - last)
            last = i
        }
        if (last >= 0) for (j in last + 1 until out.size) out[j] = out[last]
        return out
    }

    fun softAbove(x: Double, th: Double, s: Double) = 1.0 / (1.0 + exp(-(x - th) / (s * th)))
    fun softBelow(x: Double, th: Double, s: Double) = 1.0 / (1.0 + exp((x - th) / (s * th)))
    fun sigmoid(z: Double) = 1.0 / (1.0 + exp(-z))
    fun logit(p: Double): Double {
        val q = min(1 - 1e-6, max(1e-6, p))
        return ln(q / (1 - q))
    }

    /** 시각이 고르지 않은 샘플을 hz 간격으로 다시 찍기 */
    fun resampleUniform(ts: List<Double>, vals: DoubleArray, hz: Double): DoubleArray {
        val dt = 1.0 / hz
        val out = ArrayList<Double>()
        var j = 0
        var k = 0
        while (true) {
            val t = ts[0] + k * dt
            if (t > ts[ts.size - 1] + 1e-9) break
            while (j < ts.size - 2 && ts[j + 1] <= t) j++
            val a = ts[j]; val b = ts[j + 1]
            val u = if (b > a) min(1.0, max(0.0, (t - a) / (b - a))) else 0.0
            out += vals[j] + (vals[j + 1] - vals[j]) * u
            k++
        }
        return out.toDoubleArray()
    }

    fun detrend(a: DoubleArray): DoubleArray {
        val n = a.size
        val mx = (n - 1) / 2.0
        val my = mean(a)
        var num = 0.0; var den = 0.0
        for (i in 0 until n) { num += (i - mx) * (a[i] - my); den += (i - mx) * (i - mx) }
        val slope = if (den != 0.0) num / den else 0.0
        return DoubleArray(n) { a[it] - my - slope * (it - mx) }
    }

    data class Periodicity(val reg: Double, val period: Double)

    /**
     * 규칙성: 신호를 한 박자만큼 밀었을 때 원래 신호와 겹치는 정도(−1~1).
     * 밀 수 있는 최대 길이 = 구간 ÷ 최소 반복 횟수 → 같은 동작이 3번 이상 들어 있어야 봉우리가 생김
     */
    fun periodicity(x: DoubleArray, hz: Double, minS: Double, maxS: Double, minCycles: Int, prominence: Double): Periodicity {
        val n = x.size
        var e0 = 0.0
        for (v in x) e0 += v * v
        if (e0 <= 1e-12) return Periodicity(0.0, 0.0)
        val lMin = max(2, floor(minS * 0.8 * hz).toInt())
        val lMax = min(ceil(maxS * 1.2 * hz).toInt(), n / minCycles)
        if (lMax <= lMin + 1) return Periodicity(0.0, 0.0)
        val r = DoubleArray(lMax + 2)
        for (l in 1..lMax + 1) {
            var sxy = 0.0
            var i = 0
            while (i + l < n) { sxy += x[i] * x[i + l]; i++ }
            r[l] = (sxy / (n - l)) / (e0 / n)
        }
        val peaks = ArrayList<Int>()
        for (l in lMin..lMax) {
            if (!(r[l] >= r[l - 1] && r[l] >= r[l + 1])) continue
            var dip = Double.POSITIVE_INFINITY
            for (q in 1 until l) dip = min(dip, r[q])
            if (r[l] - dip < prominence) continue
            peaks += l
        }
        if (peaks.isEmpty()) return Periodicity(0.0, 0.0)
        val top = peaks.maxOf { r[it] }
        if (!(top > 0.1)) return Periodicity(0.0, 0.0)
        val bestL = peaks.first { r[it] >= 0.7 * top }
        val a = r[bestL - 1]; val b = r[bestL]; val c = r[bestL + 1]
        val d = a - 2 * b + c
        val off = if (d != 0.0) max(-0.5, min(0.5, 0.5 * (a - c) / d)) else 0.0
        return Periodicity(min(1.0, b), (bestL + off) / hz)
    }

    fun corr(a: DoubleArray, b: DoubleArray): Double {
        val n = min(a.size, b.size)
        if (n < 4) return 0.0
        var ma = 0.0; var mb = 0.0
        for (i in 0 until n) { ma += a[i]; mb += b[i] }
        ma /= n; mb /= n
        var sab = 0.0; var saa = 0.0; var sbb = 0.0
        for (i in 0 until n) { val x = a[i] - ma; val y = b[i] - mb; sab += x * y; saa += x * x; sbb += y * y }
        return if (saa > 0 && sbb > 0) sab / sqrt(saa * sbb) else 0.0
    }

    /** 세 점 A-B-C에서 B 마디가 꺾인 정도 (펴져 있으면 0, 굽을수록 커짐, 라디안, 3D) */
    fun bend(a: Px, b: Px, c: Px): Double {
        val ux = a.x - b.x; val uy = a.y - b.y; val uz = a.z - b.z
        val vx = c.x - b.x; val vy = c.y - b.y; val vz = c.z - b.z
        val nu = sqrt(ux * ux + uy * uy + uz * uz)
        val nv = sqrt(vx * vx + vy * vy + vz * vz)
        if (nu < 1e-9 || nv < 1e-9) return Double.NaN
        val cos = ((ux * vx + uy * vy + uz * vz) / (nu * nv)).coerceIn(-1.0, 1.0)
        return PI - acos(cos)
    }

    fun signedAngle(ux: Double, uy: Double, vx: Double, vy: Double) = atan2(ux * vy - uy * vx, ux * vx + uy * vy)

    fun dist(a: Px, b: Px) = hypot(a.x - b.x, a.y - b.y)
    fun mid(a: Px, b: Px) = Px((a.x + b.x) / 2, (a.y + b.y) / 2)
    fun avg(ps: List<Px>) = Px(ps.sumOf { it.x } / ps.size, ps.sumOf { it.y } / ps.size)

    fun absMax(a: Double, b: Double) = max(abs(a), abs(b))
}

/** 화면 px 좌표 한 점 (z 는 x 와 같은 크기 단위, v = 보임 정도) */
data class Px(val x: Double, val y: Double, val z: Double = 0.0, val v: Double = 1.0)
