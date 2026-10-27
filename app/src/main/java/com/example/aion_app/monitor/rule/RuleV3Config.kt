package com.example.aion_app.monitor.rule

// ============================================================
// AION 규칙모델 v3 — 기준값과 행동 정의
// ============================================================
// 1024_aion_monitor_v3_33pose_hands.html 의 TH / POSE / BEHAVIORS / INSTANCES 를 그대로 옮겼다.
// 숫자 근거는 「상동행동 감지 기준서 (규칙모델 v3)」 참고.
//
// 입력: MediaPipe 포즈 33개 관절(x, y, 보임 정도 + 3D) + 손 21개 관절 × 양손(2D + 3D)
// 출력: 0.5초마다 "최근 2초가 상동행동일 점수"
//
// 행동 17가지를 따로 감지
//   리듬형      : 같은 동작이 일정한 박자로 3번 이상 → 진폭·주기성·중심편차 (+ 위치 조건)
//   자세 유지형 : 눈 가리기·귀 막기 → 위치 조건 + 유지 시간 + 중심편차
// 지속 시간(4초)은 두 모델 확률을 합친 뒤에 적용
// 태블릿 터치 중에는 손·팔·손가락 행동 점수를 낮춤 (기능적 행동)
// ============================================================
object RuleV3 {
    const val FPS = 30
    const val WINDOW_S = 2.0              // 판정 구간 (학습모델과 같음)
    const val STRIDE_S = 0.5              // 판정 간격
    const val ANALYSIS_HZ = 15            // 리듬 분석을 초당 몇 번 할지 (계산량 조절)
    const val MIN_CYCLES = 3              // 같은 동작 최소 3번 연속
    // 아래 4개는 기준서(HTML) 원래 값. 실제로 쓰는 값은 Sensitivity 단계가 정한다
    const val DURATION_S = 4.0            // 앙상블 확률 ≥ τ가 4초 이어지면 위험
    const val DECAY_RATE = 1.5
    const val DUR_SOFTNESS = 0.05
    const val CENTER_WIN_MIN_S = 3.0
    const val CENTER_DEV_MAX = 0.35       // SW
    const val VISIBILITY_MIN = 0.5
    const val LOOSE_VIS_MIN = 0.2
    const val HAND_SCORE_MIN = 0.5        // 손 모델 신뢰도
    const val EDGE_MARGIN = 0.02
    const val SCALE_WINDOW_S = 3.0
    const val SCALE_HOLD_S = 1.0
    const val SCALE_CARRY_S = 3.0
    const val REF_KEEP_S = 10.0
    const val DT_CAP_S = 0.25
    const val KEEP_S = 16.0
    const val TIMELINE_WINDOW_S = 30.0
    const val REG_MIN = 0.5
    const val REG_PROMINENCE = 0.3
    const val ACF_HZ = 30
    const val MIN_CHANNEL_VALID = 0.7
    const val FLOOR_KEEP_S = 60.0
    const val FLOOR_MULT = 2.5
    const val SOFT_AMP = 0.12
    const val SOFT_INT = 0.10
    const val SOFT_CDEV = 0.20
    const val SOFT_REG = 0.15
    const val SOFT_GATE = 0.15
    const val HOLD_S = 1.5                // 자세 유지형: 1.5초 유지 시 점수 0.5
    const val HOLD_GAP_S = 0.3            // 잠깐 끊겨도 이어진 것으로 보는 시간
    const val PART_COVER_MIN = 0.5
    const val LOW_FPS = 20                // 이보다 느리면 손가락 행동 '정확도 낮음'

    // 기능적 행동 (터치)
    const val TOUCH_TAIL_S = 0.4          // 탭이 끝난 뒤에도 '터치 중'으로 보는 시간
    const val TOUCH_PENALTY = 0.8         // 점수 × (1 − 0.8 × f)
    const val TAP_MATCH_TOL = 0.25        // 반복 박자가 탭 간격과 ±25% 안이면 f = 1

    // 앙상블
    const val ENSEMBLE_W = 0.5
    const val DECISION = 0.5

    // 알림
    const val ALERT_ENTER_LEVEL = 0.999
    const val ALERT_EXIT_LEVEL = 0.5
    const val ALERT_MERGE_GAP_S = 5.0
    const val ALERT_COOLDOWN_S = 30.0
    const val ALERT_COUNT_WINDOW_S = 600.0
    const val ALERT_COUNT_NEED = 3

