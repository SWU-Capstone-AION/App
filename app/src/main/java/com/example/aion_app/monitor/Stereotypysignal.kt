package com.example.aion_app.monitor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// ============================================================
// 상동행동 감지 결과를 화면들에 전달하는 통로
// ============================================================
// 감지 파이프라인(StereotypyDetectionHost)은 NavHost 바깥에 있고,
// 결과를 쓰는 곳(KidsHomeScreen)은 NavHost 안쪽에 있다.
// 값을 파라미터로 흘려보내면 NavHost 의 builder 람다가 매번 새로 만들어져
// 내비게이션 그래프가 재생성되는 문제가 생긴다.
//
// 그래서 값은 여기에 두고, 읽는 쪽이 직접 구독한다.
// mutableStateOf 라서 값이 바뀌면 읽는 컴포저블만 리컴포즈된다.
object StereotypySignal {

    // StereotypyDetector.State.anyAlarm
    // 쓰는 곳: StereotypyDetectionHost / 읽는 곳: KidsHomeScreen
    var detected by mutableStateOf(false)
}