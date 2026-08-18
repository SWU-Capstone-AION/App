package com.example.aion_app.minigame.weed

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * 잡초 뽑기 게임의 순수 로직.
 * 안드로이드 의존성이 전혀 없으므로 JVM 단위 테스트로 검증할 수 있다.
 *
 * 좌표계: 화면 픽셀 (x 오른쪽 +, y 아래쪽 +)
 * 스케일 기준: S = 두 어깨 사이의 픽셀 거리
 */

data class Vec2(val x: Float, val y: Float)

enum class WeedState { IDLE, GRABBED, PULLED }

enum class Nudge { NONE, KEEP_GOING, HOW_TO_QUIT }

/** 매 프레임 엔진에 들어오는 입력. 화면 픽셀 좌표. */
data class PoseInput(
    val leftShoulder: Vec2? = null,
    val rightShoulder: Vec2? = null,
    val leftWrist: Vec2? = null,
    val rightWrist: Vec2? = null,
)

/** 매 프레임 엔진이 뱉는 출력. UI는 이것만 보고 그린다. */
data class WeedView(
    val id: Int,
    val pos: Vec2,
    val height: Float,
    val state: WeedState,
)

data class GameSnapshot(
    val weeds: List<WeedView> = emptyList(),
    val wrists: List<Vec2> = emptyList(),
    val pulled: Int = 0,
    val total: Int = 0,
    val progress: Float = 0f,
    val poseVisible: Boolean = false,
    val nudge: Nudge = Nudge.NONE,
    val cleared: Boolean = false,
)

class WeedGameEngine(
    /** 생성할 잡초 개수. 목업보다 적은 게 맞다 — 너무 많으면 겹쳐서 구분이 안 된다. */
    private val weedCount: Int = 10,
    /** 어깨 폭(S) 배수로 표현한 생성 반경 */
    private val minRadiusInS: Float = 1.2f,
    private val maxRadiusInS: Float = 1.5f,
    /** 위쪽 반원에 배치할 비율. 앉은 자세면 아래쪽은 책상에 가린다. */
    private val upperHalfBias: Float = 0.7f,
    /** 잡초 높이 (S 배수) */
    private val weedHeightInS: Float = 0.35f,
    /** 히트박스 가로 반폭 (S 배수) */
    private val hitHalfWidthInS: Float = 0.22f,
    /** 스치듯 지나가는 것을 거르기 위한 최소 연속 프레임 */
    private val grabFramesRequired: Int = 3,
    /** 잡초 높이의 몇 배만큼 위로 당겨야 뽑히는가. 아동 대상이면 낮게 시작할 것. */
    private val pullRatio: Float = 0.4f,
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
        projectWeeds(center, s)

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
     */
    private fun projectWeeds(center: Vec2, s: Float) {
        weeds.forEach { w ->
            w.pos = Vec2(
                center.x + cos(w.angleRad) * w.radiusInS * s,
                center.y + sin(w.angleRad) * w.radiusInS * s,
            )
        }
    }

    private fun stepStateMachine(wrists: List<Vec2>, s: Float): Boolean {
        val weedHeight = weedHeightInS * s
        val pullDistance = weedHeight * pullRatio
        val cancelDistance = cancelDistanceInS * s
        var interacted = false

        weeds.forEach { w ->
            when (w.state) {
                WeedState.PULLED -> Unit

                WeedState.IDLE -> {
                    val hit = wrists.firstOrNull { inHitbox(w, it, s) }
                    if (hit == null) {
                        w.grabFrames = 0
                    } else {
                        w.grabFrames++
                        if (w.grabFrames >= grabFramesRequired) {
                            w.state = WeedState.GRABBED
                            w.grabWristY = hit.y   // 잡은 순간의 높이를 기억
                            interacted = true
                        }
                    }
                }

                WeedState.GRABBED -> {
                    // 당기는 중에는 손목이 히트박스 위로 벗어나므로 가로 거리만 본다.
                    val near = wrists.minByOrNull { abs(it.x - w.pos.x) }
                    when {
                        near == null || abs(near.x - w.pos.x) > cancelDistance -> {
                            w.state = WeedState.IDLE
                            w.grabFrames = 0
                        }
                        w.grabWristY - near.y >= pullDistance -> {
                            w.state = WeedState.PULLED
                            pulledCount++
                            interacted = true
                        }
                    }
                }
            }
        }
        return interacted
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
        const val ASSIST_TARGET_RADIUS = 1.15f
        const val ASSIST_STEP = 0.01f
    }
}
