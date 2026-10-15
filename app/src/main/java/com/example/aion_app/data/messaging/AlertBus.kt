package com.example.aion_app.data.messaging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FCM 서비스와 화면 사이의 전달 통로.
 *
 * FirebaseMessagingService는 UI가 아니라서 Compose 상태를 직접 못 바꾼다.
 * 서비스가 여기에 값을 넣으면 화면이 구독해서 팝업을 띄운다.
 *
 * 팝업은 두 길로 들어온다.
 *   1. FCM 푸시 — 빠를 때는 바로 오지만 가끔 수십 초 늦는다.
 *   2. 홈 화면의 5초 조회 — FCM 이 늦어도 5초 안에 잡는다.
 * 둘 중 먼저 온 쪽이 팝업을 띄우고, 늦게 온 쪽은 alertId 로 걸러진다.
 */
object AlertBus {

    private val _dangerAlert = MutableStateFlow<DangerAlert?>(null)
    val dangerAlert: StateFlow<DangerAlert?> = _dangerAlert.asStateFlow()

    // 이미 팝업으로 띄운 알림 id.
    // 앱을 껐다 켜면 비워지지만, 그때는 홈 조회가 기존 알림을 팝업으로 띄우지 않으므로 괜찮다.
    private val shownAlertIds = mutableSetOf<String>()

    /** 앱이 화면에 보이는 중인지. 시스템 알림을 띄울지 판단하는 데 쓴다. */
    @Volatile
    var isAppForeground: Boolean = false

    @Synchronized
    fun push(alert: DangerAlert) {
        if (alert.alertId.isNotBlank()) {
            // 같은 알림이 다른 길로 한 번 더 들어온 경우 → 무시
            if (!shownAlertIds.add(alert.alertId)) return
        }
        _dangerAlert.value = alert
    }

    /** 교사가 확인 버튼을 누르면 호출 */
    fun clear() {
        _dangerAlert.value = null
    }
}
