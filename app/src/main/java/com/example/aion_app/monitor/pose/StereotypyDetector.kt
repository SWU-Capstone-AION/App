package com.example.aion_app.monitor.pose

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * HTML 원본(monitor.html)의 상동행동 판정 + 운영기록 로직을 Kotlin 으로 1:1 이식.
 * 손목 좌표를 슬라이딩 윈도우로 버퍼링하여 진폭/박자/중심편차 3기준으로 "활성"을 판정하고,
 * 전역 윈도우(3s) 내 누적 활성 시간이 임계값(2.9s)을 넘으면 알람.
 * 추가로 추이 타임라인, 손목 트레이스, 포즈 추정, 심박 시뮬레이션을 함께 계산한다.
 *
 * 스레드: MediaPipe 결과 리스너(단일 스레드)에서만 호출된다는 전제로 동기화 없음.
 * State 에 담기는 컬렉션은 복사본이라 UI 스레드에서 안전하게 읽을 수 있다.
 */
class StereotypyDetector {

    data class Arm(
        val intervalOk: Boolean = false,
        val centerDevOk: Boolean = false,
        val amplitudeOk: Boolean = false,
        val active: Boolean = false,
        val interval: Double = 0.0,
        val centerDev: Double = 0.0,
        val amplitude: Double = 0.0,
    )

    data class TimelinePoint(val t: Double, val l: Double, val r: Double, val alarm: Boolean)
    data class WristPoint(val t: Double, val ly: Double?, val ry: Double?)

    data class State(
        val left: Arm,
        val right: Arm,
        val leftDuration: Double,
        val rightDuration: Double,
        val leftAlarm: Boolean,
        val rightAlarm: Boolean,
        val anyAlarm: Boolean,
        val elapsedSec: Double,
        val leftActiveTotal: Double,
        val rightActiveTotal: Double,
        val alarmCount: Int,
        val maxStreak: Double,
        val heartRate: Int,
        val poseText: String,
        val leftWristPx: Pair<Int, Int>?,
        val rightWristPx: Pair<Int, Int>?,
        val timeline: List<TimelinePoint>,
        val wristTrace: List<WristPoint>,
    )

    data class Wrist(val xNorm: Double, val yNorm: Double, val visibility: Double)

    private class Buffer {
        val t = ArrayList<Double>()
        val xPx = ArrayList<Double>()
        val yPx = ArrayList<Double>()
        val xNorm = ArrayList<Double>()
        val yNorm = ArrayList<Double>()
        val vis = ArrayList<Double>()
        val active = ArrayList<Boolean>()
        fun clear() {
            t.clear(); xPx.clear(); yPx.clear(); xNorm.clear(); yNorm.clear(); vis.clear(); active.clear()
        }
    }

    private val left = Buffer()
    private val right = Buffer()

    private var sessionStartMs = 0L
    private var lastFrameT = 0.0
    private var leftActiveTotal = 0.0
    private var rightActiveTotal = 0.0
    private var leftStreak = 0.0
    private var rightStreak = 0.0
    private var maxStreak = 0.0
    private var alarmCount = 0
    private val alarmLatched = booleanArrayOf(false, false) // [left, right]

    private var bioHR = 75.0
    private var lastBioUpdate = 0.0

    private val timeline = ArrayList<TimelinePoint>()
    private val wristTrace = ArrayList<WristPoint>()

    fun reset() {
        left.clear(); right.clear()
        sessionStartMs = 0L
        lastFrameT = 0.0
        leftActiveTotal = 0.0; rightActiveTotal = 0.0
        leftStreak = 0.0; rightStreak = 0.0; maxStreak = 0.0
        alarmCount = 0
        alarmLatched[0] = false; alarmLatched[1] = false
        bioHR = 75.0; lastBioUpdate = 0.0
        timeline.clear(); wristTrace.clear()
    }

