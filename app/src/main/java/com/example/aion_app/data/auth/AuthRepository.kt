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

    /** 이름 + 이메일이 모두 일치하는 계정의 아이디를 찾는다. */
    suspend fun findLoginId(name: String, email: String): Result<String>

    /** 아이디로 가입 이메일을 찾아 비밀번호 재설정 메일 발송 */
    suspend fun sendPasswordReset(loginId: String): Result<Unit>

    /** 현재 로그인한 사용자 정보를 읽는다. */
    suspend fun getCurrentUser(): Result<UserInfo>

    /** 이름·성별·생년월일 수정 */
    suspend fun updateProfile(
        name: String,
        gender: String?,
        birthYear: Int?,
        birthMonth: Int?,
        birthDay: Int?
    ): Result<Unit>

    // ===== 담당 아동 연결 =====

    /** 아이디로 아동 계정을 찾는다. 없으면 null. */
    suspend fun searchChildByLoginId(loginId: String): Result<ChildSearchResult?>

    /** 아동에게 학급 연결을 요청한다. (아동이 수락해야 실제로 연결됨) */
    suspend fun requestChildLink(childUid: String): Result<Unit>

    /** 현재 로그인한 교사에게 연결된 아동 목록 */
    suspend fun getMyChildren(): Result<List<LinkedChild>>

    /** 아동 앱에서 호출 — 받아둔 초대가 있으면 돌려준다. */
    suspend fun getPendingInvite(): Result<TeacherInvite?>

    /** 아동 앱에서 호출 — 초대 수락 또는 거절 */
    suspend fun respondToInvite(accept: Boolean): Result<Unit>

    /** 로그아웃. 이 기기로 알림이 계속 가지 않도록 FCM 토큰도 지운다. */
    suspend fun logout()
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

    override suspend fun findLoginId(name: String, email: String): Result<String> {
        delay(600)

        if (name.isBlank() || email.isBlank()) {
            return Result.failure(IllegalArgumentException("이름과 이메일을 입력해 주세요."))
        }

        // 실패 화면도 확인해볼 수 있게, 이름이 '없음'이면 못 찾은 것으로 처리
        if (name.trim() == "없음") {
            return Result.failure(IllegalStateException("일치하는 가입 정보가 없습니다."))
        }

        return Result.success("aion${name.trim().length}2026")
    }

    override suspend fun sendPasswordReset(loginId: String): Result<Unit> {
        delay(600)
        return Result.success(Unit)
    }

    override suspend fun getCurrentUser(): Result<UserInfo> {
        delay(400)
        return Result.success(
            UserInfo(
                uid = "fake-uid",
                role = UserRole.TEACHER,
                loginId = "test",
                email = "test@example.com",
                name = "김슈니",
                gender = "여자",
                birthYear = 1999,
                birthMonth = 5,
                birthDay = 12,
            )
        )
    }

    override suspend fun updateProfile(
        name: String,
        gender: String?,
        birthYear: Int?,
        birthMonth: Int?,
        birthDay: Int?
    ): Result<Unit> {
        delay(500)
        return Result.success(Unit)
    }

    override suspend fun searchChildByLoginId(loginId: String): Result<ChildSearchResult?> {
        delay(600)

        // 검색 결과 없음 화면도 확인해볼 수 있게, none으로 시작하면 못 찾은 것으로 처리
        if (loginId.trim().startsWith("none")) return Result.success(null)

        return Result.success(
            ChildSearchResult(
                uid = "fake-child-uid",
                loginId = loginId.trim(),
                name = "김지우",
                gender = "남",
                birthDateText = "2019.05.17",
                alreadyLinked = false,
                hasPendingRequest = false,
            )
        )
    }

    override suspend fun requestChildLink(childUid: String): Result<Unit> {
        delay(500)
        return Result.success(Unit)
    }

    override suspend fun getMyChildren(): Result<List<LinkedChild>> {
        delay(400)
        return Result.success(
            listOf(
                LinkedChild("uid1", "Jiwoo_0517", "김지우", "남", 9),
                LinkedChild("uid2", "Jumi_0203", "이주미", "여", 9),
            )
        )
    }

    override suspend fun getPendingInvite(): Result<TeacherInvite?> {
        delay(400)
        return Result.success(
            TeacherInvite(teacherUid = "fake-teacher-uid", teacherName = "박서연")
        )
    }

    override suspend fun respondToInvite(accept: Boolean): Result<Unit> {
        delay(500)
        return Result.success(Unit)
    }

    override suspend fun logout() = Unit
}