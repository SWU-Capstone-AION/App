package com.example.aion_app.monitor.rule

import com.example.aion_app.monitor.rule.RuleMath.logit
import com.example.aion_app.monitor.rule.RuleMath.sigmoid
import kotlin.math.max
import kotlin.math.min

// ============================================================
// 소프트 보팅: 규칙모델 확률 + 학습모델 확률 → 가중 평균 → 4초 지속 → 단계
// ============================================================
//   p = w × p_학습 + (1 − w) × p_규칙      (한쪽이 판단 불가면 다른 쪽만)
//   p ≥ τ 인 상태가 4초 이어지면 D = 1 → 위험(2)
//   p ≥ τ 이거나 D ≥ 0.5 → 주의(1)
//
// 규칙모델 점수 r 은 설계 점수라서 그대로는 확률이 아니다.
// 검증 데이터로 만든 보정값(ensemble_config.json 의 rule_calibration)을 setCalibration 으로 넣어야
// "상동행동일 확률"이 된다. 넣기 전에는 r 을 그대로 쓴다(미보정).
// ============================================================
class SoftVoting {

    /** 확률 보정 방식 (HTML 대시보드 applyCalibration 과 같음) */
    sealed class Calibration {
        abstract fun apply(x: Double, feats: Map<String, Double>?): Double

        /** p = σ(a · logit(x) + b) */
        data class Platt(val a: Double, val b: Double) : Calibration() {
            override fun apply(x: Double, feats: Map<String, Double>?) = sigmoid(a * logit(x) + b)
        }
        /** p = σ(logit(x) / T) — 학습모델 보정에 주로 씀 */
        data class Temperature(val t: Double) : Calibration() {
            override fun apply(x: Double, feats: Map<String, Double>?) = sigmoid(logit(x) / (if (t > 0) t else 1.0))
        }
        /** 구간별 표(x → y)를 직선으로 이음 */
        data class Isotonic(val xs: DoubleArray, val ys: DoubleArray) : Calibration() {
            override fun apply(x: Double, feats: Map<String, Double>?): Double {
                if (x <= xs[0]) return ys[0]
                for (i in 1 until xs.size) {
                    if (x <= xs[i]) {
                        val w = xs[i] - xs[i - 1]
                        val u = (x - xs[i - 1]) / (if (w != 0.0) w else 1.0)
                        return ys[i - 1] + (ys[i] - ys[i - 1]) * u
                    }
                }
                return ys.last()
            }
        }
        /** p = σ(bias + Σ coef · 특징) — 특징: r, amp, reg, int, cdev(logit), cover, touch, g_<묶음> */
        data class Logistic(val bias: Double, val coef: Map<String, Double>) : Calibration() {
            override fun apply(x: Double, feats: Map<String, Double>?): Double {
                var z = bias
                for ((k, c) in coef) z += c * (feats?.get(k)?.takeIf { it.isFinite() } ?: 0.0)
                return sigmoid(z)
            }
        }
    }

    data class Result(
        /** 앙상블 확률. 두 모델 다 판단 불가면 null */
        val p: Double?,
        val pRule: Double?,
        val pDl: Double?,
        val ruleCalibrated: Boolean,
        /** "ensemble" / "rule" / "dl" / "none" */
        val mode: String,
        /** 지속 점수 0~1 (1 = 4초) */
        val d: Double,
        /** 0 안정 / 1 주의 / 2 위험 */
        val stage: Int,
        /** 이번 판정에서 위험이 새로 시작됐는지 */
        val newEpisode: Boolean,
        /** 알림을 보내야 하는지 (30초 간격 / 3회 누적 규칙 적용) */
        val fireAlert: Boolean,
        /** 최근 10분 위험 발생 횟수 */
        val recentEpisodes: Int,
    )

    @Volatile var ruleCalibration: Calibration? = null
    @Volatile var dlCalibration: Calibration? = null
    /** true 면 3회 누적 시 알림, false 면 위험 발생마다 알림(30초 간격) */
    @Volatile var countMode = false

    private var accDur = 0.0
    private var lastTickT: Double? = null
    private var inEpisode = false
    private var belowSince: Double? = null
    private val episodes = ArrayDeque<Double>()
    private var lastSentT = -1e9

    fun reset() {
        accDur = 0.0; lastTickT = null; inEpisode = false; belowSince = null
        episodes.clear(); lastSentT = -1e9
    }

    /** 보정에 쓰는 규칙모델 특징 */
    fun ruleFeatures(tick: RuleModelV3.Tick): Map<String, Double>? {
        val r = tick.r ?: return null
        val d = tick.dominantEvidence ?: return null
        val m = hashMapOf(
            "r" to logit(r), "amp" to logit(d.amp), "reg" to logit(d.reg), "int" to logit(d.int),
            "cdev" to logit(d.cdev), "cover" to d.cover, "touch" to tick.touch.f,
        )
        val g = BEHAVIOR_BY_KEY.getValue(d.key).group
        for (grp in BEHAVIOR_GROUPS) m["g_$grp"] = if (grp == g) 1.0 else 0.0
        return m
    }

