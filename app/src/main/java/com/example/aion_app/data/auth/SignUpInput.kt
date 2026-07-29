package com.example.aion_app.data.auth

// 회원가입 화면에서 입력한 값 (가입 유형, 아이디, 비밀번호)
// 시안이 이메일 → 아이디 방식으로 바뀌어 필드명을 userId 로 변경.
// 백엔드 붙일 때 이 데이터를 그대로 요청 DTO로 옮기면 됨
// (※ 실제 API 필드명이 userId / loginId / account 중 무엇인지는 백엔드와 맞출 것)
data class SignUpInput(
    val type: String = "",
    val userId: String = "",
    val password: String = ""
)