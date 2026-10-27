package com.example.aion_app.monitor.rule

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ============================================================
// AION 규칙모델 v3 — 계산 엔진
// ============================================================
// 1024_aion_monitor_v3_33pose_hands.html 의 ===CORE-START=== ~ ===CORE-END=== 를 옮겼다.
// 함수 이름과 계산 순서를 원본과 같게 두었으니, 기준값을 바꿀 때는 HTML 과 나란히 보면 된다.
//
// 쓰는 법
//   val engine = RuleV3Engine()
//   매 프레임: val out = engine.process(timestampMs, frameInput, dlScore)
//   화면 터치: engine.touchDown(uptimeMs) / engine.touchUp(uptimeMs)
//
// 스레드: process() 는 카메라 분석 스레드에서 부른다. reset() 은 메인 스레드에서도 불리므로 둘 다 잠근다.
//         터치 기록은 메인 스레드에서 들어오므로 따로 잠근다 (touchLock).
//
// HTML 과 다른 점
//   - 학습모델 실행은 앱의 StereotypyMlDetector 가 맡는다. 여기서는 그 점수(dlScore)만 받는다.
//   - 화면 그리기, CSV 기록은 옮기지 않았다.
// ============================================================

/** 리듬형/자세 유지형 분석 결과 (원본 emptyAnalysis 와 같은 칸) */
class Analysis(
    var valid: Boolean = false,
    var reason: String = "",
    var interval: Double = 0.0,
    var amplitude: Double = 0.0,
    var reg: Double = 0.0,
    var ampNeed: Double = 0.0,
    var axis: String = "—",
    var cycles: Int = 0,
    var subtype: String = "",
    var intOk: Boolean = false,
    var ampOk: Boolean = false,
    var regOk: Boolean = false,
    var gateOk: Boolean = false,
    var sInt: Double = 0.0,
    var sAmp: Double = 0.0,
    var sReg: Double = 0.0,
    var sGate: Double = 0.0,
    var frameScore: Double = 0.0,
    var cdev: Double = 0.0,
    var sCdev: Double = 0.0,
    var cdevOk: Boolean = false,
    var held: Double = 0.0,
)

/** 프레임마다 행동별 순간 점수 */
class InstantResult(val analysis: Analysis?, val instant: Double, val reps: Int)

class RuleEval(val inst: Map<String, InstantResult>, val instant: Double, val dominant: String?)

/** 최근 2초 근거 (행동 하나) */
class BehEvidence(
    var score: Double,
    val rawScore: Double,
    val functional: Double,
    val amp: Double,
    val reg: Double,
    val int: Double,
    val cdev: Double,
    val cover: Double,
    val side: String?,
    val inst: String,
    val key: String,
    var suppressed: String? = null,
    val subtype: String,
)

class TouchContext(val f: Double, val taps: Int, val tapInterval: Double?, val game: Boolean)

class Evidence(
    val r: Double?,
    val dominant: String?,
    val beh: Map<String, BehEvidence>,
    val touch: TouchContext?,
    /** 손(L/R)별 근거 (flap_L, flap_R …). beh 는 행동마다 높은 쪽 손 하나만 남긴다 */
    val inst: Map<String, BehEvidence> = emptyMap(),
)

/** 확률 보정 (설정 파일의 rule_calibration / dl_calibration) */
sealed class Calibration {
    data class Platt(val a: Double, val b: Double) : Calibration()
    data class Temperature(val t: Double) : Calibration()
    data class Isotonic(val x: List<Double>, val y: List<Double>) : Calibration()
    data class Logistic(val bias: Double, val coef: Map<String, Double>) : Calibration()

    val typeName: String
        get() = when (this) {
            is Platt -> "platt"; is Temperature -> "temperature"
            is Isotonic -> "isotonic"; is Logistic -> "logistic"
        }
}

class AlertResult(val fire: Boolean, val newEpisode: Boolean, val recent: Int, val repeated: Boolean)

enum class FuseMode(val label: String) {
    ENSEMBLE("앙상블"), RULE("규칙 단독"), DL("학습 단독"), NONE("판단 불가"),
    /** 한 모델이 확실해서(≥ confident) 그 점수를 그대로 쓴 경우 */
    RULE_CONFIDENT("규칙 확신"), DL_CONFIDENT("학습 확신"),
}

/** 0.5초마다 나오는 판정 */
class TickState(
    val t: Double,
    val ev: Evidence,
    val touch: TouchContext,
    val pRuleRaw: Double?,
    val pRule: Double?,
    val ruleCalibrated: Boolean,
    val pDlRaw: Double?,
    val pDl: Double?,
    /** 앙상블 확률 */
    val p: Double?,
    val mode: FuseMode,
    /** 지속 시간 0~1 (1 = 4초 이어짐) */
    val d: Double,
    /** 0 안정 / 1 주의 / 2 위험 */
    val stage: Int,
    val alert: AlertResult,
    val inEpisode: Boolean,
    /**
     * 서버로 보내는 부위 (left_arm / right_arm / head / body).
     * 지금 1위 행동이 아니라 '지속 시간을 쌓은 행동'의 부위다. 행동이 끝나고 지속 시간이 줄어드는 동안에도
     * 그 부위를 유지한다 (그때의 1위 행동은 점수 0 에 가까운 아무 행동이라 부위가 엉뚱해진다).
     * 지속 시간이 0 이면 null.
     */
    val part: String? = null,
    /** 서버로 보내는 행동 유형 (예: arm_flapping). part 와 같은 행동 기준. 지속 시간이 0 이면 null */
    val behaviorType: String? = null,
) {
    /** 가장 높은 행동 이름 (예: "왼 손·팔 퍼덕이기 (튕기기)") */
    val dominantName: String?
        get() = ev.dominant?.let { behaviorName(it, ev.beh[it]) }
}

class TimelinePoint(val t: Double, val p: Double?, val pRule: Double?, val pDl: Double?, val d: Double, val alarm: Boolean)

/** process() 결과 */
class RuleV3Output(
    val t: Double,
    val frame: Frame?,
    val rule: RuleEval,
    /** 가장 최근 0.5초 판정. 첫 판정 전이면 null */
    val tick: TickState?,
    /** 이번 프레임에 새 판정이 나왔는지 */
    val ticked: Boolean,
    val alertsSent: Int,
    val episodesTotal: Int,
    val timeline: List<TimelinePoint>,
    /** 지금 쓰는 위험 기준 (화면 표시용) */
    val durationS: Double,
    val ensembleW: Double,
    val tau: Double,
    val sensitivity: Sensitivity?,
    val confidentLevel: Double? = null,
)

fun behaviorName(key: String, e: BehEvidence?): String {
    val b = BEH.getValue(key)
    val side = e?.side?.let { SIDE_TXT[it] + " " } ?: ""
    val sub = if (e != null && e.subtype.isNotEmpty()) " (${e.subtype})" else ""
    return side + b.label + sub
}

