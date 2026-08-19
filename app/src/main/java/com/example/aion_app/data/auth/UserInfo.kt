package com.example.aion_app.data.auth

/**
 * 로그인한 사용자 정보 (users/{uid} 문서를 그대로 담는다).
 *
 * 감각특성·상동행동은 아동 계정에만 값이 들어간다.
 */
data class UserInfo(
    val uid: String = "",
    val role: UserRole = UserRole.TEACHER,
    val loginId: String = "",
    val email: String = "",
    val name: String = "",
    val gender: String? = null,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val birthDay: Int? = null,
    val sensoryTraits: List<String> = emptyList(),
    val behaviors: List<String> = emptyList(),
) {
    /** "2019.12.21" 형태. 값이 없으면 빈 문자열. */
    val birthDateText: String
        get() = if (birthYear == null || birthMonth == null || birthDay == null) ""
        else "%04d.%02d.%02d".format(birthYear, birthMonth, birthDay)
}