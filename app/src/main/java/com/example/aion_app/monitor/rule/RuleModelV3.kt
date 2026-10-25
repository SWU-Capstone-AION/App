package com.example.aion_app.monitor.rule

import com.example.aion_app.monitor.rule.RuleConfig as C
import com.example.aion_app.monitor.rule.RuleMath.absMax
import com.example.aion_app.monitor.rule.RuleMath.avg
import com.example.aion_app.monitor.rule.RuleMath.bend
import com.example.aion_app.monitor.rule.RuleMath.corr
import com.example.aion_app.monitor.rule.RuleMath.detrend
import com.example.aion_app.monitor.rule.RuleMath.dist
import com.example.aion_app.monitor.rule.RuleMath.fillGaps
import com.example.aion_app.monitor.rule.RuleMath.highpass
import com.example.aion_app.monitor.rule.RuleMath.logit
import com.example.aion_app.monitor.rule.RuleMath.mean
import com.example.aion_app.monitor.rule.RuleMath.median
import com.example.aion_app.monitor.rule.RuleMath.mid
import com.example.aion_app.monitor.rule.RuleMath.pRange
import com.example.aion_app.monitor.rule.RuleMath.percentile
import com.example.aion_app.monitor.rule.RuleMath.periodicity
import com.example.aion_app.monitor.rule.RuleMath.resampleUniform
import com.example.aion_app.monitor.rule.RuleMath.signedAngle
import com.example.aion_app.monitor.rule.RuleMath.smoothN
import com.example.aion_app.monitor.rule.RuleMath.softAbove
import com.example.aion_app.monitor.rule.RuleMath.softBelow
import com.example.aion_app.monitor.rule.RuleMath.variance
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ============================================================
// 상동행동 규칙모델 v3
// ============================================================
// 입력: MediaPipe 포즈 33개 관절(정규화 x, y, 보임 정도 + 3D) + 손 21개 관절 × 양손
// 출력: 0.5초마다 "최근 2초가 상동행동일 근거 점수 r"과 행동별 점수
//
//   리듬형      : 같은 동작이 일정한 박자로 3번 이상 → 진폭 × 박자 × 규칙성 × 중심편차 (× 위치 조건)
//   자세 유지형 : 눈 가리기·귀 막기 → 위치 조건 + 손이 멈춘 채 유지 시간 × 중심편차
//   태블릿 터치 : 퍼덕이기·흔들기·손가락 행동 점수 × (1 − 0.8 × f)
//
// 4초 지속 판정과 학습모델과의 결합은 SoftVoting 이 한다 (규칙모델 밖).
// 이 파일은 HTML 대시보드 1024_aion_monitor_v3_33pose_hands.html 의 CORE 와 같은 식이다.
//
// 스레드: MediaPipe 결과 스레드 하나에서만 부른다는 전제 (동기화 없음)
// ============================================================
class RuleModelV3 {

    /** MediaPipe 관절 1개 (정규화 좌표 0~1, 손은 z 도 정규화 값 그대로) */
    data class Landmark(val x: Double, val y: Double, val z: Double = 0.0, val visibility: Double = 1.0)

    /** 손 1개: 21개 관절 + 3D(미터) + 신뢰도 */
    data class HandInput(val landmarks: List<Landmark>, val world: List<Landmark>?, val score: Double)

    /** 터치 정도 (TouchTracker 가 만든다) */
    data class TouchContext(val f: Double, val taps: Int, val tapIntervalS: Double?, val game: Boolean) {
        companion object { val NONE = TouchContext(0.0, 0, null, false) }
    }

    /** 행동 하나의 최근 2초 근거 */
    data class BehaviorEvidence(
        val key: String,
        val side: Char?,
        /** 터치·우선 규칙까지 반영한 점수 0~1 */
        val score: Double,
        /** 터치 반영 전 점수 */
        val rawScore: Double,
        /** 터치 정도 (0 = 영향 없음) */
        val functional: Double,
        val amp: Double, val reg: Double, val int: Double, val cdev: Double, val cover: Double,
        /** 손가락 행동: 튕기기 / 꼼지락 */
        val subtype: String,
        /** 더 구체적인 행동 때문에 낮아졌으면 그 행동 key */
        val suppressedBy: String?,
    ) {
        val label: String get() = BEHAVIOR_BY_KEY.getValue(key).label
    }

    /** 0.5초마다 나오는 판정 */
    data class Tick(
        val tSec: Double,
        /** 근거 점수 r (가장 높은 행동의 점수). 판단할 수 없으면 null */
        val r: Double?,
        val dominant: String?,
        val evidence: Map<String, BehaviorEvidence>,
        val touch: TouchContext,
    ) {
        val dominantEvidence: BehaviorEvidence? get() = dominant?.let { evidence[it] }
    }

    /** 리듬형·자세 유지형 분석 한 번의 결과 */
    class Analysis(
        val valid: Boolean,
        val reason: String,
        val interval: Double = 0.0,
        val amplitude: Double = 0.0,
        val reg: Double = 0.0,
        val ampNeed: Double = 0.0,
        val axis: String = "—",
        val cycles: Int = 0,
        val subtype: String = "",
        val intOk: Boolean = false,
        val ampOk: Boolean = false,
        val regOk: Boolean = false,
        val gateOk: Boolean = false,
        val sInt: Double = 0.0,
        val sAmp: Double = 0.0,
        val sReg: Double = 0.0,
        val sGate: Double = 0.0,
        val held: Double = 0.0,
    ) {
        var frameScore = 0.0
        var cdev = 0.0
        var sCdev = 0.0
        var cdevOk = false
    }

    /** 프레임 하나 처리 결과 */
    data class FrameResult(
        /** 이번 프레임을 분석에 썼는지 (사람이 안 보이면 false) */
        val analyzed: Boolean,
        /** 행동 단위(좌우 포함) 순간 점수 */
        val instant: Map<String, Double>,
        /** 반복 횟수 추정 */
        val reps: Map<String, Int>,
        /** 0.5초마다 한 번 채워짐 */
        val tick: Tick?,
        /** 손 모델로 잡힌 손 수 */
        val handsFromModel: Int,
    )