class RuleV3Engine(
    var ensembleW: Double = DEFAULT_SENSITIVITY.ensembleW,
    var decisionTau: Double = DEFAULT_SENSITIVITY.tau,
    /** 지속 시간이 이만큼 차면 '위험' (초) */
    var durationS: Double = DEFAULT_SENSITIVITY.durationS,
    /** 반복이 끊겼을 때 지속 시간이 줄어드는 속도 */
    var decayRate: Double = DEFAULT_SENSITIVITY.decayRate,
    /** 한 모델이 이 값 이상이면 그 점수를 그대로 쓴다 (null = 안 씀) */
    var confidentLevel: Double? = DEFAULT_SENSITIVITY.confident,
    var ruleCal: Calibration? = null,
    var dlCal: Calibration? = null,
    var touchPenalty: Double = RuleV3.TOUCH_PENALTY,
    /** "immediate" = 위험 진입마다 알림(30초 쿨다운), "count3" = 10분 안 3번째마다 알림 */
    var alertMode: String = "immediate",
) {
    /** 지금 단계. 설정 파일로 값을 따로 바꾸면 null(사용자 지정)로 표시된다 */
    var sensitivity: Sensitivity? = DEFAULT_SENSITIVITY

    /** 민감도 단계를 바꾼다. 쌓인 지속 시간은 새 기준의 비율로 그대로 이어진다 */
    @Synchronized
    fun applySensitivity(s: Sensitivity) {
        val frac = accDur / durationS
        sensitivity = s
        ensembleW = s.ensembleW; decisionTau = s.tau; durationS = s.durationS; decayRate = s.decayRate
        confidentLevel = s.confident
        accDur = frac * durationS
    }

    // ---------- 상태 ----------
    private class InstBuf {
        val t = ArrayList<Double>()
        val sig = ArrayList<DoubleArray>()
        val aux = ArrayList<DoubleArray>()
        val vis = ArrayList<Double>()
        val gate = ArrayList<Double>()
        val score = ArrayList<Double>()
        val sAmp = ArrayList<Double>()
        val sReg = ArrayList<Double>()
        val sInt = ArrayList<Double>()
        val sCdev = ArrayList<Double>()
        val ok = ArrayList<Double>()
        val all: List<MutableList<*>> get() = listOf(t, sig, aux, vis, gate, score, sAmp, sReg, sInt, sCdev, ok)
    }

    /** 시각 t 와 값 v 를 같이 쌓는 기록 (원본의 { t: [], v: [] }) */
    private class Series {
        val t = ArrayList<Double>()
        val v = ArrayList<Double>()
        fun push(time: Double, value: Double) { t.add(time); v.add(value) }
        fun trim(cutoff: Double) = trimFront(cutoff, t, v)
    }

    private class Floor {
        val t = ArrayList<Double>()
        val v = ArrayList<DoubleArray>()
    }

    private class Hold { var held = 0.0; var lastOk = -1e9 }

    private class XY {
        val t = ArrayList<Double>()
        val x = ArrayList<Double>()
        val y = ArrayList<Double>()
        fun trim(cutoff: Double) = trimFront(cutoff, t, x, y)
    }

    private class AlertState {
        var inEpisode = false
        val episodes = ArrayList<Double>()
        var total = 0
        var sent = 0
        var lastSentT = -1e9
        var firedThisEpisode = false
        var belowSince: Double? = null
    }

    private val inst = HashMap<String, InstBuf>()
    private val floor = HashMap<String, Floor>()
    private val streak = HashMap<String, Double>()
    private val hold = HashMap<String, Hold>()
    private val lastA = HashMap<String, Analysis?>()
    private val center = XY()
    private val scale = Series()
    private val fwHist = Series()
    private val ratioFw = Series()
    private val ratioEye = Series()
    private val offs = XY()
    private val swHist = Series()
    private var lastEvalT: Double? = null
    private var lastAnalysisT = -1e9
    private var lastScale: Double? = null
    private var lastScaleT = -1e9
    private var accDur = 0.0
    private var lastTickT: Double? = null
    private var alertSt = AlertState()

    /** 지속 시간을 쌓고 있는 행동의 서버 부위 (지속 시간이 0 이 되면 지움) */
    private var driverPart: String? = null
    private var driverType: String? = null

    private var sessionStartMs = 0L
    private var lastTickAt = -1.0
    private var tickState: TickState? = null
    private val timeline = ArrayList<TimelinePoint>()

    // 터치 기록 (메인 스레드에서 들어옴 → touchLock 으로 잠금). 시각은 uptime 초
    private val touchLock = Any()
    private val touchDowns = ArrayList<Double>()
    private val touchSpans = ArrayList<DoubleArray>()   // [시작, 끝(NaN = 아직 누르는 중)]
    @Volatile private var gameActive = false

    init { resetState() }

    private fun resetState() {
        for (i in INSTANCES) {
            inst[i.id] = InstBuf(); floor[i.id] = Floor(); streak[i.id] = 0.0
            hold[i.id] = Hold(); lastA[i.id] = null
        }
        for (s in listOf(scale, fwHist, ratioFw, ratioEye, swHist)) { s.t.clear(); s.v.clear() }
        for (s in listOf(center, offs)) { s.t.clear(); s.x.clear(); s.y.clear() }
        lastEvalT = null; lastAnalysisT = -1e9; lastScale = null; lastScaleT = -1e9
        accDur = 0.0; lastTickT = null; driverPart = null; driverType = null
        alertSt = AlertState()
        sessionStartMs = 0L; lastTickAt = -1.0; tickState = null
        timeline.clear()
    }

    /** 처음부터 다시 (세션 시작). 메인 스레드에서 불려도 되도록 process 와 같은 잠금을 쓴다 */
    @Synchronized
    fun reset() {
        resetState()
        synchronized(touchLock) { touchDowns.clear(); touchSpans.clear() }
    }

    // ---------- 태블릿 터치 기록 (화면 터치 이벤트를 그대로 넣음) ----------
    fun touchDown(uptimeMs: Long) = synchronized(touchLock) {
        val t = uptimeMs / 1000.0
        touchDowns.add(t); touchSpans.add(doubleArrayOf(t, Double.NaN))
    }

    fun touchUp(uptimeMs: Long) = synchronized(touchLock) {
        touchSpans.firstOrNull { it[1].isNaN() }?.let { it[1] = uptimeMs / 1000.0 }
    }

    /** 손가락이 모두 떨어졌을 때 (취소 포함) */
    fun touchUpAll(uptimeMs: Long) = synchronized(touchLock) {
        touchSpans.filter { it[1].isNaN() }.forEach { it[1] = uptimeMs / 1000.0 }
    }

    /** 반복 동작이 필요한 게임 중 (f = 1) */
    fun setGameActive(on: Boolean) { gameActive = on }

    private fun touchContext(nowS: Double): TouchContext = synchronized(touchLock) {
        val w0 = nowS - RuleV3.WINDOW_S
        while (touchDowns.isNotEmpty() && touchDowns[0] < nowS - 10) touchDowns.removeAt(0)
        while (touchSpans.isNotEmpty() && !touchSpans[0][1].isNaN() &&
            touchSpans[0][1] + RuleV3.TOUCH_TAIL_S < nowS - 10) touchSpans.removeAt(0)
        // 손가락이 닿아 있던 시간 (+ 뗀 뒤 0.4초) — 여러 손가락이 겹쳐도 한 번만 셈
        val segs = touchSpans.map { (a, b) ->
            doubleArrayOf(max(a, w0), min(if (b.isNaN()) nowS else b + RuleV3.TOUCH_TAIL_S, nowS))
        }.filter { it[1] > it[0] }.sortedBy { it[0] }
        var covered = 0.0
        var curA: Double? = null
        var curB = 0.0
        for ((a, b) in segs) {
            if (curA == null) { curA = a; curB = b }
            else if (a <= curB) curB = max(curB, b)
            else { covered += curB - curA; curA = a; curB = b }
        }
        if (curA != null) covered += curB - curA
        val taps = touchDowns.count { it >= w0 }
        var f = clamp(max(covered / RuleV3.WINDOW_S, taps / 2.0), 0.0, 1.0)
        val game = gameActive
        if (game) f = 1.0
        val rec = touchDowns.filter { it >= nowS - 4 }
        val gaps = (1 until rec.size).map { rec[it] - rec[it - 1] }
        TouchContext(f, taps, if (gaps.size >= 2) median(gaps) else null, game)
    }

    // ---------- 특징 계산 (행동별 신호) ----------
    private fun computeFeatures(f: Frame, s: Double, cx: Double, cy: Double, sLong: Double): HashMap<String, Double> {
        val p = f.p
        val nan = Double.NaN
        val feat = HashMap<String, Double>()
        val noseOk = p[Pose.NOSE].v >= RuleV3.VISIBILITY_MIN
        // 얼굴 폭 FW: 두 귀 사이 (없으면 두 눈 바깥쪽 × 기억한 비율)
        var fw = f.earW ?: 0.0
        if (fw == 0.0 && f.eyeW != null && f.eyeW != 0.0 && ratioEye.v.isNotEmpty()) fw = f.eyeW * median(ratioEye.v)
        if (fw == 0.0) fw = 0.55 * s
        val ref = if (f.earOk) mid(p[Pose.LEAR], p[Pose.REAR]) else if (f.eyeOk) mid(p[Pose.LEYE_O], p[Pose.REYE_O]) else null
        val nose = p[Pose.NOSE]
        // 머리
        feat["head_x"] = if (noseOk) (nose.x - cx) / s else nan
        feat["head_y"] = if (noseOk) (nose.y - cy) / s else nan
        feat["head_pitch"] = if (noseOk && ref != null) (nose.y - ref.y) / fw else nan
        feat["head_yaw"] = if (noseOk && ref != null) (nose.x - ref.x) / fw else nan
        feat["head_roll"] = when {
            f.earOk -> atan2(p[Pose.LEAR].y - p[Pose.REAR].y, p[Pose.LEAR].x - p[Pose.REAR].x)
            f.eyeOk -> atan2(p[Pose.LEYE_O].y - p[Pose.REYE_O].y, p[Pose.LEYE_O].x - p[Pose.REYE_O].x)
            else -> nan
        }
        // 몸통
        val fwRef = if (fwHist.v.isNotEmpty()) median(fwHist.v) else null
        feat["trunk_sw"] = if (f.shOk) f.swPx / s - 1 else nan
        feat["trunk_face"] = if (f.earW != null && f.earW != 0.0 && fwRef != null && fwRef != 0.0) f.earW / fwRef - 1 else nan
        // 몸 위치는 10초 동안 안정된 어깨 너비로 나눔 (앞뒤 흔들기로 기준 길이가 출렁여도 좌우·상하 값이 흔들리지 않게)
        feat["trunk_y"] = if (f.shOk) f.cy / sLong else nan
        feat["trunk_x"] = if (f.shOk) f.cx / sLong else nan
        feat["trunk_tilt"] = if (f.shOk) atan2(p[Pose.LS].y - p[Pose.RS].y, p[Pose.LS].x - p[Pose.RS].x) else nan
        val w3 = f.world
        feat["trunk_zlean"] = if (w3 != null) (w3[Pose.LS].z + w3[Pose.RS].z) / 2 - (w3[Pose.LH].z + w3[Pose.RH].z) / 2 else nan
        // 어깨
        val lear = p[Pose.LEAR]; val rear = p[Pose.REAR]
        feat["shrug_mid"] = if (f.shOk && lear.v >= 0.3 && rear.v >= 0.3) (f.cy - (lear.y + rear.y) / 2) / s else nan
        feat["shrug_l"] = if (f.shOk && lear.v >= 0.3) (p[Pose.LS].y - lear.y) / s else nan
        feat["shrug_r"] = if (f.shOk && rear.v >= 0.3) (p[Pose.RS].y - rear.y) / s else nan
        val hipsVis = min(p[Pose.LH].v, p[Pose.RH].v)
        val hipMid = mid(p[Pose.LH], p[Pose.RH])
        feat["torso_len"] = if (f.shOk && hipsVis >= 0.3) (hipMid.y - f.cy) / s else nan
        // 으쓱하기 구분용: 귀와 어깨 각각의 높이 (어깨만 움직여야 으쓱하기)
        feat["ear_y"] = if (lear.v >= 0.3 && rear.v >= 0.3) (lear.y + rear.y) / 2 / sLong else nan
        feat["sh_y"] = if (f.shOk) f.cy / sLong else nan
        feat["hips_vis"] = hipsVis
        // 손
        val eyeMid = mid(p[Pose.LEYE], p[Pose.REYE])
        val chest = if (hipsVis >= 0.3) Pt(cx + 0.35 * (hipMid.x - cx), cy + 0.35 * (hipMid.y - cy)) else Pt(cx, cy + 0.4 * s)
        val eyesHidden = min(p[Pose.LEYE].v, p[Pose.REYE].v) < 0.5
        for (sd in listOf(SIDE_L, SIDE_R)) {
            val hd = f.hands.getValue(sd)
            val sh = if (sd == SIDE_L) p[Pose.LS] else p[Pose.RS]
            val ok = hd.score >= 0.3
            val elOk = hd.elbow.v >= 0.3
            fun k(n: String) = "${n}_$sd"
            feat[k("hand_x")] = if (ok) (hd.palm.x - cx) / s else nan
            feat[k("hand_y")] = if (ok) (hd.palm.y - sh.y) / s else nan
            feat[k("elbow_y")] = if (elOk) (hd.elbow.y - sh.y) / s else nan
            feat[k("fore_x")] = if (ok && elOk) (hd.wrist.x - hd.elbow.x) / s else nan
            feat[k("wflex")] = if (ok && elOk) signedAngle(
                hd.wrist.x - hd.elbow.x, hd.wrist.y - hd.elbow.y,
                hd.mcp9.x - hd.wrist.x, hd.mcp9.y - hd.wrist.y,
            ) else nan
            // 보이는 손바닥 폭 (돌리면 작아짐)
            feat[k("palm_open")] = if (hd.model && hd.pts != null) dist(hd.pts[5], hd.pts[17]) / hd.pw else nan
            for (i in 0 until 5) feat[k("curl$i")] = if (hd.model && hd.curls != null) hd.curls[i] else nan
            feat[k("curlm")] = if (hd.model && hd.curls != null) mean(hd.curls.subList(1, 5)) else nan
            // 손끝-눈
            var bestD = Double.POSITIVE_INFINITY; var bestDx = 0.0; var bestDy = 0.0
            for (tp in hd.tips) for (ey in listOf(p[Pose.LEYE], p[Pose.REYE])) {
                val d = dist(tp, ey)
                if (d < bestD) { bestD = d; bestDx = tp.x - ey.x; bestDy = tp.y - ey.y }
            }
            feat[k("eyetip_x")] = if (ok) bestDx / fw else nan
            feat[k("eyetip_y")] = if (ok) bestDy / fw else nan
            feat[k("eyetip_d")] = if (ok) bestD / fw else nan
            // 몸까지 거리 (머리·귀·가슴 중 가장 가까운 곳)
            feat[k("hit_d")] = if (ok) minOf(
                dist(hd.palm, nose), dist(hd.palm, lear), dist(hd.palm, rear), dist(hd.palm, chest),
            ) / s else nan
            val faceD = dist(hd.palm, eyeMid) / fw
            val eyeD = min(dist(hd.palm, p[Pose.LEYE]), dist(hd.palm, p[Pose.REYE])) / fw
            var earD = Double.POSITIVE_INFINITY
            for (q in listOf(hd.palm) + hd.allTips) earD = minOf(earD, dist(q, lear), dist(q, rear))
            earD /= fw
            val above = hd.palm.y < sh.y
            feat[k("g_flutter")] = if (ok) softBelow(max(faceD, 1e-3), 1.6, RuleV3.SOFT_GATE) * (if (above) 1.0 else 0.0) else nan
            // 눈 가리기와 귀 막기는 손이 눈·귀 중 더 가까운 쪽에만 인정 (둘이 동시에 켜지지 않게)
            val nearEye = min(eyeD, faceD) <= earD
            feat[k("g_eyecover")] = if (ok) max(
                softBelow(max(eyeD, 1e-3), 0.4, 0.2),
                softBelow(max(faceD, 1e-3), 0.4, 0.2) * (if (hd.palmW / fw >= 0.6) 1.0 else 0.0),
            ) * (if (eyesHidden) 1.0 else 0.85) * (if (nearEye) 1.0 else 0.0) else nan
            feat[k("g_earcover")] = if (ok) softBelow(max(earD, 1e-3), 0.4, 0.2) *
                (if (above) 1.0 else 0.0) * (if (nearEye) 0.0 else 1.0) else nan
        }
        val hL = f.hands.getValue(SIDE_L)
        val hR = f.hands.getValue(SIDE_R)
        feat["hands_d"] = if (hL.score >= 0.3 && hR.score >= 0.3) dist(hL.palm, hR.palm) / s else nan
        // 전신 (실험)
        val swRef = if (swHist.v.size >= 10) percentile(swHist.v, 0.9) else s
        feat["whole_y"] = if (f.shLoose) f.cy / sLong else nan
        feat["whole_rot"] = if (f.shLoose) (p[Pose.LS].x - p[Pose.RS].x) / swRef else nan
        feat["_fw"] = fw
        return feat
    }

    private fun visFor(i: Instance, f: Frame, feat: Map<String, Double>): Double {
        val sd = i.side
        return when (i.beh.vis) {
            VisKind.TRUNK -> if (f.shOk) f.shVis else if (f.earW != null && f.earW != 0.0) 0.6 else 0.0
            VisKind.HEAD -> f.p[Pose.NOSE].v
            VisKind.HIPS -> if (f.shOk) feat["hips_vis"] ?: 0.0 else 0.0
            VisKind.HAND -> f.hands.getValue(sd!!).score
            VisKind.HAND_MODEL -> f.hands.getValue(sd!!).let { if (it.model) it.score else 0.0 }
            VisKind.BOTH_HANDS -> min(f.hands.getValue(SIDE_L).score, f.hands.getValue(SIDE_R).score)
            VisKind.LOOSE -> if (f.shLoose) max(f.shVis, 0.5) else 0.0
        }
    }

    // ---------- 프레임을 기록에 넣기 ----------
    private fun ingestFrame(f: Frame, t: Double): Double? {
        val p = f.p
        val nose = p[Pose.NOSE]
        val noseOk = nose.v >= RuleV3.VISIBILITY_MIN
        val earW = f.earW?.takeIf { it != 0.0 }
        val eyeW = f.eyeW?.takeIf { it != 0.0 }
        if (earW != null) { fwHist.push(t, earW); fwHist.trim(t - RuleV3.SCALE_WINDOW_S) }
        if (earW != null && eyeW != null) { ratioEye.push(t, earW / eyeW); ratioEye.trim(t - RuleV3.REF_KEEP_S) }
        if (f.shOk && f.swPx > 1) {
            scale.push(t, f.swPx); scale.trim(t - RuleV3.SCALE_WINDOW_S)
            swHist.push(t, f.swPx); swHist.trim(t - RuleV3.REF_KEEP_S)
            if (earW != null) { ratioFw.push(t, f.swPx / earW); ratioFw.trim(t - RuleV3.REF_KEEP_S) }
            if (noseOk) {
                offs.t.add(t); offs.x.add((f.cx - nose.x) / f.swPx); offs.y.add((f.cy - nose.y) / f.swPx)
                offs.trim(t - RuleV3.REF_KEEP_S)
            }
        }
        var s: Double? = null
        val lastSh = if (scale.t.isNotEmpty()) scale.t.last() else -1e9
        if (scale.v.isNotEmpty() && t - lastSh <= RuleV3.SCALE_HOLD_S) s = median(scale.v)
        else if (earW != null && ratioFw.v.isNotEmpty()) s = earW * median(ratioFw.v)
        if (s != null && s != 0.0) { lastScale = s; lastScaleT = t }
        else if (lastScale != null && t - lastScaleT <= RuleV3.SCALE_CARRY_S) s = lastScale
        if (s == null || s == 0.0) return null

        val cx: Double
        val cy: Double
        if (f.shOk) { cx = f.cx; cy = f.cy }
        else if (noseOk && offs.x.isNotEmpty()) { cx = nose.x + median(offs.x) * s; cy = nose.y + median(offs.y) * s }
        else if (f.shLoose) { cx = f.cx; cy = f.cy }
        else return null

        val sLong = if (swHist.v.size >= 10) median(swHist.v) else s
        val feat = computeFeatures(f, s, cx, cy, sLong)
        f.features = feat
        for (i in INSTANCES) {
            val b = inst.getValue(i.id)
            b.t.add(t)
            b.sig.add(DoubleArray(i.chKeys.size) { feat[i.chKeys[it]] ?: Double.NaN })
            b.aux.add(DoubleArray(i.auxKeys.size) { feat[i.auxKeys[it]] ?: Double.NaN })
            b.vis.add(visFor(i, f, feat))
            b.gate.add(i.gateKey?.let { feat[it] } ?: Double.NaN)
            b.score.add(0.0); b.sAmp.add(0.0); b.sReg.add(0.0); b.sInt.add(0.0); b.sCdev.add(0.0); b.ok.add(0.0)
            trimFront(t - RuleV3.KEEP_S, b.t, *b.all.drop(1).toTypedArray())
        }
        if (f.shOk) {
            center.t.add(t); center.x.add(f.cx / sLong); center.y.add(f.cy / sLong)
            center.trim(t - RuleV3.KEEP_S)
        }
        return s
    }

    // ---------- 리듬형 분석 ----------
    private fun emptyAnalysis(reason: String) = Analysis(reason = reason)

    private fun windowSlice(t: List<Double>, tNow: Double, winS: Double): Int {
        var lo = t.size - 1
        while (lo > 0 && t[lo - 1] >= tNow - winS) lo--
        return lo
    }

    private fun prepChannel(ts: List<Double>, raw: List<Double>, cfg: Behavior): List<Double> {
        val u = resampleUniform(ts, fillGaps(raw), RuleV3.ACF_HZ)
        return smoothN(
            highpass(detrend(u), Math.round(cfg.iMax * RuleV3.ACF_HZ / 2).toInt()),
            floor(cfg.iMin * RuleV3.ACF_HZ * 0.1).toInt(),
        )
    }

    private fun winGateScore(kind: WinGate, i: Instance, b: InstBuf, lo: Int, ts: List<Double>): Double {
        fun col(c: Int) = b.sig.subList(lo, b.sig.size).map { it[c] }
        fun auxCol(c: Int) = b.aux.subList(lo, b.aux.size).map { it[c] }
        when (kind) {
            WinGate.CLAP -> {                    // 손이 맞닿아야 함: 구간 최소 거리 ≤ 0.35 SW
                val d = col(0).filter { it.isFinite() }
                return if (d.isNotEmpty()) softBelow(max(d.min(), 1e-3), 0.35, 0.2) else 0.0
            }
            WinGate.EYE_RUB -> {                 // 손끝이 눈 근처(≤ 0.5 FW)에 70% 이상
                val d = auxCol(0)
                val fin = d.filter { it.isFinite() }
                if (fin.isEmpty()) return 0.0
                // 손가락은 크게 굽혔다 폈다 하지 않아야 함 (굽힘 폭 ≤ 0.35 rad) — 얼굴 앞 팔랑이기와 구분
                val cu = auxCol(1).filter { it.isFinite() }
                val still = if (cu.size > 3) softBelow(max(pRange(cu), 1e-3), 0.35, 0.3) else 1.0
                return softAbove(max(fin.count { it <= 0.5 }.toDouble() / d.size, 1e-3), 0.7, 0.1) * still
            }
            WinGate.HIT -> {                     // 닿음(≤ 0.2 SW) + 빠르게 다가감(≥ 2.0 SW/초)
                val d = col(0)
                if (d.count { it.isFinite() } < 4) return 0.0
                val u = smoothN(resampleUniform(ts, fillGaps(d), RuleV3.ACF_HZ), 1)
                var vmax = 0.0
                for (k in 1 until u.size) vmax = max(vmax, -(u[k] - u[k - 1]) * RuleV3.ACF_HZ)
                return softBelow(max(u.min(), 1e-3), 0.2, 0.25) * softAbove(max(vmax, 1e-3), 2.0, 0.2)
            }
            WinGate.SHRUG -> {                   // 어깨 상하 폭에 비해 귀(머리) 상하 폭이 작아야 함 (끄덕이기·앞뒤 흔들기와 구분)
                fun a(c: Int): Double {
                    val v = auxCol(c)
                    return if (v.count { it.isFinite() } >= 4) pRange(prepChannel(ts, v, i.beh)) else Double.NaN
                }
                val earA = a(0)
                val shA = a(1)
                if (!shA.isFinite() || shA <= 1e-6) return 0.0
                return if (earA.isFinite()) softBelow(max(earA / shA, 1e-3), 0.6, 0.3) else 1.0
            }
            WinGate.NOT_CLAP -> {                // 양손이 맞닿는 박수면 흔들기로 세지 않음
                val d = auxCol(0).filter { it.isFinite() }
                return if (d.isNotEmpty()) 1 - softBelow(max(d.min(), 1e-3), 0.35, 0.2) else 1.0
            }
        }
    }

    private class ChanRes(
        val c: Int, val x: List<Double>, val amplitude: Double, val ampNeed: Double,
        val reg: Double, var period: Double, var sAmp: Double, var sReg: Double, var sInt: Double, var s: Double,
        var extra: Double = 1.0,
    ) {
        fun copy() = ChanRes(c, x, amplitude, ampNeed, reg, period, sAmp, sReg, sInt, s, extra)
    }

    private fun analyzeRhythm(i: Instance, tNow: Double): Analysis {
        val cfg = i.beh
        val b = inst.getValue(i.id)
        if (b.t.size < 8) return emptyAnalysis("수집 중")
        val lo = windowSlice(b.t, tNow, i.localWindowS)
        val ts = b.t.subList(lo, b.t.size).toList()
        val span = ts.last() - ts.first()
        if (ts.size < 8 || span < i.localWindowS * 0.8) return emptyAnalysis("수집 중")
        val vis = b.vis.subList(lo, b.vis.size)
        if (vis.count { it >= cfg.vis.min }.toDouble() / vis.size < cfg.vis.frac) return emptyAnalysis("인식 약함")

        val fl = floor.getValue(i.id)
        val rows = b.sig.subList(lo, b.sig.size)
        val nC = rows[0].size
        val amps = DoubleArray(nC) { Double.NaN }
        val res = ArrayList<ChanRes?>()
        for (c in 0 until nC) {
            val raw = rows.map { it[c] }
            if (raw.count { it.isFinite() }.toDouble() / raw.size < RuleV3.MIN_CHANNEL_VALID) { res.add(null); continue }
            val x = prepChannel(ts, raw, cfg)
            val recentN = min(x.size, max(4, Math.round(cfg.iMax * 1.5 * RuleV3.ACF_HZ).toInt()))
            val amplitude = pRange(x.subList(x.size - recentN, x.size))
            amps[c] = amplitude
            val hist = fl.v.map { it[c] }.filter { it.isFinite() }
            val noise = if (hist.size >= 30) percentile(hist, 0.2) else 0.0
            val ampMin = cfg.ch[c].ampMin
            val ampNeed = min(2 * ampMin, max(ampMin, RuleV3.FLOOR_MULT * noise))
            val (reg, period) = periodicity(x, RuleV3.ACF_HZ, cfg.iMin, cfg.iMax)
            val sAmp = softAbove(amplitude, ampNeed, RuleV3.SOFT_AMP)
            val sReg = softAbove(max(reg, 1e-6), RuleV3.REG_MIN, RuleV3.SOFT_REG)
            val sInt = if (period > 0) softAbove(period, cfg.iMin, RuleV3.SOFT_INT) * softBelow(period, cfg.iMax, RuleV3.SOFT_INT) else 0.0
            res.add(ChanRes(c, x, amplitude, ampNeed, reg, period, sAmp, sReg, sInt, sAmp * sReg * sInt))
        }
        val valid = res.filterNotNull()
        if (valid.isEmpty()) return emptyAnalysis("인식 약함")
        val best: ChanRes
        if (cfg.type == BehaviorType.CIRCLE) {
            // 머리 굴리기: 좌우·상하 둘 다 같은 박자로 반복 + 1/4박자 어긋나게 겹침(원 모양)
            if (valid.size < 2) return emptyAnalysis("인식 약함")
            val a = valid[0]
            val c2 = valid[1]
            val per = if (a.period > 0 && c2.period > 0 &&
                abs(a.period - c2.period) / max(a.period, c2.period) < 0.3) (a.period + c2.period) / 2 else 0.0
            var sCirc = 0.0
            if (per > 0) {
                val q = max(1, Math.round(per / 4 * RuleV3.ACF_HZ).toInt())
                val circ = max(
                    abs(corr(a.x.dropLast(q), c2.x.drop(q))),
                    abs(corr(a.x.drop(q), c2.x.dropLast(q))),
                )
                val zero = abs(corr(a.x, c2.x))
                sCirc = softAbove(max(circ, 1e-3), 0.5, 0.15) * softBelow(max(zero, 1e-3), 0.6, 0.3)
            }
            val pick = if (a.amplitude / a.ampNeed < c2.amplitude / c2.ampNeed) a else c2
            best = pick.copy().apply {
                sAmp = min(a.sAmp, c2.sAmp); sReg = min(a.sReg, c2.sReg); period = per
                sInt = if (per > 0) softAbove(per, cfg.iMin, RuleV3.SOFT_INT) * softBelow(per, cfg.iMax, RuleV3.SOFT_INT) else 0.0
                extra = sCirc
                s = sAmp * sReg * sInt * sCirc
            }
        } else if (cfg.needAll) {
            // 모든 신호가 함께 반복해야 함 (등 젖히기 = 몸이 뒤로 + 고개도 뒤로)
            if (valid.size < cfg.ch.size) return emptyAnalysis("인식 약함")
            best = valid.reduce { a, b2 -> if (b2.s < a.s) b2 else a }
            best.extra = 1.0
        } else {
            var bb: ChanRes? = null
            for (r in valid) {
                if (bb == null || r.s > bb.s || (r.s == bb.s && r.amplitude / r.ampNeed > bb.amplitude / bb.ampNeed)) bb = r
            }
            best = bb!!
            best.extra = 1.0
        }
        if (best.reg < 0.3) {
            fl.t.add(tNow); fl.v.add(amps)
            trimFront(tNow - RuleV3.FLOOR_KEEP_S, fl.t, fl.v)
        }

        // 위치 조건
        var sGate = 1.0
        if (i.gateKey != null) {                       // 프레임 조건: 최근 1초 평균
            val glo = windowSlice(b.t, tNow, 1.0)
            val g = b.gate.subList(glo, b.gate.size).filter { it.isFinite() }
            sGate = if (g.isNotEmpty()) mean(g) else 0.0
        }
        if (cfg.winGate != null) sGate *= winGateScore(cfg.winGate, i, b, lo, ts)
        // 손가락: 같이 움직이면 '튕기기', 따로 움직이면 '꼼지락'
        var subtype = ""
        if (cfg.fingerType) {
            val fx = res.subList(1, min(5, res.size)).filterNotNull().map { it.x }
            val cs = ArrayList<Double>()
            for (a in fx.indices) for (c in a + 1 until fx.size) cs.add(corr(fx[a], fx[c]))
            subtype = if (cs.isNotEmpty()) (if (median(cs) >= 0.6) "튕기기" else "꼼지락") else ""
        }
        return Analysis(
            valid = true, reason = "", interval = best.period, amplitude = best.amplitude, reg = best.reg,
            ampNeed = best.ampNeed,
            axis = cfg.ch[best.c].label + (if (cfg.type == BehaviorType.CIRCLE) " · 원" else ""),
            subtype = subtype,
            cycles = if (best.period > 0) floor(span / best.period).toInt() else 0,
            intOk = best.sInt >= 0.5, ampOk = best.amplitude >= best.ampNeed, regOk = best.reg >= RuleV3.REG_MIN,
            gateOk = sGate * best.extra >= 0.5,
            sInt = best.sInt, sAmp = best.sAmp, sReg = best.sReg, sGate = sGate * best.extra,
        )
    }

    // ---------- 자세 유지형 분석 ----------
    private fun analyzeHold(i: Instance, tNow: Double, dt: Double): Analysis {
        val b = inst.getValue(i.id)
        val last = b.t.size - 1
        val hs = hold.getValue(i.id)
        val g = b.gate[last]
        val v = b.vis[last]
        if (!g.isFinite() || v < i.beh.vis.min) {
            if (tNow - hs.lastOk > RuleV3.HOLD_GAP_S) hs.held = 0.0
            return emptyAnalysis("인식 약함").apply { held = hs.held }
        }
        // 손이 멈춰 있어야 함 (최근 1초 손 위치 폭 ≤ 0.05 SW, 손가락 굽힘 폭 ≤ 0.35 rad) — 팔랑이기·때리기와 구분
        val slo = windowSlice(b.t, tNow, 1.0)
        fun col(c: Int) = b.aux.subList(slo, b.aux.size).map { it[c] }.filter { it.isFinite() }
        val hx = col(0); val hy = col(1); val cu = col(2)
        val posR = max(if (hx.size > 1) pRange(hx) else 0.0, if (hy.size > 1) pRange(hy) else 0.0)
        val sStill = softBelow(max(posR, 1e-3), 0.05, 0.3) *
            (if (cu.size > 3) softBelow(max(pRange(cu), 1e-3), 0.35, 0.3) else 1.0)
        val gEff = g * sStill
        if (gEff >= 0.5) { hs.held += dt; hs.lastOk = tNow }
        else if (tNow - hs.lastOk > RuleV3.HOLD_GAP_S) hs.held = 0.0
        val sHold = softAbove(max(hs.held, 1e-3), RuleV3.HOLD_S, RuleV3.SOFT_GATE)
        return Analysis(
            valid = true, reason = "", axis = "유지 %.1fs".format(hs.held), held = hs.held,
            sAmp = sHold, sReg = sHold, sInt = sHold, sGate = gEff, gateOk = gEff >= 0.5,
            ampOk = hs.held >= RuleV3.HOLD_S, regOk = true, intOk = true,
        )
    }

    // ---------- 중심편차 ----------
    private fun computeCenterDev(tNow: Double, winS: Double): Double {
        var lo = center.t.size
        while (lo > 0 && center.t[lo - 1] >= tNow - winS) lo--
        val xs = center.x.subList(lo, center.x.size)
        val ys = center.y.subList(lo, center.y.size)
        val third = xs.size / 3
        if (third < 3) return 0.0
        val mx = listOf(mean(xs.subList(0, third)), mean(xs.subList(third, 2 * third)), mean(xs.subList(2 * third, xs.size)))
        val my = listOf(mean(ys.subList(0, third)), mean(ys.subList(third, 2 * third)), mean(ys.subList(2 * third, ys.size)))
        return sqrt(variance(mx) + variance(my))
    }

    // ---------- 규칙모델: 프레임마다 행동별 순간 점수 ----------
    private fun evaluateRule(tNow: Double, analyzed: Boolean): RuleEval {
        val prev = lastEvalT
        val dt = if (prev == null) 0.0 else max(0.0, min(RuleV3.DT_CAP_S, tNow - prev))
        lastEvalT = tNow
        val due = tNow - lastAnalysisT >= 1.0 / RuleV3.ANALYSIS_HZ
        if (analyzed && due) lastAnalysisT = tNow
        val out = LinkedHashMap<String, InstantResult>()
        var instant = 0.0
        var dominant: String? = null
        for (i in INSTANCES) {
            val b = inst.getValue(i.id)
            var a = lastA[i.id]
            var fs = 0.0
            if (analyzed && b.t.isNotEmpty()) {
                a = if (i.beh.type == BehaviorType.HOLD) analyzeHold(i, tNow, dt)
                else if (due || a == null) analyzeRhythm(i, tNow) else a
                if (due || i.beh.type == BehaviorType.HOLD || a.cdev == 0.0) {
                    a.cdev = computeCenterDev(tNow, i.cdevWindowS)
                    a.sCdev = softBelow(max(a.cdev, 1e-6), RuleV3.CENTER_DEV_MAX, RuleV3.SOFT_CDEV)
                    a.cdevOk = a.cdev <= RuleV3.CENTER_DEV_MAX
                }
                if (a.valid) fs = if (i.beh.type == BehaviorType.HOLD) a.sAmp * a.sCdev
                else a.sAmp * a.sInt * a.sReg * a.sGate * a.sCdev
                if (!fs.isFinite()) fs = 0.0
                a.frameScore = fs
                lastA[i.id] = a
                val last = b.t.size - 1
                b.score[last] = fs; b.ok[last] = if (a.valid) 1.0 else 0.0
                b.sAmp[last] = if (a.valid) a.sAmp else 0.0; b.sReg[last] = if (a.valid) a.sReg else 0.0
                b.sInt[last] = if (a.valid) a.sInt else 0.0; b.sCdev[last] = if (a.valid) a.sCdev else 0.0
            } else a = null
            streak[i.id] = if (fs >= 0.5) streak.getValue(i.id) + dt else 0.0
            val st = streak.getValue(i.id)
            val reps = if (a != null && a.valid && a.interval > 0 && st > 0) floor(st / a.interval).toInt() + RuleV3.MIN_CYCLES else 0
            out[i.id] = InstantResult(a, fs, reps)
            if (fs > instant) { instant = fs; dominant = i.id }
        }
        return RuleEval(out, instant, dominant)
    }

    // ---------- 규칙모델: 최근 2초 근거 (+ 터치 중 점수 낮추기) ----------
    private fun windowEvidence(tNow: Double, touch: TouchContext?): Evidence {
        val ei = LinkedHashMap<String, BehEvidence>()
        for (i in INSTANCES) {
            val b = inst.getValue(i.id)
            var lo = b.t.size
            while (lo > 0 && b.t[lo - 1] >= tNow - RuleV3.WINDOW_S) lo--
            val n = b.t.size - lo
            if (n < 8 || b.t.last() - b.t[lo] < 0.7 * RuleV3.WINDOW_S) continue
            val cover = mean(b.ok.subList(lo, b.ok.size))
            if (cover < RuleV3.PART_COVER_MIN) continue
            fun m(arr: List<Double>) = mean(arr.subList(lo, arr.size))
            val raw = m(b.score)
            var fInst = 0.0
            if (i.beh.functional && touch != null) {
                fInst = touch.f
                val a = lastA[i.id]
                if (touch.tapInterval != null && a != null && a.valid && a.interval > 0 &&
                    abs(a.interval - touch.tapInterval) / touch.tapInterval <= RuleV3.TAP_MATCH_TOL) fInst = 1.0
            }
            val score = raw * (1 - touchPenalty * fInst)
            ei[i.id] = BehEvidence(
                score = score, rawScore = raw, functional = fInst,
                amp = m(b.sAmp), reg = m(b.sReg), int = m(b.sInt), cdev = m(b.sCdev), cover = cover,
                side = i.side, inst = i.id, key = i.beh.key, subtype = lastA[i.id]?.subtype ?: "",
            )
        }
        for ((spec, gens) in SPECIFIC_OVER) {
            for (e in ei.values) {
                if (e.key != spec || e.score < 0.5) continue
                for (g in gens) for (sd in (e.side?.let { listOf(it) } ?: listOf(SIDE_L, SIDE_R))) {
                    val t = ei["${g}_$sd"]
                    if (t != null && t.suppressed == null) { t.score *= RuleV3.SUPPRESS; t.suppressed = spec }
                }
            }
        }
        val beh = LinkedHashMap<String, BehEvidence>()
        for (e in ei.values) if (beh[e.key] == null || e.score > beh.getValue(e.key).score) beh[e.key] = e
        var r: Double? = null
        var dominant: String? = null
        for ((k, e) in beh) if (r == null || e.score > r) { r = e.score; dominant = k }
        return Evidence(r, dominant, beh, touch, ei)
    }

    // ---------- 확률 보정 ----------
    private fun applyCalibration(cal: Calibration?, x: Double?, feats: Map<String, Double>?): Double? {
        if (cal == null || x == null) return x
        return when (cal) {
            is Calibration.Platt -> sigmoid(cal.a * logit(x) + cal.b)
            is Calibration.Temperature -> sigmoid(logit(x) / (if (cal.t != 0.0) cal.t else 1.0))
            is Calibration.Isotonic -> {
                val xs = cal.x; val ys = cal.y
                if (x <= xs[0]) return ys[0]
                for (k in 1 until xs.size) {
                    if (x <= xs[k]) {
                        val span = (xs[k] - xs[k - 1]).takeIf { it != 0.0 } ?: 1.0
                        val u = (x - xs[k - 1]) / span
                        return ys[k - 1] + (ys[k] - ys[k - 1]) * u
                    }
                }
                ys.last()
            }
            is Calibration.Logistic -> {
                var z = cal.bias
                for ((key, coef) in cal.coef) z += coef * (feats?.get(key)?.takeIf { it.isFinite() } ?: 0.0)
                sigmoid(z)
            }
        }
    }

    /** 보정 특징: r, amp, reg, int, cdev (logit), cover, touch, 그리고 행동 묶음 표시 g_<묶음> */
    private fun ruleFeatures(ev: Evidence): Map<String, Double>? {
        val r = ev.r ?: return null
        val d = ev.beh.getValue(ev.dominant!!)
        val feats = hashMapOf(
            "r" to logit(r), "amp" to logit(d.amp), "reg" to logit(d.reg), "int" to logit(d.int),
            "cdev" to logit(d.cdev), "cover" to d.cover, "touch" to (ev.touch?.f ?: 0.0),
        )
        for (g in GROUPS) feats["g_$g"] = if (BEH.getValue(ev.dominant).group == g) 1.0 else 0.0
        return feats
    }

    // ---------- 소프트 보팅 + 지속 시간 ----------
    private class Fused(val p: Double?, val mode: FuseMode, val d: Double, val stage: Int)

    private fun fuseTick(tNow: Double, pRule: Double?, pDl: Double?, w: Double, tau: Double): Fused {
        val p: Double?
        val mode: FuseMode
        var pp: Double?
        var mm: FuseMode
        if (pRule != null && pDl != null) { pp = w * pDl + (1 - w) * pRule; mm = FuseMode.ENSEMBLE }
        else if (pDl != null) { pp = pDl; mm = FuseMode.DL }
        else if (pRule != null) { pp = pRule; mm = FuseMode.RULE }
        else { pp = null; mm = FuseMode.NONE }
        // 한 모델이 아주 확실하면 그 점수를 쓴다 (다른 모델이 낮아서 평균이 τ 아래로 깎이는 것을 막음)
        val conf = confidentLevel
        if (conf != null && pp != null && mm == FuseMode.ENSEMBLE) {
            if (pRule != null && pRule >= conf && pRule > pp) { pp = pRule; mm = FuseMode.RULE_CONFIDENT }
            if (pDl != null && pDl >= conf && pDl > pp) { pp = pDl; mm = FuseMode.DL_CONFIDENT }
        }
        p = pp; mode = mm
        var dt = 0.0
        val lt = lastTickT
        if (lt == null || tNow > lt) {
            dt = if (lt == null) 0.0 else min(RuleV3.STRIDE_S * 3, tNow - lt)
            lastTickT = tNow
        }
        val x = if (p == null) 0.0 else sigmoid((p - tau) / RuleV3.DUR_SOFTNESS)
        accDur = clamp(accDur + dt * (x - (1 - x) * decayRate), 0.0, durationS)
        val d = accDur / durationS
        val stage = if (d >= RuleV3.ALERT_ENTER_LEVEL) 2 else if ((p != null && p >= tau) || d >= 0.5) 1 else 0
        return Fused(p, mode, d, stage)
    }

    // ---------- 알림 ----------
    private fun updateAlert(d: Double, tNow: Double): AlertResult {
        val a = alertSt
        var newEpisode = false
        var fire = false
        if (!a.inEpisode && d >= RuleV3.ALERT_ENTER_LEVEL) {
            a.inEpisode = true; a.firedThisEpisode = false; newEpisode = true
            a.episodes.add(tNow); a.total++
        } else if (a.inEpisode) {
            if (d < RuleV3.ALERT_EXIT_LEVEL) {
                val since = a.belowSince ?: tNow.also { a.belowSince = it }
                if (tNow - since >= RuleV3.ALERT_MERGE_GAP_S) a.inEpisode = false
            } else a.belowSince = null
        }
        if (!a.inEpisode) a.belowSince = null
        while (a.episodes.isNotEmpty() && a.episodes[0] < tNow - RuleV3.ALERT_COUNT_WINDOW_S) a.episodes.removeAt(0)
        val recent = a.episodes.size
        if (newEpisode) {
            fire = if (alertMode == "count3") recent >= RuleV3.ALERT_COUNT_NEED && recent % RuleV3.ALERT_COUNT_NEED == 0
            else tNow - a.lastSentT >= RuleV3.ALERT_COOLDOWN_S
        }
        if (fire) { a.sent++; a.lastSentT = tNow; a.firedThisEpisode = true }
        return AlertResult(fire, newEpisode, recent, recent >= RuleV3.ALERT_COUNT_NEED)
    }

    // ---------- 판정 (0.5초마다) ----------
    private fun onTick(tNow: Double, nowUptimeS: Double, pDlRaw: Double?): TickState {
        val touch = touchContext(nowUptimeS)
        val ev = windowEvidence(tNow, touch)
        val feats = ruleFeatures(ev)
        val pRule = if (ev.r == null) null else applyCalibration(ruleCal, ev.r, feats)
        val pDl = if (pDlRaw == null) null else applyCalibration(dlCal, pDlRaw, mapOf("p" to logit(pDlRaw)))
        val prevD = accDur / durationS
        val f = fuseTick(tNow, pRule, pDl, ensembleW, decisionTau)
        val al = updateAlert(f.d, tNow)
        // 지속 시간이 쌓이는 동안의 1위 행동을 기억한다 (규칙모델 근거가 있을 때만)
        val dom = ev.dominant
        val trusted = dom != null && (ev.r ?: 0.0) >= PART_MIN_SCORE
        if (f.d <= 0.0) { driverPart = null; driverType = null }
        else if (trusted && (f.d > prevD || driverPart == null)) {
            driverPart = serverPart(dom!!, ev)
            driverType = BEH[dom]?.typeCode
        }
        val ts = TickState(
            t = tNow, ev = ev, touch = touch, pRuleRaw = ev.r, pRule = pRule, ruleCalibrated = ruleCal != null,
            pDlRaw = pDlRaw, pDl = pDl, p = f.p, mode = f.mode, d = f.d, stage = f.stage,
            alert = al, inEpisode = alertSt.inEpisode, part = if (f.d > 0.0) driverPart else null,
            behaviorType = if (f.d > 0.0) driverType else null,
        )
        timeline.add(TimelinePoint(tNow, f.p, pRule, pDl, f.d, alertSt.inEpisode))
        while (timeline.isNotEmpty() && timeline[0].t < tNow - RuleV3.TIMELINE_WINDOW_S) timeline.removeAt(0)
        return ts
    }

    /**
     * 카메라 프레임 1장을 넣는다.
     * @param timestampMs SystemClock.uptimeMillis() 기준 시각 (터치 기록과 같은 시계)
     * @param input 사람이 안 잡혔으면 null
     * @param dlScore 학습모델의 최근 점수 0~1 (없거나 오래됐으면 null)
     */
    @Synchronized
    fun process(timestampMs: Long, input: FrameInput?, dlScore: Double?): RuleV3Output {
        if (sessionStartMs == 0L) sessionStartMs = timestampMs
        val tNow = (timestampMs - sessionStartMs) / 1000.0

        var frame: Frame? = null
        var s: Double? = null
        if (input != null && input.pose.size >= 33) {
            frame = extractFrame(input)
            s = ingestFrame(frame, tNow)
        }
        val rule = evaluateRule(tNow, s != null)
        var ticked = false
        if (lastTickAt < 0 || tNow - lastTickAt >= RuleV3.STRIDE_S) {
            lastTickAt = tNow
            tickState = onTick(tNow, timestampMs / 1000.0, dlScore)
            ticked = true
        }
        return RuleV3Output(
            t = tNow, frame = frame, rule = rule, tick = tickState, ticked = ticked,
            alertsSent = alertSt.sent, episodesTotal = alertSt.total, timeline = ArrayList(timeline),
            durationS = durationS, ensembleW = ensembleW, tau = decisionTau, sensitivity = sensitivity,
            confidentLevel = confidentLevel,
        )
    }
}

