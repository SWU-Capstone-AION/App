package com.example.aion_app.function.password.eunseo.data

/**
 * 비밀번호 관련 작업을 추상화한 Repository.
 *
 * 서버/DB가 확정되지 않은 시점이므로 인터페이스로 분리한다.
 * 실제 API가 정해지면 RemotePasswordRepository(api: PasswordApi) 같은 구현체를
 * 새로 만들어서 갈아끼우면 된다.
 */
interface PasswordRepository {

    /**
     * 아이디 + 현재 비밀번호 검증.
     * (화면 1: 비밀번호 변경 전 확인 화면에서 사용)
     */
    suspend fun verifyCurrentPassword(userId: String, currentPassword: String): PasswordResult

    /**
     * 비밀번호 변경.
     * (화면 2에서 사용 — 다음 PR에서 활용)
     */
    suspend fun changePassword(
        userId: String,
        currentPassword: String,
        newPassword: String
    ): PasswordResult

    /**
     * 아이디로 마스킹된 비밀번호 조회.
     * (화면 3 비밀번호 찾기 — 다음 PR에서 활용)
     */
    suspend fun findMaskedPassword(userId: String): MaskedPasswordResult
}

/**
 * 비밀번호 찾기 결과. 일반 PasswordResult와 결이 달라서 분리.
 */
sealed class MaskedPasswordResult {
    data class Success(val maskedPassword: String) : MaskedPasswordResult()
    object UserNotFound : MaskedPasswordResult()
    data class Error(val message: String) : MaskedPasswordResult()
}
