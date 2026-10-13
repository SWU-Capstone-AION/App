package com.aion.hrtest

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "AionHr"
private const val PREFS = "pc_upload"
private const val BATCH = 200
private const val IDLE_MS = 2_000L
private const val RETRY_MS = 5_000L

/**
 * 받은 심박을 같은 Wi-Fi의 PC(tools/hr_server.py)로 보내, PC의 watch-hr/data/ 에 자동 저장되게 한다.
 *
 * 폰 DB가 원본이다. "PC에 마지막으로 보낸 기록 id"를 기억해 두고, 2초마다 그 다음 기록부터 보낸다.
 * PC가 꺼져 있거나 Wi-Fi가 끊겨도 기록은 DB에 남아 있다가, 다시 연결되면 밀린 것부터 이어서 간다.
 * 응답을 못 받아 같은 기록을 다시 보내도 PC 쪽에서 중복을 걸러낸다.
 */
class PcUploader private constructor(context: Context) {

    private val dao = HrDatabase.get(context).hrDao()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

    /** "IP:포트" (예: 172.19.69.233:8765) */
    val address: String get() = prefs.getString("address", "") ?: ""

    private var lastSentId: Long
        get() = prefs.getLong("lastSentId", 0)
        set(v) { prefs.edit().putLong("lastSentId", v).apply() }

    private val _status = MutableStateFlow("PC 주소를 넣고 [연결]을 누르세요")
    val status: StateFlow<String> = _status.asStateFlow()

    fun setAddress(value: String) {
        prefs.edit().putString("address", value.trim()).apply()
        _status.value = "연결 시도 중… ($value)"
        start()
    }

    /** 여러 번 불러도 한 번만 돈다 (수신 서비스·화면에서 부름) */
    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val addr = address
                if (addr.isBlank()) { delay(IDLE_MS); continue }
                val rows = dao.after(lastSentId, BATCH)
                if (rows.isEmpty()) { delay(IDLE_MS); continue }
                val ok = runCatching { post(addr, rows) }
                    .onFailure {
                        _status.value = "PC 연결 실패 (${it.javaClass.simpleName}): 대기 중 ${dao.maxId() - lastSentId}건"
                        Log.w(TAG, "PC 전송 실패: ${it.message}")
                    }.isSuccess
                if (ok) {
                    lastSentId = rows.last().id
                    val left = dao.maxId() - lastSentId
                    _status.value = "PC 저장 중 · 마지막 ${timeFmt.format(Date())} · ${rows.size}건 전송" +
                        (if (left > 0) " · 남은 ${left}건" else "")
                    if (rows.size < BATCH) delay(IDLE_MS)   // 밀린 게 많으면 바로 다음 묶음
                } else {
                    delay(RETRY_MS)
                }
            }
        }
    }

    private fun post(addr: String, rows: List<HrRecord>) {
        val body = JSONObject()
            .put("device", Build.MODEL)
            .put("rows", JSONArray().apply {
                rows.forEach { r ->
                    put(JSONObject()
                        .put("id", r.id).put("bpm", r.bpm)
                        .put("at", r.at).put("receivedAt", r.receivedAt)
                        .put("source", r.sourceNode)
                        .put("valid", r.valid).put("baseline", r.quiet))
                }
            })
            .toString().toByteArray(Charsets.UTF_8)

        val conn = URL("http://$addr/hr").openConnection() as HttpURLConnection
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

    companion object {
        @Volatile private var instance: PcUploader? = null

        fun get(context: Context): PcUploader =
            instance ?: synchronized(this) {
                instance ?: PcUploader(context.applicationContext).also { instance = it }
            }
    }
}