    // ---------------- 내부 상태 ----------------
    private class Series {
        val t = ArrayList<Double>(); val v = ArrayList<Double>()
        fun add(time: Double, value: Double) { t += time; v += value }
        fun trim(cut: Double) { var n = 0; while (n < t.size && t[n] < cut) n++; if (n > 0) { t.subList(0, n).clear(); v.subList(0, n).clear() } }
    }

    private class Buf {
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
        fun trim(cut: Double) {
            var n = 0
            while (n < t.size && t[n] < cut) n++
            if (n == 0) return
            for (l in listOf<MutableList<*>>(t, sig, aux, vis, gate, score, sAmp, sReg, sInt, sCdev, ok)) l.subList(0, n).clear()
        }
    }

    private class Floor { val t = ArrayList<Double>(); val v = ArrayList<DoubleArray>() }
    private class Hold { var held = 0.0; var lastOk = -1e9 }

    private val bufs = INSTANCES.associate { it.id to Buf() }
    private val floors = INSTANCES.associate { it.id to Floor() }
    private val streak = INSTANCES.associate { it.id to doubleArrayOf(0.0) }
    private val holds = INSTANCES.associate { it.id to Hold() }
    private val lastA = HashMap<String, Analysis?>()

    private val center = object { val t = ArrayList<Double>(); val x = ArrayList<Double>(); val y = ArrayList<Double>() }
    private val scaleS = Series()
    private val fwHist = Series()
    private val ratioFw = Series()
    private val ratioEye = Series()
    private val swHist = Series()
    private val offX = Series()
    private val offY = Series()

    private var startMs = -1L
    private var lastEvalT: Double? = null
    private var lastAnalysisT = -1e9
    private var lastTickT = -1e9
    private var lastScale: Double? = null
    private var lastScaleT = -1e9

    fun reset() {
        bufs.values.forEach { it.trim(Double.POSITIVE_INFINITY) }
        floors.values.forEach { it.t.clear(); it.v.clear() }
        streak.values.forEach { it[0] = 0.0 }
        holds.values.forEach { it.held = 0.0; it.lastOk = -1e9 }
        lastA.clear()
        center.t.clear(); center.x.clear(); center.y.clear()
        for (s in listOf(scaleS, fwHist, ratioFw, ratioEye, swHist, offX, offY)) s.trim(Double.POSITIVE_INFINITY)
        startMs = -1L; lastEvalT = null; lastAnalysisT = -1e9; lastTickT = -1e9
        lastScale = null; lastScaleT = -1e9
    }

    /** 처음 프레임 시각을 0초로 한 경과 시간 */
    fun secondsOf(timestampMs: Long): Double {
        if (startMs < 0) startMs = timestampMs
        return (timestampMs - startMs) / 1000.0
    }

    // ---------------- 프레임 정리 ----------------
    private class HandInfo(
        val model: Boolean, val score: Double, val pts: List<Px>?, val palm: Px, val pw: Double, val palmW: Double,
        val wrist: Px, val mcp9: Px, val tips: List<Px>, val allTips: List<Px>, val curls: DoubleArray?, val elbow: Px,
    )

    private class Frame(
        val p: Array<Px>, val world: Array<Px>?, val hands: Map<Char, HandInfo>,
        val shOk: Boolean, val shLoose: Boolean, val earOk: Boolean, val eyeOk: Boolean,
        val shVis: Double, val swPx: Double, val cx: Double, val cy: Double, val earW: Double?, val eyeW: Double?,
    )

    private fun fingerCurl(p: List<Px>, f: IntArray): Double =
        if (f[0] == 1) bend(p[1], p[2], p[3]) + bend(p[2], p[3], p[4])
        else bend(p[0], p[f[0]], p[f[1]]) + bend(p[f[0]], p[f[1]], p[f[2]])

    private fun handInfo(hd: HandRaw?, p: Array<Px>, side: Char): HandInfo {
        val wr = p[if (side == 'L') LW else RW]
        val el = p[if (side == 'L') LE else RE]
        if (hd != null) {
            val q = hd.pts
            val curlSrc = hd.w3 ?: q
            return HandInfo(
                model = true, score = hd.score, pts = q, palm = avg(listOf(q[0], q[5], q[9], q[13], q[17])),
                pw = dist(q[0], q[9]).takeIf { it > 0 } ?: 1.0, palmW = dist(q[5], q[17]), wrist = q[0], mcp9 = q[9],
                tips = listOf(q[8], q[12]), allTips = listOf(q[4], q[8], q[12], q[16], q[20]),
                curls = DoubleArray(5) { fingerCurl(curlSrc, FINGERS[it]) }, elbow = el,
            )
        }
        // 손 모델이 놓쳤을 때: 포즈의 손 관절 3개(손목·검지·새끼)로 대신
        val ix = p[if (side == 'L') LIX else RIX]
        val pk = p[if (side == 'L') LPK else RPK]
        return HandInfo(
            model = false, score = min(wr.v, ix.v) * 0.8, pts = null, palm = avg(listOf(wr, ix, pk)),
            pw = dist(wr, ix).takeIf { it > 0 } ?: 1.0, palmW = dist(ix, pk), wrist = wr, mcp9 = ix,
            tips = listOf(ix), allTips = listOf(ix, pk), curls = null, elbow = el,
        )
    }

    private class HandRaw(val pts: List<Px>, val w3: List<Px>?, val score: Double)

