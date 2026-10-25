package com.example.aion_app.monitor.rule

import kotlin.math.max

// ============================================================
// 규칙모델 v3 설정값과 행동 17가지 정의
// 값의 근거와 뜻은 「AION 상동행동 감지 기준서 (규칙모델 v3)」 문서와 같다.
// 숫자를 바꿀 때는 문서도 같이 고칠 것.
// ============================================================
object RuleConfig {
    const val FPS = 30
    const val WINDOW_S = 2.0          // 판정 구간 (학습모델 60프레임과 같음)
    const val STRIDE_S = 0.5          // 판정 간격
    const val ANALYSIS_HZ = 15.0      // 리듬 분석을 초당 몇 번 할지 (계산량 조절)
    const val MIN_CYCLES = 3          // 같은 동작 최소 3번 연속 (Watt et al. 2008)
    const val DURATION_S = 4.0        // 앙상블 확률 ≥ τ 가 4초 이어지면 위험 (라벨 기준)
    const val DECAY_RATE = 1.5
    const val DUR_SOFTNESS = 0.05
    const val CENTER_WIN_MIN_S = 3.0
    const val CENTER_DEV_MAX = 0.35   // 어깨 너비(SW) 단위
    const val VISIBILITY_MIN = 0.5
    const val LOOSE_VIS_MIN = 0.2
    const val HAND_SCORE_MIN = 0.5    // 손 모델 신뢰도
    const val EDGE_MARGIN = 0.02
    const val SCALE_WINDOW_S = 3.0
    const val SCALE_HOLD_S = 1.0
    const val SCALE_CARRY_S = 3.0
    const val REF_KEEP_S = 10.0
    const val DT_CAP_S = 0.25
    const val KEEP_S = 16.0
    const val REG_MIN = 0.5
    const val REG_PROMINENCE = 0.3
    const val ACF_HZ = 30.0
    const val MIN_CHANNEL_VALID = 0.7
    const val FLOOR_KEEP_S = 60.0
    const val FLOOR_MULT = 2.5
    const val SOFT_AMP = 0.12
    const val SOFT_INT = 0.10
    const val SOFT_CDEV = 0.20
    const val SOFT_REG = 0.15
    const val SOFT_GATE = 0.15
    const val HOLD_S = 1.5            // 자세 유지형: 1.5초 유지 시 점수 0.5
    const val HOLD_GAP_S = 0.3
    const val PART_COVER_MIN = 0.5
    const val LOW_FPS = 20            // 이보다 느리면 손가락 행동 정확도 낮음

    // 기능적 행동 (태블릿 터치)
    const val TOUCH_TAIL_MS = 400L
    const val TAP_MATCH_TOL = 0.25
    /** 점수 × (1 − touchPenalty × f). 검증 데이터로 바꿀 수 있게 var */
    @Volatile var touchPenalty = 0.8

    // 소프트 보팅 기본값 (검증 데이터로 정한 값으로 바꿀 것)
    @Volatile var ensembleW = 0.5     // 학습모델 비중
    @Volatile var decision = 0.5      // 판정 기준값 τ

    // 알림
    const val ALERT_ENTER = 0.999
    const val ALERT_EXIT = 0.5
    const val ALERT_MERGE_GAP_S = 5.0
    const val ALERT_COOLDOWN_S = 30.0
    const val ALERT_COUNT_WINDOW_S = 600.0
    const val ALERT_COUNT_NEED = 3
}

enum class BehaviorType { RHYTHM, CIRCLE, HOLD }
enum class VisSource { TRUNK, HEAD, HIPS, HAND, HAND_MODEL, BOTH_HANDS, LOOSE }

data class Channel(val key: String, val label: String, val ampMin: Double)

data class Behavior(
    val key: String,
    val label: String,
    val group: String,
    val type: BehaviorType,
    val vis: VisSource,
    val iMin: Double = 0.0,
    val iMax: Double = 0.0,
    val ch: List<Channel> = emptyList(),
    val sides: Boolean = false,
    val gate: String? = null,        // 프레임마다 보는 위치 조건 (특징 이름 앞부분)
    val winGate: String? = null,     // 구간 전체로 보는 조건
    val aux: List<String> = emptyList(),
    val functional: Boolean = false, // 태블릿 터치 중 점수를 낮출 행동
    val fingerType: Boolean = false,
    val needAll: Boolean = false,
    val experimental: Boolean = false,
)

