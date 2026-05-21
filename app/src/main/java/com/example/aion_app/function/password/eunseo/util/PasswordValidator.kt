package com.example.aion_app.function.password.eunseo.util

/**
 * 비밀번호 유효성 검사 유틸.
 *
 * 규칙:
 *   - 영문(대소문자 무관) + 숫자 모두 포함
 *   - 길이 8자 이상
 *
 * 화면 2 (비밀번호 변경) / 화면 3 에서 공통 사용.
 * Object로 만들어서 어디서든 부담 없이 호출할 수 있게 함.
 */
object PasswordValidator {

    private const val MIN_LENGTH = 8
    private val LETTER_REGEX = Regex("[A-Za-z]")
    private val DIGIT_REGEX = Regex("\\d")

    /**
     * 비밀번호 형식 검증.
     * @return null 이면 유효, 아니면 사용자에게 보여줄 에러 메시지 키
     */
    fun validate(password: String): PasswordValidationError? {
        if (password.length < MIN_LENGTH) return PasswordValidationError.TOO_SHORT
        val hasLetter = LETTER_REGEX.containsMatchIn(password)
        val hasDigit = DIGIT_REGEX.containsMatchIn(password)
        if (!hasLetter || !hasDigit) return PasswordValidationError.NEEDS_LETTER_AND_DIGIT
        return null
    }

    /** 새 비밀번호 / 확인 비밀번호 일치 검증. */
    fun matches(newPassword: String, confirmPassword: String): Boolean =
        newPassword == confirmPassword
}

enum class PasswordValidationError {
    /** 8자 미만 */
    TOO_SHORT,

    /** 영문 또는 숫자 누락 */
    NEEDS_LETTER_AND_DIGIT
}
