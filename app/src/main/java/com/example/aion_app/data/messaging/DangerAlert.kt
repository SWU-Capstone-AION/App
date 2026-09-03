package com.example.aion_app.data.messaging

/** 알림 종류. 팝업 문구와 색이 달라진다. */
enum class AlertKind { DANGER, HELP }

/**
 * 서버가 보낸 알림 한 건.
 *
 * FCM data 페이로드를 그대로 담는다.
 */
data class DangerAlert(
    val kind: AlertKind,
    val childId: String,
    val childName: String,
    val gender: String,
    val age: Int,
)