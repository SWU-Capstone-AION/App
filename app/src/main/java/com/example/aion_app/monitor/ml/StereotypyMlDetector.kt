package com.example.aion_app.monitor.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

// ============================================================
// 상동행동 학습모델 (TFLite) 판정기
// ============================================================
// assets 폴더의 model_*.tflite + model_meta.json 을 읽어서
// 0.5초마다 상동행동 점수(0~1)를 낸다.
//
//   assets/
//    ├─ pose_landmarker_full.task   (MediaPipe, 원래 있던 것)
//    ├─ model_1.tflite              학습모델 (여러 개면 model_2.tflite … 전부 평균)
//    └─ model_meta.json             mean / std / 관절 순서 / 임계값
//
// 알람 규칙 (model_meta.json 값을 그대로 씀)
//   - 점수가 threshold_on(0.5) 이상인 판정이 consecutive(2)번 연속 → 알람 켜짐
//   - 점수가 threshold_off(0.35) 아래로 떨어지면 → 알람 꺼짐
//   (켜는 기준과 끄는 기준을 다르게 둬서 0.5 근처에서 알람이 깜빡이지 않게)
//
// ⚠ update() 는 MediaPipe 결과 스레드에서 불린다. 모델이 23KB 정도로 작아서
//   그 자리에서 바로 돌려도 1~2ms 수준이라 따로 스레드를 만들지 않았다.
// ============================================================
class StereotypyMlDetector private constructor(
    private val interpreters: List<Interpreter>,
    private val builder: MlFeatureBuilder,
    val thresholdOn: Float,
    val thresholdOff: Float,
    val consecutive: Int,
    val modelFiles: List<String>,
) : Closeable {

    data class State(
        /** 마지막 판정 점수 0~1. 아직 판정 전이거나 건너뛴 경우 null */
        val score: Float?,
        /** 알람 켜짐 여부 */
        val alarm: Boolean,
        /** 이번 프레임에서 새 판정이 나왔는지 (0.5초에 한 번 true) */
        val updated: Boolean,
        /** 건너뛴 이유 (사람이 잘 안 잡힘 등). 정상 판정이면 null */
        val skipReason: String? = null,
    )

    private val inputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(builder.window * builder.channels * 4)
        .order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(1) }

    private var lastScore: Float? = null
    private var alarm = false
    private var overCount = 0
    private var closed = false

    /**
     * 카메라 프레임 1장의 MediaPipe 결과를 넣는다.
     * @param landmarks 첫 번째 사람의 33관절. 사람이 안 잡혔으면 null
     */
    @Synchronized
    fun update(timestampMs: Long, landmarks: List<NormalizedLandmark>?): State {
        if (closed) return State(null, false, false)

        val points = landmarks?.takeIf { it.size >= 33 }?.let { lm ->
            FloatArray(66).also { p ->
                for (i in 0 until 33) {
                    p[i * 2] = lm[i].x()
                    p[i * 2 + 1] = lm[i].y()
                }
            }
        }

        var updated = false
        var skip: String? = null
        for (o in builder.addFrame(timestampMs, points)) {
            updated = true
            when (o) {
                is MlFeatureBuilder.Output.Ready -> {
                    skip = null
                    val score = infer(o.input)
                    lastScore = score
                    applyAlarm(score)
                }
                is MlFeatureBuilder.Output.Skipped -> {
                    // 학습 때도 버렸을 조각 → 판정 안 함. 알람 상태는 그대로 두지 않고 연속 횟수만 끊는다
                    skip = o.reason
                    lastScore = null
                    overCount = 0
                }
            }
        }
        return State(lastScore, alarm, updated, skip)
    }

    /** 모델 여러 개를 돌려 평균 */
    private fun infer(input: FloatArray): Float {
        val t0 = SystemClock.elapsedRealtime()
        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().put(input)
        var sum = 0f
        for (itp in interpreters) {
            inputBuffer.rewind()
            itp.run(inputBuffer, output)
            sum += output[0][0]
        }
        val score = sum / interpreters.size
        if (DEBUG_LOG) {
            Log.d(TAG, "점수 %.3f  (모델 %d개, %dms)".format(
                score, interpreters.size, SystemClock.elapsedRealtime() - t0))
        }
        return score
    }

    private fun applyAlarm(score: Float) {
        if (score >= thresholdOn) overCount++ else overCount = 0
        if (!alarm && overCount >= consecutive) alarm = true
        if (alarm && score < thresholdOff) alarm = false
    }

    /** 감지를 멈췄다 다시 켤 때 (미니게임, 화면 이동) */
    @Synchronized
    fun reset() {
        builder.reset()
        lastScore = null
        alarm = false
        overCount = 0
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        interpreters.forEach { it.close() }
    }

    companion object {
        private const val TAG = "StereotypyMl"
        private const val META_FILE = "model_meta.json"
        private val MODEL_FILE = Regex("""model(_\d+)?\.tflite""")

        /** true 면 판정마다 Logcat 에 점수를 찍는다 (Logcat 에서 StereotypyMl 검색) */
        var DEBUG_LOG = true

        /**
         * assets 에서 모델과 meta 를 읽어 만든다.
         * 파일이 없거나 모양이 안 맞으면 null (앱은 죽지 않고 학습모델만 꺼진다)
         */
        fun createOrNull(context: Context): StereotypyMlDetector? = try {
            create(context)
        } catch (t: Throwable) {
            Log.e(TAG, "학습모델 불러오기 실패 → 학습모델 판정 꺼짐: ${t.message}", t)
            null
        }

        fun create(context: Context): StereotypyMlDetector {
            val assets = context.assets
            val files = (assets.list("") ?: emptyArray())
                .filter { MODEL_FILE.matches(it) }
                .sorted()
            require(files.isNotEmpty()) { "assets 에 model_1.tflite 같은 파일이 없음" }

            val meta = JSONObject(assets.open(META_FILE).bufferedReader().use { it.readText() })
            val window = meta.optInt("window", 60)
            val nCh = meta.getInt("n_channels")
            val columns = meta.getJSONArray("coordinate_columns").toStringList()
            val mean = meta.getJSONArray("mean").toFloatArray()
            val std = meta.getJSONArray("std").toFloatArray()
            val clip = if (meta.isNull("clip_abs")) null
                       else meta.getJSONArray("clip_abs").toFloatArray()

            // 학습 설정이 앱이 아는 방식(속도만 사용)과 같은지 확인
            require(meta.optBoolean("use_velocity", true) && !meta.optBoolean("use_raw", false)) {
                "use_raw=True 모델은 아직 앱에서 지원 안 함"
            }
            require(columns.size == nCh) { "coordinate_columns ${columns.size}개 ≠ n_channels $nCh" }

            val builder = MlFeatureBuilder.fromColumns(
                columns, mean, std, clip, window = window, stride = STRIDE,
            )

            val opts = Interpreter.Options().setNumThreads(2)
            val interpreters = files.map { name ->
                Interpreter(loadModelFile(context, name), opts).also { itp ->
                    val inShape = itp.getInputTensor(0).shape()
                    val outShape = itp.getOutputTensor(0).shape()
                    require(inShape.contentEquals(intArrayOf(1, window, nCh))) {
                        "$name 입력 모양 ${inShape.contentToString()} ≠ [1, $window, $nCh]"
                    }
                    require(outShape.contentEquals(intArrayOf(1, 1))) {
                        "$name 출력 모양 ${outShape.contentToString()} ≠ [1, 1]"
                    }
                }
            }

            Log.i(TAG, "학습모델 준비 완료: ${files.joinToString()} / " +
                    "입력 [$window, $nCh] / 검증 F1 ${meta.optDouble("val_f1")}")

            return StereotypyMlDetector(
                interpreters = interpreters,
                builder = builder,
                thresholdOn = meta.optDouble("threshold_on", 0.5).toFloat(),
                thresholdOff = meta.optDouble("threshold_off", 0.35).toFloat(),
                consecutive = meta.optInt("consecutive", 2),
                modelFiles = files,
            )
        }

        /** 학습 때 STRIDE 15 = 0.5초마다 판정 */
        private const val STRIDE = 15

        private fun loadModelFile(context: Context, name: String): MappedByteBuffer {
            context.assets.openFd(name).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    return stream.channel.map(
                        FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength,
                    )
                }
            }
        }

        private fun JSONArray.toFloatArray() = FloatArray(length()) { getDouble(it).toFloat() }
        private fun JSONArray.toStringList() = List(length()) { getString(it) }
    }
}