    // 더 구체적인 행동이 확실하면(≥ 0.5) 같은 손의 일반 동작 점수를 30%로 낮춤
    const val SUPPRESS = 0.3
}

/** MediaPipe 포즈 33개 관절 번호 */
object Pose {
    const val NOSE = 0
    const val LEYE_I = 1; const val LEYE = 2; const val LEYE_O = 3
    const val REYE_I = 4; const val REYE = 5; const val REYE_O = 6
    const val LEAR = 7; const val REAR = 8
    const val MOUTH_L = 9; const val MOUTH_R = 10
    const val LS = 11; const val RS = 12
    const val LE = 13; const val RE = 14
    const val LW = 15; const val RW = 16
    const val LPK = 17; const val RPK = 18
    const val LIX = 19; const val RIX = 20
    const val LTH = 21; const val RTH = 22
    const val LH = 23; const val RH = 24
    const val LK = 25; const val RK = 26
    const val LA = 27; const val RA = 28
    const val LHE = 29; const val RHE = 30
    const val LFT = 31; const val RFT = 32
}

/** 손 관절 번호: 0 손목 / 엄지 1~4 / 검지 5~8 / 중지 9~12 / 약지 13~16 / 새끼 17~20 */
val FINGERS: Array<IntArray> = arrayOf(
    intArrayOf(1, 2, 3, 4), intArrayOf(5, 6, 7, 8), intArrayOf(9, 10, 11, 12),
    intArrayOf(13, 14, 15, 16), intArrayOf(17, 18, 19, 20),
)

enum class BehaviorType { RHYTHM, CIRCLE, HOLD }

/** 인식 확인에 쓸 부위. [min] 이상인 프레임이 [frac] 비율 이상이어야 판정한다 */
enum class VisKind(val min: Double, val frac: Double) {
    TRUNK(0.5, 0.6), HEAD(0.5, 0.7), HIPS(0.3, 0.6), HAND(0.3, 0.5),
    HAND_MODEL(0.5, 0.5), BOTH_HANDS(0.3, 0.5), LOOSE(0.2, 0.5),
}

/** 구간 전체로 보는 위치 조건 */
enum class WinGate { CLAP, EYE_RUB, HIT, SHRUG, NOT_CLAP }

/** 신호 채널: 특징 이름, 화면 이름, 최소 진폭 */
data class Channel(val key: String, val label: String, val ampMin: Double)

/**
 * 행동 정의.
 *  sides      : true 면 왼손·오른손을 따로 계산 (특징 이름에 _L/_R 이 붙음)
 *  iMin/iMax  : 한 번 반복 시간(초)
 *  gate       : 프레임마다 보는 위치 조건 특징 (g_flutter 등)
 *  winGate    : 구간 전체로 보는 조건
 *  functional : 태블릿 터치 중 점수를 낮출 행동
 */
data class Behavior(
    val key: String,
    val label: String,
    val group: String,
    val type: BehaviorType,
    val vis: VisKind,
    val iMin: Double = 0.0,
    val iMax: Double = 0.0,
    val ch: List<Channel> = emptyList(),
    val sides: Boolean = false,
    val gate: String? = null,
    val winGate: WinGate? = null,
    val aux: List<String> = emptyList(),
    val functional: Boolean = false,
    val fingerType: Boolean = false,
    val needAll: Boolean = false,
    val experimental: Boolean = false,
)

