package com.example.aion_app.ui.screen.password

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.auth.AuthRepository
import com.example.aion_app.data.auth.FirebaseAuthRepository
import kotlinx.coroutines.launch

// ============================================
// 아이디 찾기 화면용 ViewModel
// ============================================
// 이름 + 이메일이 일치하면 아이디를 돌려준다.
// (인증번호 단계는 두지 않기로 함 — Firebase가 코드 메일 발송을 지원하지 않음)
class IdFindViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository()
) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun findId(name: String, email: String, onSuccess: (loginId: String) -> Unit) {
        if (isLoading) return

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            val result = authRepository.findLoginId(name, email)
            isLoading = false

            result
                .onSuccess { loginId -> onSuccess(loginId) }
                .onFailure { error ->
                    errorMessage = error.message ?: "아이디를 찾을 수 없습니다."
                }
        }
    }
}