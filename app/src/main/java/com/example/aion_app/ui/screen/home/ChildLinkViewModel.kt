package com.example.aion_app.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.auth.AuthRepository
import com.example.aion_app.data.auth.ChildSearchResult
import com.example.aion_app.data.auth.FirebaseAuthRepository
import kotlinx.coroutines.launch

// ============================================
// 담당 아동 연결 화면용 ViewModel
// ============================================
class ChildLinkViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository()
) : ViewModel() {

    var isSearching by mutableStateOf(false)
        private set

    /** 검색을 한 번이라도 했는지. '찾는 아이가 없나요?' 안내를 언제 띄울지 판단용. */
    var hasSearched by mutableStateOf(false)
        private set

    var searchResult by mutableStateOf<ChildSearchResult?>(null)
        private set

    var isRequesting by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun search(loginId: String) {
        if (isSearching || loginId.isBlank()) return

        isSearching = true
        errorMessage = null
        searchResult = null

        viewModelScope.launch {
            authRepository.searchChildByLoginId(loginId)
                .onSuccess { result -> searchResult = result }
                .onFailure { error ->
                    errorMessage = error.message ?: "검색에 실패했습니다."
                }

            hasSearched = true
            isSearching = false
        }
    }

    /** 연결 요청 전송. 아동이 태블릿에서 수락해야 실제로 연결된다. */
    fun requestLink(childUid: String, onSuccess: () -> Unit) {
        if (isRequesting) return

        isRequesting = true
        errorMessage = null

        viewModelScope.launch {
            authRepository.requestChildLink(childUid)
                .onSuccess { onSuccess() }
                .onFailure { error ->
                    errorMessage = error.message ?: "연결 요청에 실패했습니다."
                }

            isRequesting = false
        }
    }
}