package com.example.aion_app.monitor.rule

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * assets/rule_v3_config.json 이 있으면 읽어서 엔진에 적용한다 (없으면 기본값 그대로).
 * HTML 대시보드의 '설정 불러오기'와 같은 형식이다.
 *
 * {
 *   "sensitivity": "SENSITIVE",  NORMAL / SENSITIVE / VERY_SENSITIVE (먼저 적용, 아래 값이 있으면 덮어씀)
 *   "duration_s": 2.5,        위험까지 필요한 지속 시간(초)
 *   "decay_rate": 0.75,       반복이 끊겼을 때 지속 시간이 줄어드는 속도
 *   "confident": 0.85,        한 모델이 이 값 이상이면 그 점수를 그대로 씀 (null 이면 안 씀)
 *   "w": 0.5,                 앙상블 가중치 (학습모델 쪽)
 *   "decision": 0.5,          판단 기준 τ
 *   "touch_penalty": 0.8,     터치 중 점수 낮추는 정도
 *   "rule_calibration": { "type": "platt", "a": 1.2, "b": -0.3 },
 *   "dl_calibration":   { "type": "temperature", "T": 1.5 }
 * }
 * 보정 형식: platt(a, b) / temperature(T) / isotonic(x[], y[]) / logistic(bias, coef{})
 */
object RuleV3ConfigLoader {
    private const val ASSET = "rule_v3_config.json"

    fun applyFromAssets(context: Context, engine: RuleV3Engine) {
        val text = try {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return   // 파일 없음 → 기본값
        }
        try {
            val o = JSONObject(text)
            engine.ruleCal = parseCal(o.optJSONObject("rule_calibration"))
            engine.dlCal = parseCal(o.optJSONObject("dl_calibration"))
            if (o.has("sensitivity")) engine.applySensitivity(Sensitivity.valueOf(o.getString("sensitivity")))
            if (listOf("duration_s", "decay_rate", "w", "decision", "confident").any { o.has(it) }) engine.sensitivity = null
            if (o.has("confident")) engine.confidentLevel = if (o.isNull("confident")) null else clamp(o.getDouble("confident"), 0.5, 1.0)
            if (o.has("duration_s")) engine.durationS = clamp(o.getDouble("duration_s"), 0.5, 10.0)
            if (o.has("decay_rate")) engine.decayRate = clamp(o.getDouble("decay_rate"), 0.0, 5.0)
            if (o.has("w")) engine.ensembleW = clamp(o.getDouble("w"), 0.0, 1.0)
            if (o.has("decision")) engine.decisionTau = clamp(o.getDouble("decision"), 0.05, 0.95)
            if (o.has("touch_penalty")) engine.touchPenalty = clamp(o.getDouble("touch_penalty"), 0.0, 1.0)
        } catch (e: Exception) {
            Log.w("RuleV3Config", "설정 오류 — 기본값 사용: ${e.message}")
        }
    }

    private fun parseCal(c: JSONObject?): Calibration? {
        if (c == null) return null
        return when (c.optString("type")) {
            "platt" -> Calibration.Platt(c.getDouble("a"), c.getDouble("b"))
            "temperature" -> Calibration.Temperature(c.getDouble("T").takeIf { it > 0 } ?: 1.0)
            "isotonic" -> {
                val xs = c.getJSONArray("x"); val ys = c.getJSONArray("y")
                require(xs.length() == ys.length() && xs.length() >= 2) { "isotonic x/y 길이" }
                Calibration.Isotonic(List(xs.length()) { xs.getDouble(it) }, List(ys.length()) { ys.getDouble(it) })
            }
            "logistic" -> {
                val coef = c.getJSONObject("coef")
                Calibration.Logistic(c.optDouble("bias", 0.0), coef.keys().asSequence().associateWith { coef.getDouble(it) })
            }
            else -> throw IllegalArgumentException("알 수 없는 보정 형식: ${c.optString("type")}")
        }
    }
}
