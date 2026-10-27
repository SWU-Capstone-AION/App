package com.example.aion_app.monitor.rule

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
// 규칙모델 v3 판정 기록 (0.5초마다 한 줄 → CSV)
// ============================================================
// HTML 대시보드의 '기록 CSV' 와 같은 칸에 label 칸을 더했다.
// 시험하는 사람이 화면의 '라벨' 버튼으로 "지금 상동행동 중"을 표시하면 label = 1 로 남는다.
// 이 파일을 tools/rule_v3_tune.py 에 넣으면 w·τ 를 골라서 rule_v3_config.json 을 만들어 준다.
//
// v3.5: 관절 좌표도 프레임마다 따로 남긴다 → aion_v3_날짜_시각_landmarks.csv
//   칸은 PC 의 extract_landmarks.py 와 같다 (포즈 33 + 3D + 손 21×2, 픽셀 좌표) + label, hand_fresh.
//   그래서 규칙을 고친 뒤 이 파일로 PC 에서 같은 동작을 다시 계산해 볼 수 있다 (tools/offline/OfflineEval.kt).
//
// 저장 위치: /sdcard/Android/data/com.example.aion_app/files/records/aion_v3_날짜_시각.csv
//   (안드로이드 스튜디오 → View → Tool Windows → Device Explorer 에서 꺼낼 수 있음)
//
// ⚠ p_rule / p_dl 은 보정 전(raw) 값도 같이 남긴다. 보정·가중치를 바꿔 다시 계산하려면 raw 값이 필요하다.
// ============================================================
class RuleV3Recorder {

    /** 시험하는 사람이 표시하는 정답. 1 = 지금 상동행동 중 */
    @Volatile var label: Int = 0

    @Volatile var recording: Boolean = false
        private set

    private val rows = ArrayList<String>()
    private val lmRows = ArrayList<String>()
    private var startedAt = 0L
    private var firstFrameMs = -1L
    private var frameNo = 0

    val rowCount: Int @Synchronized get() = rows.size

    @Synchronized
    fun start() {
        rows.clear(); lmRows.clear()
        firstFrameMs = -1L; frameNo = 0
        startedAt = System.currentTimeMillis()
        recording = true
    }

    fun stop() { recording = false }

    /** 0.5초 판정이 나올 때마다 부른다 (out.ticked == true 일 때) */
    @Synchronized
    fun add(out: RuleV3Output, fps: Int) {
        if (!recording) return
        val t = out.tick ?: return
        val ev = t.ev
        val dom = ev.dominant
        val cols = ArrayList<String>(HEADER.size)
        cols += f(t.t, 2)
        cols += TIME_FMT.format(Date())
        cols += label.toString()
        cols += f(ev.r)
        cols += dom ?: ""
        cols += dom?.let { ev.beh[it]?.side } ?: ""
        cols += f(t.pRuleRaw)
        cols += f(t.pRule)
        cols += if (t.ruleCalibrated) "1" else "0"
        cols += f(t.pDlRaw)
        cols += f(t.pDl)
        cols += f(t.p)
        cols += f(t.d)
        cols += t.stage.toString()
        cols += t.mode.name.lowercase()
        cols += f(out.ensembleW, 3)
        cols += f(out.tau, 3)
        cols += f(out.durationS, 2)
        cols += out.sensitivity?.name ?: "CUSTOM"
        cols += f(t.touch.f, 3)
        cols += t.touch.taps.toString()
        cols += if (t.touch.game) "1" else "0"
        cols += fps.toString()
        cols += if (t.alert.fire) "1" else "0"
        cols += RuleV3.MODEL_VERSION
        cols += t.part ?: ""
        cols += t.behaviorType ?: ""
        cols += out.confidentLevel?.let { f(it, 2) } ?: ""
        for (b in BEHAVIORS) {
            val e = ev.beh[b.key]
            cols += f(e?.score)
            cols += f(e?.rawScore)
            cols += e?.side ?: ""
        }
        rows += cols.joinToString(",")
    }