    /**
     * 0.5초마다 한 번 부른다.
     * @param tick 규칙모델 판정 (RuleModelV3.update 의 tick)
     * @param pDlRaw 학습모델 점수 0~1 (최근 1.5초 안의 값). 없으면 null
     */
    fun fuse(tick: RuleModelV3.Tick, pDlRaw: Double?): Result {
        val rc = ruleCalibration
        val pRule = tick.r?.let { r -> rc?.apply(r, ruleFeatures(tick)) ?: r }
        val pDl = pDlRaw?.let { x -> dlCalibration?.apply(x, mapOf("p" to logit(x))) ?: x }
        val w = RuleConfig.ensembleW
        val tau = RuleConfig.decision
        val (p, mode) = when {
            pRule != null && pDl != null -> (w * pDl + (1 - w) * pRule) to "ensemble"
            pDl != null -> pDl to "dl"
            pRule != null -> pRule to "rule"
            else -> null to "none"
        }
        val t = tick.tSec
        var dt = 0.0
        val lt = lastTickT
        if (lt == null || t > lt) {
            dt = if (lt == null) 0.0 else min(RuleConfig.STRIDE_S * 3, t - lt)
            lastTickT = t
        }
        val x = if (p == null) 0.0 else sigmoid((p - tau) / RuleConfig.DUR_SOFTNESS)
        accDur = (accDur + dt * (x - (1 - x) * RuleConfig.DECAY_RATE)).coerceIn(0.0, RuleConfig.DURATION_S)
        val d = accDur / RuleConfig.DURATION_S
        val stage = if (d >= RuleConfig.ALERT_ENTER) 2 else if ((p != null && p >= tau) || d >= 0.5) 1 else 0

        // 위험 '발생 1회' = D 가 1에 닿았다가 0.5 아래로 내려와 5초 이상 머물기까지
        var newEpisode = false
        if (!inEpisode && d >= RuleConfig.ALERT_ENTER) {
            inEpisode = true; newEpisode = true; episodes.addLast(t)
        } else if (inEpisode) {
            if (d < RuleConfig.ALERT_EXIT) {
                val bs = belowSince ?: t.also { belowSince = it }
                if (t - bs >= RuleConfig.ALERT_MERGE_GAP_S) inEpisode = false
            } else belowSince = null
        }
        if (!inEpisode) belowSince = null
        while (episodes.isNotEmpty() && episodes.first() < t - RuleConfig.ALERT_COUNT_WINDOW_S) episodes.removeFirst()
        val recent = episodes.size
        var fire = false
        if (newEpisode) {
            fire = if (countMode) recent >= RuleConfig.ALERT_COUNT_NEED && recent % RuleConfig.ALERT_COUNT_NEED == 0
                   else t - lastSentT >= RuleConfig.ALERT_COOLDOWN_S
        }
        if (fire) lastSentT = t
        return Result(p, pRule, pDl, rc != null, mode, d, stage, newEpisode, fire, recent)
    }

    companion object {
        /**
         * ensemble_config.json 내용을 읽어 보정값·가중치·기준값을 넣는다.
         *  {"w":0.6,"decision":0.45,"touch_penalty":0.8,
         *   "rule_calibration":{"type":"logistic","bias":-2.1,"coef":{"r":0.9,"reg":0.6}},
         *   "dl_calibration":{"type":"temperature","T":1.4}}
         * org.json 없이 쓰려고 이미 꺼낸 값(Map)을 받는다. 안드로이드에서는 SoftVotingConfig.load 를 쓸 것.
         */
        fun calibrationOf(m: Map<String, Any?>?): Calibration? {
            if (m == null) return null
            fun d(k: String) = (m[k] as? Number)?.toDouble()
            return when (m["type"]) {
                "platt" -> Calibration.Platt(d("a") ?: return null, d("b") ?: return null)
                "temperature" -> Calibration.Temperature(d("T") ?: return null)
                "isotonic" -> {
                    val xs = (m["x"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: return null
                    val ys = (m["y"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: return null
                    if (xs.size != ys.size || xs.size < 2) null else Calibration.Isotonic(xs.toDoubleArray(), ys.toDoubleArray())
                }
                "logistic" -> Calibration.Logistic(
                    d("bias") ?: 0.0,
                    (m["coef"] as? Map<*, *>)?.entries?.mapNotNull { (k, v) -> (v as? Number)?.let { k.toString() to it.toDouble() } }?.toMap() ?: return null,
                )
                else -> null
            }
        }

        fun clampW(v: Double) = max(0.0, min(1.0, v))
    }
}
