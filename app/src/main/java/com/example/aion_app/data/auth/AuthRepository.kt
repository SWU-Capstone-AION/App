package com.example.aion_app.data.auth

import com.example.aion_app.ui.screen.login.ChildProfile
import kotlinx.coroutines.delay

// ============================================
// 회원가입 Repository
// ============================================
interface AuthRepository {
    /** 아이디 사용 가능 여부. true면 사용 가능. */
    suspend fun isIdAvailable(loginId: String): Result<Boolean>

    /** 계정 생성 + 아동 프로필 저장 */
    suspend fun register(signUpInput: SignUpInput, childProfile: ChildProfile): Result<Unit>
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
}