val BEHAVIORS: List<Behavior> = listOf(
    // 전신
    Behavior("body_rock", "몸 앞뒤 흔들기", "전신", BehaviorType.RHYTHM, VisKind.TRUNK, 0.4, 2.5,
        listOf(Channel("trunk_sw", "어깨 너비 변화", 0.08), Channel("trunk_face", "얼굴 폭 변화", 0.08), Channel("trunk_zlean", "3D 앞뒤 기울기", 0.04))),
    Behavior("body_sway", "몸 좌우 흔들기", "전신", BehaviorType.RHYTHM, VisKind.TRUNK, 0.4, 2.5,
        listOf(Channel("trunk_x", "어깨 좌우", 0.08), Channel("trunk_tilt", "어깨선 기울기", 0.10))),
    // 머리
    Behavior("head_roll", "머리 굴리기", "머리", BehaviorType.CIRCLE, VisKind.HEAD, 0.5, 3.0,
        listOf(Channel("head_x", "코 좌우", 0.10), Channel("head_y", "코 상하", 0.10))),
    Behavior("head_nod", "끄덕이기", "머리", BehaviorType.RHYTHM, VisKind.HEAD, 0.25, 1.5,
        listOf(Channel("head_pitch", "코-귀 높이차", 0.15))),
    Behavior("head_tilt", "갸웃거리기", "머리", BehaviorType.RHYTHM, VisKind.HEAD, 0.4, 2.0,
        listOf(Channel("head_roll", "귀 선 기울기", 0.15))),
    Behavior("head_turn", "고개 돌리기", "머리", BehaviorType.RHYTHM, VisKind.HEAD, 0.3, 2.0,
        listOf(Channel("head_yaw", "고개 돌림", 0.25))),
    // 어깨
    Behavior("shrug", "으쓱하기", "어깨", BehaviorType.RHYTHM, VisKind.TRUNK, 0.4, 2.5,
        listOf(Channel("shrug_mid", "양 어깨", 0.06), Channel("shrug_l", "왼 어깨", 0.06), Channel("shrug_r", "오른 어깨", 0.06)),
        winGate = WinGate.SHRUG, aux = listOf("ear_y", "sh_y")),
    Behavior("arch", "등 젖히기", "어깨", BehaviorType.RHYTHM, VisKind.HIPS, 0.6, 4.0,
        listOf(Channel("trunk_zlean", "3D 뒤로 기울기", 0.04), Channel("head_pitch", "고개 젖힘", 0.15)),
        needAll = true, experimental = true),
    // 손·손가락
    Behavior("flap", "손·팔 퍼덕이기", "손·손가락", BehaviorType.RHYTHM, VisKind.HAND, 0.15, 1.0,
        listOf(Channel("hand_y", "손 상하", 0.15), Channel("elbow_y", "팔꿈치 상하", 0.12), Channel("wflex", "손목 꺾임", 0.5)),
        sides = true, functional = true),
    Behavior("finger", "손가락 꼼지락·튕기기", "손·손가락", BehaviorType.RHYTHM, VisKind.HAND_MODEL, 0.12, 0.8,
        listOf(Channel("curl0", "엄지 굽힘", 0.35), Channel("curl1", "검지 굽힘", 0.35), Channel("curl2", "중지 굽힘", 0.35),
            Channel("curl3", "약지 굽힘", 0.35), Channel("curl4", "새끼 굽힘", 0.35), Channel("curlm", "평균 굽힘", 0.35)),
        sides = true, functional = true, fingerType = true),
    Behavior("flutter", "얼굴 앞 손가락 팔랑이기", "손·손가락", BehaviorType.RHYTHM, VisKind.HAND_MODEL, 0.12, 0.8,
        listOf(Channel("curl1", "검지 굽힘", 0.35), Channel("curl2", "중지 굽힘", 0.35), Channel("curl3", "약지 굽힘", 0.35),
            Channel("curl4", "새끼 굽힘", 0.35), Channel("curlm", "평균 굽힘", 0.35)),
        sides = true, gate = "g_flutter"),
    Behavior("clap", "박수", "손·손가락", BehaviorType.RHYTHM, VisKind.BOTH_HANDS, 0.2, 1.2,
        listOf(Channel("hands_d", "양손 거리", 0.25)), winGate = WinGate.CLAP),
    Behavior("wave", "손·팔 흔들기", "손·손가락", BehaviorType.RHYTHM, VisKind.HAND, 0.2, 1.2,
        listOf(Channel("hand_x", "손 좌우", 0.15), Channel("fore_x", "아래팔 좌우", 0.12), Channel("palm_open", "손바닥 회전", 0.4)),
        sides = true, functional = true, winGate = WinGate.NOT_CLAP, aux = listOf("hands_d")),
    // 감각
    Behavior("eye_cover", "눈 가리기", "감각", BehaviorType.HOLD, VisKind.HAND,
        sides = true, gate = "g_eyecover", aux = listOf("hand_x", "hand_y", "curlm")),
    Behavior("ear_cover", "귀 막기", "감각", BehaviorType.HOLD, VisKind.HAND,
        sides = true, gate = "g_earcover", aux = listOf("hand_x", "hand_y", "curlm")),
    Behavior("eye_rub", "눈 비비기", "감각", BehaviorType.RHYTHM, VisKind.HAND_MODEL, 0.2, 1.0,
        listOf(Channel("eyetip_x", "손끝 좌우", 0.08), Channel("eyetip_y", "손끝 상하", 0.08)),
        sides = true, winGate = WinGate.EYE_RUB, aux = listOf("eyetip_d", "curlm")),
    Behavior("hit", "자기 때리기", "감각", BehaviorType.RHYTHM, VisKind.HAND, 0.25, 1.5,
        listOf(Channel("hit_d", "몸까지 거리", 0.25)), sides = true, winGate = WinGate.HIT),
    // 전신 큰 움직임 (실험 — 앉은 자세에서는 드묾)
    Behavior("jump", "점프·바운스", "전신(실험)", BehaviorType.RHYTHM, VisKind.LOOSE, 0.3, 2.0,
        listOf(Channel("whole_y", "큰 상하", 0.35)), experimental = true),
    Behavior("spin", "회전", "전신(실험)", BehaviorType.RHYTHM, VisKind.LOOSE, 0.5, 3.0,
        listOf(Channel("whole_rot", "몸 방향", 1.0)), experimental = true),
)

