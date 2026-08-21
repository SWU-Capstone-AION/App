package com.example.aion_app.ui.screen.kids

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.auth.AuthRepository
import com.example.aion_app.data.auth.FirebaseAuthRepository
import com.example.aion_app.data.auth.TeacherInvite
import kotlinx.coroutines.launch

// ============================================
// 아동 앱 — 학급 초대 확인/응답
// ============================================
// 홈에 들어올 때 받아둔 초대가 있는지 확인하고,
// 있으면 팝업으로 보여준다.
class ChildInviteViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository()
) : ViewModel() {

    var invite by mutableStateOf<TeacherInvite?>(null)
        private set

    var isResponding by mutableStateOf(false)
        private set

    init {
        loadInvite()
    }

    fun loadInvite() {
        viewModelScope.launch {
            authRepository.getPendingInvite()
                .onSuccess { result -> invite = result }
                // 초대 조회 실패는 아이에게 보여줄 필요가 없다. 조용히 넘어간다.
                .onFailure { invite = null }
        }
    }

    fun respond(accept: Boolean) {
        if (isResponding) return

        isResponding = true

        viewModelScope.launch {
            authRepository.respondToInvite(accept)
            // 성공이든 실패든 팝업은 닫는다.
            // 실패했다면 다음에 홈에 들어올 때 다시 뜬다.
            invite = null
            isResponding = false
        }
    }
}