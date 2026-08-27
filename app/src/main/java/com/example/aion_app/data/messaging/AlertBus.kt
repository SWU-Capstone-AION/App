package com.example.aion_app.data.messaging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FCM 서비스와 화면 사이의 전달 통로.
 *
 * FirebaseMessagingService는 UI가 아니라서 Compose 상태를 직접 못 바꾼다.
 * 서비스가 여기에 값을 넣으면 화면이 구독해서 팝업을 띄운다.
 */
object AlertBus {

    private val _dangerAlert = MutableStateFlow<DangerAlert?>(null)
    val dangerAlert: StateFlow<DangerAlert?> = _dangerAlert.asStateFlow()

    /** 앱이 화면에 보이는 중인지. 시스템 알림을 띄울지 판단하는 데 쓴다. */
    @Volatile
    var isAppForeground: Boolean = false

    fun push(alert: DangerAlert) {
        _dangerAlert.value = alert
    }

    /** 교사가 확인 버튼을 누르면 호출 */
    fun clear() {
        _dangerAlert.value = null
    }
}