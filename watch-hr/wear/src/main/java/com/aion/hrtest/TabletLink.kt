package com.aion.hrtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "AionHr"

/** 태블릿 AION 앱이 같은 Wi-Fi에 알리는 서비스 이름 (AION 앱 WifiHrReceiver와 같아야 한다) */
private const val SERVICE_TYPE = "_aionhr._tcp."

/** "휴대폰(블루투스)"로 보낼 때 저장하는 값 */
private const val PHONE = ""

/**
 * 갤럭시 탭은 워치와 페어링이 안 되므로, 같은 Wi-Fi 안의 태블릿으로 직접 보낸다.
 *
 *  1. Wi-Fi를 켜 달라고 요청한다. 워치는 휴대폰과 블루투스로 붙어 있으면 Wi-Fi를 꺼 두기 때문
 *  2. 같은 Wi-Fi에서 AION 태블릿을 찾는다 (NSD). 태블릿 이름이 목록으로 보인다
 *  3. 교사가 고른 태블릿으로 HTTP POST /aion/hr {"node","bpm","at"} 를 보낸다
 *
 * 아무 태블릿도 고르지 않으면 지금처럼 페어링된 휴대폰으로(블루투스 Data Layer) 보낸다.
 * 화면과 측정 서비스가 같이 쓰므로 사용 횟수를 세어 마지막 사용자가 놓을 때 Wi-Fi 요청을 푼다.
 */
object TabletLink {

    private lateinit var app: Context
    private val prefs by lazy { app.getSharedPreferences("aion_link", Context.MODE_PRIVATE) }
    private val cm by lazy { app.getSystemService(ConnectivityManager::class.java) }
    private val nsd by lazy { app.getSystemService(NsdManager::class.java) }

    private var users = 0
    private var wifi: Network? = null

    /** 지금 돌고 있는 찾기. NsdManager는 리스너 하나를 한 번만 쓸 수 있어서, 다시 찾을 때마다 새로 만든다 */
    private var discovery: NsdManager.DiscoveryListener? = null

    /** 연속 전송 실패 수. 태블릿 앱이 다시 켜지면 주소가 바뀔 수 있어 몇 번 실패하면 다시 찾는다 */
    private var failures = 0

    /** 찾은 태블릿: 이름 → 주소 */
    private val resolved = mutableMapOf<String, Pair<String, Int>>()
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    private val _found = MutableStateFlow<List<String>>(emptyList())
    val found: StateFlow<List<String>> = _found.asStateFlow()

    /** 고른 태블릿 이름. null 이면 휴대폰(블루투스) */
    private val _chosen = MutableStateFlow<String?>(null)
    val chosen: StateFlow<String?> = _chosen.asStateFlow()

    @Synchronized
    fun acquire(context: Context) {
        if (!::app.isInitialized) {
            app = context.applicationContext
            _chosen.value = prefs.getString("tablet", PHONE)?.takeIf { it.isNotEmpty() }
        }
        users++
        if (users == 1) requestWifi()
    }

    @Synchronized
    fun release() {
        if (users == 0) return
        users--
        if (users == 0) stopAll()
    }

    /** 보낼 곳을 고른다. null = 휴대폰(블루투스) */
    fun choose(name: String?) {
        _chosen.value = name
        prefs.edit().putString("tablet", name ?: PHONE).apply()
        Log.d(TAG, "보낼 곳: ${name ?: "휴대폰(블루투스)"}")
    }

    /** 고른 태블릿으로 보낼 주소가 있는지 (지금 찾았거나, 전에 찾은 주소를 기억하고 있거나) */
    fun reachable(): Boolean = _chosen.value?.let { resolved.containsKey(it) || lastKnown(it) != null } == true

