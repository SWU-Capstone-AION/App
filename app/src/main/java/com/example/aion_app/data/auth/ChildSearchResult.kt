package com.example.aion_app.data.auth

/** 아이디로 찾은 아동 계정 정보 */
data class ChildSearchResult(
    val uid: String,
    val loginId: String,
    val name: String,
    val gender: String,          // "남" / "여"
    val birthDateText: String,   // "2019.05.17"
    /** 이미 다른 교사에게 연결된 아동인지 */
    val alreadyLinked: Boolean,
    /** 연결 요청을 보내둔 상태인지 */
    val hasPendingRequest: Boolean,
)