package com.example.aion_app.data.messaging

/**
 * 서버가 보낸 위험 알림 한 건.
 *
 * FCM data 페이로드를 그대로 담는다.
 */
data class DangerAlert(
    val childId: String,
    val childName: String,
    val gender: String,
    val age: Int,
)