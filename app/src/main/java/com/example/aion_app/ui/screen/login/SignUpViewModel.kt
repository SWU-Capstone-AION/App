package com.example.aion_app.ui.screen.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.auth.AuthRepository
import com.example.aion_app.data.auth.FirebaseAuthRepository
import com.example.aion_app.data.auth.SignUpInput
import kotlinx.coroutines.launch

// ============================================
// 회원가입 플로우용 ViewModel
// ============================================
// SignUpScreen → ChildProfileSetupScreen → OnboardingCompleteScreen
// 세 화면이 이 ViewModel 하나를 공유함 (AionNavHost에서 같은 parentEntry로 묶어줌).
class SignUpViewModel(
    // 실제 Firebase 연동. UI만 테스트하려면 FakeAuthRepository()로 바꾸면 된다.
    private val authRepository: AuthRepository = FirebaseAuthRepository()
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

    // 마지막 제출 실패 시 에러 메시지
    var submitError by mutableStateOf<String?>(null)
        private set

    // 아이디 중복확인 결과 문구 (SignUpAccountScreen의 duplicateMessage로 전달)
    var idCheckMessage by mutableStateOf<String?>(null)
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
    // 아이디 중복확인 — '확인' 버튼에서 호출
    // ============================================
    fun checkDuplicateId(userId: String) {
        val trimmed = userId.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            idCheckMessage = "확인 중..."
            authRepository.isIdAvailable(trimmed)
                .onSuccess { available ->
                    idCheckMessage =
                        if (available) "사용 가능한 아이디입니다."
                        else "이미 사용 중인 아이디입니다."
                }
                .onFailure {
                    idCheckMessage = "확인에 실패했습니다. 다시 시도해 주세요."
                }
        }
    }

    // ============================================
    // 최종 제출 — 프로필 마지막 단계에서 호출
    // ============================================
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