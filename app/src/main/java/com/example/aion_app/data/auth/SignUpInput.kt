package com.example.aion_app.data.auth

// 회원가입 화면에서 입력한 값 (가입 유형, 이메일, 비밀번호)
// 백엔드 붙일 때 이 데이터를 그대로 요청 DTO로 옮기면 됨
data class SignUpInput(
    val type: String = "",
    val email: String = "",
    val password: String = ""
)