    /**
     * 카메라 프레임마다 부른다 (기록 중일 때만 쌓임). 사람이 안 잡힌 프레임은 좌표 칸이 빈다.
     * @param handFresh 이 프레임에서 손 모델을 새로 돌렸는지 (false = 직전 손 결과를 다시 씀)
     */
    @Synchronized
    fun addFrame(timestampMs: Long, input: FrameInput?, imageWidth: Int, imageHeight: Int, handFresh: Boolean) {
        if (!recording) return
        if (firstFrameMs < 0) firstFrameMs = timestampMs
        val w = (input?.width ?: imageWidth).toDouble()
        val h = (input?.height ?: imageHeight).toDouble()
        val sb = StringBuilder(4096)
        sb.append("aion_v3,").append(frameNo++).append(',').append(f((timestampMs - firstFrameMs) / 1000.0, 3))
            .append(',').append(w.toInt()).append(',').append(h.toInt())
        val pose = input?.pose?.takeIf { it.size >= 33 }
        for (i in 0 until 33) {
            val p = pose?.get(i)
            if (p == null) sb.append(",,,,") else sb.append(',').append(f(p.x * w, 2)).append(',').append(f(p.y * h, 2))
                .append(',').append(f(p.z * w, 2)).append(',').append(f(p.v, 4))
        }
        val world = input?.poseWorld?.takeIf { it.size >= 33 }
        for (i in 0 until 33) {
            val p = world?.get(i)
            if (p == null) sb.append(",,,") else sb.append(',').append(f(p.x)).append(',').append(f(p.y)).append(',').append(f(p.z))
        }
        for (k in 0 until 2) {
            val hd = input?.hands?.getOrNull(k)
            if (hd == null) { sb.append(",".repeat(1 + 21 * 6)); continue }
            sb.append(',').append(f(hd.score))
            for (i in 0 until 21) {
                val p = hd.norm.getOrNull(i)
                if (p == null) sb.append(",,,") else sb.append(',').append(f(p.x * w, 2)).append(',').append(f(p.y * h, 2)).append(',').append(f(p.z * w, 2))
            }
            for (i in 0 until 21) {
                val p = hd.world?.getOrNull(i)
                if (p == null) sb.append(",,,") else sb.append(',').append(f(p.x)).append(',').append(f(p.y)).append(',').append(f(p.z))
            }
        }
        sb.append(',').append(label).append(',').append(if (handFresh) "1" else "0")
        lmRows += sb.toString()
    }

    /** CSV 파일로 저장하고 그 파일들을 돌려준다 (판정 CSV, 관절 좌표 CSV). 기록이 없으면 빈 목록 */
    @Synchronized
    fun save(context: Context): List<File> {
        if (rows.isEmpty()) return emptyList()
        val dir = File(context.getExternalFilesDir(null), "records").apply { mkdirs() }
        val base = "aion_v3_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startedAt))
        val file = File(dir, "$base.csv")
        // 엑셀에서 한글이 깨지지 않게 BOM 을 붙인다
        file.writeText("\uFEFF" + HEADER.joinToString(",") + "\n" + rows.joinToString("\n") + "\n")
        val out = arrayListOf(file)
        if (lmRows.isNotEmpty()) {
            val lm = File(dir, "${base}_landmarks.csv")
            lm.bufferedWriter().use { wr ->
                wr.write(LANDMARK_HEADER.joinToString(",")); wr.write("\n")
                for (r in lmRows) { wr.write(r); wr.write("\n") }
            }
            out += lm
        }
        return out
    }

    companion object {
        val HEADER: List<String> = listOf(
            "t_sec", "time", "label", "r", "dominant", "dominant_side",
            "p_rule_raw", "p_rule", "rule_cal", "p_dl_raw", "p_dl", "p_ens", "D", "stage", "mode",
            "w", "tau", "duration_s", "sensitivity", "touch_f", "taps", "game", "fps", "alert",
            "model_version", "part", "behavior_type", "confident",
        ) + BEHAVIORS.flatMap { listOf("${it.key}_score", "${it.key}_raw", "${it.key}_side") }

        private val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

        private val POSE_NAMES = listOf(
            "nose", "left_eye_inner", "left_eye", "left_eye_outer", "right_eye_inner", "right_eye", "right_eye_outer",
            "left_ear", "right_ear", "mouth_left", "mouth_right", "left_shoulder", "right_shoulder", "left_elbow",
            "right_elbow", "left_wrist", "right_wrist", "left_pinky", "right_pinky", "left_index", "right_index",
            "left_thumb", "right_thumb", "left_hip", "right_hip", "left_knee", "right_knee", "left_ankle", "right_ankle",
            "left_heel", "right_heel", "left_foot_index", "right_foot_index",
        )

        /** 관절 좌표 CSV 칸 (tools/offline/extract_landmarks.py 와 같은 순서 + label, hand_fresh) */
        val LANDMARK_HEADER: List<String> = buildList {
            addAll(listOf("video_id", "frame", "timestamp", "img_width", "img_height"))
            for (n in POSE_NAMES) addAll(listOf("${n}_x", "${n}_y", "${n}_z", "${n}_v"))
            for (n in POSE_NAMES) addAll(listOf("${n}_wx", "${n}_wy", "${n}_wz"))
            for (k in 0 until 2) {
                add("hand${k}_score")
                for (i in 0 until 21) addAll(listOf("hand${k}_${i}_x", "hand${k}_${i}_y", "hand${k}_${i}_z"))
                for (i in 0 until 21) addAll(listOf("hand${k}_${i}_wx", "hand${k}_${i}_wy", "hand${k}_${i}_wz"))
            }
            add("label"); add("hand_fresh")
        }

        private fun f(v: Double?, digits: Int = 4): String = if (v == null || !v.isFinite()) "" else "%.${digits}f".format(Locale.US, v)

        /** 저장한 파일(들)을 카카오톡·드라이브·메일 등으로 보내는 공유 창 */
        fun share(context: Context, files: List<File>) {
            if (files.isEmpty()) return
            val uris = ArrayList(files.map { FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it) })
            val send = (if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
                        else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }).apply {
                type = "text/csv"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "기록 CSV 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