    private fun extractFrame(lms: List<Landmark>, world: List<Landmark>?, hands: List<HandInput>, w: Double, h: Double): Frame {
        val p = Array(33) { i -> Px(lms[i].x * w, lms[i].y * h, 0.0, lms[i].visibility) }
        val w3 = world?.takeIf { it.size >= 33 }?.let { wl -> Array(33) { Px(wl[it].x, wl[it].y, wl[it].z) } }
        val raws = hands.filter { it.score >= C.HAND_SCORE_MIN && it.landmarks.size >= 21 }.map { hd ->
            HandRaw(
                pts = hd.landmarks.map { Px(it.x * w, it.y * h, it.z * w) },
                w3 = hd.world?.takeIf { it.size >= 21 }?.map { Px(it.x, it.y, it.z) },
                score = hd.score,
            )
        }
        // 왼손·오른손: 손 모델의 손목이 포즈의 어느 손목에 가까운지로 정함 (화면 반전과 상관없음)
        var hl: HandRaw? = null; var hr: HandRaw? = null
        if (raws.size == 1) {
            if (dist(raws[0].pts[0], p[LW]) <= dist(raws[0].pts[0], p[RW])) hl = raws[0] else hr = raws[0]
        } else if (raws.size >= 2) {
            val a = raws[0]; val b = raws[1]
            if (dist(a.pts[0], p[LW]) + dist(b.pts[0], p[RW]) <= dist(a.pts[0], p[RW]) + dist(b.pts[0], p[LW])) { hl = a; hr = b }
            else { hl = b; hr = a }
        }
        val m = C.EDGE_MARGIN
        fun inFrame(l: Landmark) = l.x > m && l.x < 1 - m && l.y > m && l.y < 1 - m
        val shIn = inFrame(lms[LS]) && inFrame(lms[RS])
        val shOk = p[LS].v >= C.VISIBILITY_MIN && p[RS].v >= C.VISIBILITY_MIN && shIn
        val shLoose = p[LS].v >= C.LOOSE_VIS_MIN && p[RS].v >= C.LOOSE_VIS_MIN && shIn
        val earOk = p[LEAR].v >= C.VISIBILITY_MIN && p[REAR].v >= C.VISIBILITY_MIN
        val eyeOk = p[LEYE_O].v >= C.VISIBILITY_MIN && p[REYE_O].v >= C.VISIBILITY_MIN
        return Frame(
            p = p, world = w3, hands = mapOf('L' to handInfo(hl, p, 'L'), 'R' to handInfo(hr, p, 'R')),
            shOk = shOk, shLoose = shLoose, earOk = earOk, eyeOk = eyeOk,
            shVis = min(p[LS].v, p[RS].v), swPx = dist(p[LS], p[RS]),
            cx = (p[LS].x + p[RS].x) / 2, cy = (p[LS].y + p[RS].y) / 2,
            earW = if (earOk) dist(p[LEAR], p[REAR]) else null,
            eyeW = if (eyeOk) dist(p[LEYE_O], p[REYE_O]) else null,
        )
    }

