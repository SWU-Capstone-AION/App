package com.example.aion_app.data.alert

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ============================================
// 서버 알림의 시각 표기 변환
// ============================================
// 알림센터와 홈 배너가 같이 쓴다.

/** "2026-08-27T10:03:00+09:00" → Date. 파싱 실패하면 현재 시각. */
internal fun parseIsoDate(text: String): Date {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    )
    for (pattern in patterns) {
        runCatching {
            return SimpleDateFormat(pattern, Locale.KOREA).parse(text)!!
        }
    }
    return Date()
}

/** "오늘 · 8월 27일" / "어제 · 8월 26일" / "8월 20일" */
internal fun Date.toDateGroup(): String {
    val label = SimpleDateFormat("M월 d일", Locale.KOREA).format(this)

    val target = Calendar.getInstance().apply { time = this@toDateGroup }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        target.isSameDay(today) -> "오늘 · $label"
        target.isSameDay(yesterday) -> "어제 · $label"
        else -> label
    }
}

/** "방금 전" / "5분 전" / "3시간 전" / "2일 전" */
internal fun Date.toRelativeTime(): String {
    // 서버와 기기의 시계가 어긋나면 음수가 나올 수 있다. 그때는 방금 전으로 본다.
    val minutes = ((System.currentTimeMillis() - time) / 60_000).coerceAtLeast(0)

    return when {
        minutes < 1 -> "방금 전"
        minutes < 60 -> "${minutes}분 전"
        minutes < 60 * 24 -> "${minutes / 60}시간 전"
        else -> "${minutes / (60 * 24)}일 전"
    }
}

/** 오늘 발생한 알림인지 */
internal fun Date.isToday(): Boolean {
    val target = Calendar.getInstance().apply { time = this@isToday }
    return target.isSameDay(Calendar.getInstance())
}

private fun Calendar.isSameDay(other: Calendar): Boolean =
    get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)