package com.example.aion_app.function.password.eunseo.data

import kotlinx.coroutines.delay

/**
 * 서버 없이 동작하는 가짜 Repository.
 *
 * 테스트 계정:
 *   - id: "testuser",  pw: "test1234"
 *   - id: "aion_user", pw: "aion2025"
 *
 * 의도적으로 약간의 delay를 줘서 실제 네트워크처럼 비동기 흐름을 만든다.
 */
class MockPasswordRepository : PasswordRepository {

    private val mockUsers: MutableMap<String, String> = mutableMapOf(
        "testuser" to "test1234",
        "aion_user" to "aion2025"
    )

    override suspend fun verifyCurrentPassword(
        userId: String,
        currentPassword: String
    ): PasswordResult {
        delay(MOCK_DELAY_MS)
        val storedPw = mockUsers[userId] ?: return PasswordResult.UserNotFound
        return if (storedPw == currentPassword) {
            PasswordResult.Success
        } else {
            PasswordResult.PasswordMismatch
        }
    }

    override suspend fun changePassword(
        userId: String,
        currentPassword: String,
        newPassword: String
    ): PasswordResult {
        delay(MOCK_DELAY_MS)
        val storedPw = mockUsers[userId] ?: return PasswordResult.UserNotFound
        if (storedPw != currentPassword) return PasswordResult.PasswordMismatch
        mockUsers[userId] = newPassword
        return PasswordResult.Success
    }

    override suspend fun findMaskedPassword(userId: String): MaskedPasswordResult {
        delay(MOCK_DELAY_MS)
        val storedPw = mockUsers[userId] ?: return MaskedPasswordResult.UserNotFound
        return MaskedPasswordResult.Success(mask(storedPw))
    }

    /** 비밀번호 마스킹: 앞 2자만 보이고 나머지는 *. */
    private fun mask(password: String): String {
        if (password.length <= 2) return "*".repeat(password.length)
        return password.take(2) + "*".repeat(password.length - 2)
    }

    companion object {
        private const val MOCK_DELAY_MS = 300L
    }
}
