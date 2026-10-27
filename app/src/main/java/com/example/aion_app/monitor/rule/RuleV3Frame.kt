package com.example.aion_app.monitor.rule

import kotlin.math.hypot

// ============================================================
// 규칙모델 v3 — 프레임 하나 정리 (포즈 33 + 손 21 × 2)
// HTML 의 handInfo / extractFrame 과 같다.
// ============================================================

/** 손 모델 결과 하나 (MediaPipe HandLandmarker 의 손 한 개) */
class HandRaw(
    /** 21개, 정규화 좌표 0~1 (z 는 MediaPipe 그대로) */
    val norm: List<Pt>,
    /** 21개, 3D 미터 좌표. 없으면 null */
    val world: List<Pt>?,
    /** 왼손/오른손 판정 신뢰도 */
    val score: Double,
)

/** 엔진에 넣는 프레임 하나. MediaPipe 타입과 분리해 두어 단위 테스트가 쉽다 */
class FrameInput(
    /** 포즈 33개, 정규화 좌표 0~1, v = 보임 정도 */
    val pose: List<Pt>,
    /** 포즈 33개 3D (미터, 엉덩이 기준). 없으면 null */
    val poseWorld: List<Pt>?,
    val hands: List<HandRaw>,
    /** 입력 이미지 크기(px). 좌표를 px 로 바꿔야 가로세로 비율이 맞는다 */
    val width: Int,
    val height: Int,
)

/** 손 하나의 정리된 정보. 손 모델이 놓치면 포즈의 손 관절 3개로 대신한다 */
class HandInfo(
    val model: Boolean,
    val score: Double,
    val pts: List<Pt>?,
    val palm: Pt,
    val pw: Double,
    val palmW: Double,
    val wrist: Pt,
    val mcp9: Pt,
    val tips: List<Pt>,
    val allTips: List<Pt>,
    val curls: List<Double>?,
    val elbow: Pt,
)

/** 정리된 손 (화면 px 좌표 + 3D) */
class HandPx(val pts: List<Pt>, val w3: List<Pt>?, val score: Double)

class Frame(
    /** 33 × (x, y) 정규화 — 학습모델 입력은 여기서 고름 */
    val xy: FloatArray,
    /** 포즈 33개, 화면 px 좌표 */
    val p: List<Pt>,
    val world: List<Pt>?,
    val handRaw: Map<String, HandPx?>,
    val hands: Map<String, HandInfo>,
    val shOk: Boolean,
    val shLoose: Boolean,
    val earOk: Boolean,
    val eyeOk: Boolean,
    val shVis: Double,
    val swPx: Double,
    val cx: Double,
    val cy: Double,
    val earW: Double?,
    val eyeW: Double?,
    val scxN: Double,
    val scyN: Double,
    val swN: Double,
) {
    /** ingest 때 채워짐 (화면 표시용) */
    var features: Map<String, Double>? = null
}

private fun handInfo(hd: HandPx?, p: List<Pt>, side: String): HandInfo {
    val left = side == SIDE_L
    val wr = p[if (left) Pose.LW else Pose.RW]
    val el = p[if (left) Pose.LE else Pose.RE]
    if (hd != null) {
        val q = hd.pts
        val curlSrc = hd.w3 ?: q
        return HandInfo(
            model = true, score = hd.score, pts = q,
            palm = avgPts(listOf(q[0], q[5], q[9], q[13], q[17])),
            pw = dist(q[0], q[9]).takeIf { it != 0.0 } ?: 1.0,
            palmW = dist(q[5], q[17]), wrist = q[0], mcp9 = q[9],
            tips = listOf(q[8], q[12]), allTips = listOf(q[4], q[8], q[12], q[16], q[20]),
            curls = FINGERS.map { fingerCurl(curlSrc, it) }, elbow = el,
        )
    }
    // 손 모델이 놓쳤을 때: 포즈의 손 관절 3개(손목·검지·새끼)로 대신
    val ix = p[if (left) Pose.LIX else Pose.RIX]
    val pk = p[if (left) Pose.LPK else Pose.RPK]
    return HandInfo(
        model = false, score = minOf(wr.v, ix.v) * 0.8, pts = null,
        palm = avgPts(listOf(wr, ix, pk)),
        pw = dist(wr, ix).takeIf { it != 0.0 } ?: 1.0,
        palmW = dist(ix, pk), wrist = wr, mcp9 = ix, tips = listOf(ix), allTips = listOf(ix, pk),
        curls = null, elbow = el,
    )
}