val BEH: Map<String, Behavior> = BEHAVIORS.associateBy { it.key }
val GROUPS: List<String> = BEHAVIORS.map { it.group }.distinct()

/** 양손 공통 특징 (aux 에 있어도 _L/_R 를 붙이지 않음) */
private val SHARED_AUX = setOf("hands_d", "ear_y", "sh_y")

/** 행동 × 손(L/R) 하나. 손을 따로 보지 않는 행동은 side = null */
class Instance(val beh: Behavior, val side: String?) {
    private fun sfx(k: String) = if (side != null) "${k}_$side" else k

    val id: String = sfx(beh.key)
    val chKeys: List<String> = beh.ch.map { sfx(it.key) }
    val auxKeys: List<String> = beh.aux.map { if (it in SHARED_AUX) it else sfx(it) }
    val gateKey: String? = beh.gate?.let { sfx(it) }
    val localWindowS: Double =
        if (beh.type == BehaviorType.HOLD) RuleV3.WINDOW_S else (RuleV3.MIN_CYCLES + 0.5) * beh.iMax
    val cdevWindowS: Double =
        maxOf(RuleV3.CENTER_WIN_MIN_S, 3 * (if (beh.iMax > 0) beh.iMax else 1.0))
}

val INSTANCES: List<Instance> = BEHAVIORS.flatMap { b ->
    if (b.sides) listOf(Instance(b, "L"), Instance(b, "R")) else listOf(Instance(b, null))
}

/**
 * 더 구체적인 행동이 확실하면(≥ 0.5) 같은 손의 일반 동작 점수를 30%로 낮춤
 *  예) 자기 때리기 중에는 손이 오가므로 '흔들기'도 높게 나오지만, 때리기로 보는 것이 맞음
 */
val SPECIFIC_OVER: List<Pair<String, List<String>>> = listOf(
    "hit" to listOf("flap", "wave", "flutter"),
    "clap" to listOf("flap", "wave"),
    "flutter" to listOf("finger", "flap", "wave", "eye_rub"),
    "eye_rub" to listOf("eye_cover", "finger"),
    "shrug" to listOf("flap"),
)

/**
 * 위험 판정 민감도. 행동 감지(규칙모델 점수) 자체는 그대로 두고, 그 점수를 '위험'으로 넘기는 단계만 바꾼다.
 *
 *  ensembleW  : 앙상블에서 학습모델 비중 (나머지가 규칙모델). 앙상블 = w × 학습 + (1 − w) × 규칙
 *  tau        : 앙상블 확률이 이 값 이상이면 '반복 중'으로 보고 지속 시간을 쌓는다
 *  durationS  : 지속 시간이 이만큼 차면 '위험'
 *  decayRate  : 반복이 끊겼을 때 지속 시간이 줄어드는 속도 (1 = 쌓이는 속도와 같음)
 *
 * NORMAL 은 HTML 원본·기준서 값이다 (라벨 기준 4초).
 * 학습모델이 아직 보정되지 않아 점수가 낮게 나오므로, 실기기 시험에서는 SENSITIVE 를 기본으로 쓴다.
 */
enum class Sensitivity(val label: String, val ensembleW: Double, val tau: Double, val durationS: Double, val decayRate: Double) {
    NORMAL("보통 (기준서)", 0.5, 0.5, 4.0, 1.5),
    SENSITIVE("민감", 0.3, 0.45, 2.5, 0.75),
    VERY_SENSITIVE("매우 민감", 0.2, 0.4, 1.5, 0.5),
}

val DEFAULT_SENSITIVITY = Sensitivity.SENSITIVE

const val SIDE_L = "L"
const val SIDE_R = "R"
val SIDE_TXT = mapOf(SIDE_L to "왼", SIDE_R to "오른")
