package com.example.aion_app.ui.screen.kids

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.alert.AlertRepository
import kotlinx.coroutines.launch

// ============================================
// 아동 앱 — 도움 요청
// ============================================
class HelpRequestViewModel(
    private val alertRepository: AlertRepository = AlertRepository()
) : ViewModel() {

    /** 담당 교사에게 도움 요청 알림을 보낸다. 실패는 조용히 넘어간다. */
    fun requestHelp() {
        viewModelScope.launch {
            alertRepository.requestHelp()
        }
    }
}