package com.example.aion_app.monitor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.aion_app.monitor.rule.RuleModelV3
import com.example.aion_app.monitor.rule.SoftVoting

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

    // 학습모델(TFLite) 점수 0~1. 판정 전이거나 사람이 잘 안 잡힌 구간이면 null
    // 쓰는 곳: StereotypyDetectionHost (0.5초마다 갱신)
    var mlScore by mutableStateOf<Float?>(null)

    // 학습모델 알람 (0.5 이상 2번 연속이면 켜짐, 0.35 아래면 꺼짐 — model_meta.json 값)
    var mlAlarm by mutableStateOf(false)

    // ---------- 규칙모델 v3 + 소프트 보팅 (0.5초마다 갱신) ----------
    // 쓰는 곳: StereotypyDetectionHost
    // RuleModelV3.USE_FOR_ALARM = true 일 때만 detected 를 이 결과(위험 단계)로 정한다.

    // 규칙모델 v3 의 최근 2초 판정 (행동별 점수, 가장 높은 행동, 터치 정도)
    var ruleV3 by mutableStateOf<RuleModelV3.Tick?>(null)

    // 규칙 확률 + 학습 확률 가중 평균 → 4초 지속 → 단계(0 안정 / 1 주의 / 2 위험)
    var ensemble by mutableStateOf<SoftVoting.Result?>(null)
}
