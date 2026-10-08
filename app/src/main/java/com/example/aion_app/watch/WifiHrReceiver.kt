package com.example.aion_app.watch

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "AionHr"

/** 워치 앱 TabletLink 와 같아야 한다 */
private const val SERVICE_TYPE = "_aionhr._tcp"

/** 이 포트로 받는다. 이미 쓰고 있으면 빈 포트를 받아서 그 번호를 알린다 */
const val HR_WIFI_PORT = 8766

/**
 * 갤럭시 탭은 워치와 페어링이 안 되므로, 워치가 같은 Wi-Fi로 직접 보내는 심박을 받는다.
 *
 *  - 같은 Wi-Fi에 "AION · <기기 이름>" 으로 자신을 알린다 (NSD). 워치 화면의 "보낼 곳" 목록에 이 이름이 보인다
 *  - POST /aion/hr  {"node":"<워치 id>","bpm":78,"at":1759...}  을 받아 WatchHeartRate 로 넘긴다
 *  - 워치는 못 보낸 값을 다시 보내므로, 블루투스 수신과 같은 기준(워치 id + 측정 시각)으로 중복을 거른다
 *
 * 아동 화면(감지 호스트·모니터링)이 켜져 있는 동안만 연다. 두 곳이 같이 쓰므로 사용 횟수를 센다.
 * ⚠ 같은 Wi-Fi 안에서 평문 http 로 받는다. 학교망에서 쓰기 전에 암호화·인증을 붙여야 한다.
 */
object WifiHrReceiver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var users = 0
    private var job: Job? = null
    private var server: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var registered = false

    @Synchronized
    fun acquire(context: Context) {
        users++
        if (users == 1) start(context.applicationContext)
    }

    @Synchronized
    fun release() {
        if (users == 0) return
        users--
        if (users == 0) stop()
    }

    private fun start(context: Context) {
        HrRecordLog.init(context)
        job = scope.launch {
            val ss = runCatching {
                ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(HR_WIFI_PORT)) }
            }.recoverCatching { ServerSocket(0) }.getOrElse {
                Log.w(TAG, "Wi-Fi 수신 시작 실패: ${it.message}")
                return@launch
            }
            server = ss
            advertise(context, ss.localPort)
            Log.d(TAG, "Wi-Fi 수신 대기: 포트 ${ss.localPort}")
            while (isActive) {
                val socket = runCatching { ss.accept() }.getOrNull() ?: break
                launch { handle(socket) }
            }
        }
    }

    @Synchronized
    private fun stop() {
        if (registered) runCatching { nsd?.unregisterService(registration) }
        registered = false
        runCatching { server?.close() }
        server = null
        job?.cancel()
        job = null
        Log.d(TAG, "Wi-Fi 수신 종료")
    }

    // ---- 같은 Wi-Fi에 알리기 ----

    private val registration = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) {
            registered = true
            Log.d(TAG, "Wi-Fi 알림 등록: ${info.serviceName}")
        }
        override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG, "Wi-Fi 알림 등록 실패: $errorCode")
        }
        override fun onServiceUnregistered(info: NsdServiceInfo) { registered = false }
        override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
    }

    private fun advertise(context: Context, port: Int) {
        val manager = context.getSystemService(NsdManager::class.java) ?: return
        nsd = manager
        val info = NsdServiceInfo().apply {
            serviceName = "AION · ${deviceName(context)}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration) }
            .onFailure { Log.w(TAG, "Wi-Fi 알림 실패: ${it.message}") }
    }

    /** 설정 > 휴대전화 정보의 기기 이름 (예: "은서의 Galaxy Tab S9"). 없으면 모델명 */
    private fun deviceName(context: Context): String =
        runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    // ---- 요청 하나 처리 ----

    private fun handle(socket: Socket) = socket.use { s ->
        val now = System.currentTimeMillis()
        s.soTimeout = 5_000
        val input = s.getInputStream().buffered()
        val requestLine = input.readLine() ?: return
        var length = 0
        while (true) {
            val line = input.readLine() ?: return
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                length = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        val ok = requestLine.startsWith("POST /aion/hr") && length in 1..4_096 &&
            runCatching { accept(String(input.readExactly(length), Charsets.UTF_8), now) }
                .onFailure { Log.w(TAG, "Wi-Fi 수신 파싱 실패: ${it.message}") }
                .isSuccess
        val status = if (ok) "200 OK" else "400 Bad Request"
        s.getOutputStream().write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
    }

    private fun accept(json: String, now: Long) {
        val obj = JSONObject(json)
        val node = obj.getString("node")
        val bpm = obj.getInt("bpm")
        val at = obj.getLong("at")
        if (!RecentKeys.firstTime("$node:$at")) return
        Log.d(TAG, "Wi-Fi 수신: bpm=$bpm, 지연=${now - at}ms")
        WatchHeartRate.onReceived(bpm, at = at, receivedAt = now)
    }

    /** HTTP 머리글 한 줄 (CRLF 제외). 스트림이 끝나면 null */
    private fun InputStream.readLine(): String? {
        val sb = StringBuilder()
        while (true) {
            val c = read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
    }

    private fun InputStream.readExactly(n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(buf, off, n - off)
            if (r == -1) error("본문이 짧음")
            off += r
        }
        return buf
    }
}
