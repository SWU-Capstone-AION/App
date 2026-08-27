package com.example.aion_app.ui.screen.notification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.alert.AlertDto
import com.example.aion_app.data.alert.AlertRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ============================================
// 알림센터용 ViewModel
// ============================================
// 서버(Django)에서 알림 목록을 받아 화면용 모델로 바꾼다.
class NotificationViewModel(
    private val alertRepository: AlertRepository = AlertRepository()
) : ViewModel() {

    var notifications by mutableStateOf<List<NotificationItem>>(emptyList())
        private set

    /** 필터 칩에 쓸 (childId, 이름) 목록. 알림에 등장한 아동만 나온다. */
    var children by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            alertRepository.getAlerts()
                .onSuccess { alerts ->
                    notifications = alerts.map { it.toNotificationItem() }
                    // 같은 아동이 여러 번 나오므로 childId 기준으로 한 번씩만
                    children = alerts
                        .map { it.childId to it.childName }
                        .distinctBy { it.first }
                }
                .onFailure {
                    notifications = emptyList()
                    children = emptyList()
                    // 서버가 꺼져 있거나 다른 와이파이일 때가 대부분이라
                    // 기술적인 오류명 대신 상황을 알려준다
                    errorMessage = "알림을 불러올 수 없어요.\n네트워크 연결을 확인해 주세요."
                }

            isLoading = false
        }
    }

    /** 알림 삭제. 서버에서도 감춰진다. */
    fun delete(item: NotificationItem) {
        val id = item.id.toIntOrNull() ?: return

        // 화면에서 먼저 빼서 스와이프가 매끄럽게 끝나도록 한다
        notifications = notifications.filterNot { it.id == item.id }

        viewModelScope.launch {
            alertRepository.deleteAlert(id)
                .onFailure {
                    // 서버에서 못 지웠으면 되돌린다
                    errorMessage = "삭제하지 못했어요. 다시 시도해 주세요."
                    load()
                }
        }
    }
}

// ============================================
// 서버 응답 → 화면 모델 변환
// ============================================

private fun AlertDto.toNotificationItem(): NotificationItem {
    val occurred = parseIsoDate(occurredAt)

    return NotificationItem(
        id = id.toString(),
        type = when (level) {
            "DANGER" -> NotificationType.DANGER
            "CAUTION" -> NotificationType.CAUTION
            else -> NotificationType.STABLE
        },
        message = body,
        studentName = childName,
        childId = childId,
        dateGroup = occurred.toDateGroup(),
        timeText = occurred.toRelativeTime(),
    )
}

/** "2026-08-27T10:03:00+09:00" → Date. 파싱 실패하면 현재 시각. */
private fun parseIsoDate(text: String): Date {
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
private fun Date.toDateGroup(): String {
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

private fun Calendar.isSameDay(other: Calendar): Boolean =
    get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

/** "방금 전" / "5분 전" / "3시간 전" / "2일 전" */
private fun Date.toRelativeTime(): String {
    val minutes = (System.currentTimeMillis() - time) / 60_000

    return when {
        minutes < 1 -> "방금 전"
        minutes < 60 -> "${minutes}분 전"
        minutes < 60 * 24 -> "${minutes / 60}시간 전"
        else -> "${minutes / (60 * 24)}일 전"
    }
}