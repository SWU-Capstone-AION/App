package com.example.aion_app.ui.screen.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.auth.AuthRepository
import com.example.aion_app.data.auth.FakeAuthRepository
import com.example.aion_app.data.auth.SignUpInput
import kotlinx.coroutines.launch

// ============================================
// 회원가입 플로우용 ViewModel
// ============================================
// SignUpScreen → ChildProfileSetupScreen → OnboardingCompleteScreen
// 세 화면이 이 ViewModel 하나를 공유함 (AionNavHost에서 같은 parentEntry로 묶어줌).
// 그래서 각 화면에서 입력한 값이 화면 이동해도 안 사라지고 여기 모임.
class SignUpViewModel(
    // 기본값은 가짜 구현체. 백엔드 붙일 때 실제 구현체를 주입하면 됨.
    private val authRepository: AuthRepository = FakeAuthRepository()
) : ViewModel() {

    // 로그인·회원가입 화면(아이디/비번/유형) 입력값
    var signUpInput by mutableStateOf(SignUpInput())
        private set

    // 아동 프로필 설정 화면(5단계) 입력값
    var childProfile by mutableStateOf(ChildProfile())
        private set

    // 제출(서버 호출) 진행 상태 — 로딩 표시, 중복 클릭 방지용
    var isSubmitting by mutableStateOf(false)
        private set

    // 마지막 제출 실패 시 에러 메시지 (화면에서 보여주고 싶으면 사용)
    var submitError by mutableStateOf<String?>(null)
        private set

    // 로그인 화면(1p)에서 고른 가입 유형만 먼저 저장
    fun updateType(type: String) {
        signUpInput = signUpInput.copy(type = type)
    }

    // 회원가입 화면(2p)에서 입력한 아이디/비밀번호 저장
    fun updateAccount(userId: String, password: String) {
        signUpInput = signUpInput.copy(userId = userId, password = password)
    }

    fun updateChildProfile(profile: ChildProfile) {
        childProfile = profile
    }

    // ============================================
    // 백엔드 연결 지점 ★
    // ============================================
    // 지금은 authRepository가 FakeAuthRepository라서 항상(거의) 성공 처리됨.
    // 백엔드 API 완성되면 AuthRepository 구현체만 RetrofitAuthRepository로 바꾸면
    // 이 함수, 그리고 이걸 호출하는 화면 쪽 코드는 전부 그대로 써도 됨.
    fun submit(onResult: (success: Boolean) -> Unit) {
        if (isSubmitting) return // 중복 제출 방지

        isSubmitting = true
        submitError = null

        viewModelScope.launch {
            val result = authRepository.register(signUpInput, childProfile)
            isSubmitting = false

            result
                .onSuccess { onResult(true) }
                .onFailure { error ->
                    submitError = error.message ?: "알 수 없는 오류가 발생했습니다."
                    onResult(false)
                }
        }
    }
}