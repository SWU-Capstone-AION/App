package com.example.aion_app.ui.kids.reward

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 박스가 지금 어떤 상태인지.
 * CLOSED(닫힘) → SHAKING(흔들림) → OPENED(열림)
 */
enum class BoxPhase {
    CLOSED,
    SHAKING,
    OPENED
}

/**
 * 구슬 보상 전체를 관리한다.
 * Context가 필요해서 AndroidViewModel을 쓴다. (앱의 다른 화면들과 같은 방식)
 */
class RewardViewModel(app: Application) : AndroidViewModel(app) {

    private val store = RewardStore(app.applicationContext)

    /** 아직 안 연 박스 개수 — 0보다 크면 메인 홈에 박스를 보여준다 */
    val unopenedBoxes: StateFlow<Int> = store.unopenedBoxes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 색깔별 구슬 개수 — 구슬 주머니 화면에서 사용 */
    val marbleCounts: StateFlow<Map<Marble, Int>> = store.marbleCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 모은 구슬 총 개수 */
    val totalMarbles: StateFlow<Int> = store.totalMarbles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 박스 애니메이션 상태 */
    private val _boxPhase = MutableStateFlow(BoxPhase.CLOSED)
    val boxPhase: StateFlow<BoxPhase> = _boxPhase.asStateFlow()

    /** 방금 뽑은 구슬 (열기 전에는 null) */
    private val _openedMarble = MutableStateFlow<Marble?>(null)
    val openedMarble: StateFlow<Marble?> = _openedMarble.asStateFlow()

    /**
     * 활동을 마쳤을 때 부른다.
     * 호흡 가이드·잡초 뽑기·칠판 닦기 끝나는 자리에서 이 줄 하나만 부르면 된다.
     */
    fun grant(source: RewardSource) {
        viewModelScope.launch {
            store.grant(source)
        }
    }

    /**
     * 아이가 박스를 탭했을 때. 흔들림 시작.
     * 닫힌 상태일 때만 반응한다. (흔드는 중에 또 눌러도 무시)
     */
    fun onBoxTapped() {
        if (_boxPhase.value != BoxPhase.CLOSED) return
        if (unopenedBoxes.value <= 0) return
        _boxPhase.value = BoxPhase.SHAKING
    }

    /**
     * 흔들림 애니메이션이 끝나면 화면에서 이 함수를 부른다.
     * 구슬을 뽑아 주머니에 넣고 박스를 열린 상태로 바꾼다.
     */
    fun onShakeFinished() {
        if (_boxPhase.value != BoxPhase.SHAKING) return

        viewModelScope.launch {
            val marble = store.openBox()
            if (marble == null) {
                // 박스가 없는 예외 상황 — 그냥 닫힌 상태로 되돌린다
                _boxPhase.value = BoxPhase.CLOSED
                return@launch
            }
            _openedMarble.value = marble
            _boxPhase.value = BoxPhase.OPENED
        }
    }

    /**
     * 구슬을 확인하고 화면을 닫을 때.
     * 박스가 더 남아 있으면 다시 닫힌 박스로 돌아간다.
     */
    fun onRewardClosed() {
        _openedMarble.value = null
        _boxPhase.value = BoxPhase.CLOSED
    }

    /** 테스트용 — 모은 구슬과 박스를 전부 지운다 */
    fun clearAll() {
        viewModelScope.launch {
            store.clearAll()
            _openedMarble.value = null
            _boxPhase.value = BoxPhase.CLOSED
        }
    }
}