    // ---------------- 특징 계산 ----------------
    private fun computeFeatures(f: Frame, s: Double, cx: Double, cy: Double, sLong: Double): HashMap<String, Double> {
        val p = f.p
        val n = Double.NaN
        val F = HashMap<String, Double>(128)
        val noseOk = p[NOSE].v >= C.VISIBILITY_MIN
        var fw = f.earW
        if (fw == null && f.eyeW != null && ratioEye.v.isNotEmpty()) fw = f.eyeW * median(ratioEye.v)
        val fwv = fw ?: (0.55 * s)
        val ref = if (f.earOk) mid(p[LEAR], p[REAR]) else if (f.eyeOk) mid(p[LEYE_O], p[REYE_O]) else null
        val nose = p[NOSE]
        // 머리
        F["head_x"] = if (noseOk) (nose.x - cx) / s else n
        F["head_y"] = if (noseOk) (nose.y - cy) / s else n
        F["head_pitch"] = if (noseOk && ref != null) (nose.y - ref.y) / fwv else n
        F["head_yaw"] = if (noseOk && ref != null) (nose.x - ref.x) / fwv else n
        F["head_roll"] = when {
            f.earOk -> atan2(p[LEAR].y - p[REAR].y, p[LEAR].x - p[REAR].x)
            f.eyeOk -> atan2(p[LEYE_O].y - p[REYE_O].y, p[LEYE_O].x - p[REYE_O].x)
            else -> n
        }
        // 몸통
        val fwRef = if (fwHist.v.isNotEmpty()) median(fwHist.v) else null
        F["trunk_sw"] = if (f.shOk) f.swPx / s - 1 else n
        F["trunk_face"] = if (f.earW != null && fwRef != null) f.earW / fwRef - 1 else n
        F["trunk_y"] = if (f.shOk) f.cy / sLong else n
        F["trunk_x"] = if (f.shOk) f.cx / sLong else n
        F["trunk_tilt"] = if (f.shOk) atan2(p[LS].y - p[RS].y, p[LS].x - p[RS].x) else n
        val w3 = f.world
        F["trunk_zlean"] = if (w3 != null) (w3[LS].z + w3[RS].z) / 2 - (w3[LH].z + w3[RH].z) / 2 else n
        // 어깨
        F["shrug_mid"] = if (f.shOk && p[LEAR].v >= 0.3 && p[REAR].v >= 0.3) (f.cy - (p[LEAR].y + p[REAR].y) / 2) / s else n
        F["shrug_l"] = if (f.shOk && p[LEAR].v >= 0.3) (p[LS].y - p[LEAR].y) / s else n
        F["shrug_r"] = if (f.shOk && p[REAR].v >= 0.3) (p[RS].y - p[REAR].y) / s else n
        val hipsVis = min(p[LH].v, p[RH].v)
        val hipMid = mid(p[LH], p[RH])
        F["torso_len"] = if (f.shOk && hipsVis >= 0.3) (hipMid.y - f.cy) / s else n
        F["hips_vis"] = hipsVis
        F["ear_y"] = if (p[LEAR].v >= 0.3 && p[REAR].v >= 0.3) (p[LEAR].y + p[REAR].y) / 2 / sLong else n
        F["sh_y"] = if (f.shOk) f.cy / sLong else n
        // 손
        val eyeMid = mid(p[LEYE], p[REYE])
        val chest = if (hipsVis >= 0.3) Px(cx + 0.35 * (hipMid.x - cx), cy + 0.35 * (hipMid.y - cy)) else Px(cx, cy + 0.4 * s)
        val eyesHidden = min(p[LEYE].v, p[REYE].v) < 0.5
        for (sd in charArrayOf('L', 'R')) {
            val hd = f.hands.getValue(sd)
            val sh = p[if (sd == 'L') LS else RS]
            val ok = hd.score >= 0.3
            val elOk = hd.elbow.v >= 0.3
            fun k(x: String) = "${x}_$sd"
            F[k("hand_x")] = if (ok) (hd.palm.x - cx) / s else n
            F[k("hand_y")] = if (ok) (hd.palm.y - sh.y) / s else n
            F[k("elbow_y")] = if (elOk) (hd.elbow.y - sh.y) / s else n
            F[k("fore_x")] = if (ok && elOk) (hd.wrist.x - hd.elbow.x) / s else n
            F[k("wflex")] = if (ok && elOk)
                signedAngle(hd.wrist.x - hd.elbow.x, hd.wrist.y - hd.elbow.y, hd.mcp9.x - hd.wrist.x, hd.mcp9.y - hd.wrist.y) else n
            F[k("palm_open")] = if (hd.model) dist(hd.pts!![5], hd.pts[17]) / hd.pw else n
            val curls = hd.curls
            for (i in 0 until 5) F[k("curl$i")] = curls?.get(i) ?: n
            F[k("curlm")] = if (curls != null) (curls[1] + curls[2] + curls[3] + curls[4]) / 4 else n
            // 손끝-눈
            var bd = Double.POSITIVE_INFINITY; var bdx = 0.0; var bdy = 0.0
            for (tp in hd.tips) for (ey in listOf(p[LEYE], p[REYE])) {
                val d = dist(tp, ey)
                if (d < bd) { bd = d; bdx = tp.x - ey.x; bdy = tp.y - ey.y }
            }
            F[k("eyetip_x")] = if (ok) bdx / fwv else n
            F[k("eyetip_y")] = if (ok) bdy / fwv else n
            F[k("eyetip_d")] = if (ok) bd / fwv else n
            // 몸까지 거리 (머리·귀·가슴 중 가장 가까운 곳)
            F[k("hit_d")] = if (ok) minOf(dist(hd.palm, nose), dist(hd.palm, p[LEAR]), dist(hd.palm, p[REAR]), dist(hd.palm, chest)) / s else n
            val faceD = dist(hd.palm, eyeMid) / fwv
            val eyeD = min(dist(hd.palm, p[LEYE]), dist(hd.palm, p[REYE])) / fwv
            var earD = Double.POSITIVE_INFINITY
            for (q in listOf(hd.palm) + hd.allTips) earD = minOf(earD, dist(q, p[LEAR]), dist(q, p[REAR]))
            earD /= fwv
            val above = hd.palm.y < sh.y
            // 눈 가리기와 귀 막기는 손이 눈·귀 중 더 가까운 쪽에만 인정 (둘이 동시에 켜지지 않게)
            val nearEye = min(eyeD, faceD) <= earD
            F[k("g_flutter")] = if (ok) softBelow(max(faceD, 1e-3), 1.6, C.SOFT_GATE) * (if (above) 1.0 else 0.0) else n
            F[k("g_eyecover")] = if (ok)
                max(softBelow(max(eyeD, 1e-3), 0.4, 0.2), softBelow(max(faceD, 1e-3), 0.4, 0.2) * (if (hd.palmW / fwv >= 0.6) 1.0 else 0.0)) *
                    (if (eyesHidden) 1.0 else 0.85) * (if (nearEye) 1.0 else 0.0)
            else n
            F[k("g_earcover")] = if (ok) softBelow(max(earD, 1e-3), 0.4, 0.2) * (if (above) 1.0 else 0.0) * (if (nearEye) 0.0 else 1.0) else n
        }
        val hl = f.hands.getValue('L'); val hr = f.hands.getValue('R')
        F["hands_d"] = if (hl.score >= 0.3 && hr.score >= 0.3) dist(hl.palm, hr.palm) / s else n
        // 전신 (실험)
        val swRef = if (swHist.v.size >= 10) percentile(swHist.v, 0.9) else s
        F["whole_y"] = if (f.shLoose) f.cy / sLong else n
        F["whole_rot"] = if (f.shLoose) (p[LS].x - p[RS].x) / swRef else n
        return F
    }

    private fun visFor(i: Instance, f: Frame, F: Map<String, Double>): Double {
        val sd = i.side ?: 'L'
        return when (i.beh.vis) {
            VisSource.TRUNK -> if (f.shOk) f.shVis else if (f.earW != null) 0.6 else 0.0
            VisSource.HEAD -> f.p[NOSE].v
            VisSource.HIPS -> if (f.shOk) F["hips_vis"] ?: 0.0 else 0.0
            VisSource.HAND -> f.hands.getValue(sd).score
            VisSource.HAND_MODEL -> f.hands.getValue(sd).let { if (it.model) it.score else 0.0 }
            VisSource.BOTH_HANDS -> min(f.hands.getValue('L').score, f.hands.getValue('R').score)
            VisSource.LOOSE -> if (f.shLoose) max(f.shVis, 0.5) else 0.0
        }
    }

    private fun visMin(v: VisSource): Pair<Double, Double> = when (v) {
        VisSource.TRUNK -> 0.5 to 0.6
        VisSource.HEAD -> 0.5 to 0.7
        VisSource.HIPS -> 0.3 to 0.6
        VisSource.HAND, VisSource.BOTH_HANDS -> 0.3 to 0.5
        VisSource.HAND_MODEL -> 0.5 to 0.5
        VisSource.LOOSE -> 0.2 to 0.5
    }

