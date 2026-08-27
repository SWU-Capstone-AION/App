package com.example.aion_app.minigame.weed

import com.example.aion_app.minigame.MinigameStatus
import com.example.aion_app.minigame.Nudge
import com.example.aion_app.minigame.PoseInput
import com.example.aion_app.minigame.Vec2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 잡초 뽑기 게임의 순수 로직.
 * 안드로이드 의존성이 전혀 없으므로 JVM 단위 테스트로 검증할 수 있다.
 *
 * 좌표계: 화면 픽셀 (x 오른쪽 +, y 아래쪽 +)
 * 스케일 기준: S = 두 어깨 사이의 픽셀 거리
 */

enum class WeedState { IDLE, GRABBED, PULLED }

/** 매 프레임 엔진이 뱉는 출력. UI는 이것만 보고 그린다. */
data class WeedView(
    val id: Int,
    val pos: Vec2,
    val height: Float,
    val state: WeedState,
)

data class GameSnapshot(
    val weeds: List<WeedView> = emptyList(),
    override val wrists: List<Vec2> = emptyList(),
    val pulled: Int = 0,
    val total: Int = 0,
    override val progress: Float = 0f,
    override val poseVisible: Boolean = false,
    override val nudge: Nudge = Nudge.NONE,
    override val cleared: Boolean = false,
) : MinigameStatus

