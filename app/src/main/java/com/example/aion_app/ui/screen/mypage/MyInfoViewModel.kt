package com.example.aion_app.ui.screen.mypage

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

// 내 정보 데이터
data class MyInfo(
    val name: String = "김슈니",
    val gender: String = "남자",
    val birthDate: String = "2019.12.21",
    val sensitiveStimuli: List<String> = listOf("시각", "청각"),
    val behaviorTraits: List<String> = listOf("손이나 팔을 흔들어요", "박수치듯 손을 맞부딪혀요"),
    val profileImageUri: Uri? = null
)

class MyInfoViewModel : ViewModel() {

    // 외부에서 읽을 수 있는 state
    var myInfo by mutableStateOf(MyInfo())
        private set  // 외부에서는 직접 수정 못 함, 아래 함수로만 가능

    // 정보 업데이트 함수
    fun updateMyInfo(newInfo: MyInfo) {
        myInfo = newInfo
    }
}

// "2019.12.21" → 만 나이 계산
fun calculateAge(birthDateString: String): Int {
    return try {
        val parts = birthDateString.split(".")
        val birthYear = parts[0].toInt()
        val birthMonth = parts[1].toInt()
        val birthDay = parts[2].toInt()

        val today = java.util.Calendar.getInstance()
        val currentYear = today.get(java.util.Calendar.YEAR)
        val currentMonth = today.get(java.util.Calendar.MONTH) + 1  // 0부터 시작
        val currentDay = today.get(java.util.Calendar.DAY_OF_MONTH)

        var age = currentYear - birthYear

        // 아직 올해 생일이 안 지났으면 -1
        if (currentMonth < birthMonth || (currentMonth == birthMonth && currentDay < birthDay)) {
            age--
        }

        age
    } catch (e: Exception) {
        0  // 에러 시 0살 처리
    }
}