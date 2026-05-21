package com.example.aion_app.function.password.eunseo.data

/**
 * Repository 호출 결과를 표현하는 sealed class.
 * 실제 서버 연동 시 네트워크 에러 등 케이스를 추가하면 된다.
 */
sealed class PasswordResult {
    /** 성공 */
    object Success : PasswordResult()

    /** 아이디가 존재하지 않음 */
    object UserNotFound : PasswordResult()

    /** 비밀번호가 일치하지 않음 */
    object PasswordMismatch : PasswordResult()

    /** 그 외 알 수 없는 에러 (네트워크 등). 추후 확장용. */
    data class Error(val message: String) : PasswordResult()
}