    /** 고른 태블릿으로 한 건 보낸다. 못 찾았거나 실패하면 false (대기열에 남아 다시 보낸다) */
    suspend fun send(node: String, sample: HrSample): Boolean {
        val name = _chosen.value ?: return false
        // 지금 찾은 주소가 없으면 마지막으로 알던 주소로 보내 본다.
        // 태블릿 앱을 다시 켜면 NSD로 다시 찾기까지 실기기에서 약 3분 걸렸지만, 주소(IP·포트)는 대개 그대로다
        val target = synchronized(this) { resolved[name] } ?: lastKnown(name)
        val network = wifi
        if (target == null || network == null) {
            // 태블릿을 아직 못 찾았거나(앱 재시작 등) Wi-Fi가 없을 때도 실패로 세어, 15초마다 다시 찾게 한다
            if (++failures % 3 == 0) refresh()
            return false
        }
        val (host, port) = target
        val body = """{"node":"$node","bpm":${sample.bpm},"at":${sample.at}}""".toByteArray()
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = network.openConnection(URL("http://$host:$port/aion/hr")) as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 3_000
                conn.readTimeout = 3_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body) }
                val ok = conn.responseCode == 200
                conn.disconnect()
                ok
            }.getOrElse {
                Log.w(TAG, "Wi-Fi 전송 실패 ($name): ${it.message}")
                false
            }.also { ok ->
                if (ok) failures = 0
                // 대기열은 5초마다 다시 보내 보므로, 3번(약 15초) 연속 실패하면 태블릿을 다시 찾는다
                else if (++failures % 3 == 0) refresh()
            }
        }
    }

    /** [다시 연결] 또는 연속 실패 때: 태블릿을 처음부터 다시 찾는다. 찾은 주소는 새로 찾을 때까지 그대로 쓴다 */
    fun refresh() {
        Log.d(TAG, "태블릿 다시 찾기")
        if (wifi == null) {
            // Wi-Fi 요청이 풀렸으면 다시 요청한다 (연결되면 찾기가 시작됨)
            runCatching { cm.unregisterNetworkCallback(wifiCallback) }
            if (users > 0) requestWifi()
            return
        }
        stopDiscovery()
        startDiscovery()
    }

    // ---- Wi-Fi 요청 ----

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Wi-Fi 연결됨 → 태블릿 찾기")
            wifi = network
            startDiscovery()
        }

        override fun onLost(network: Network) {
            if (wifi == network) wifi = null
            Log.d(TAG, "Wi-Fi 끊김")
        }
    }

    private fun requestWifi() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching { cm.requestNetwork(request, wifiCallback) }
            .onFailure { Log.w(TAG, "Wi-Fi 요청 실패: ${it.message}") }
    }

    private fun stopAll() {
        stopDiscovery()
        runCatching { cm.unregisterNetworkCallback(wifiCallback) }
        wifi = null
    }

    // ---- 태블릿 찾기 (NSD) ----

    private fun newDiscoveryListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "태블릿 찾기 시작 실패: $errorCode")
            synchronized(this@TabletLink) { if (discovery === this) discovery = null }
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

        override fun onServiceFound(info: NsdServiceInfo) {
            Log.d(TAG, "태블릿 발견: ${info.serviceName}")
            synchronized(this@TabletLink) { resolveQueue.addLast(info) }
            resolveNext()
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            Log.d(TAG, "태블릿 사라짐: ${info.serviceName}")
            synchronized(this@TabletLink) { resolved.remove(info.serviceName) }
            publish()
        }
    }

    @Synchronized
    private fun startDiscovery() {
        if (discovery != null) return
        val listener = newDiscoveryListener()
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onSuccess { discovery = listener }
            .onFailure { Log.w(TAG, "태블릿 찾기 실패: ${it.message}") }
    }

    @Synchronized
    private fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
    }

    /** NsdManager는 주소 확인(resolve)을 한 번에 하나만 받는다 */
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val info = synchronized(this) {
            if (resolving) return
            resolveQueue.removeFirstOrNull()?.also { resolving = true }
        } ?: return
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "태블릿 주소 확인 실패: ${info.serviceName} ($errorCode)")
                done()
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress
                if (host != null) {
                    Log.d(TAG, "태블릿 주소: ${info.serviceName} → $host:${info.port}")
                    synchronized(this@TabletLink) { resolved[info.serviceName] = host to info.port }
                    remember(info.serviceName, host, info.port)
                    publish()
                }
                done()
            }

            private fun done() {
                synchronized(this@TabletLink) { resolving = false }
                resolveNext()
            }
        })
    }

    /** 마지막으로 찾은 태블릿 주소. 워치를 다시 켜도 남도록 저장한다 */
    private fun remember(name: String, host: String, port: Int) {
        prefs.edit().putString("addr:$name", "$host:$port").apply()
    }

    private fun lastKnown(name: String): Pair<String, Int>? {
        val saved = prefs.getString("addr:$name", null) ?: return null
        val port = saved.substringAfterLast(':').toIntOrNull() ?: return null
        return saved.substringBeforeLast(':') to port
    }

    private fun publish() {
        _found.value = synchronized(this) { resolved.keys.sorted() }
    }
}
