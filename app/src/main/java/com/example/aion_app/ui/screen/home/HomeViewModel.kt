package com.example.aion_app.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.BuildConfig
import com.example.aion_app.data.alert.AlertDto
import com.example.aion_app.data.alert.AlertRepository
import com.example.aion_app.data.alert.ChildStateDto
import com.example.aion_app.data.alert.isToday
import com.example.aion_app.data.alert.parseIsoDate
import com.example.aion_app.data.alert.toRelativeTime
import com.example.aion_app.data.auth.AuthRepository
import com.example.aion_app.data.auth.FirebaseAuthRepository
import com.example.aion_app.data.messaging.AlertBus
import com.example.aion_app.data.messaging.AlertKind
import com.example.aion_app.data.messaging.DangerAlert
import com.example.aion_app.watch.HR_STALE_MS
import com.example.aion_app.watch.WatchHeartRate
import kotlinx.coroutines.launch

// ============================================
// 교사 홈 화면용 ViewModel
// ============================================
class HomeViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository(),
    private val alertRepository: AlertRepository = AlertRepository(),
) : ViewModel() {

    var students by mutableStateOf<List<Student>>(emptyList())
        private set

    /** 상단 NEW 배너에 띄울 최근 알림 1건. 없거나 서버 연결이 안 되면 null. */
    var recentAlert by mutableStateOf<HomeAlert?>(null)
        private set

    /** 오늘 발생한 주의·위험 건수 */
    var todayCautionCount by mutableStateOf(0)
        private set

    var todayDangerCount by mutableStateOf(0)
        private set

    var isLoading by mutableStateOf(true)
        private set

    // 지금까지 본 알림 중 가장 큰 id.
    // null 이면 아직 한 번도 못 받아온 것 → 첫 조회는 기준만 잡고 팝업을 띄우지 않는다.
    // (홈에 들어올 때마다 예전 알림이 팝업으로 쏟아지면 안 되니까)
    private var lastSeenAlertId: Int? = null

    init {
        loadChildren()
        loadAlerts()
    }

    /**
     * 담당 아동 목록과 현재 상태를 불러온다.
     *
     * 서버가 상태까지 담아 돌려주므로 평소에는 그것만 쓰면 된다.
     * 서버가 꺼져 있을 때는 Firestore에서 목록만 읽어와, 상태는 비어 있어도
     * 아이들이 화면에서 사라지지 않게 한다.
     */
    fun loadChildren() {
        viewModelScope.launch {
            alertRepository.getChildStates()
                .onSuccess { states ->
                    students = states.map { it.toStudent() }.withLocalWatchDemo()
                    isLoading = false
                }
                .onFailure {
                    loadChildrenFromFirestore()
                }
        }
    }

    private suspend fun loadChildrenFromFirestore() {
        authRepository.getMyChildren()
            .onSuccess { children ->
                students = children.map { child ->
                    Student(
                        id = child.uid,
                        name = child.name,
                        gender = child.gender,
                        age = child.age,
                        // 서버에 못 붙었으니 상태는 알 수 없다
                        status = StudentStatus.INACTIVE,
                        stressScore = 0,
                        stressLevel = StressLevel.NO_DATA,
                        heartRate = null,
                    )
                }.withLocalWatchDemo()
            }
            .onFailure { students = emptyList() }

        isLoading = false
    }

    /**
     * 알림을 불러와 배너와 오늘 건수를 채운다.
     *
     * 서버가 꺼져 있으면 실패하는데, 홈 전체를 막을 일은 아니므로
     * 배너만 비우고 넘어간다.
     */
    fun loadAlerts() {
        viewModelScope.launch {
            alertRepository.getAlerts()
                .onSuccess { alerts ->
                    // 목록은 최신순이라 첫 항목이 가장 최근 알림
                    recentAlert = alerts.firstOrNull()?.let { alert ->
                        HomeAlert(
                            message = alert.body,
                            timeText = parseIsoDate(alert.occurredAt).toRelativeTime(),
                        )
                    }

                    val todayAlerts = alerts.filter { parseIsoDate(it.occurredAt).isToday() }
                    todayCautionCount = todayAlerts.count { it.level == "CAUTION" }
                    todayDangerCount = todayAlerts.count { it.level == "DANGER" }

                    popupNewAlert(alerts)
                }
                .onFailure {
                    recentAlert = null
                    todayCautionCount = 0
                    todayDangerCount = 0
                }
        }
    }

    /**
     * 5초 조회에서 새로 생긴 위험·도움 요청 알림을 찾아 팝업으로 띄운다.
     *
     * 원래 팝업은 FCM 푸시로만 떴는데, FCM 이 늦게 오면 배너만 먼저 바뀌고
     * 팝업은 한참 뒤에 떴다. 그래서 목록 조회에서도 팝업을 띄운다.
     * FCM 이 나중에 같은 알림을 가져와도 AlertBus 가 alertId 로 걸러 준다.
     */
    private fun popupNewAlert(alerts: List<AlertDto>) {
        val maxId = alerts.maxOfOrNull { it.id } ?: return
        val lastSeen = lastSeenAlertId

        // 첫 조회: 기준만 잡는다
        if (lastSeen == null) {
            lastSeenAlertId = maxId
            return
        }
        if (maxId <= lastSeen) return
        lastSeenAlertId = maxId

        // 새 알림 중 팝업 대상(위험·도움 요청)인 가장 최근 1건
        val newest = alerts
            .filter { it.id > lastSeen }
            .filter { it.level == LEVEL_DANGER || it.level == LEVEL_HELP }
            .maxByOrNull { it.id }
            ?: return

        // 목록 API 에는 성별·나이가 없어서 홈 아동 카드 정보에서 가져온다
        val student = students.firstOrNull { it.id == newest.childId }

        AlertBus.push(
            DangerAlert(
                kind = if (newest.level == LEVEL_HELP) AlertKind.HELP else AlertKind.DANGER,
                childId = newest.childId,
                childName = newest.childName.ifBlank { student?.name.orEmpty() },
                gender = student?.gender.orEmpty(),
                age = student?.age ?: 0,
                alertId = newest.id.toString(),
            )
        )
    }

    private companion object {
        const val LEVEL_DANGER = "DANGER"
        const val LEVEL_HELP = "HELP"
    }
}