fun extractFrame(input: FrameInput): Frame {
    val w = input.width.toDouble()
    val h = input.height.toDouble()
    val lms = input.pose
    val p = lms.map { Pt(it.x * w, it.y * h, 0.0, it.v) }

    // 손 21개: 화면 px 좌표 (z는 x와 같은 크기로) + 3D(미터)
    val good = input.hands
        .filter { it.score >= RuleV3.HAND_SCORE_MIN }
        .map { r -> HandPx(r.norm.map { Pt(it.x * w, it.y * h, it.z * w) }, r.world, r.score) }

    // 왼손·오른손 정하기: 손 모델의 손목이 포즈의 어느 손목에 가까운지로 정함 (화면 반전과 상관없음)
    var hL: HandPx? = null
    var hR: HandPx? = null
    val c = good.map { Triple(it, dist(it.pts[0], p[Pose.LW]), dist(it.pts[0], p[Pose.RW])) }
    if (c.size == 1) {
        if (c[0].second <= c[0].third) hL = c[0].first else hR = c[0].first
    } else if (c.size >= 2) {
        if (c[0].second + c[1].third <= c[0].third + c[1].second) { hL = c[0].first; hR = c[1].first }
        else { hL = c[1].first; hR = c[0].first }
    }

    val m = RuleV3.EDGE_MARGIN
    fun inFrame(q: Pt) = q.x > m && q.x < 1 - m && q.y > m && q.y < 1 - m
    val ls = lms[Pose.LS]
    val rs = lms[Pose.RS]
    val shIn = inFrame(ls) && inFrame(rs)
    val shOk = p[Pose.LS].v >= RuleV3.VISIBILITY_MIN && p[Pose.RS].v >= RuleV3.VISIBILITY_MIN && shIn
    val shLoose = p[Pose.LS].v >= RuleV3.LOOSE_VIS_MIN && p[Pose.RS].v >= RuleV3.LOOSE_VIS_MIN && shIn
    val earOk = p[Pose.LEAR].v >= RuleV3.VISIBILITY_MIN && p[Pose.REAR].v >= RuleV3.VISIBILITY_MIN
    val eyeOk = p[Pose.LEYE_O].v >= RuleV3.VISIBILITY_MIN && p[Pose.REYE_O].v >= RuleV3.VISIBILITY_MIN

    val xy = FloatArray(lms.size * 2)
    lms.forEachIndexed { i, l -> xy[i * 2] = l.x.toFloat(); xy[i * 2 + 1] = l.y.toFloat() }

    return Frame(
        xy = xy,
        p = p,
        world = input.poseWorld,
        handRaw = mapOf(SIDE_L to hL, SIDE_R to hR),
        hands = mapOf(SIDE_L to handInfo(hL, p, SIDE_L), SIDE_R to handInfo(hR, p, SIDE_R)),
        shOk = shOk, shLoose = shLoose, earOk = earOk, eyeOk = eyeOk,
        shVis = minOf(p[Pose.LS].v, p[Pose.RS].v),
        swPx = dist(p[Pose.LS], p[Pose.RS]),
        cx = (p[Pose.LS].x + p[Pose.RS].x) / 2,
        cy = (p[Pose.LS].y + p[Pose.RS].y) / 2,
        earW = if (earOk) dist(p[Pose.LEAR], p[Pose.REAR]) else null,
        eyeW = if (eyeOk) dist(p[Pose.LEYE_O], p[Pose.REYE_O]) else null,
        scxN = (ls.x + rs.x) / 2,
        scyN = (ls.y + rs.y) / 2,
        swN = hypot(rs.x - ls.x, rs.y - ls.y),
    )
}
