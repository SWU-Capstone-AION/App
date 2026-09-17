package com.example.aion_app.ui.screen.report

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.alert.AlertRepository
import com.example.aion_app.data.auth.AuthRepository
import com.example.aion_app.data.auth.FirebaseAuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ============================================
// 리포트 목록 화면용 ViewModel
//
// 홈 화면(HomeViewModel)과 같은 방식으로 담당 아동을 불러온다.
//   1) 서버(/api/children/states/) — 활동 중 여부까지 온다
//   2) 서버가 안 되면 Firestore에서 목록만
//
// 상세 화면도 이 ViewModel을 같이 써서, 누른 아동 정보를 다시 불러오지 않는다.
// ============================================
class ReportListViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository(),
    private val alertRepository: AlertRepository = AlertRepository(),
) : ViewModel() {

    var students by mutableStateOf<List<ReportStudent>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    /** 목록을 아예 못 불러왔을 때만 채운다 (이미 보이는 목록이 있으면 그대로 둠) */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var loadJob: Job? = null

    /** 화면에 들어올 때마다 부른다. 이미 불러오는 중이면 겹쳐서 부르지 않는다. */
    fun load() {
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            // 처음 한 번만 로딩 표시. 돌아왔을 때는 기존 목록을 보여준 채로 새로 받는다.
            if (students.isEmpty()) isLoading = true
            errorMessage = null

            alertRepository.getChildStates()
                .onSuccess { states ->
                    students = states.map { state ->
                        ReportStudent(
                            id = state.childId,
                            name = state.name,
                            gender = state.gender.take(1),   // "남자" → "남" ("남"이면 그대로)
                            age = state.age,
                            isActive = state.active
                        )
                    }
                }
                .onFailure {
                    loadFromFirestore()
                }

            isLoading = false
        }
    }

    private suspend fun loadFromFirestore() {
        authRepository.getMyChildren()
            .onSuccess { children ->
                students = children.map { child ->
                    ReportStudent(
                        id = child.uid,
                        name = child.name,
                        gender = child.gender.take(1),
                        age = child.age,
                        // 서버에 못 붙었으니 활동 중인지 알 수 없다
                        isActive = false
                    )
                }
            }
            .onFailure {
                if (students.isEmpty()) {
                    errorMessage = "아동 목록을 불러오지 못했어요.\n잠시 후 다시 시도해 주세요."
                }
            }
    }

    /** 상세 화면에서 id로 아동 찾기 */
    fun findStudent(id: String): ReportStudent? =
        students.firstOrNull { it.id == id }
}