    // ---------------- 프레임 넣기 ----------------
    private fun ingest(f: Frame, t: Double): Double? {
        val p = f.p
        val nose = p[NOSE]
        val noseOk = nose.v >= C.VISIBILITY_MIN
        if (f.earW != null) { fwHist.add(t, f.earW); fwHist.trim(t - C.SCALE_WINDOW_S) }
        if (f.earW != null && f.eyeW != null) { ratioEye.add(t, f.earW / f.eyeW); ratioEye.trim(t - C.REF_KEEP_S) }
        if (f.shOk && f.swPx > 1) {
            scaleS.add(t, f.swPx); scaleS.trim(t - C.SCALE_WINDOW_S)
            swHist.add(t, f.swPx); swHist.trim(t - C.REF_KEEP_S)
            if (f.earW != null) { ratioFw.add(t, f.swPx / f.earW); ratioFw.trim(t - C.REF_KEEP_S) }
            if (noseOk) {
                offX.add(t, (f.cx - nose.x) / f.swPx); offX.trim(t - C.REF_KEEP_S)
                offY.add(t, (f.cy - nose.y) / f.swPx); offY.trim(t - C.REF_KEEP_S)
            }
        }
        var scale: Double? = null
        val lastSh = if (scaleS.t.isNotEmpty()) scaleS.t.last() else -1e9
        if (scaleS.v.isNotEmpty() && t - lastSh <= C.SCALE_HOLD_S) scale = median(scaleS.v)
        else if (f.earW != null && ratioFw.v.isNotEmpty()) scale = f.earW * median(ratioFw.v)
        if (scale != null) { lastScale = scale; lastScaleT = t }
        else if (lastScale != null && t - lastScaleT <= C.SCALE_CARRY_S) scale = lastScale
        if (scale == null) return null
        val cx: Double; val cy: Double
        when {
            f.shOk -> { cx = f.cx; cy = f.cy }
            noseOk && offX.v.isNotEmpty() -> { cx = nose.x + median(offX.v) * scale; cy = nose.y + median(offY.v) * scale }
            f.shLoose -> { cx = f.cx; cy = f.cy }
            else -> return null
        }
        val sLong = if (swHist.v.size >= 10) median(swHist.v) else scale
        val F = computeFeatures(f, scale, cx, cy, sLong)
        for (i in INSTANCES) {
            val b = bufs.getValue(i.id)
            b.t += t
            b.sig += DoubleArray(i.chKeys.size) { F[i.chKeys[it]] ?: Double.NaN }
            b.aux += DoubleArray(i.auxKeys.size) { F[i.auxKeys[it]] ?: Double.NaN }
            b.vis += visFor(i, f, F)
            b.gate += if (i.gateKey != null) F[i.gateKey] ?: Double.NaN else Double.NaN
            b.score += 0.0; b.sAmp += 0.0; b.sReg += 0.0; b.sInt += 0.0; b.sCdev += 0.0; b.ok += 0.0
            b.trim(t - C.KEEP_S)
        }
        if (f.shOk) {
            center.t += t; center.x += f.cx / sLong; center.y += f.cy / sLong
            var n = 0
            while (n < center.t.size && center.t[n] < t - C.KEEP_S) n++
            if (n > 0) { center.t.subList(0, n).clear(); center.x.subList(0, n).clear(); center.y.subList(0, n).clear() }
        }
        return scale
    }

    // ---------------- 분석 ----------------
    private fun windowStart(t: List<Double>, tNow: Double, winS: Double): Int {
        var lo = t.size - 1
        while (lo > 0 && t[lo - 1] >= tNow - winS) lo--
        return lo
    }

    private fun prep(ts: List<Double>, raw: DoubleArray, b: Behavior): DoubleArray {
        val u = resampleUniform(ts, fillGaps(raw), C.ACF_HZ)
        return smoothN(highpass(detrend(u), (b.iMax * C.ACF_HZ / 2).roundToInt()), floor(b.iMin * C.ACF_HZ * 0.1).toInt())
    }

    private fun col(rows: List<DoubleArray>, c: Int) = DoubleArray(rows.size) { rows[it][c] }

    private fun winGateScore(kind: String, i: Instance, b: Buf, lo: Int, ts: List<Double>): Double {
        val sig = b.sig.subList(lo, b.sig.size)
        val aux = b.aux.subList(lo, b.aux.size)
        when (kind) {
            "clap" -> {                    // 손이 맞닿아야 함: 구간 최소 거리 ≤ 0.35 SW
                val d = col(sig, 0).filter { it.isFinite() }
                return if (d.isNotEmpty()) softBelow(max(d.min(), 1e-3), 0.35, 0.2) else 0.0
            }
            "eyerub" -> {                  // 손끝이 눈 근처(≤ 0.5 FW)에 70% 이상 + 손가락은 크게 안 움직임
                val d = col(aux, 0)
                val fin = d.filter { it.isFinite() }
                if (fin.isEmpty()) return 0.0
                val cu = col(aux, 1).filter { it.isFinite() }
                val still = if (cu.size > 3) softBelow(max(pRange(cu), 1e-3), 0.35, 0.3) else 1.0
                return softAbove(max(fin.count { it <= 0.5 }.toDouble() / d.size, 1e-3), 0.7, 0.1) * still
            }
            "hit" -> {                     // 닿음(≤ 0.2 SW) + 빠르게 다가감(≥ 2.0 SW/초)
                val d = col(sig, 0)
                if (d.count { it.isFinite() } < 4) return 0.0
                val u = smoothN(resampleUniform(ts, fillGaps(d), C.ACF_HZ), 1)
                var vmax = 0.0
                for (k in 1 until u.size) vmax = max(vmax, -(u[k] - u[k - 1]) * C.ACF_HZ)
                return softBelow(max(u.min(), 1e-3), 0.2, 0.25) * softAbove(max(vmax, 1e-3), 2.0, 0.2)
            }
            "shrug" -> {                   // 귀(머리) 상하 폭이 어깨 상하 폭의 0.6배 이하
                fun amp(c: Int): Double {
                    val v = col(aux, c)
                    return if (v.count { it.isFinite() } >= 4) pRange(prep(ts, v, i.beh)) else Double.NaN
                }
                val earA = amp(0); val shA = amp(1)
                if (!shA.isFinite() || shA <= 1e-6) return 0.0
                return if (earA.isFinite()) softBelow(max(earA / shA, 1e-3), 0.6, 0.3) else 1.0
            }
            "notclap" -> {                 // 양손이 맞닿는 박수면 흔들기로 세지 않음
                val d = col(aux, 0).filter { it.isFinite() }
                return if (d.isNotEmpty()) 1 - softBelow(max(d.min(), 1e-3), 0.35, 0.2) else 1.0
            }
        }
        return 1.0
    }

