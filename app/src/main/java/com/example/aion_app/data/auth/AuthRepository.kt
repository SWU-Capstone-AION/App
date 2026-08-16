package com.example.aion_app.data.auth

import com.example.aion_app.ui.screen.login.ChildProfile
import kotlinx.coroutines.delay

// ============================================
// 인증 Repository
// ============================================
interface AuthRepository {
    /** 아이디 사용 가능 여부. true면 사용 가능. */
    suspend fun isIdAvailable(loginId: String): Result<Boolean>

    /** 계정 생성 + 아동 프로필 저장 */
    suspend fun register(signUpInput: SignUpInput, childProfile: ChildProfile): Result<Unit>

    /** 로그인. 성공하면 계정 유형을 돌려준다. */
    suspend fun login(loginId: String, password: String): Result<UserRole>

    /** 아이디로 가입 이메일을 찾아 비밀번호 재설정 메일 발송 */
    suspend fun sendPasswordReset(loginId: String): Result<Unit>

    /** 로그아웃 */
    fun logout()
}

// ============================================
// 가짜(Fake) 구현체 — 인터넷 없이 UI만 테스트할 때 사용
// ============================================
// 실제 연동은 FirebaseAuthRepository 를 쓴다. 이 클래스는 지우지 말 것.
class FakeAuthRepository : AuthRepository {

    // 중복 상황을 화면에서 확인해보기 위한 더미 목록
    private val takenIds = setOf("test", "admin", "aion")

    override suspend fun isIdAvailable(loginId: String): Result<Boolean> {
        delay(400)
        return Result.success(loginId.trim().lowercase() !in takenIds)
    }

    override suspend fun register(
        signUpInput: SignUpInput,
        childProfile: ChildProfile
    ): Result<Unit> {
        delay(800) // 네트워크 호출 흉내 (로딩 상태 확인용)

        if (signUpInput.userId.isBlank() || signUpInput.password.isBlank()) {
            return Result.failure(IllegalArgumentException("아이디/비밀번호가 비어있습니다."))
        }

        return Result.success(Unit)
    }

    override suspend fun login(loginId: String, password: String): Result<UserRole> {
        delay(600)

        if (loginId.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("아이디와 비밀번호를 입력해 주세요."))
        }

        // 역할 분기를 화면에서 확인해보기 위한 규칙 — 아이디가 child 로 시작하면 아동으로 취급
        val role =
            if (loginId.trim().lowercase().startsWith("child")) UserRole.CHILD
            else UserRole.TEACHER

        return Result.success(role)
    }

    override suspend fun sendPasswordReset(loginId: String): Result<Unit> {
        delay(600)
        return Result.success(Unit)
    }

    override fun logout() = Unit
}