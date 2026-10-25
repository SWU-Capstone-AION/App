package com.example.aion_app.monitor.rule

import kotlin.math.max

// ============================================================
// 태블릿 화면 터치 기록 → 기능적 행동(목적 있는 반복) 판단
// ============================================================
// 화면을 두드리는 동안의 손·팔 반복은 목적이 있는 행동이라 상동행동 점수를 낮춘다.
//   f = max(최근 2초 중 화면을 누르고 있던 시간 비율, 탭 횟수 ÷ 2)  (0~1)
//   손을 뗀 뒤 0.4초까지는 '터치 중'으로 본다 (손이 화면에서 떨어지는 동작 포함)
//   반복 동작이 필요한 게임 중(gameActive)에는 f = 1
//
// 쓰는 곳
//   MainActivity.dispatchTouchEvent → onDown / onUp   (MotionEvent.eventTime = uptimeMillis)
//   StereotypyDetectionHost         → context(포즈 타임스탬프)  (PoseLandmarkerHelper 도 uptimeMillis)
//   두 시각이 같은 기준(SystemClock.uptimeMillis)이어야 한다.
// ============================================================
object TouchTracker {
    private const val KEEP_MS = 10_000L
    private val downs = ArrayDeque<Long>()
    private val spans = ArrayDeque<LongArray>()   // [누른 시각, 뗀 시각(-1 = 아직 누르는 중)]

    /** 반복 동작이 필요한 게임 중이면 true (손·팔 행동 점수를 낮춤) */
    @Volatile var gameActive = false

    @Synchronized fun onDown(tMs: Long) {
        downs.addLast(tMs)
        spans.addLast(longArrayOf(tMs, -1L))
        trim(tMs)
    }

    @Synchronized fun onUp(tMs: Long) {
        spans.firstOrNull { it[1] == -1L }?.let { it[1] = tMs }
    }

    /** 터치 취소 (스크롤로 넘어감 등): 누르고 있던 손가락을 모두 뗀 것으로 */
    @Synchronized fun onCancel(tMs: Long) {
        spans.filter { it[1] == -1L }.forEach { it[1] = tMs }
    }

    @Synchronized fun isDown(): Boolean = spans.any { it[1] == -1L }

    @Synchronized fun reset() { downs.clear(); spans.clear() }

    private fun trim(now: Long) {
        val cut = now - KEEP_MS
        while (downs.isNotEmpty() && downs.first() < cut) downs.removeFirst()
        while (spans.isNotEmpty() && spans.first()[1] in 0 until cut) spans.removeFirst()
    }

    @Synchronized fun context(nowMs: Long): RuleModelV3.TouchContext {
        val win = (RuleConfig.WINDOW_S * 1000).toLong()
        val w0 = nowMs - win
        // 누르고 있던 시간 (+뗀 뒤 0.4초). 여러 손가락이 겹쳐도 한 번만 셈
        val segs = spans.map { (a, b) ->
            max(a, w0) to minOf(if (b < 0) nowMs else b + RuleConfig.TOUCH_TAIL_MS, nowMs)
        }.filter { it.second > it.first }.sortedBy { it.first }
        var covered = 0L; var s = -1L; var e = -1L
        for ((a, b) in segs) {
            if (s < 0) { s = a; e = b } else if (a <= e) e = max(e, b) else { covered += e - s; s = a; e = b }
        }
        if (s >= 0) covered += e - s
        val taps = downs.count { it >= w0 && it <= nowMs }
        var f = max(covered.toDouble() / win, taps / 2.0).coerceIn(0.0, 1.0)
        if (gameActive) f = 1.0
        val rec = downs.filter { it >= nowMs - 4000 && it <= nowMs }
        val gaps = rec.zipWithNext { a, b -> (b - a) / 1000.0 }.sorted()
        val interval = if (gaps.size >= 2) gaps[gaps.size / 2] else null
        return RuleModelV3.TouchContext(f, taps, interval, gameActive)
    }
}