    private class ChRes(
        val c: Int, val x: DoubleArray, val amplitude: Double, val ampNeed: Double, val reg: Double, val period: Double,
        val sAmp: Double, val sReg: Double, val sInt: Double,
    ) { val s get() = sAmp * sReg * sInt }

    private fun analyzeRhythm(i: Instance, tNow: Double): Analysis {
        val cfg = i.beh
        val b = bufs.getValue(i.id)
        if (b.t.size < 8) return Analysis(false, "수집 중")
        val lo = windowStart(b.t, tNow, i.localWindowS)
        val ts = b.t.subList(lo, b.t.size)
        val span = ts.last() - ts.first()
        if (ts.size < 8 || span < i.localWindowS * 0.8) return Analysis(false, "수집 중")
        val vis = b.vis.subList(lo, b.vis.size)
        val (vMin, vFrac) = visMin(cfg.vis)
        if (vis.count { it >= vMin }.toDouble() / vis.size < vFrac) return Analysis(false, "인식 약함")

        val fl = floors.getValue(i.id)
        val rows = b.sig.subList(lo, b.sig.size)
        val nCh = cfg.ch.size
        val amps = DoubleArray(nCh) { Double.NaN }
        val res = arrayOfNulls<ChRes>(nCh)
        for (c in 0 until nCh) {
            val raw = col(rows, c)
            if (raw.count { it.isFinite() }.toDouble() / raw.size < C.MIN_CHANNEL_VALID) continue
            val x = prep(ts, raw, cfg)
            val recentN = min(x.size, max(4, (cfg.iMax * 1.5 * C.ACF_HZ).roundToInt()))
            val amplitude = pRange(x.copyOfRange(x.size - recentN, x.size))
            amps[c] = amplitude
            val hist = fl.v.map { it[c] }.filter { it.isFinite() }
            val noise = if (hist.size >= 30) percentile(hist, 0.2) else 0.0
            val ampMin = cfg.ch[c].ampMin
            val ampNeed = min(2 * ampMin, max(ampMin, C.FLOOR_MULT * noise))
            val pr = periodicity(x, C.ACF_HZ, cfg.iMin, cfg.iMax, C.MIN_CYCLES, C.REG_PROMINENCE)
            val sAmp = softAbove(amplitude, ampNeed, C.SOFT_AMP)
            val sReg = softAbove(max(pr.reg, 1e-6), C.REG_MIN, C.SOFT_REG)
            val sInt = if (pr.period > 0) softAbove(pr.period, cfg.iMin, C.SOFT_INT) * softBelow(pr.period, cfg.iMax, C.SOFT_INT) else 0.0
            res[c] = ChRes(c, x, amplitude, ampNeed, pr.reg, pr.period, sAmp, sReg, sInt)
        }
        val valid = res.filterNotNull()
        if (valid.isEmpty()) return Analysis(false, "인식 약함")

        var bestC: Int; var bAmp: Double; var bNeed: Double; var bReg: Double; var bPeriod: Double
        var sAmp: Double; var sReg: Double; var sInt: Double; var extra = 1.0
        when {
            cfg.type == BehaviorType.CIRCLE -> {
                // 머리 굴리기: 좌우·상하 둘 다 같은 박자로 반복 + 1/4박자 어긋나게 겹침(원 모양)
                if (valid.size < 2) return Analysis(false, "인식 약함")
                val a = valid[0]; val c2 = valid[1]
                val per = if (a.period > 0 && c2.period > 0 && abs(a.period - c2.period) / max(a.period, c2.period) < 0.3)
                    (a.period + c2.period) / 2 else 0.0
                var sCirc = 0.0
                if (per > 0) {
                    val q = max(1, (per / 4 * C.ACF_HZ).roundToInt())
                    val circ = absMax(
                        corr(a.x.copyOfRange(0, a.x.size - q), c2.x.copyOfRange(q, c2.x.size)),
                        corr(a.x.copyOfRange(q, a.x.size), c2.x.copyOfRange(0, c2.x.size - q)),
                    )
                    val zero = abs(corr(a.x, c2.x))
                    sCirc = softAbove(max(circ, 1e-3), 0.5, 0.15) * softBelow(max(zero, 1e-3), 0.6, 0.3)
                }
                val pick = if (a.amplitude / a.ampNeed < c2.amplitude / c2.ampNeed) a else c2
                bestC = pick.c; bAmp = pick.amplitude; bNeed = pick.ampNeed; bReg = pick.reg; bPeriod = per
                sAmp = min(a.sAmp, c2.sAmp); sReg = min(a.sReg, c2.sReg)
                sInt = if (per > 0) softAbove(per, cfg.iMin, C.SOFT_INT) * softBelow(per, cfg.iMax, C.SOFT_INT) else 0.0
                extra = sCirc
            }
            cfg.needAll -> {
                // 모든 신호가 함께 반복해야 함 (등 젖히기 = 몸이 뒤로 + 고개도 뒤로)
                if (valid.size < nCh) return Analysis(false, "인식 약함")
                val w = valid.minBy { it.s }
                bestC = w.c; bAmp = w.amplitude; bNeed = w.ampNeed; bReg = w.reg; bPeriod = w.period
                sAmp = w.sAmp; sReg = w.sReg; sInt = w.sInt
            }
            else -> {
                var best = valid[0]
                for (r in valid) if (r.s > best.s || (r.s == best.s && r.amplitude / r.ampNeed > best.amplitude / best.ampNeed)) best = r
                bestC = best.c; bAmp = best.amplitude; bNeed = best.ampNeed; bReg = best.reg; bPeriod = best.period
                sAmp = best.sAmp; sReg = best.sReg; sInt = best.sInt
            }
        }
        if (bReg < 0.3) {
            fl.t += tNow; fl.v += amps
            var n = 0
            while (n < fl.t.size && fl.t[n] < tNow - C.FLOOR_KEEP_S) n++
            if (n > 0) { fl.t.subList(0, n).clear(); fl.v.subList(0, n).clear() }
        }
        // 위치 조건
        var sGate = 1.0
        if (i.gateKey != null) {                    // 프레임 조건: 최근 1초 평균
            val glo = windowStart(b.t, tNow, 1.0)
            val g = b.gate.subList(glo, b.gate.size).filter { it.isFinite() }
            sGate = if (g.isNotEmpty()) mean(g) else 0.0
        }
        if (cfg.winGate != null) sGate *= winGateScore(cfg.winGate, i, b, lo, ts)
        // 손가락: 같이 움직이면 '튕기기', 따로 움직이면 '꼼지락'
        var subtype = ""
        if (cfg.fingerType) {
            val fx = (1..4).mapNotNull { res.getOrNull(it)?.x }
            val cs = ArrayList<Double>()
            for (a in fx.indices) for (c in a + 1 until fx.size) cs += corr(fx[a], fx[c])
            subtype = if (cs.isEmpty()) "" else if (median(cs) >= 0.6) "튕기기" else "꼼지락"
        }
        return Analysis(
            valid = true, reason = "", interval = bPeriod, amplitude = bAmp, reg = bReg, ampNeed = bNeed,
            axis = cfg.ch[bestC].label + if (cfg.type == BehaviorType.CIRCLE) " · 원" else "", subtype = subtype,
            cycles = if (bPeriod > 0) floor(span / bPeriod).toInt() else 0,
            intOk = sInt >= 0.5, ampOk = bAmp >= bNeed, regOk = bReg >= C.REG_MIN, gateOk = sGate * extra >= 0.5,
            sInt = sInt, sAmp = sAmp, sReg = sReg, sGate = sGate * extra,
        )
    }