/** 서버 부위를 정할 때 규칙모델 행동 점수가 이 이상이어야 그 행동을 믿는다 */
private const val PART_MIN_SCORE = 0.1

/**
 * 행동 → 서버로 보내는 부위 (Behavior.bodyPart).
 *  팔·손 행동은 왼/오른손을 따로 보므로 그 쪽을 붙인다: left_arm / right_arm / left_hand / right_hand
 *  (L = 아동 본인의 왼쪽. 영상을 반전하지 않으므로)
 *  양손(박수) → both_hands,  머리 → head,  어깨 → shoulder,  몸통·점프·회전 → body
 */
fun serverPart(key: String, ev: Evidence): String {
    val bp = BEH[key]?.bodyPart ?: BodyPart.BODY
    if (bp == BodyPart.ARM || bp == BodyPart.HAND) {
        val side = ev.beh[key]?.side ?: run {
            // 손을 따로 보지 않는 경우(없음)에 대비: 팔·손 점수가 높은 쪽
            fun sc(sd: String) = listOf("flap", "wave", "finger").maxOf { ev.inst["${it}_$sd"]?.rawScore ?: 0.0 }
            if (sc(SIDE_L) > sc(SIDE_R)) SIDE_L else SIDE_R
        }
        return (if (side == SIDE_L) "left_" else "right_") + bp.code
    }
    return bp.code
}

/** 서버 부위 코드 → 화면 이름 (예: left_hand → 왼손) */
fun partLabel(code: String?): String? {
    if (code == null) return null
    val side = when { code.startsWith("left_") -> "왼"; code.startsWith("right_") -> "오른"; else -> "" }
    val base = code.removePrefix("left_").removePrefix("right_")
    val bp = BodyPart.entries.firstOrNull { it.code == base } ?: return code
    return side + bp.label
}

/** 시각 목록 t 가 cutoff 보다 앞인 칸을 같이 들고 있는 모든 목록에서 지운다 */
private fun trimFront(cutoff: Double, t: MutableList<Double>, vararg others: MutableList<*>) {
    var n = 0
    while (n < t.size && t[n] < cutoff) n++
    if (n == 0) return
    t.subList(0, n).clear()
    for (o in others) o.subList(0, n).clear()
}
