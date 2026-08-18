package com.example.aion_app.monitor.pose

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 상동행동 판정기. 여러 신체 부위의 좌표를 슬라이딩 윈도우로 버퍼링하여
 * 진폭/박자/중심편차 3기준으로 "활성"을 판정하고, 전역 윈도우(4s) 내 누적 활성 시간이
 * 임계값(3.9s)을 넘으면 알람.
 *
 * 부위(Part): 좌팔·우팔(손목 절대좌표), 머리(코-어깨중심 상대좌표), 몸통(어깨중심-엉덩이중심 상대좌표).
 * 각 부위는 X/Y 중 진동이 큰 축을 자동 선택하므로 상하·좌우 흔들림을 모두 잡는다.
 *
 * 스레드: MediaPipe 결과 리스너(단일 스레드)에서만 호출된다는 전제로 동기화 없음.
 */
class StereotypyDetector {

    enum class Part { LEFT_ARM, RIGHT_ARM, HEAD, BODY }

    data class Arm(
        val intervalOk: Boolean = false,
        val centerDevOk: Boolean = false,
        val amplitudeOk: Boolean = false,
        val active: Boolean = false,
        val interval: Double = 0.0,
        val centerDev: Double = 0.0,
        val amplitude: Double = 0.0,
    )

    data class PartState(val analysis: Arm, val duration: Double, val alarm: Boolean)

    data class TimelinePoint(
        val t: Double,
        val leftArm: Double, val rightArm: Double, val head: Double, val body: Double,
        val alarm: Boolean,
    )
    data class WristPoint(val t: Double, val ly: Double?, val ry: Double?)

    data class State(
        val parts: Map<Part, PartState>,
        val anyAlarm: Boolean,
        val elapsedSec: Double,
        val alarmCount: Int,
        val maxStreak: Double,
        val heartRate: Int,
        val poseText: String,
        val activeTotals: Map<Part, Double>,
        val timeline: List<TimelinePoint>,
        val wristTrace: List<WristPoint>,
    )

    /** 추적 점(정규화 좌표). 손목·코·중심점 등 무엇이든 담는다. */
    data class Wrist(val xNorm: Double, val yNorm: Double, val visibility: Double)

    private class Buffer {
        val t = ArrayList<Double>()
        val xPx = ArrayList<Double>()
        val yPx = ArrayList<Double>()
        val xNorm = ArrayList<Double>()
        val yNorm = ArrayList<Double>()
        val vis = ArrayList<Double>()
        val active = ArrayList<Boolean>()
        var lastX = Double.NaN   // ① 글리치 게이트용 직전 위치
        var lastY = Double.NaN
        fun clear() {
            t.clear(); xPx.clear(); yPx.clear(); xNorm.clear(); yNorm.clear(); vis.clear(); active.clear()
            lastX = Double.NaN; lastY = Double.NaN
        }
    }

    private val buffers = Part.entries.associateWith { Buffer() }
    private val alarmLatched = Part.entries.associateWith { false }.toMutableMap()
    private val activeTotal = Part.entries.associateWith { 0.0 }.toMutableMap()
    private val streak = Part.entries.associateWith { 0.0 }.toMutableMap()

    private var sessionStartMs = 0L
    private var lastFrameT = 0.0
    private var maxStreak = 0.0
    private var alarmCount = 0
    private var bioHR = 75.0
    private var lastBioUpdate = 0.0
    private var shoulderW = 0.0   // ② 신체크기 정규화용 어깨너비(EMA)
    private var refShoulder = 0.0 // 세션 기준 어깨너비(자동 보정)

    private val timeline = ArrayList<TimelinePoint>()
    private val wristTrace = ArrayList<WristPoint>()

    /**
     * 판정 스킵 플래그. 미니게임처럼 "의도된 반복 동작"을 하는 동안 켜 둔다.
     * 잡초 뽑기의 팔 상하 반복은 상동행동 조건(진폭·박자·중심편차)을 그대로 만족하기 때문에
     * 끄지 않으면 게임 중 교사폰으로 알림이 나간다.
     */
    var paused: Boolean = false
        private set

