package com.example.aion_app.watch

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private const val TAG = "AionHr"

/** 탭에 기록을 남기는 기간. 지나면 앱을 켤 때 지운다 */
private const val KEEP_DAYS = 7

/**
 * 심박 계산 근거 기록. 탭 안(앱 전용 폴더 hr_log/)에 날짜별 CSV로 1초마다 한 줄씩 남긴다.
 *
 *  - calc_YYYYMMDD.csv : 받은 심박 한 개마다 → 필터·행동·기준선 처리 결과, 그때의 M·S·5초 평균·위험도
 *  - send_YYYYMMDD.csv : 서버로 보낸 요청마다 → 보낸 심박·위험도와 서버 응답
 *
 * 이 두 파일로 "실제로 받은 값으로 이렇게 계산해서 이 값을 보냈다"를 엑셀에서 다시 계산해 확인할 수 있다.
 * 아동 화면에는 보이지 않고, 같은 Wi-Fi의 PC로 자동 저장된다 (PcLogUploader).
 * 심박은 건강 정보라 서버·GitHub에는 올리지 않는다.
 */
object HrRecordLog {

    val CALC_HEADER = listOf(
        "기록id", "받은시각", "잰시각", "심박", "필터", "행동중", "기준선처리",
        "이번에_기준선에_넣은_값(잰시각)", "버린_보류값(잰시각)",
        "기준선_개수", "기준선_시간폭(초)", "M", "S", "기준선_준비", "5초평균", "위험도",
    )
    val SEND_HEADER = listOf(
        "기록id", "보낸시각", "비전AI_점수", "부위", "heartRate", "heartRateAt", "hrRisk", "서버응답",
    )

    private val io = Executors.newSingleThreadExecutor()
    private val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.KOREA)
    @Volatile private var dir: File? = null
    private var seq = 0L

    /** 수신기·화면에서 여러 번 불러도 된다 */
    @Synchronized
    fun init(context: Context) {
        if (dir != null) return
        val d = File(context.applicationContext.filesDir, "hr_log").apply { mkdirs() }
        dir = d
        io.execute {
            val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24 * 3600_000L
            d.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
        PcLogUploader.init(context)
    }

    val folder: File? get() = dir

    fun time(ms: Long?): String = if (ms == null) "" else synchronized(timeFmt) { timeFmt.format(Date(ms)) }

    fun calc(cols: List<Any?>) = append("calc", CALC_HEADER, cols)

    fun send(cols: List<Any?>) = append("send", SEND_HEADER, cols)

    private fun append(kind: String, header: List<String>, cols: List<Any?>) {
        val d = dir ?: return   // 아직 초기화 전(단위 테스트 등)이면 남기지 않는다
        val now = System.currentTimeMillis()
        val id = synchronized(this) { "$now-${seq++}" }
        val line = (listOf(id) + cols).joinToString(",") { csv(it) } + "\n"
        io.execute {
            runCatching {
                val file = File(d, "${kind}_${synchronized(dayFmt) { dayFmt.format(Date(now)) }}.csv")
                if (!file.exists()) file.writeText(header.joinToString(",") + "\n")
                file.appendText(line)
            }.onFailure { Log.w(TAG, "기록 저장 실패: ${it.message}") }
        }
    }

    private fun csv(v: Any?): String {
        val s = when (v) {
            null -> ""
            is Double -> "%.3f".format(Locale.US, v)
            is Boolean -> if (v) "예" else "아니오"
            else -> v.toString()
        }
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
