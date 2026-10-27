package com.example.aion_app.monitor.rule

import kotlin.math.PI
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
// 규칙모델 v3 — 작은 계산 도구 (HTML 원본과 같은 이름·같은 동작)
// 값이 없는 칸은 NaN 으로 표시한다 (JS 의 Number.NaN 과 같음)
// ============================================================

/** 좌표 한 점. 포즈는 v 에 보임 정도, 손은 z 에 깊이를 담는다 */
data class Pt(val x: Double, val y: Double, val z: Double = 0.0, val v: Double = 1.0)

internal fun mean(a: List<Double>): Double = if (a.isEmpty()) 0.0 else a.sum() / a.size

internal fun variance(a: List<Double>): Double {
    val m = mean(a)
    return a.sumOf { (it - m) * (it - m) } / a.size
}

internal fun median(a: List<Double>): Double {
    if (a.isEmpty()) return 0.0
    val s = a.sorted()
    val n = s.size
    return if (n % 2 == 1) s[(n - 1) / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
}

internal fun percentile(a: List<Double>, q: Double): Double {
    if (a.isEmpty()) return 0.0
    val s = a.sorted()
    return s[floor(q * (s.size - 1)).toInt()]
}

/** 5%~95% 폭 (튀는 값 무시한 진폭) */
internal fun pRange(a: List<Double>): Double {
    if (a.size < 2) return 0.0
    val s = a.sorted()
    return s[ceil(0.95 * (s.size - 1)).toInt()] - s[floor(0.05 * (s.size - 1)).toInt()]
}

internal fun smoothN(a: List<Double>, n: Int): List<Double> {
    if (n < 1) return a.toList()
    return List(a.size) { i ->
        var s = 0.0
        var c = 0
        for (j in max(0, i - n)..min(a.size - 1, i + n)) { s += a[j]; c++ }
        s / c
    }
}

internal fun highpass(a: List<Double>, n: Int): List<Double> {
    if (n < 1) return a.toList()
    val base = smoothN(a, n)
    return List(a.size) { a[it] - base[it] }
}

/** 빈칸(NaN)을 앞뒤 값으로 선형 보간 */
internal fun fillGaps(a: List<Double>): List<Double> {
    val out = a.toMutableList()
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

internal fun softAbove(x: Double, th: Double, s: Double): Double = 1 / (1 + exp(-(x - th) / (s * th)))
internal fun softBelow(x: Double, th: Double, s: Double): Double = 1 / (1 + exp((x - th) / (s * th)))
internal fun sigmoid(z: Double): Double = 1 / (1 + exp(-z))
internal fun logit(p: Double): Double {
    val q = min(1 - 1e-6, max(1e-6, p))
    return ln(q / (1 - q))
}
internal fun clamp(v: Double, a: Double, b: Double): Double = max(a, min(b, v))

internal fun dist(a: Pt, b: Pt): Double = hypot(a.x - b.x, a.y - b.y)
internal fun mid(a: Pt, b: Pt): Pt = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
internal fun avgPts(ps: List<Pt>): Pt = Pt(mean(ps.map { it.x }), mean(ps.map { it.y }))

internal fun signedAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double =
    atan2(ux * vy - uy * vx, ux * vx + uy * vy)

/** 세 점 A-B-C에서 B 마디가 꺾인 정도 (펴져 있으면 0, 굽을수록 커짐, 라디안, 3D) */
internal fun bend(a: Pt, b: Pt, c: Pt): Double {
    val ux = a.x - b.x; val uy = a.y - b.y; val uz = a.z - b.z
    val vx = c.x - b.x; val vy = c.y - b.y; val vz = c.z - b.z
    val nu = sqrt(ux * ux + uy * uy + uz * uz)
    val nv = sqrt(vx * vx + vy * vy + vz * vz)
    if (nu < 1e-9 || nv < 1e-9) return Double.NaN
    val cos = clamp((ux * vx + uy * vy + uz * vz) / (nu * nv), -1.0, 1.0)
    return PI - acos(cos)
}

/** 손가락 굽힘 = 뿌리 마디 + 가운데 마디 꺾임 (엄지는 두 마디) */
internal fun fingerCurl(p: List<Pt>, f: IntArray): Double =
    if (f[0] == 1) bend(p[1], p[2], p[3]) + bend(p[2], p[3], p[4])
    else bend(p[0], p[f[0]], p[f[1]]) + bend(p[f[0]], p[f[1]], p[f[2]])

internal fun corr(a: List<Double>, b: List<Double>): Double {
    val n = min(a.size, b.size)
    if (n < 4) return 0.0
    val ma = mean(a.subList(0, n))
    val mb = mean(b.subList(0, n))
    var sab = 0.0; var saa = 0.0; var sbb = 0.0
    for (i in 0 until n) {
        val x = a[i] - ma; val y = b[i] - mb
        sab += x * y; saa += x * x; sbb += y * y
    }
    return if (saa > 0 && sbb > 0) sab / sqrt(saa * sbb) else 0.0
}

/** 들쭉날쭉한 시각의 값을 hz 간격으로 다시 뽑음 (선형 보간) */
internal fun resampleUniform(ts: List<Double>, vals: List<Double>, hz: Int): List<Double> {
    val out = ArrayList<Double>()
    val dt = 1.0 / hz
    var j = 0
    var t = ts[0]
    val end = ts[ts.size - 1]
    while (t <= end) {
        while (j < ts.size - 2 && ts[j + 1] <= t) j++
        val a = ts[j]; val b = ts[j + 1]
        val u = if (b > a) min(1.0, max(0.0, (t - a) / (b - a))) else 0.0
        out.add(vals[j] + (vals[j + 1] - vals[j]) * u)
        t += dt
    }
    return out
}

/** 직선 추세 제거 */
internal fun detrend(a: List<Double>): List<Double> {
    val n = a.size
    val mx = (n - 1) / 2.0
    val my = mean(a)
    var num = 0.0; var den = 0.0
    for (i in 0 until n) { num += (i - mx) * (a[i] - my); den += (i - mx) * (i - mx) }
    val slope = if (den != 0.0) num / den else 0.0
    return List(n) { a[it] - my - slope * (it - mx) }
}

internal data class Periodicity(val reg: Double, val period: Double)

/** 규칙성: 신호를 한 박자만큼 밀었을 때 원래 신호와 겹치는 정도. 밀 수 있는 최대 길이 = 구간 ÷ 3 */
internal fun periodicity(x: List<Double>, hz: Int, minS: Double, maxS: Double): Periodicity {
    val n = x.size
    var e0 = 0.0
    for (v in x) e0 += v * v
    if (e0 <= 1e-12) return Periodicity(0.0, 0.0)
    val lMin = max(2, floor(minS * 0.8 * hz).toInt())
    val lMax = min(ceil(maxS * 1.2 * hz).toInt(), n / RuleV3.MIN_CYCLES)
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
        if (r[l] - dip < RuleV3.REG_PROMINENCE) continue
        peaks.add(l)
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
