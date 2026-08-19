package com.example.aion_app.ui.screen.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.auth.AuthRepository
import com.example.aion_app.data.auth.FirebaseAuthRepository
import com.example.aion_app.data.auth.UserRole
import kotlinx.coroutines.launch

// ============================================
// 로그인 화면용 ViewModel
// ============================================
// 교사용 로그인(SignUpScreen)과 아동용 로그인(KidsLoginScreen)이 같은 걸 쓴다.
// 화면마다 별도 인스턴스라 상태가 섞이지 않는다.
class LoginViewModel(
    // 실제 Firebase 연동. UI만 테스트하려면 FakeAuthRepository()로 바꾸면 된다.
    private val authRepository: AuthRepository = FirebaseAuthRepository()
) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /**
     * 로그인 시도.
     *
     * 화면의 교사용/아동용 토글과 무관하게, 실제 역할은 서버에 저장된 값을 따른다.
     * (아동 계정으로 교사용 화면에 들어가는 일이 없도록)
     */
    fun login(userId: String, password: String, onSuccess: (UserRole) -> Unit) {
        if (isLoading) return

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            val result = authRepository.login(userId, password)
            isLoading = false

            result
                .onSuccess { role -> onSuccess(role) }
                .onFailure { error ->
                    errorMessage = error.message ?: "로그인에 실패했습니다."
                }
        }
    }

    /**
     * 로그아웃.
     *
     * onComplete 에서 화면을 옮겨야 한다.
     * 먼저 화면을 옮기면 이 ViewModel이 파괴되면서 viewModelScope가 취소돼
     * FCM 토큰 삭제와 signOut 이 중간에 끊긴다.
     */
    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onComplete()
        }
    }
}