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
// 비밀번호 찾기 화면용 ViewModel
// ============================================
// 아이디를 받아 가입 이메일로 재설정 메일을 보낸다.
// 새 비밀번호는 메일 속 링크(Firebase가 제공하는 페이지)에서 설정하므로
// 앱 안에서 비밀번호를 바꾸는 화면은 필요 없다.
class PasswordFindViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository()
) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun sendResetMail(loginId: String, onSuccess: () -> Unit) {
        if (isLoading) return

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(loginId)
            isLoading = false

            result
                .onSuccess { onSuccess() }
                .onFailure { error ->
                    errorMessage = error.message ?: "메일 발송에 실패했습니다."
                }
        }
    }
}