    fun update(
        timestampMs: Long,
        leftWrist: Wrist?,
        rightWrist: Wrist?,
        leftShoulder: Wrist?,
        rightShoulder: Wrist?,
    ): State {
        if (sessionStartMs == 0L) sessionStartMs = timestampMs
        val tNow = (timestampMs - sessionStartMs) / 1000.0

        var la = Arm()
        var ra = Arm()
        var leftDur = 0.0
        var rightDur = 0.0

        if (leftWrist != null) {
            pushSample(left, tNow, leftWrist)
            la = analyze(left, tNow)
            left.active[left.active.size - 1] = la.active
            leftDur = durationInWindow(left, tNow)
        }
        if (rightWrist != null) {
            pushSample(right, tNow, rightWrist)
            ra = analyze(right, tNow)
            right.active[right.active.size - 1] = ra.active
            rightDur = durationInWindow(right, tNow)
        }

        val leftAlarm = leftDur > DURATION_THRESHOLD
        val rightAlarm = rightDur > DURATION_THRESHOLD
        val anyAlarm = leftAlarm || rightAlarm

        if (leftAlarm && !alarmLatched[0]) { alarmLatched[0] = true; alarmCount++ }
        else if (!leftAlarm) alarmLatched[0] = false
        if (rightAlarm && !alarmLatched[1]) { alarmLatched[1] = true; alarmCount++ }
        else if (!rightAlarm) alarmLatched[1] = false

        var dt = 0.0
        if (lastFrameT > 0) dt = (tNow - lastFrameT).coerceIn(0.0, 0.25)
        lastFrameT = tNow
        if (la.active) { leftActiveTotal += dt; leftStreak += dt } else leftStreak = 0.0
        if (ra.active) { rightActiveTotal += dt; rightStreak += dt } else rightStreak = 0.0
        maxStreak = maxOf(maxStreak, leftStreak, rightStreak)

        // 심박 시뮬레이션 (1s 주기, 활동 강도 연동)
        if (tNow - lastBioUpdate >= 1.0) {
            lastBioUpdate = tNow
            val intensity =
                (if (la.active) 1.0 else 0.0) + (if (ra.active) 1.0 else 0.0) + (if (anyAlarm) 1.5 else 0.0)
            val hrTarget = 72 + intensity * 11 + (Random.nextDouble() * 4 - 2)
            bioHR += (hrTarget - bioHR) * 0.35
        }

        // 포즈 추정 텍스트
        val poseText = computePose(leftShoulder, rightShoulder, leftWrist, rightWrist)

        // 손목 트레이스 (매 프레임, 최근 WRIST_WIN 초)
        wristTrace.add(WristPoint(tNow, leftWrist?.yNorm, rightWrist?.yNorm))
        while (wristTrace.isNotEmpty() && wristTrace[0].t < tNow - WRIST_WIN) wristTrace.removeAt(0)

        // 추이 타임라인 (0.1s 간격, 최근 TIMELINE_WINDOW_S 초)
        if (timeline.isEmpty() || tNow - timeline.last().t > 0.1) {
            timeline.add(TimelinePoint(tNow, leftDur, rightDur, anyAlarm))
            while (timeline.isNotEmpty() && timeline[0].t < tNow - TIMELINE_WINDOW_S) timeline.removeAt(0)
        }

        return State(
            left = la, right = ra,
            leftDuration = leftDur, rightDuration = rightDur,
            leftAlarm = leftAlarm, rightAlarm = rightAlarm, anyAlarm = anyAlarm,
            elapsedSec = tNow,
            leftActiveTotal = leftActiveTotal, rightActiveTotal = rightActiveTotal,
            alarmCount = alarmCount, maxStreak = maxStreak,
            heartRate = bioHR.roundToInt(),
            poseText = poseText,
            leftWristPx = leftWrist?.let { (it.xNorm * REF_WIDTH).toInt() to (it.yNorm * REF_HEIGHT).toInt() },
            rightWristPx = rightWrist?.let { (it.xNorm * REF_WIDTH).toInt() to (it.yNorm * REF_HEIGHT).toInt() },
            timeline = ArrayList(timeline),
            wristTrace = ArrayList(wristTrace),
        )
    }