    /**
     * 판정을 멈추거나 재개한다.
     *
     * 들어갈 때·나올 때 모두 버퍼를 비운다. 게임 중의 팔 동작이 슬라이딩 윈도우에 남아 있으면
     * 재개 직후 곧바로 오탐이 뜨기 때문이다. 누적 통계(alarmCount·activeTotal·timeline)는
     * 세션 기록이므로 건드리지 않는다.
     */
    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        buffers.values.forEach { it.clear() }
        Part.entries.forEach { alarmLatched[it] = false; streak[it] = 0.0 }
        lastFrameT = 0.0
    }

    fun reset() {
        buffers.values.forEach { it.clear() }
        Part.entries.forEach { alarmLatched[it] = false; activeTotal[it] = 0.0; streak[it] = 0.0 }
        sessionStartMs = 0L
        lastFrameT = 0.0
        maxStreak = 0.0
        alarmCount = 0
        bioHR = 75.0; lastBioUpdate = 0.0
        shoulderW = 0.0; refShoulder = 0.0
        timeline.clear(); wristTrace.clear()
    }

    private fun mid(a: Wrist?, b: Wrist?): Wrist? =
        if (a != null && b != null)
            Wrist((a.xNorm + b.xNorm) / 2, (a.yNorm + b.yNorm) / 2, minOf(a.visibility, b.visibility))
        else null

    private fun rel(p: Wrist?, origin: Wrist?): Wrist? =
        if (p != null && origin != null)
            Wrist(p.xNorm - origin.xNorm, p.yNorm - origin.yNorm, minOf(p.visibility, origin.visibility))
        else null

    fun update(
        timestampMs: Long,
        nose: Wrist?,
        leftShoulder: Wrist?, rightShoulder: Wrist?,
        leftHip: Wrist?, rightHip: Wrist?,
        leftWrist: Wrist?, rightWrist: Wrist?,
    ): State {
        if (sessionStartMs == 0L) sessionStartMs = timestampMs
        val tNow = (timestampMs - sessionStartMs) / 1000.0

        // 미니게임 중에는 아예 분석하지 않는다. 버퍼에 샘플도 쌓지 않으므로
        // 게임이 끝난 직후 남은 데이터로 알람이 뜨는 일도 없다.
        if (paused) return pausedState(tNow)

        val shoulderMid = mid(leftShoulder, rightShoulder)
        val hipMid = mid(leftHip, rightHip)

        // ② 신체크기 정규화: 어깨너비(EMA) 기준 스케일 → 카메라 거리·사람 크기에 무관
        val rawSW = if (leftShoulder != null && rightShoulder != null) {
            val dxs = leftShoulder.xNorm - rightShoulder.xNorm
            val dys = leftShoulder.yNorm - rightShoulder.yNorm
            sqrt(dxs * dxs + dys * dys)
        } else 0.0
        if (rawSW > 0.02) {
            shoulderW = if (shoulderW <= 0.0) rawSW else shoulderW * 0.9 + rawSW * 0.1
            // 세션 시작 시점(0.5초 후 안정값)의 어깨너비를 기준으로 자동 보정 → 이후 거리 변화 보상
            if (refShoulder <= 0.0 && tNow > 0.5) refShoulder = shoulderW
        }
        val scale = if (shoulderW > 0.02 && refShoulder > 0.0)
            (refShoulder / shoulderW).coerceIn(0.5, 2.0) else 1.0

        // 부위별 추적 점
        // 주의: 전면 카메라 미러링으로 MediaPipe의 좌/우 손목이 뒤집혀 들어오므로 스왑하여
        // 화면의 "좌측 팔"이 아동 본인의 왼팔과 일치하도록 맞춘다.
        val points = mapOf(
            Part.LEFT_ARM to rightWrist,
            Part.RIGHT_ARM to leftWrist,
            Part.HEAD to rel(nose, shoulderMid),          // 코 - 어깨중심 (몸통 움직임 제거)
            Part.BODY to rel(shoulderMid, hipMid),        // 어깨중심 - 엉덩이중심 (서있는 위치 제거)
        )

        var dt = 0.0
        if (lastFrameT > 0) dt = (tNow - lastFrameT).coerceIn(0.0, 0.25)
        lastFrameT = tNow

        val results = HashMap<Part, PartState>()
        var anyAlarm = false

        for (part in Part.entries) {
            val buf = buffers.getValue(part)
            val p = points[part]
            var a = Arm()
            var dur = 0.0
            if (p != null) {
                pushSample(buf, tNow, p, scale)
                val ampMin = when (part) {
                    Part.LEFT_ARM, Part.RIGHT_ARM -> ARM_AMP_MIN
                    Part.HEAD -> HEAD_AMP_MIN
                    Part.BODY -> BODY_AMP_MIN
                }
                a = analyze(buf, tNow, ampMin)
                buf.active[buf.active.size - 1] = a.active
                dur = durationInWindow(buf, tNow)
            } else if (buf.t.isNotEmpty()) {
                dur = durationInWindow(buf, tNow)
            }

            val alarm = dur > DURATION_THRESHOLD
            if (alarm && !alarmLatched.getValue(part)) { alarmLatched[part] = true; alarmCount++ }
            else if (!alarm) alarmLatched[part] = false
            if (alarm) anyAlarm = true

            if (a.active) {
                activeTotal[part] = activeTotal.getValue(part) + dt
                streak[part] = streak.getValue(part) + dt
            } else streak[part] = 0.0

            results[part] = PartState(a, dur, alarm)
        }
        maxStreak = maxOf(maxStreak, streak.values.maxOrNull() ?: 0.0)

        val anyActive = results.values.any { it.analysis.active }

        // 심박 시뮬레이션 (1s 주기)
        if (tNow - lastBioUpdate >= 1.0) {
            lastBioUpdate = tNow
            val activeCount = results.values.count { it.analysis.active }
            val intensity = activeCount.toDouble() + (if (anyAlarm) 1.5 else 0.0)
            val hrTarget = 72 + intensity * 8 + (Random.nextDouble() * 4 - 2)
            bioHR += (hrTarget - bioHR) * 0.35
        }

        val poseText = computePose(leftShoulder, rightShoulder, leftWrist, rightWrist)

        // 손목 트레이스 (운영기록 그래프용)
        wristTrace.add(WristPoint(tNow, leftWrist?.yNorm, rightWrist?.yNorm))
        while (wristTrace.isNotEmpty() && wristTrace[0].t < tNow - WRIST_WIN) wristTrace.removeAt(0)

        // 추이 타임라인
        if (timeline.isEmpty() || tNow - timeline.last().t > 0.1) {
            timeline.add(
                TimelinePoint(
                    tNow,
                    results.getValue(Part.LEFT_ARM).duration,
                    results.getValue(Part.RIGHT_ARM).duration,
                    results.getValue(Part.HEAD).duration,
                    results.getValue(Part.BODY).duration,
                    anyAlarm,
                )
            )
            while (timeline.isNotEmpty() && timeline[0].t < tNow - TIMELINE_WINDOW_S) timeline.removeAt(0)
        }

        return State(
            parts = results,
            anyAlarm = anyAlarm,
            elapsedSec = tNow,
            alarmCount = alarmCount,
            maxStreak = maxStreak,
            heartRate = bioHR.roundToInt(),
            poseText = poseText,
            activeTotals = HashMap(activeTotal),
            timeline = ArrayList(timeline),
            wristTrace = ArrayList(wristTrace),
        )
    }

    /** 판정을 멈춘 동안 돌려주는 상태. 알람은 무조건 꺼져 있다. */
    private fun pausedState(tNow: Double): State = State(
        parts = Part.entries.associateWith { PartState(Arm(), 0.0, false) },
        anyAlarm = false,
        elapsedSec = tNow,
        alarmCount = alarmCount,
        maxStreak = maxStreak,
        heartRate = bioHR.roundToInt(),
        poseText = "미니게임 중 · 판정 일시정지",
        activeTotals = HashMap(activeTotal),
        timeline = ArrayList(timeline),
        wristTrace = ArrayList(wristTrace),
    )

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

    private fun pushSample(buf: Buffer, t: Double, w: Wrist, scale: Double) {
        // ① 글리치 게이트: 직전 위치에서 물리적으로 불가능하게 튀면(랜드마크 오검출)
        //    직전 위치로 고정하여 가짜 진폭 스파이크를 막는다.
        var xn = w.xNorm
        var yn = w.yNorm
        if (!buf.lastX.isNaN()) {
            val dx = xn - buf.lastX; val dy = yn - buf.lastY
            if (dx * dx + dy * dy > MAX_JUMP_NORM * MAX_JUMP_NORM) {
                xn = buf.lastX; yn = buf.lastY
            }
        }
        buf.lastX = xn; buf.lastY = yn

        buf.t.add(t)
        buf.xPx.add(xn * REF_WIDTH * scale)   // ② 신체크기 정규화 적용
        buf.yPx.add(yn * REF_HEIGHT * scale)
        buf.xNorm.add(xn)
        buf.yNorm.add(yn)
        buf.vis.add(w.visibility)
        buf.active.add(false)
        val cutoff = t - GLOBAL_WINDOW_S - 1.0
        while (buf.t.isNotEmpty() && buf.t[0] < cutoff) {
            buf.t.removeAt(0); buf.xPx.removeAt(0); buf.yPx.removeAt(0)
            buf.xNorm.removeAt(0); buf.yNorm.removeAt(0); buf.vis.removeAt(0); buf.active.removeAt(0)
        }
    }

    private fun analyze(buf: Buffer, currentT: Double, ampMin: Double): Arm {
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

        val useY = variance(buf.yPx.subList(lo, buf.yPx.size)) >= variance(buf.xPx.subList(lo, buf.xPx.size))
        // 노이즈성 가짜 피크 제거를 위한 이동평균 스무딩 (박자 안정화)
        val sig = smooth(if (useY) buf.yPx.subList(lo, buf.yPx.size) else buf.xPx.subList(lo, buf.xPx.size), SMOOTH_WIN)
        val sigNorm = smooth(if (useY) buf.yNorm.subList(lo, buf.yNorm.size) else buf.xNorm.subList(lo, buf.xNorm.size), SMOOTH_WIN)

        val amplitude = sig.max() - sig.min()
        val ampOk = amplitude >= ampMin

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
        // 중심편차를 진폭 대비 상대값으로 판정 (큰 왕복운동도 통과, 한 방향 드리프트는 탈락)
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
        const val SMOOTH_WIN = 3               // 신호 이동평균 창
        const val AMPLITUDE_MIN = 8.0     // (미사용, 참고용 기본값)
        // 부위별 진폭 임계값(REF 1280x720 px) — 오탐(중앙값 52) 대비 진짜 움직임 분리
        const val ARM_AMP_MIN = 200.0
        const val HEAD_AMP_MIN = 120.0
        const val BODY_AMP_MIN = 70.0
        // ① 글리치 게이트: 한 프레임에 이 이상(정규화 거리) 튀면 오검출로 보고 무시.
        //   실제 움직임은 프레임당 0.1 미만이라 안전.
        const val MAX_JUMP_NORM = 0.28
        const val LOCAL_WINDOW_S = 0.8
        const val GLOBAL_WINDOW_S = 4.0
        const val DURATION_THRESHOLD = 3.9
        const val VISIBILITY_MIN = 0.3
        const val TIMELINE_WINDOW_S = 30.0
        const val WRIST_WIN = 6.0

        // HTML 원본이 1280x720 비디오 픽셀 기준이므로 동일 스케일로 고정(해상도 무관 일관성)
        const val REF_WIDTH = 1280.0
        const val REF_HEIGHT = 720.0

        val PART_LABEL = mapOf(
            Part.LEFT_ARM to "좌측 팔",
            Part.RIGHT_ARM to "우측 팔",
            Part.HEAD to "머리",
            Part.BODY to "몸통",
        )
    }
}