private fun ch(key: String, label: String, amp: Double) = Channel(key, label, amp)

val BEHAVIORS: List<Behavior> = listOf(
    // 전신
    Behavior("body_rock", "몸 앞뒤 흔들기", "전신", BehaviorType.RHYTHM, VisSource.TRUNK, 0.4, 2.5,
        listOf(ch("trunk_sw", "어깨 너비 변화", 0.08), ch("trunk_face", "얼굴 폭 변화", 0.08), ch("trunk_zlean", "3D 앞뒤 기울기", 0.04))),
    Behavior("body_sway", "몸 좌우 흔들기", "전신", BehaviorType.RHYTHM, VisSource.TRUNK, 0.4, 2.5,
        listOf(ch("trunk_x", "어깨 좌우", 0.08), ch("trunk_tilt", "어깨선 기울기", 0.10))),
    // 머리
    Behavior("head_roll", "머리 굴리기", "머리", BehaviorType.CIRCLE, VisSource.HEAD, 0.5, 3.0,
        listOf(ch("head_x", "코 좌우", 0.10), ch("head_y", "코 상하", 0.10))),
    Behavior("head_nod", "끄덕이기", "머리", BehaviorType.RHYTHM, VisSource.HEAD, 0.25, 1.5,
        listOf(ch("head_pitch", "코-귀 높이차", 0.15))),
    Behavior("head_tilt", "갸웃거리기", "머리", BehaviorType.RHYTHM, VisSource.HEAD, 0.4, 2.0,
        listOf(ch("head_roll", "귀 선 기울기", 0.15))),
    Behavior("head_turn", "고개 돌리기", "머리", BehaviorType.RHYTHM, VisSource.HEAD, 0.3, 2.0,
        listOf(ch("head_yaw", "고개 돌림", 0.25))),
    // 어깨
    Behavior("shrug", "으쓱하기", "어깨", BehaviorType.RHYTHM, VisSource.TRUNK, 0.4, 2.5,
        listOf(ch("shrug_mid", "양 어깨", 0.06), ch("shrug_l", "왼 어깨", 0.06), ch("shrug_r", "오른 어깨", 0.06)),
        winGate = "shrug", aux = listOf("ear_y", "sh_y")),
    Behavior("arch", "등 젖히기", "어깨", BehaviorType.RHYTHM, VisSource.HIPS, 0.6, 4.0,
        listOf(ch("trunk_zlean", "3D 뒤로 기울기", 0.04), ch("head_pitch", "고개 젖힘", 0.15)),
        needAll = true, experimental = true),
    // 손·손가락
    Behavior("flap", "손·팔 퍼덕이기", "손·손가락", BehaviorType.RHYTHM, VisSource.HAND, 0.15, 1.0,
        listOf(ch("hand_y", "손 상하", 0.15), ch("elbow_y", "팔꿈치 상하", 0.12), ch("wflex", "손목 꺾임", 0.5)),
        sides = true, functional = true),
    Behavior("finger", "손가락 꼼지락·튕기기", "손·손가락", BehaviorType.RHYTHM, VisSource.HAND_MODEL, 0.12, 0.8,
        listOf(ch("curl0", "엄지 굽힘", 0.35), ch("curl1", "검지 굽힘", 0.35), ch("curl2", "중지 굽힘", 0.35),
            ch("curl3", "약지 굽힘", 0.35), ch("curl4", "새끼 굽힘", 0.35), ch("curlm", "평균 굽힘", 0.35)),
        sides = true, functional = true, fingerType = true),
    Behavior("flutter", "얼굴 앞 손가락 팔랑이기", "손·손가락", BehaviorType.RHYTHM, VisSource.HAND_MODEL, 0.12, 0.8,
        listOf(ch("curl1", "검지 굽힘", 0.35), ch("curl2", "중지 굽힘", 0.35), ch("curl3", "약지 굽힘", 0.35),
            ch("curl4", "새끼 굽힘", 0.35), ch("curlm", "평균 굽힘", 0.35)),
        sides = true, gate = "g_flutter"),
    Behavior("clap", "박수", "손·손가락", BehaviorType.RHYTHM, VisSource.BOTH_HANDS, 0.2, 1.2,
        listOf(ch("hands_d", "양손 거리", 0.25)), winGate = "clap"),
    Behavior("wave", "손·팔 흔들기", "손·손가락", BehaviorType.RHYTHM, VisSource.HAND, 0.2, 1.2,
        listOf(ch("hand_x", "손 좌우", 0.15), ch("fore_x", "아래팔 좌우", 0.12), ch("palm_open", "손바닥 회전", 0.4)),
        sides = true, functional = true, winGate = "notclap", aux = listOf("hands_d")),
    // 감각
    Behavior("eye_cover", "눈 가리기", "감각", BehaviorType.HOLD, VisSource.HAND,
        sides = true, gate = "g_eyecover", aux = listOf("hand_x", "hand_y", "curlm")),
    Behavior("ear_cover", "귀 막기", "감각", BehaviorType.HOLD, VisSource.HAND,
        sides = true, gate = "g_earcover", aux = listOf("hand_x", "hand_y", "curlm")),
    Behavior("eye_rub", "눈 비비기", "감각", BehaviorType.RHYTHM, VisSource.HAND_MODEL, 0.2, 1.0,
        listOf(ch("eyetip_x", "손끝 좌우", 0.08), ch("eyetip_y", "손끝 상하", 0.08)),
        sides = true, winGate = "eyerub", aux = listOf("eyetip_d", "curlm")),
    Behavior("hit", "자기 때리기", "감각", BehaviorType.RHYTHM, VisSource.HAND, 0.25, 1.5,
        listOf(ch("hit_d", "몸까지 거리", 0.25)), sides = true, winGate = "hit"),
    // 전신 큰 움직임 (실험 — 앉은 자세에서는 드묾)
    Behavior("jump", "점프·바운스", "전신(실험)", BehaviorType.RHYTHM, VisSource.LOOSE, 0.3, 2.0,
        listOf(ch("whole_y", "큰 상하", 0.35)), experimental = true),
    Behavior("spin", "회전", "전신(실험)", BehaviorType.RHYTHM, VisSource.LOOSE, 0.5, 3.0,
        listOf(ch("whole_rot", "몸 방향", 1.0)), experimental = true),
)
val BEHAVIOR_BY_KEY: Map<String, Behavior> = BEHAVIORS.associateBy { it.key }
val BEHAVIOR_GROUPS: List<String> = BEHAVIORS.map { it.group }.distinct()

