package com.example.aion_app.data.messaging

/** 알림 종류. 팝업 문구와 색이 달라진다. */
enum class AlertKind { DANGER, HELP }

/**
 * 서버가 보낸 알림 한 건.
 *
 * FCM data 페이로드를 그대로 담는다.
 * 홈 화면의 5초 조회에서 새 알림을 찾았을 때도 같은 모양으로 만든다.
 */
data class DangerAlert(
    val kind: AlertKind,
    val childId: String,
    val childName: String,
    val gender: String,
    val age: Int,
    // 서버 알림 목록의 id 와 같은 값 (문자열).
    // FCM 과 5초 조회 두 길로 같은 알림이 들어와도 팝업을 한 번만 띄우는 데 쓴다.
    // 콘솔 테스트처럼 id 가 없는 알림은 빈 문자열.
    val alertId: String = "",
)
