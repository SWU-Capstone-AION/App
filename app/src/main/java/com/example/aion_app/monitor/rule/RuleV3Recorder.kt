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
    private var startedAt = 0L

    val rowCount: Int @Synchronized get() = rows.size

    @Synchronized
    fun start() {
        rows.clear()
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
        for (b in BEHAVIORS) {
            val e = ev.beh[b.key]
            cols += f(e?.score)
            cols += f(e?.rawScore)
            cols += e?.side ?: ""
        }
        rows += cols.joinToString(",")
    }

    /** CSV 파일로 저장하고 그 파일을 돌려준다. 기록이 없으면 null */
    @Synchronized
    fun save(context: Context): File? {
        if (rows.isEmpty()) return null
        val dir = File(context.getExternalFilesDir(null), "records").apply { mkdirs() }
        val name = "aion_v3_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startedAt)) + ".csv"
        val file = File(dir, name)
        // 엑셀에서 한글이 깨지지 않게 BOM 을 붙인다
        file.writeText("﻿" + HEADER.joinToString(",") + "\n" + rows.joinToString("\n") + "\n")
        return file
    }

    companion object {
        val HEADER: List<String> = listOf(
            "t_sec", "time", "label", "r", "dominant", "dominant_side",
            "p_rule_raw", "p_rule", "rule_cal", "p_dl_raw", "p_dl", "p_ens", "D", "stage", "mode",
            "w", "tau", "duration_s", "sensitivity", "touch_f", "taps", "game", "fps", "alert",
            "model_version", "part", "behavior_type",
        ) + BEHAVIORS.flatMap { listOf("${it.key}_score", "${it.key}_raw", "${it.key}_side") }

        private val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

        private fun f(v: Double?, digits: Int = 4): String = if (v == null || !v.isFinite()) "" else "%.${digits}f".format(Locale.US, v)

        /** 저장한 파일을 카카오톡·드라이브·메일 등으로 보내는 공유 창 */
        fun share(context: Context, file: File) {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "기록 CSV 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
