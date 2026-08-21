package com.example.aion_app.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.auth.AuthRepository
import com.example.aion_app.data.auth.FirebaseAuthRepository
import kotlinx.coroutines.launch

// ============================================
// 교사 홈 화면용 ViewModel
// ============================================
class HomeViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository()
) : ViewModel() {

    var students by mutableStateOf<List<Student>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    init {
        loadChildren()
    }

    /** 연결된 아동 목록을 불러온다. 아동을 새로 연결한 뒤에도 호출해서 갱신한다. */
    fun loadChildren() {
        viewModelScope.launch {
            isLoading = true

            authRepository.getMyChildren()
                .onSuccess { children ->
                    students = children.map { child ->
                        Student(
                            id = child.uid,
                            name = child.name,
                            gender = child.gender,
                            age = child.age,
                            // 실시간 상태는 아직 서버에서 받아오지 않는다.
                            // 감지 결과 연동이 붙으면 여기서 채운다.
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
    }
}