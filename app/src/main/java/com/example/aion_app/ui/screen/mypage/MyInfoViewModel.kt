package com.example.aion_app.ui.screen.mypage

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.auth.AuthRepository
import com.example.aion_app.data.auth.FirebaseAuthRepository
import com.example.aion_app.data.auth.UserRole
import kotlinx.coroutines.launch

// 내 정보 데이터
// 감각특성·행동특성은 아동 계정에만 값이 들어간다.
data class MyInfo(
    val role: UserRole = UserRole.TEACHER,
    val name: String = "",
    val gender: String = "",
    val birthDate: String = "",
    val sensitiveStimuli: List<String> = emptyList(),
    val behaviorTraits: List<String> = emptyList(),
    val profileImageUri: Uri? = null
)

class MyInfoViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository()
) : ViewModel() {

    // 외부에서 읽을 수 있는 state
    var myInfo by mutableStateOf(MyInfo())
        private set  // 외부에서는 직접 수정 못 함, 아래 함수로만 가능

    var isLoading by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    /** 로그인한 계정 정보를 Firestore에서 읽어온다. */
    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            authRepository.getCurrentUser()
                .onSuccess { user ->
                    myInfo = MyInfo(
                        role = user.role,
                        name = user.name,
                        gender = user.gender.orEmpty(),
                        birthDate = user.birthDateText,
                        sensitiveStimuli = user.sensoryTraits,
                        behaviorTraits = user.behaviors,
                        // 프로필 사진은 아직 서버에 저장하지 않는다.
                        // (기기 안의 경로라 재설치하거나 다른 기기에서 로그인하면 사라짐)
                        profileImageUri = myInfo.profileImageUri,
                    )
                }
                .onFailure { error ->
                    errorMessage = error.message ?: "정보를 불러오지 못했습니다."
                }

            isLoading = false
        }
    }

    // 정보 업데이트 함수 — 화면 상태와 Firestore를 함께 갱신한다
    fun updateMyInfo(newInfo: MyInfo) {
        // 역할은 수정 화면에서 다루지 않으므로 기존 값을 유지한다
        myInfo = newInfo.copy(role = myInfo.role)

        val (year, month, day) = newInfo.birthDate.toYearMonthDay()
        viewModelScope.launch {
            authRepository.updateProfile(
                name = newInfo.name,
                gender = newInfo.gender.ifBlank { null },
                birthYear = year,
                birthMonth = month,
                birthDay = day,
            ).onFailure { error ->
                errorMessage = error.message ?: "저장에 실패했습니다."
            }
        }
    }
}

/** "2019.12.21" → (2019, 12, 21). 형식이 안 맞으면 전부 null. */
private fun String.toYearMonthDay(): Triple<Int?, Int?, Int?> {
    val parts = split(".")
    if (parts.size != 3) return Triple(null, null, null)
    return Triple(parts[0].toIntOrNull(), parts[1].toIntOrNull(), parts[2].toIntOrNull())
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