class WeedGameEngine(
    /** 생성할 잡초 개수. 목업보다 적은 게 맞다 — 너무 많으면 겹쳐서 구분이 안 된다. */
    private val weedCount: Int = 8,
    /**
     * 어깨 폭(S) 배수로 표현한 생성 반경.
     * 화면 밖으로 나가면 어차피 아래 clampToScreen 이 당겨오므로, 여기서도 여유 있게 잡지 않는다.
     */
    private val minRadiusInS: Float = 1.0f,
    private val maxRadiusInS: Float = 1.35f,
    /** 위쪽 반원에 배치할 비율. 앉은 자세면 아래쪽은 책상에 가린다. */
    private val upperHalfBias: Float = 0.7f,
    /** 잡초 높이 (S 배수) */
    private val weedHeightInS: Float = 0.35f,
    /** 히트박스 가로 반폭 (S 배수) */
    private val hitHalfWidthInS: Float = 0.22f,
    /** 스치듯 지나가는 것을 거르기 위한 최소 연속 프레임 */
    private val grabFramesRequired: Int = 4,
    /** 잡초 높이의 몇 배만큼 위로 당겨야 뽑히는가. 낮으면 살짝만 올려도 뽑힌다. */
    private val pullRatio: Float = 0.8f,
    /** 잡은 뒤 손이 이만큼 옆으로 벗어나면 취소 (S 배수) */
    private val cancelDistanceInS: Float = 0.55f,
    /** 이만큼 아무 진전이 없으면 남은 잡초를 몸 쪽으로 당겨준다 (시연 중 막힘 방지) */
    private val assistAfterIdleMs: Long = 30_000L,
    private val nudgeKeepGoingAfterMs: Long = 60_000L,
    private val nudgeHowToQuitAfterMs: Long = 180_000L,
    private val random: Random = Random.Default,
) {

    private class WeedItem(
        val id: Int,
        val angleRad: Float,
        var radiusInS: Float,
    ) {
        var state = WeedState.IDLE
        var pos = Vec2(0f, 0f)
        var grabFrames = 0
        var grabWristY = 0f
    }

    private val weeds = mutableListOf<WeedItem>()

    // 어깨 기준점은 랜드마크 노이즈 때문에 그대로 쓰면 잡초가 떨린다. EMA로 완충.
    private var anchorCenter: Vec2? = null
    private var anchorS = 0f

    private var pulledCount = 0
    private var lastUpdateMs = 0L
    private var idleElapsedMs = 0L

    fun reset() {
        weeds.clear()
        anchorCenter = null
        anchorS = 0f
        pulledCount = 0
        idleElapsedMs = 0L
        lastUpdateMs = 0L
    }

    /** 시연 중 인식이 안 될 때 빠져나가기 위한 수동 클리어. */
    fun forceClear() {
        weeds.forEach { it.state = WeedState.PULLED }
        pulledCount = weeds.size
    }

    fun update(pose: PoseInput, nowMs: Long): GameSnapshot {
        val dt = if (lastUpdateMs == 0L) 0L else (nowMs - lastUpdateMs).coerceAtLeast(0L)
        lastUpdateMs = nowMs

        val ls = pose.leftShoulder
        val rs = pose.rightShoulder

        // 어깨가 안 잡히면 아이가 자리에 없는 것. 게임을 멈추고 무반응 타이머도 세지 않는다.
        if (ls == null || rs == null) {
            return snapshot(poseVisible = false)
        }

        updateAnchor(ls, rs)
        val center = anchorCenter ?: return snapshot(poseVisible = false)
        val s = anchorS
        if (s <= 1f) return snapshot(poseVisible = false)

        spawnIfNeeded()
        projectWeeds(center, s, pose.viewWidth, pose.viewHeight)

        val wrists = listOfNotNull(pose.leftWrist, pose.rightWrist)
        val interacted = stepStateMachine(wrists, s)

        idleElapsedMs = if (interacted) 0L else idleElapsedMs + dt

        applyAssist()

        return snapshot(poseVisible = true, wrists = wrists)
    }

    // ---------------------------------------------------------------- 내부

    private fun updateAnchor(ls: Vec2, rs: Vec2) {
        val center = Vec2((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f)
        val s = kotlin.math.hypot(ls.x - rs.x, ls.y - rs.y)

        val prev = anchorCenter
        if (prev == null) {
            anchorCenter = center
            anchorS = s
        } else {
            val a = SMOOTHING
            anchorCenter = Vec2(
                prev.x + (center.x - prev.x) * a,
                prev.y + (center.y - prev.y) * a,
            )
            anchorS += (s - anchorS) * a
        }
    }

    private fun spawnIfNeeded() {
        if (weeds.isNotEmpty()) return
        repeat(weedCount) { i ->
            val radius = minRadiusInS + random.nextFloat() * (maxRadiusInS - minRadiusInS)
            weeds += WeedItem(i, randomAngle(), radius)
        }
    }

    /** 화면 좌표계는 y가 아래로 증가하므로, 위쪽 반원은 sin < 0 즉 각도 (PI, 2PI). */
    private fun randomAngle(): Float =
        if (random.nextFloat() < upperHalfBias) {
            (Math.PI + random.nextDouble() * Math.PI).toFloat()
        } else {
            (random.nextDouble() * Math.PI).toFloat()
        }

    /**
     * 잡초는 화면 절대 좌표가 아니라 몸 기준 (각도, S 배수)로 저장된다.
     * 매 프레임 현재 어깨 위치로 다시 투영하므로, 아이가 다가오거나 멀어져도
     * 항상 팔이 닿는 범위에 놓인다.
     *
     * 다만 몸 기준 반경만 쓰면 아이가 카메라에 가까이 앉았을 때(=S 가 커질 때)
     * 잡초가 화면 밖으로 밀려나 "몸을 움직여야 보이는" 잡초가 생긴다.
     * 그래서 각도는 그대로 두고 반경만 화면 안쪽으로 줄인다. 각도가 유지되므로
     * 잡초가 한쪽에 뭉치지 않고, 아이는 자리에서 움직이지 않고도 전부 볼 수 있다.
     */
    private fun projectWeeds(center: Vec2, s: Float, viewWidth: Float, viewHeight: Float) {
        val halfWidth = hitHalfWidthInS * s
        val height = weedHeightInS * s
        weeds.forEach { w ->
            val dx = cos(w.angleRad)
            val dy = sin(w.angleRad)
            val desired = w.radiusInS * s
            val limit = maxRadiusOnScreen(center, dx, dy, viewWidth, viewHeight, halfWidth, height)
            val radius = if (limit < 0f) desired else min(desired, limit)
            w.pos = Vec2(center.x + dx * radius, center.y + dy * radius)
        }
    }

    /**
     * 중심에서 (dx, dy) 방향으로 나갈 수 있는 최대 반경(픽셀).
     * 잡초는 밑동에서 위로 자라므로 위쪽 여백을 잎 높이만큼 더 준다.
     * 화면 크기를 모르면(-1) 호출부에서 제한을 걸지 않는다.
     */
    private fun maxRadiusOnScreen(
        center: Vec2,
        dx: Float,
        dy: Float,
        viewWidth: Float,
        viewHeight: Float,
        halfWidth: Float,
        height: Float,
    ): Float {
        if (viewWidth <= 0f || viewHeight <= 0f) return -1f

        val minX = halfWidth
        val maxX = viewWidth - halfWidth
        val minY = height * 1.15f
        val maxY = viewHeight - height * 0.4f
        if (minX >= maxX || minY >= maxY) return -1f

        var t = Float.MAX_VALUE
        if (dx > EPSILON) t = min(t, (maxX - center.x) / dx)
        else if (dx < -EPSILON) t = min(t, (minX - center.x) / dx)
        if (dy > EPSILON) t = min(t, (maxY - center.y) / dy)
        else if (dy < -EPSILON) t = min(t, (minY - center.y) / dy)

        return if (t == Float.MAX_VALUE) -1f else max(0f, t)
    }

    /**
     * 한 번에 한 포기만 잡힌다.
     *
     * 잡초마다 따로 판정하면, 겹쳐 있는 잡초가 전부 GRABBED 가 되고
     * 팔을 한 번 올릴 때 그것들이 한꺼번에 뽑힌다. 그래서
     *   1) 이미 잡은 게 있으면 그 한 포기만 처리하고
     *   2) 잡은 게 없을 때만 손목에 가장 가까운 한 포기를 후보로 고른다.
     */
    private fun stepStateMachine(wrists: List<Vec2>, s: Float): Boolean {
        val weedHeight = weedHeightInS * s
        val pullDistance = weedHeight * pullRatio
        val cancelDistance = cancelDistanceInS * s

        val held = weeds.firstOrNull { it.state == WeedState.GRABBED }
        if (held != null) {
            weeds.forEach { if (it !== held) it.grabFrames = 0 }
            // 당기는 중에는 손목이 히트박스 위로 벗어나므로 가로 거리만 본다.
            val near = wrists.minByOrNull { abs(it.x - held.pos.x) }
            return when {
                near == null || abs(near.x - held.pos.x) > cancelDistance -> {
                    held.state = WeedState.IDLE
                    held.grabFrames = 0
                    false
                }
                held.grabWristY - near.y >= pullDistance -> {
                    held.state = WeedState.PULLED
                    pulledCount++
                    true
                }
                else -> false
            }
        }

        // 겹쳐 있어도 가장 가까운 한 포기만 후보가 된다.
        var candidate: WeedItem? = null
        var candidateWrist: Vec2? = null
        var bestDistance = Float.MAX_VALUE
        weeds.forEach { w ->
            if (w.state != WeedState.IDLE) return@forEach
            wrists.forEach { wrist ->
                if (!inHitbox(w, wrist, s)) return@forEach
                val distance = kotlin.math.hypot(wrist.x - w.pos.x, wrist.y - w.pos.y)
                if (distance < bestDistance) {
                    bestDistance = distance
                    candidate = w
                    candidateWrist = wrist
                }
            }
        }

        val target = candidate
        val wrist = candidateWrist
        weeds.forEach { if (it !== target) it.grabFrames = 0 }
        if (target == null || wrist == null) return false

        target.grabFrames++
        if (target.grabFrames < grabFramesRequired) return false

        target.state = WeedState.GRABBED
        target.grabWristY = wrist.y   // 잡은 순간의 높이를 기억
        return true
    }

    private fun inHitbox(w: WeedItem, wrist: Vec2, s: Float): Boolean {
        val halfWidth = hitHalfWidthInS * s
        val height = weedHeightInS * s
        return abs(wrist.x - w.pos.x) <= halfWidth &&
            wrist.y >= w.pos.y - height &&
            wrist.y <= w.pos.y + height * 0.3f
    }

    /** 오래 진전이 없으면 남은 잡초를 몸 쪽으로 당겨 막힘을 푼다. */
    private fun applyAssist() {
        if (weeds.isEmpty() || idleElapsedMs < assistAfterIdleMs) return
        weeds.forEach { w ->
            if (w.state != WeedState.PULLED && w.radiusInS > ASSIST_TARGET_RADIUS) {
                w.radiusInS = max(ASSIST_TARGET_RADIUS, w.radiusInS - ASSIST_STEP)
            }
        }
    }

    private fun snapshot(
        poseVisible: Boolean,
        wrists: List<Vec2> = emptyList(),
    ): GameSnapshot {
        val total = weeds.size
        val progress = if (total == 0) 0f else pulledCount.toFloat() / total
        val cleared = total > 0 && pulledCount >= total
        val nudge = when {
            cleared || !poseVisible -> Nudge.NONE
            idleElapsedMs >= nudgeHowToQuitAfterMs -> Nudge.HOW_TO_QUIT
            idleElapsedMs >= nudgeKeepGoingAfterMs -> Nudge.KEEP_GOING
            else -> Nudge.NONE
        }
        return GameSnapshot(
            weeds = weeds.map { WeedView(it.id, it.pos, weedHeightInS * anchorS, it.state) },
            wrists = wrists,
            pulled = pulledCount,
            total = total,
            progress = progress,
            poseVisible = poseVisible,
            nudge = nudge,
            cleared = cleared,
        )
    }

    private companion object {
        const val SMOOTHING = 0.25f
        const val EPSILON = 1e-4f
        const val ASSIST_TARGET_RADIUS = 1.0f
        const val ASSIST_STEP = 0.01f
    }
}
