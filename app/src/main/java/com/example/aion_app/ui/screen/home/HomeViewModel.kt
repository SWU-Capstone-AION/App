package com.example.aion_app.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.alert.AlertRepository
import com.example.aion_app.data.alert.ChildStateDto
import com.example.aion_app.data.alert.isToday
import com.example.aion_app.data.alert.parseIsoDate
import com.example.aion_app.data.alert.toRelativeTime
import com.example.aion_app.data.auth.AuthRepository
import com.example.aion_app.data.auth.FirebaseAuthRepository
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
                    students = states.map { it.toStudent() }
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
                }
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
                }
                .onFailure {
                    recentAlert = null
                    todayCautionCount = 0
                    todayDangerCount = 0
                }
        }
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
    // 심박수는 아직 서버가 보내지 않는다 (갤럭시 워치 연동 예정)
    heartRate = null,
)