    private fun computePose(lsh: Wrist?, rsh: Wrist?, lw: Wrist?, rw: Wrist?): String {
        if (lsh == null || rsh == null) return "대상 미검출"
        if (lw == null && rw == null) return "자세 분석 중…"
        val shY = (lsh.yNorm + rsh.yNorm) / 2
        val lUp = lw?.let { it.yNorm < shY - 0.04 } ?: false
        val rUp = rw?.let { it.yNorm < shY - 0.04 } ?: false
        val arms = when {
            lUp && rUp -> "양팔 위로"
            lUp || rUp -> "한쪽 팔 위로"
            else -> "팔 내림"
        }
        return "앉아있는 상태 · $arms"
    }

    private fun pushSample(buf: Buffer, t: Double, w: Wrist) {
        buf.t.add(t)
        buf.xPx.add(w.xNorm * REF_WIDTH)
        buf.yPx.add(w.yNorm * REF_HEIGHT)
        buf.xNorm.add(w.xNorm)
        buf.yNorm.add(w.yNorm)
        buf.vis.add(w.visibility)
        buf.active.add(false)
        val cutoff = t - GLOBAL_WINDOW_S - 1.0
        while (buf.t.isNotEmpty() && buf.t[0] < cutoff) {
            buf.t.removeAt(0); buf.xPx.removeAt(0); buf.yPx.removeAt(0)
            buf.xNorm.removeAt(0); buf.yNorm.removeAt(0); buf.vis.removeAt(0); buf.active.removeAt(0)
        }
    }

    private fun analyze(buf: Buffer, currentT: Double): Arm {
        if (buf.t.size < 5) return Arm()
        val winStart = currentT - LOCAL_WINDOW_S
        var lo = 0
        for (i in buf.t.indices) {
            if (buf.t[i] >= winStart) { lo = i; break }
        }
        val ts = buf.t.subList(lo, buf.t.size)
        val vis = buf.vis.subList(lo, buf.vis.size)
        if (ts.size < 5) return Arm()

        val visOk = vis.count { it >= VISIBILITY_MIN }.toDouble() / vis.size
        if (visOk < 0.5) return Arm()

        val yPx = buf.yPx.subList(lo, buf.yPx.size)
        val xPx = buf.xPx.subList(lo, buf.xPx.size)
        val yNorm = buf.yNorm.subList(lo, buf.yNorm.size)
        val xNorm = buf.xNorm.subList(lo, buf.xNorm.size)

        val useY = variance(yPx) >= variance(xPx)
        // 노이즈성 가짜 피크 제거를 위한 이동평균 스무딩 (박자 안정화)
        val sig = smooth(if (useY) yPx else xPx, SMOOTH_WIN)
        val sigNorm = smooth(if (useY) yNorm else xNorm, SMOOTH_WIN)

        val amplitude = sig.max() - sig.min()
        val ampOk = amplitude >= AMPLITUDE_MIN

        var avgFps = 30.0
        if (ts.size > 1) avgFps = (ts.size - 1) / (ts[ts.size - 1] - ts[0])
        val minDist = maxOf(2, floor(INTERVAL_MIN * avgFps * 0.5).toInt())
        val peaksUp = findPeaks(sig, minDist)
        val peaksDn = findPeaks(sig.map { -it }, minDist)

        var avgInterval = 0.0
        if (peaksUp.size >= 2) {
            var sum = 0.0
            for (i in 1 until peaksUp.size) sum += ts[peaksUp[i]] - ts[peaksUp[i - 1]]
            avgInterval = sum / (peaksUp.size - 1)
        } else if (peaksDn.size >= 2) {
            var sum = 0.0
            for (i in 1 until peaksDn.size) sum += ts[peaksDn[i]] - ts[peaksDn[i - 1]]
            avgInterval = sum / (peaksDn.size - 1)
        } else {
            val merged = (peaksUp + peaksDn).sorted()
            if (merged.size >= 2) {
                var sum = 0.0
                for (i in 1 until merged.size) sum += ts[merged[i]] - ts[merged[i - 1]]
                avgInterval = (sum / (merged.size - 1)) * 2
            }
        }
        val intervalOk = avgInterval in INTERVAL_MIN..INTERVAL_MAX

        val third = sigNorm.size / 3
        var centerDev = 0.0
        if (third >= 2) {
            val m1 = mean(sigNorm.subList(0, third))
            val m2 = mean(sigNorm.subList(third, 2 * third))
            val m3 = mean(sigNorm.subList(2 * third, sigNorm.size))
            centerDev = sqrt(variance(listOf(m1, m2, m3)))
        }
        // 중심편차를 진폭 대비 상대값으로 판정 — 큰 왕복운동도 '제자리 반복'으로 통과시키되,
        // 한 방향으로 쓸고 가는 드리프트(비율 큼)는 계속 탈락. 작은 움직임은 기존 절대 임계 유지.
        val amplitudeNorm = sigNorm.max() - sigNorm.min()
        val centerDevRatio = if (amplitudeNorm > 1e-4) centerDev / amplitudeNorm else 1.0
        val centerDevOk = centerDev <= CENTER_DEV_MAX || centerDevRatio <= CENTER_DEV_RATIO_MAX

        val active = intervalOk && centerDevOk && ampOk
        return Arm(intervalOk, centerDevOk, ampOk, active, avgInterval, centerDev, amplitude)
    }