/** 더 구체적인 행동이 확실하면(≥ 0.5) 같은 손의 일반 동작 점수를 30%로 낮춤 */
internal val SPECIFIC_OVER: Map<String, List<String>> = linkedMapOf(
    "hit" to listOf("flap", "wave", "flutter"),
    "clap" to listOf("flap", "wave"),
    "flutter" to listOf("finger", "flap", "wave", "eye_rub"),
    "eye_rub" to listOf("eye_cover", "finger"),
    "shrug" to listOf("flap"),
)
internal const val SUPPRESS = 0.3

/** 양손 공통 특징 (_L/_R 를 붙이지 않음) */
private val SHARED_KEYS = setOf("hands_d", "ear_y", "sh_y")

/** 행동 × 좌우 = 실제로 계산하는 단위 */
internal class Instance(val beh: Behavior, val side: Char?) {
    private fun sfx(k: String) = if (side != null) "${k}_$side" else k
    val id = sfx(beh.key)
    val chKeys = beh.ch.map { sfx(it.key) }
    val auxKeys = beh.aux.map { if (it in SHARED_KEYS) it else sfx(it) }
    val gateKey = beh.gate?.let { sfx(it) }
    val localWindowS = if (beh.type == BehaviorType.HOLD) RuleConfig.WINDOW_S else (RuleConfig.MIN_CYCLES + 0.5) * beh.iMax
    val cdevWindowS = max(RuleConfig.CENTER_WIN_MIN_S, 3 * (if (beh.iMax > 0) beh.iMax else 1.0))
}

internal val INSTANCES: List<Instance> = BEHAVIORS.flatMap { b ->
    if (b.sides) listOf(Instance(b, 'L'), Instance(b, 'R')) else listOf(Instance(b, null))
}
internal val INSTANCE_BY_ID: Map<String, Instance> = INSTANCES.associateBy { it.id }