    private fun analyzeHold(i: Instance, tNow: Double, dt: Double): Analysis {
        val b = bufs.getValue(i.id)
        val hs = holds.getValue(i.id)
        val last = b.t.size - 1
        val g = b.gate[last]
        val v = b.vis[last]
        val (vMin, _) = visMin(i.beh.vis)
        if (!g.isFinite() || v < vMin) {
            if (tNow - hs.lastOk > C.HOLD_GAP_S) hs.held = 0.0
            return Analysis(false, "인식 약함", held = hs.held)
        }
        // 손이 멈춰 있어야 함 (최근 1초 손 위치 폭 ≤ 0.05 SW, 손가락 굽힘 폭 ≤ 0.35 rad) — 팔랑이기·때리기와 구분
        val slo = windowStart(b.t, tNow, 1.0)
        val aux = b.aux.subList(slo, b.aux.size)
        val hx = col(aux, 0).filter { it.isFinite() }
        val hy = col(aux, 1).filter { it.isFinite() }
        val cu = col(aux, 2).filter { it.isFinite() }
        val posR = max(if (hx.size > 1) pRange(hx) else 0.0, if (hy.size > 1) pRange(hy) else 0.0)
        val sStill = softBelow(max(posR, 1e-3), 0.05, 0.3) * (if (cu.size > 3) softBelow(max(pRange(cu), 1e-3), 0.35, 0.3) else 1.0)
        val gEff = g * sStill
        if (gEff >= 0.5) { hs.held += dt; hs.lastOk = tNow }
        else if (tNow - hs.lastOk > C.HOLD_GAP_S) hs.held = 0.0
        val sHold = softAbove(max(hs.held, 1e-3), C.HOLD_S, C.SOFT_GATE)
        return Analysis(
            valid = true, reason = "", axis = "유지 %.1fs".format(hs.held), held = hs.held,
            sAmp = sHold, sReg = sHold, sInt = sHold, sGate = gEff, gateOk = gEff >= 0.5, ampOk = hs.held >= C.HOLD_S,
            regOk = true, intOk = true,
        )
    }