/** 서버 응답 → 홈 화면 카드 모델 */
private fun ChildStateDto.toStudent(): Student = Student(
    id = childId,
    name = name,
    gender = gender,
    age = age,
    status = if (active) StudentStatus.ACTIVE else StudentStatus.INACTIVE,
    stressScore = score,
    stressLevel = when (level) {
        "DANGER" -> StressLevel.DANGER
        "CAUTION" -> StressLevel.CAUTION
        "STABLE" -> StressLevel.STABLE
        // level 이 null 이면 아직 측정 기록이 없는 아동
        else -> StressLevel.NO_DATA
    },
    // 아동 태블릿이 보낸 5초 평균. 20초 넘게 새 값이 없으면 "--"
    heartRate = heartRate?.takeIf { heartRateAt == null || !isStale(heartRateAt) },
)

private fun isStale(at: String): Boolean =
    System.currentTimeMillis() - parseIsoDate(at).time > HR_STALE_MS

/**
 * 시연용 (디버그 빌드 + local.properties 의 aion.hr.demoChildId 에 아동 uid 또는 이름이 있을 때만).
 * 서버에 심박 기능이 붙기 전에, 이 폰에 직접 연결된 워치 심박을 그 아동 카드에 띄운다.
 * 서버가 심박을 보내 주면 서버 값을 그대로 쓴다.
 */
private fun List<Student>.withLocalWatchDemo(): List<Student> {
    val demoId = BuildConfig.HR_DEMO_CHILD_ID
    if (!BuildConfig.DEBUG || demoId.isBlank()) return this
    val local = WatchHeartRate.displayBpm.value ?: return this
    return map {
        // uid 또는 이름으로 지정 (local.properties 의 aion.hr.demoChildId)
        if ((it.id == demoId || it.name == demoId) && it.heartRate == null) it.copy(heartRate = local, status = StudentStatus.ACTIVE)
        else it
    }
}