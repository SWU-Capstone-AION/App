package com.example.aion_app.monitor.pose

/**
 * 미니게임(잡초 뽑기 등) 진행 여부를 알리는 전역 스위치.
 *
 * 잡초를 뽑는 동작은 팔의 반복 상하 운동이라 [StereotypyDetector] 의 판정 조건에
 * 그대로 걸린다. 게임 중에 교사폰으로 위험 알림이 나가는 걸 막으려면 판정을 멈춰야 한다.
 *
 * 게임 화면과 모니터링 화면은 서로 다른 라우트라 검출기 인스턴스를 직접 넘길 수 없다.
 * 그래서 상태만 여기 한 곳에 두고, 양쪽이 각자 읽고 쓴다.
 *
 * 쓰기: 게임 화면(메인 스레드) / 읽기: MediaPipe 결과 콜백(백그라운드 스레드)
 * → 서로 다른 스레드에서 접근하므로 @Volatile 이 필요하다.
 */
object MinigameGate {

    @Volatile
    var active: Boolean = false
}
