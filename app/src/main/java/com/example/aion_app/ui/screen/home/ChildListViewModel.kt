package com.example.aion_app.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.auth.AuthRepository
import com.example.aion_app.data.auth.FirebaseAuthRepository
import com.example.aion_app.data.auth.LinkedChild
import kotlinx.coroutines.launch

// ============================================
// 아동 목록 화면용 ViewModel
// ============================================
class ChildListViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository()
) : ViewModel() {

    var children by mutableStateOf<List<LinkedChild>>(emptyList())
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

            authRepository.getMyChildren()
                .onSuccess { result -> children = result }
                .onFailure { error ->
                    errorMessage = error.message ?: "목록을 불러오지 못했습니다."
                }

            isLoading = false
        }
    }

    /** 담당 아동 연결 해제. 아동 계정 자체는 지우지 않는다. */
    fun unlink(childUid: String) {
        viewModelScope.launch {
            authRepository.unlinkChild(childUid)
                .onSuccess {
                    // 목록에서 바로 빼서 화면에 반영
                    children = children.filterNot { it.uid == childUid }
                }
                .onFailure { error ->
                    errorMessage = error.message ?: "연결 해제에 실패했습니다."
                }
        }
    }
}