    private fun centerDev(tNow: Double, winS: Double): Double {
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

    // ---------------- 프레임마다: 행동별 순간 점수 ----------------
    private fun evaluate(tNow: Double, analyzed: Boolean): Pair<Map<String, Double>, Map<String, Int>> {
        val dt = lastEvalT?.let { max(0.0, min(C.DT_CAP_S, tNow - it)) } ?: 0.0
        lastEvalT = tNow
        val due = tNow - lastAnalysisT >= 1 / C.ANALYSIS_HZ
        if (analyzed && due) lastAnalysisT = tNow
        val inst = HashMap<String, Double>()
        val reps = HashMap<String, Int>()
        for (i in INSTANCES) {
            val b = bufs.getValue(i.id)
            var a: Analysis? = lastA[i.id]
            var fs = 0.0
            if (analyzed && b.t.isNotEmpty()) {
                a = when {
                    i.beh.type == BehaviorType.HOLD -> analyzeHold(i, tNow, dt)
                    due || a == null -> analyzeRhythm(i, tNow)
                    else -> a
                }
                if (due || i.beh.type == BehaviorType.HOLD || a.cdev == 0.0) {
                    a.cdev = centerDev(tNow, i.cdevWindowS)
                    a.sCdev = softBelow(max(a.cdev, 1e-6), C.CENTER_DEV_MAX, C.SOFT_CDEV)
                    a.cdevOk = a.cdev <= C.CENTER_DEV_MAX
                }
                if (a.valid) fs = if (i.beh.type == BehaviorType.HOLD) a.sAmp * a.sCdev else a.sAmp * a.sInt * a.sReg * a.sGate * a.sCdev
                if (!fs.isFinite()) fs = 0.0
                a.frameScore = fs
                lastA[i.id] = a
                val l = b.t.size - 1
                b.score[l] = fs; b.ok[l] = if (a.valid) 1.0 else 0.0
                b.sAmp[l] = if (a.valid) a.sAmp else 0.0; b.sReg[l] = if (a.valid) a.sReg else 0.0
                b.sInt[l] = if (a.valid) a.sInt else 0.0; b.sCdev[l] = if (a.valid) a.sCdev else 0.0
            } else a = null
            val st = streak.getValue(i.id)
            st[0] = if (fs >= 0.5) st[0] + dt else 0.0
            reps[i.id] = if (a != null && a.valid && a.interval > 0 && st[0] > 0) floor(st[0] / a.interval).toInt() + C.MIN_CYCLES else 0
            inst[i.id] = fs
        }
        return inst to reps
    }

    // ---------------- 0.5초마다: 최근 2초 근거 (+ 터치) ----------------
    private fun evidence(tNow: Double, touch: TouchContext): Tick {
        val ei = LinkedHashMap<String, MutableEv>()
        for (i in INSTANCES) {
            val b = bufs.getValue(i.id)
            var lo = b.t.size
            while (lo > 0 && b.t[lo - 1] >= tNow - C.WINDOW_S) lo--
            if (b.t.size - lo < 8 || b.t.last() - b.t[lo] < 0.7 * C.WINDOW_S) continue
            fun m(l: List<Double>) = mean(l.subList(lo, l.size))
            val cover = m(b.ok)
            if (cover < C.PART_COVER_MIN) continue
            val raw = m(b.score)
            var fInst = 0.0
            if (i.beh.functional) {
                fInst = touch.f
                val a = lastA[i.id]
                val ti = touch.tapIntervalS
                if (ti != null && a != null && a.valid && a.interval > 0 && abs(a.interval - ti) / ti <= C.TAP_MATCH_TOL) fInst = 1.0
            }
            ei[i.id] = MutableEv(
                i, raw * (1 - C.touchPenalty * fInst), raw, fInst,
                m(b.sAmp), m(b.sReg), m(b.sInt), m(b.sCdev), cover, lastA[i.id]?.subtype ?: "",
            )
        }
        for ((spec, gens) in SPECIFIC_OVER) {
            for (e in ei.values.toList()) {
                if (e.inst.beh.key != spec || e.score < 0.5) continue
                for (g in gens) for (sd in (e.inst.side?.let { listOf(it) } ?: listOf('L', 'R'))) {
                    val t = ei["${g}_$sd"] ?: continue
                    if (t.suppressedBy == null) { t.score *= SUPPRESS; t.suppressedBy = spec }
                }
            }
        }
        val beh = LinkedHashMap<String, BehaviorEvidence>()
        for (e in ei.values) {
            val key = e.inst.beh.key
            if (beh[key] == null || e.score > beh.getValue(key).score) beh[key] = e.freeze()
        }
        var r: Double? = null; var dom: String? = null
        for ((k, e) in beh) if (r == null || e.score > r) { r = e.score; dom = k }
        return Tick(tNow, r, dom, beh, touch)
    }

    private class MutableEv(
        val inst: Instance, var score: Double, val raw: Double, val functional: Double,
        val amp: Double, val reg: Double, val int: Double, val cdev: Double, val cover: Double, val subtype: String,
    ) {
        var suppressedBy: String? = null
        fun freeze() = BehaviorEvidence(inst.beh.key, inst.side, score, raw, functional, amp, reg, int, cdev, cover, subtype, suppressedBy)
    }

    // ---------------- 공개 함수 ----------------
    /**
     * 카메라 프레임 1장을 넣는다.
     * @param pose 포즈 33개 관절 (정규화). 사람이 안 잡혔으면 null
     * @param world 포즈 33개 3D 좌표 (미터). 없으면 null
     * @param hands 손 모델 결과 (없으면 빈 목록 — 포즈의 손 관절 3개로 대신)
     * @param imageWidth/imageHeight 입력 영상 크기 (px). 손가락 각도·거리 계산에 씀
     * @param touch 이번 판정의 터치 정도 (0.5초마다 한 번만 읽음)
     */
    fun update(
        timestampMs: Long,
        pose: List<Landmark>?,
        world: List<Landmark>?,
        hands: List<HandInput>,
        imageWidth: Int,
        imageHeight: Int,
        touch: () -> TouchContext = { TouchContext.NONE },
    ): FrameResult {
        val t = secondsOf(timestampMs)
        var scale: Double? = null
        if (pose != null && pose.size >= 33) {
            val f = extractFrame(pose, world, hands, imageWidth.toDouble(), imageHeight.toDouble())
            scale = ingest(f, t)
        }
        val (inst, reps) = evaluate(t, scale != null)
        var tick: Tick? = null
        if (t - lastTickT >= C.STRIDE_S) {
            lastTickT = t
            tick = evidence(t, touch())
        }
        return FrameResult(scale != null, inst, reps, tick, hands.count { it.score >= C.HAND_SCORE_MIN })
    }

    /** 행동 단위(좌우 포함)의 마지막 분석 — 정밀 분석 화면용 */
    fun lastAnalysis(instanceId: String): Analysis? = lastA[instanceId]

    companion object {
        /** true 면 아동 화면의 알람(StereotypySignal.detected)을 v3 + 소프트 보팅 결과로 정한다.
         *  false 면 기존 StereotypyDetector 알람을 그대로 쓰고 v3 는 값만 계산한다. */
        @Volatile var USE_FOR_ALARM = false

        /** 행동 단위 id 목록 (예: "flap_L", "clap") */
        val INSTANCE_IDS: List<String> get() = INSTANCES.map { it.id }

        // MediaPipe 포즈 33개 관절 번호
        const val NOSE = 0
        const val LEYE = 2; const val LEYE_O = 3; const val REYE = 5; const val REYE_O = 6
        const val LEAR = 7; const val REAR = 8
        const val LS = 11; const val RS = 12; const val LE = 13; const val RE = 14; const val LW = 15; const val RW = 16
        const val LPK = 17; const val RPK = 18; const val LIX = 19; const val RIX = 20
        const val LH = 23; const val RH = 24

        // 손 관절: 0 손목 / 엄지 1~4 / 검지 5~8 / 중지 9~12 / 약지 13~16 / 새끼 17~20
        internal val FINGERS = arrayOf(intArrayOf(1, 2, 3, 4), intArrayOf(5, 6, 7, 8), intArrayOf(9, 10, 11, 12),
            intArrayOf(13, 14, 15, 16), intArrayOf(17, 18, 19, 20))
    }
}
