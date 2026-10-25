package com.example.aion_app.monitor.rule

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import org.json.JSONArray
import org.json.JSONObject

// ============================================================
// 규칙모델 v3 ↔ 안드로이드 연결 도구
//   - MediaPipe 결과를 RuleModelV3 입력으로 바꾸기
//   - assets/ensemble_config.json 읽기 (보정값·가중치·기준값)
// 계산 자체는 RuleModelV3 / SoftVoting (순수 Kotlin) 에 있다.
// ============================================================
object RuleV3Android {
    private const val TAG = "RuleV3"
    const val CONFIG_ASSET = "ensemble_config.json"

    /** true 면 0.5초마다 Logcat 에 판정을 찍는다 (Logcat 에서 RuleV3 검색) */
    @Volatile var DEBUG_LOG = true

    fun poseOf(result: PoseLandmarkerResult): List<RuleModelV3.Landmark>? =
        result.landmarks().firstOrNull()?.takeIf { it.size >= 33 }?.map {
            RuleModelV3.Landmark(it.x().toDouble(), it.y().toDouble(), it.z().toDouble(), it.visibility().orElse(1f).toDouble())
        }

    fun worldOf(result: PoseLandmarkerResult): List<RuleModelV3.Landmark>? =
        result.worldLandmarks().firstOrNull()?.takeIf { it.size >= 33 }?.map {
            RuleModelV3.Landmark(it.x().toDouble(), it.y().toDouble(), it.z().toDouble())
        }

    fun handsOf(result: HandLandmarkerResult?): List<RuleModelV3.HandInput> {
        if (result == null) return emptyList()
        val lms = result.landmarks()
        val world = result.worldLandmarks()
        val handed = result.handedness()
        return lms.indices.map { i ->
            RuleModelV3.HandInput(
                landmarks = lms[i].map { RuleModelV3.Landmark(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) },
                world = world.getOrNull(i)?.map { RuleModelV3.Landmark(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) },
                score = handed.getOrNull(i)?.firstOrNull()?.score()?.toDouble() ?: 0.8,
            )
        }
    }

    /**
     * assets/ensemble_config.json 이 있으면 읽어서 SoftVoting 과 RuleConfig 에 넣는다.
     * 없으면 기본값(w = 0.5, τ = 0.5, 규칙모델 미보정) 그대로.
     * 형식은 HTML 대시보드 '앙상블 설정 불러오기'와 같다.
     */
    fun loadConfig(context: Context, voting: SoftVoting): Boolean {
        val text = runCatching { context.assets.open(CONFIG_ASSET).bufferedReader().use { it.readText() } }.getOrNull()
            ?: return false
        return try {
            val o = JSONObject(text)
            if (o.has("w")) RuleConfig.ensembleW = o.getDouble("w").coerceIn(0.0, 1.0)
            if (o.has("decision")) RuleConfig.decision = o.getDouble("decision").coerceIn(0.05, 0.95)
            if (o.has("touch_penalty")) RuleConfig.touchPenalty = o.getDouble("touch_penalty").coerceIn(0.0, 1.0)
            voting.ruleCalibration = SoftVoting.calibrationOf(o.optJSONObject("rule_calibration")?.toMap())
            voting.dlCalibration = SoftVoting.calibrationOf(o.optJSONObject("dl_calibration")?.toMap())
            voting.countMode = o.optString("alert_mode") == "count3"
            Log.i(TAG, "앙상블 설정: w=${RuleConfig.ensembleW} τ=${RuleConfig.decision} 규칙보정=${voting.ruleCalibration != null}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "앙상블 설정 읽기 실패: ${e.message}")
            false
        }
    }

    fun log(tick: RuleModelV3.Tick, fused: SoftVoting.Result) {
        if (!DEBUG_LOG) return
        val top = tick.evidence.values.sortedByDescending { it.score }.take(3).joinToString {
            "${it.label}${it.side?.let { s -> "($s)" } ?: ""}=%.2f".format(it.score)
        }
        Log.d(TAG, "t=%.1f r=%s p=%s D=%.2f 단계=%d 터치=%.2f | %s".format(
            tick.tSec, tick.r?.let { "%.2f".format(it) }, fused.p?.let { "%.2f".format(it) },
            fused.d, fused.stage, tick.touch.f, top))
    }

    private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { k -> unwrap(get(k)) }
    private fun JSONArray.toList(): List<Any?> = List(length()) { unwrap(get(it)) }
    private fun unwrap(v: Any?): Any? = when (v) {
        is JSONObject -> v.toMap()
        is JSONArray -> v.toList()
        JSONObject.NULL -> null
        else -> v
    }
}
