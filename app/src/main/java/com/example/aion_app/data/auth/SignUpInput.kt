package com.example.aion_app.data.auth

// 회원가입 화면에서 입력한 값 (가입 유형, 아이디, 이메일, 비밀번호)
// email은 교사 계정에서만 사용한다. 비밀번호 재설정 메일 발송에 필요.
// 아동 계정은 이메일이 없으므로 빈 문자열로 두고, 내부적으로 가짜 주소를 만들어 쓴다.
data class SignUpInput(
    val type: String = "",
    val userId: String = "",
    val email: String = "",
    val password: String = ""
)