package com.example.aion_app.data.auth

/** 교사에게 연결이 확정된 아동 */
data class LinkedChild(
    val uid: String,
    val loginId: String,
    val name: String,
    val gender: String,   // "남" / "여"
    val age: Int,
)