    private fun durationInWindow(buf: Buffer, currentT: Double): Double {
        if (buf.t.size < 2) return 0.0
        val winStart = currentT - GLOBAL_WINDOW_S
        var activeTime = 0.0
        for (i in 1 until buf.t.size) {
            val t1 = buf.t[i - 1]
            val t2 = buf.t[i]
            if (t2 < winStart) continue
            val segStart = maxOf(t1, winStart)
            val dt = t2 - segStart
            if (dt <= 0) continue
            if (buf.active[i] && buf.active[i - 1]) activeTime += dt
            else if (buf.active[i] || buf.active[i - 1]) activeTime += dt * 0.5
        }
        return activeTime
    }

    private fun findPeaks(arr: List<Double>, minDistance: Int): List<Int> {
        val peaks = ArrayList<Int>()
        var lastPeak = -minDistance
        for (i in 1 until arr.size - 1) {
            if (arr[i] > arr[i - 1] && arr[i] > arr[i + 1] && (i - lastPeak) >= minDistance) {
                peaks.add(i)
                lastPeak = i
            }
        }
        return peaks
    }

    /** 이동평균 스무딩 (길이 보존). */
    private fun smooth(a: List<Double>, win: Int): List<Double> {
        if (win <= 1 || a.size < win) return a.toList()
        val half = win / 2
        return List(a.size) { i ->
            var sum = 0.0; var n = 0
            for (j in maxOf(0, i - half)..minOf(a.size - 1, i + half)) { sum += a[j]; n++ }
            sum / n
        }
    }

    private fun mean(a: List<Double>): Double = a.sum() / a.size
    private fun variance(a: List<Double>): Double {
        val m = mean(a)
        return a.sumOf { (it - m) * (it - m) } / a.size
    }

    companion object {
        const val INTERVAL_MIN = 0.2
        const val INTERVAL_MAX = 1.2
        const val CENTER_DEV_MAX = 0.05
        const val CENTER_DEV_RATIO_MAX = 0.35  // 진폭 대비 중심편차 상한(큰 왕복운동 허용)
        const val SMOOTH_WIN = 3               // 손목 신호 이동평균 창
        const val AMPLITUDE_MIN = 8.0
        const val LOCAL_WINDOW_S = 0.8
        const val GLOBAL_WINDOW_S = 4.0
        const val DURATION_THRESHOLD = 3.9
        const val VISIBILITY_MIN = 0.3
        const val TIMELINE_WINDOW_S = 30.0
        const val WRIST_WIN = 6.0

        // HTML 원본이 1280x720 비디오 픽셀 기준이므로 동일 스케일로 고정(해상도 무관 일관성)
        const val REF_WIDTH = 1280.0
        const val REF_HEIGHT = 720.0
    }
}
