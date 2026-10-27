package com.example.aion_app.monitor.rule

import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import java.util.concurrent.ConcurrentHashMap

// ============================================================
// 화면 터치 → 규칙모델 v3 (기능적 행동 제외 규칙)
// ============================================================
// 태블릿을 두드리는 동안의 손·팔 반복은 목적이 있는 행동이라 점수를 낮춘다. (기준서 5쪽)
// MainActivity.dispatchTouchEvent 가 모든 터치를 여기로 넘기고,
// 지금 돌고 있는 엔진(감지 호스트 또는 모니터링 화면)이 있으면 그 엔진에 기록한다.
// 터치를 가로채지는 않는다 (기록만 하고 화면에는 그대로 전달).
//
// 모니터링 화면의 버튼·패널처럼 '시험하는 사람이 누르는 곳'은 [excludeFromTouchRule] 로 빼 둔다.
// (HTML 도 버튼·설정 영역의 터치는 세지 않는다)
// ============================================================
object RuleV3TouchBus {

    @Volatile
    var engine: RuleV3Engine? = null

    /** 터치로 세지 않을 영역 (창 좌표) */
    private val excluded = ConcurrentHashMap<String, RectF>()

    /** 제외 영역에서 시작된 손가락 (뗄 때도 무시해야 다른 손가락 기록이 엉키지 않는다) */
    private val ignoredPointers = HashSet<Int>()

    fun setExcluded(key: String, rect: RectF?) {
        if (rect == null) excluded.remove(key) else excluded[key] = rect
    }

    /** 모니터링 화면을 나갈 때 부른다 (아동 화면에서는 모든 터치를 센다) */
    fun clearExcluded() = excluded.clear()

    private fun isExcluded(x: Float, y: Float) = excluded.values.any { it.contains(x, y) }

    fun onMotionEvent(ev: MotionEvent) {
        val e = engine ?: return
        val now = SystemClock.uptimeMillis()
        val idx = ev.actionIndex
        val id = ev.getPointerId(idx)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (isExcluded(ev.getX(idx), ev.getY(idx))) ignoredPointers.add(id) else e.touchDown(now)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (!ignoredPointers.remove(id)) e.touchUp(now)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ignoredPointers.clear()
                e.touchUpAll(now)
            }
        }
    }
}

/** 이 영역의 터치는 '태블릿 터치(기능적 행동)'로 세지 않는다 */
fun Modifier.excludeFromTouchRule(key: String): Modifier = onGloballyPositioned { c ->
    val b = c.boundsInWindow()
    RuleV3TouchBus.setExcluded(key, RectF(b.left, b.top, b.right, b.bottom))
}
