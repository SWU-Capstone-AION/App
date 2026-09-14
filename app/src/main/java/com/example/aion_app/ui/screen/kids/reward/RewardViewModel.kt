package com.example.aion_app.ui.screen.kids.reward

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
 * 상자 팝업이 지금 어떤 상태인지. 시안의 네 장면과 그대로 이어진다.
 *
 *   CLOSED  "우와, 끝까지 해냈어!"   닫힌 상자 (누를 수 있음)
 *   SHAKING "두근두근..."            흔들리는 중 (약 1.2초)
 *   OPENED  "짜잔!"                  상자가 사라지고 구슬이 나온다
 *   STORED  "구슬 주머니에 쏙 넣었어!" 확인 버튼만 있는 마지막 장면
 */
enum class BoxPhase { CLOSED, SHAKING, OPENED, STORED }

// ============================================================
// 구슬 보상
// ============================================================
// 저장은 RewardStore(기기 파일), 애니메이션은 화면이 맡는다.
// 이 클래스는 그 사이에서 "언제 구슬을 뽑을지" 만 정한다.
//
// 애니메이션 시간·각도는 여기서 모른다. 화면이 흔들림을 끝내고
// onShakeFinished() 를 불러주면 그때 구슬을 뽑는다.
// 덕분에 나중에 애니메이션을 바꿔도 이 파일은 건드릴 필요가 없다.
//
// Context 가 필요해서 AndroidViewModel 을 쓴다 (모니터링 쪽과 같은 방식).
class RewardViewModel(app: Application) : AndroidViewModel(app) {

    private val store = RewardStore(app)

    /**
     * 받아뒀지만 아직 안 연 상자 개수.
     * 0 보다 크면 홈에 상자 팝업이 뜬다. 하나 열면 하나 줄어든다.
     *
     * 개수를 세는 이유: 미니게임을 끝내면 그 화면에서 상자를 받는데,
     * 팝업은 홈으로 돌아온 뒤에 떠야 한다. 그 사이를 이 값이 들고 있는다.
     */
    val pendingBoxes: StateFlow<Int> = store.unopenedBoxes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 얻은 순서대로 늘어놓은 구슬 목록 — 구슬 주머니 팝업이 정렬해서 쓴다 */
    val marbleHistory: StateFlow<List<MarbleRecord>> = store.marbleHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 모은 구슬 총 개수 — 홈 상단 배지 */
    val totalMarbles: StateFlow<Int> = store.totalMarbles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _boxPhase = MutableStateFlow(BoxPhase.CLOSED)
    val boxPhase: StateFlow<BoxPhase> = _boxPhase.asStateFlow()

    /** 방금 뽑은 구슬. 상자를 열기 전에는 null */
    private val _openedMarble = MutableStateFlow<Marble?>(null)
    val openedMarble: StateFlow<Marble?> = _openedMarble.asStateFlow()

    /**
     * 활동을 마쳤을 때 부른다. 상자가 1개 생긴다.
     *
     * 미니게임은 카메라 결과를 처리하는 백그라운드 스레드에서 부르게 되는데,
     * viewModelScope 가 알아서 메인으로 넘겨주므로 그대로 불러도 된다.
     */
    fun grant(source: RewardSource) {
        viewModelScope.launch { store.grant(source) }
    }

    /** 아이가 상자를 탭했을 때. 흔들림 시작 */
    fun onBoxTapped() {
        if (_boxPhase.value != BoxPhase.CLOSED) return   // 흔드는 중에 또 눌러도 무시
        if (pendingBoxes.value <= 0) return
        _boxPhase.value = BoxPhase.SHAKING
    }

    /** 흔들림 애니메이션이 끝나면 화면이 불러준다. 여기서 구슬을 뽑는다 */
    fun onShakeFinished() {
        if (_boxPhase.value != BoxPhase.SHAKING) return

        viewModelScope.launch {
            val marble = store.openBox()
            if (marble == null) {
                _boxPhase.value = BoxPhase.CLOSED   // 상자가 없는 예외 상황
                return@launch
            }
            _openedMarble.value = marble
            _boxPhase.value = BoxPhase.OPENED
        }
    }

    /**
     * 구슬을 충분히 보여준 뒤 마지막 장면("구슬 주머니에 쏙 넣었어!")으로 넘어간다.
     * 화면이 알아서 불러준다 — 아이가 버튼을 누를 필요가 없다.
     */
    fun onMarbleShown() {
        if (_boxPhase.value != BoxPhase.OPENED) return
        _boxPhase.value = BoxPhase.STORED
    }

    /**
     * 구슬을 확인하고 팝업을 닫을 때.
     *
     * 구슬은 흔들림이 끝난 시점에 이미 저장됐다.
     * 이 버튼을 안 누르고 앱을 꺼도 구슬은 남는다.
     * 받아둔 상자가 더 있으면 닫힌 상자가 바로 이어서 뜬다.
     */
    fun onRewardClosed() {
        _openedMarble.value = null
        _boxPhase.value = BoxPhase.CLOSED
    }

    /** 테스트용 — 모은 구슬과 상자를 전부 지운다 */
    fun clearAll() {
        viewModelScope.launch {
            store.clearAll()
            _openedMarble.value = null
            _boxPhase.value = BoxPhase.CLOSED
        }
    }
}