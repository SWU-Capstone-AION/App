package com.example.aion_app.data.auth

import com.example.aion_app.ui.screen.login.ChildProfile
import kotlinx.coroutines.delay

// ============================================
// 회원가입 Repository
// ============================================
// "서버에 데이터를 보낸다"는 행위 자체를 인터페이스로 분리해 둠.
// ViewModel/화면 쪽 코드는 이 인터페이스만 알고 있으면 되고,
// 실제 구현체(Fake ↔ Retrofit)는 자유롭게 교체 가능.
interface AuthRepository {
    // 회원가입 + 아동 프로필을 한 번에 등록한다고 가정.
    // (백엔드 API 스펙에 따라 회원가입/프로필 등록을 분리해야 하면 함수도 분리하면 됨)
    suspend fun register(signUpInput: SignUpInput, childProfile: ChildProfile): Result<Unit>
}

// ============================================
// 가짜(Fake) 구현체 — 백엔드 준비되기 전까지 사용
// ============================================
// TODO: 백엔드 API 준비되면 이 클래스를 지우고 RetrofitAuthRepository로 교체.
// 예시:
//   class RetrofitAuthRepository(private val api: AuthApiService) : AuthRepository {
//       override suspend fun register(signUpInput: SignUpInput, childProfile: ChildProfile): Result<Unit> {
//           return try {
//               api.signUp(SignUpRequestDto(signUpInput, childProfile))
//               Result.success(Unit)
//           } catch (e: Exception) {
//               Result.failure(e)
//           }
//       }
//   }
// ViewModel 쪽 코드는 한 줄도 안 바꿔도 됨 (인터페이스가 같으니까).
class FakeAuthRepository : AuthRepository {
    override suspend fun register(signUpInput: SignUpInput, childProfile: ChildProfile): Result<Unit> {
        delay(800) // 네트워크 호출 흉내 (로딩 상태 확인용)

        // 아주 기초적인 검증만 흉내냄 — 실제 검증은 서버가 하게 될 부분
        if (signUpInput.userId.isBlank() || signUpInput.password.isBlank()) {
            return Result.failure(IllegalArgumentException("아이디/비밀번호가 비어있습니다."))
        }

        return Result.success(Unit)
    }
}