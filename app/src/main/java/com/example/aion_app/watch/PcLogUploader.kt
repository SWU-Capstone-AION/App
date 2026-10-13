package com.example.aion_app.watch

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.aion_app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "AionHr"
private const val BATCH_LINES = 300
private const val IDLE_MS = 2_000L
private const val RETRY_MS = 5_000L

/**
 * 탭의 계산 근거 기록(hr_log 폴더의 CSV)을 같은 Wi-Fi의 PC(watch-hr/tools/hr_server.py)로 보내 자동 저장한다.
 *
 * 탭의 파일이 원본이다. 파일마다 "PC에 보낸 위치"를 기억해 두고, 2초마다 그 뒤에 새로 쌓인 줄만 보낸다.
 * PC가 꺼져 있어도 기록은 탭에 남아 있다가, 다시 연결되면 밀린 것부터 이어서 간다.
 * 같은 줄을 다시 보내도 PC가 기록id로 중복을 거른다.
 *
 * PC 주소: 모니터링 화면에서 바꿀 수 있고, 처음 값은 local.properties 의 aion.hr.pcAddr (예: 192.168.0.5:8765).
 */
object PcLogUploader {

    private lateinit var app: Context
    private val prefs by lazy { app.getSharedPreferences("pc_log_upload", Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

    private val _status = MutableStateFlow("PC 주소 없음")
    val status: StateFlow<String> = _status.asStateFlow()

    /** "IP:포트" */
    val address: String
        get() = prefs.getString("address", null) ?: BuildConfig.HR_PC_ADDR

    @Synchronized
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        scope.launch { loop() }
    }

    fun setAddress(value: String) {
        prefs.edit().putString("address", value.trim()).apply()
        _status.value = "연결 시도 중… (${value.trim()})"
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val addr = address
            val dir = HrRecordLog.folder
            if (addr.isBlank() || dir == null) {
                _status.value = "PC 주소 없음"
                delay(IDLE_MS); continue
            }
            var sentAny = false
            var failed = false
            for (file in dir.listFiles { f -> f.name.endsWith(".csv") }.orEmpty().sortedBy { it.name }) {
                val result = runCatching { uploadNew(addr, file) }
                result.onFailure {
                    failed = true
                    _status.value = "PC 연결 실패 (${it.javaClass.simpleName}) · 기록은 탭에 보관 중"
                    Log.w(TAG, "PC 기록 전송 실패: ${it.message}")
                }
                if (failed) break
                if ((result.getOrNull() ?: 0) > 0) sentAny = true
            }
            if (!failed && sentAny) _status.value = "PC 저장 중 · 마지막 ${timeFmt.format(Date())}"
            delay(if (failed) RETRY_MS else IDLE_MS)
        }
    }

    /** 파일에서 아직 안 보낸 완전한 줄들을 보낸다. 보낸 줄 수를 돌려준다 */
    private fun uploadNew(addr: String, file: File): Int {
        val key = "offset:${file.name}"
        val offset = prefs.getLong(key, 0L)
        if (file.length() <= offset) return 0

        val (lines, nextOffset) = RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val bytes = ByteArray(minOf(raf.length() - offset, 256_000L).toInt())
            raf.readFully(bytes)
            // 끝에 덜 써진 줄이 있으면 다음에 보낸다
            val lastNl = bytes.lastIndexOf('\n'.code.toByte())
            if (lastNl < 0) return 0
            val text = String(bytes, 0, lastNl + 1, Charsets.UTF_8)
            var all = text.split('\n').dropLast(1)
            var used = lastNl + 1
            if (all.size > BATCH_LINES) {
                all = all.take(BATCH_LINES)
                used = all.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 }
            }
            all to offset + used
        }
        // 첫 줄(머리글)은 PC가 따로 받으므로 기록 줄만 보낸다
        val header = file.bufferedReader(Charsets.UTF_8).use { it.readLine() }
        val rows = lines.filter { it.isNotBlank() && it != header }
        if (rows.isNotEmpty()) post(addr, file.name, header, rows)
        prefs.edit().putLong(key, nextOffset).apply()
        return rows.size
    }

    private fun post(addr: String, fileName: String, header: String, rows: List<String>) {
        val body = JSONObject()
            .put("device", Build.MODEL)
            .put("file", fileName)
            .put("header", header)
            .put("lines", JSONArray(rows))
            .toString().toByteArray(Charsets.UTF_8)
        val conn = URL("http://$addr/log").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 3_000
            conn.readTimeout = 5_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code != 200) error("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }
}
