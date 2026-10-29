package com.example.aion_app.minigame.weed

import com.example.aion_app.minigame.HandPose
import com.example.aion_app.minigame.HandView
import com.example.aion_app.minigame.MinigameStatus
import com.example.aion_app.minigame.NUDGE_HOW_TO_QUIT_MS
import com.example.aion_app.minigame.NUDGE_KEEP_GOING_MS
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
 *
 * 잡는 방식은 실제 잡초를 뽑는 동작을 따라간다.
 *   1) 손바닥을 잡초에 가져간다
 *   2) 손을 쥔다        → 잡힌다
 *   3) 쥔 채 위로 올린다 → 뽑힌다. 중간에 손을 펴면 놓친다
 */

enum class WeedState { IDLE, GRABBED, PULLED }

/** 손이 왼쪽인지 오른쪽인지. 어느 손이 잡고 있는지 표시하는 데 쓴다. */
enum class HandSide { LEFT, RIGHT }

/** 매 프레임 엔진이 뱉는 출력. UI는 이것만 보고 그린다. */
data class WeedView(
    val id: Int,
    val pos: Vec2,
    val height: Float,
    val state: WeedState,
    /** 잡고 있는 손의 위치. 잎이 이쪽으로 쏠린다. 안 잡혔으면 null */
    val handPos: Vec2? = null,
    /** 0 = 막 잡은 순간, 1 = 뽑히기 직전. 당긴 만큼 줄기가 늘어난다 */
    val pullProgress: Float = 0f,
    /** 뽑힌 직후 위로 사라지는 연출. 1 → 0 으로 줄어든다 */
    val fade: Float = 0f,
)

data class GameSnapshot(
    val weeds: List<WeedView> = emptyList(),
    override val hands: List<HandView> = emptyList(),
    val pulled: Int = 0,
    val total: Int = 0,
    override val progress: Float = 0f,
    override val poseVisible: Boolean = false,
    override val nudge: Nudge = Nudge.NONE,
    override val cleared: Boolean = false,
    override val lastActiveAt: Long = 0L,
) : MinigameStatus

class WeedGameEngine(
    /** 생성할 잡초 개수. 목업보다 적은 게 맞다 — 너무 많으면 겹쳐서 구분이 안 된다. */
    private val weedCount: Int = 6,
    /**
     * 어깨 폭(S) 배수로 표현한 생성 반경.
     * 화면 밖으로 나가면 어차피 아래 clampToScreen 이 당겨오므로, 여기서도 여유 있게 잡지 않는다.
     */
    private val minRadiusInS: Float = 1.0f,
    private val maxRadiusInS: Float = 1.35f,
    /**
     * 잡초를 놓는 각도 구간. 화면 좌표계라 0 = 오른쪽, PI/2 = 아래, PI = 왼쪽, 1.5PI = 위(얼굴)다.
     * 어깨 중심 위쪽은 얼굴 자리이므로 비워 두고, 양옆에서 몸 앞쪽에 걸친 구간에만 놓는다.
     * 어깨선보다 살짝 위까지만 올라간다.
     */
    private val bandStartRad: Float = (-0.15 * Math.PI).toFloat(),
    private val bandEndRad: Float = (1.15 * Math.PI).toFloat(),
    /** 잡초끼리 최소로 떨어져 있어야 하는 거리 (S 배수). 히트박스 폭(0.44S)보다 넓게 잡는다. */
    private val minSeparationInS: Float = 0.55f,
    /** 손 아이콘 크기 (S 배수). 잡초(0.35S)보다 작아야 무엇을 잡는지 가리지 않는다. */
    private val handSizeInS: Float = 0.26f,
    /** 잡초 높이 (S 배수) */
    private val weedHeightInS: Float = 0.35f,
    /** 히트박스 가로 반폭 (S 배수) */
    private val hitHalfWidthInS: Float = 0.22f,
    /**
     * 손을 쥐었을 때 필요한 최소 연속 프레임.
     * 쥐는 동작 자체가 "잡겠다"는 뜻이라 오래 기다릴 필요가 없다.
     */
    private val grabFramesWithFist: Int = 2,
    /**
     * 손을 안 쥐었을 때 필요한 연속 프레임.
     * 손 모양이 잘 안 읽히는 아이도 **가만히 대고 있으면 결국 잡힌다**. 게임이 막히지 않게 하는 안전장치다.
     */
    private val grabFramesOpenHand: Int = 14,
    /** 잡초 높이의 몇 배만큼 위로 당겨야 뽑히는가. 낮으면 살짝만 올려도 뽑힌다. */
    private val pullRatio: Float = 0.8f,
    /** 잡은 뒤 손이 이만큼 옆으로 벗어나면 취소 (S 배수) */
    private val cancelDistanceInS: Float = 0.55f,
    private val nudgeKeepGoingAfterMs: Long = NUDGE_KEEP_GOING_MS,
    private val nudgeHowToQuitAfterMs: Long = NUDGE_HOW_TO_QUIT_MS,
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

        /** 이번 프레임에 실제로 쓰인 반경(픽셀)과 화면이 허용하는 최대 반경 */
        var drawRadius = 0f
        var limitRadius = Float.MAX_VALUE

        /** 잡은 순간의 손 높이. 여기서 얼마나 올렸는지로 뽑기를 판단한다 */
        var grabHandY = 0f
        var holderSide: HandSide? = null
        var holderPos: Vec2? = null
        var pullProgress = 0f
        var pulledAtMs = 0L
    }

    /** 한 프레임 동안의 손. 쥔 정도는 엔진이 완충한 값이다. */
    private class Hand(
        val side: HandSide,
        val pos: Vec2,
        val grip: Float,
        val closed: Boolean,
    )

    /** 손마다 쥔 정도를 완충해 둔다. 한 프레임 튀는 값으로 잡았다 놓았다 하지 않도록. */
    private class GripFilter {
        var grip = 0f
        var closed = false

        fun update(raw: Float): Float {
            grip += (raw - grip) * GRIP_SMOOTHING
            // 쥘 때와 펼 때의 기준을 다르게 둬서 경계에서 깜빡이지 않게 한다.
            closed = when {
                grip >= HandPose.GRIP_CLOSED -> true
                grip <= HandPose.GRIP_OPEN -> false
                else -> closed
            }
            return grip
        }

        fun reset() {
            grip = 0f
            closed = false
        }
    }

    private val weeds = mutableListOf<WeedItem>()

    // 어깨 기준점은 랜드마크 노이즈 때문에 그대로 쓰면 잡초가 떨린다. EMA로 완충.
    private var anchorCenter: Vec2? = null
    private var anchorS = 0f

    private val leftGrip = GripFilter()
    private val rightGrip = GripFilter()

    private var pulledCount = 0
    private var lastUpdateMs = 0L
    private var idleElapsedMs = 0L
    private var lastActiveAt = 0L
    private var nowMs = 0L

    fun reset() {
        weeds.clear()
        anchorCenter = null
        anchorS = 0f
        leftGrip.reset()
        rightGrip.reset()
        pulledCount = 0
        idleElapsedMs = 0L
        lastActiveAt = 0L
        lastUpdateMs = 0L
    }

    /** 조명이 나쁘거나 인식이 안 될 때 빠져나가기 위한 수동 클리어. */
    fun forceClear() {
        weeds.forEach { it.state = WeedState.PULLED }
        pulledCount = weeds.size
    }

    fun update(pose: PoseInput, nowMs: Long): GameSnapshot {
        val dt = if (lastUpdateMs == 0L) 0L else (nowMs - lastUpdateMs).coerceAtLeast(0L)
        lastUpdateMs = nowMs
        this.nowMs = nowMs

        val ls = pose.leftShoulder
        val rs = pose.rightShoulder

        // 어깨가 안 잡히면 아이가 자리에 없는 것. 게임을 멈추고 무반응 타이머도 세지 않는다.
        if (ls == null || rs == null) {
            leftGrip.reset()
            rightGrip.reset()
            return snapshot(poseVisible = false)
        }

        updateAnchor(ls, rs)
        val center = anchorCenter ?: return snapshot(poseVisible = false)
        val s = anchorS
        if (s <= 1f) return snapshot(poseVisible = false)

        spawnIfNeeded()
        projectWeeds(center, s, pose.viewWidth, pose.viewHeight, pose.topInset)

        val hands = listOfNotNull(
            track(HandSide.LEFT, pose.leftHand, leftGrip),
            track(HandSide.RIGHT, pose.rightHand, rightGrip),
        )
        val interacted = stepStateMachine(hands, s)

        idleElapsedMs = if (interacted) 0L else idleElapsedMs + dt
        if (interacted) lastActiveAt = nowMs

        return snapshot(poseVisible = true, hands = hands)
    }

    // ---------------------------------------------------------------- 내부

    private fun track(side: HandSide, hand: HandPose?, filter: GripFilter): Hand? {
        if (hand == null) {
            filter.reset()
            return null
        }
        val grip = filter.update(hand.grip)
        return Hand(side, hand.pos, grip, filter.closed)
    }

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

    /**
     * 잡초를 겹치지 않게, 얼굴을 피해 몸 주변에 뿌린다.
     *
     * 각도를 완전히 무작위로 뽑으면 두 포기가 거의 같은 방향에 서서 그림이 포개진다.
     * 그래서 [bandStartRad]~[bandEndRad] 구간을 포기 수만큼 구역으로 나누고 한 구역에
     * 한 포기씩 넣은 뒤, 구역 안에서만 흔들어 기계적으로 늘어선 느낌을 없앤다.
     */
    private fun spawnIfNeeded() {
        if (weeds.isNotEmpty()) return
        if (weedCount <= 0) return

        val sector = (bandEndRad - bandStartRad) / weedCount
        repeat(weedCount) { i ->
            val jitter = ((random.nextFloat() - 0.5f) * sector * SECTOR_JITTER)
            val angle = bandStartRad + sector * (i + 0.5f) + jitter
            val radius = minRadiusInS + random.nextFloat() * (maxRadiusInS - minRadiusInS)
            weeds += WeedItem(i, angle, radius)
        }
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
    private fun projectWeeds(
        center: Vec2,
        s: Float,
        viewWidth: Float,
        viewHeight: Float,
        topInset: Float,
    ) {
        val halfWidth = hitHalfWidthInS * s
        val height = weedHeightInS * s
        // 화면이 좁아도 이보다 가까이 당기지는 않는다. 몸 위로 잡초가 쌓이는 것을 막는다.
        val floorRadius = minRadiusInS * 0.6f * s

        weeds.forEach { w ->
            val dx = cos(w.angleRad)
            val dy = sin(w.angleRad)
            val desired = w.radiusInS * s
            val limit =
                maxRadiusOnScreen(center, dx, dy, viewWidth, viewHeight, halfWidth, height, topInset)
            w.limitRadius = if (limit < 0f) Float.MAX_VALUE else max(limit, floorRadius)
            w.drawRadius = min(desired, w.limitRadius)
            w.pos = Vec2(center.x + dx * w.drawRadius, center.y + dy * w.drawRadius)
        }

        separate(center, s)
    }

    /**
     * 화면 제한에 걸려 서로 당겨 들어온 잡초를 떼어 놓는다.
     * 각도는 그대로 두고 바깥쪽 잡초를 조금 더 바깥으로 밀기 때문에 배치가 흐트러지지 않는다.
     */
    private fun separate(center: Vec2, s: Float) {
        val minGap = minSeparationInS * s
        if (minGap <= 0f || weeds.size < 2) return

        repeat(SEPARATION_PASSES) {
            for (i in weeds.indices) {
                for (j in i + 1 until weeds.size) {
                    val a = weeds[i]
                    val b = weeds[j]
                    val gap = kotlin.math.hypot(a.pos.x - b.pos.x, a.pos.y - b.pos.y)
                    if (gap >= minGap) continue

                    val outer = if (a.drawRadius >= b.drawRadius) a else b
                    val pushed = min(outer.limitRadius, outer.drawRadius + (minGap - gap) * 0.5f)
                    if (pushed <= outer.drawRadius) continue

                    outer.drawRadius = pushed
                    outer.pos = Vec2(
                        center.x + cos(outer.angleRad) * pushed,
                        center.y + sin(outer.angleRad) * pushed,
                    )
                }
            }
        }
    }

    /**
     * 중심에서 (dx, dy) 방향으로 나갈 수 있는 최대 반경(픽셀).
     * 잡초는 밑동에서 위로 자라므로 위쪽 여백을 잎 높이만큼 더 준다.
     * [topInset] 아래에만 놓아 제목 문구와 겹치지 않게 한다.
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
        topInset: Float,
    ): Float {
        if (viewWidth <= 0f || viewHeight <= 0f) return -1f

        val minX = halfWidth
        val maxX = viewWidth - halfWidth
        val minY = max(height * 1.15f, topInset + height * 1.05f)
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
     *   2) 잡은 게 없을 때만 손에 가장 가까운 한 포기를 후보로 고른다.
     */
    private fun stepStateMachine(hands: List<Hand>, s: Float): Boolean {
        val weedHeight = weedHeightInS * s
        val pullDistance = weedHeight * pullRatio
        val cancelDistance = cancelDistanceInS * s

        val held = weeds.firstOrNull { it.state == WeedState.GRABBED }
        if (held != null) return stepHeld(held, hands, pullDistance, cancelDistance)

        // 겹쳐 있어도 가장 가까운 한 포기만 후보가 된다.
        var candidate: WeedItem? = null
        var candidateHand: Hand? = null
        var bestDistance = Float.MAX_VALUE
        weeds.forEach { w ->
            if (w.state != WeedState.IDLE) return@forEach
            hands.forEach { hand ->
                if (!inHitbox(w, hand.pos, s)) return@forEach
                val distance = kotlin.math.hypot(hand.pos.x - w.pos.x, hand.pos.y - w.pos.y)
                if (distance < bestDistance) {
                    bestDistance = distance
                    candidate = w
                    candidateHand = hand
                }
            }
        }

        val target = candidate
        val hand = candidateHand
        weeds.forEach { if (it !== target) it.grabFrames = 0 }
        if (target == null || hand == null) return false

        target.grabFrames++
        // 손을 쥐면 바로, 펴고 있으면 한참 대고 있어야 잡힌다.
        val required = if (hand.closed) grabFramesWithFist else grabFramesOpenHand
        if (target.grabFrames < required) return false

        target.state = WeedState.GRABBED
        target.grabHandY = hand.pos.y    // 잡은 순간의 높이를 기억
        target.holderSide = hand.side
        target.holderPos = hand.pos
        target.pullProgress = 0f
        return true
    }

    /**
     * 잡고 있는 한 포기를 처리한다.
     * 손을 펴면 놓치고, 쥔 채 위로 올리면 뽑힌다. 그 사이 진행률이 매 프레임 바뀐다.
     */
    private fun stepHeld(
        held: WeedItem,
        hands: List<Hand>,
        pullDistance: Float,
        cancelDistance: Float,
    ): Boolean {
        weeds.forEach { if (it !== held) it.grabFrames = 0 }

        // 잡은 손을 계속 따라간다. 그 손이 사라지면 반대 손이라도 가까우면 이어받는다.
        val hand = hands.firstOrNull { it.side == held.holderSide }
            ?: hands.minByOrNull { abs(it.pos.x - held.pos.x) }

        // 당기는 중에는 손이 히트박스 위로 벗어나므로 가로 거리만 본다.
        if (hand == null || abs(hand.pos.x - held.pos.x) > cancelDistance) {
            release(held)
            return false
        }
        // 손을 펴면 놓친다. 실제로 잡초를 놓는 것과 같다.
        if (!hand.closed) {
            release(held)
            return false
        }

        held.holderSide = hand.side
        held.holderPos = hand.pos

        val lifted = held.grabHandY - hand.pos.y
        held.pullProgress = (lifted / pullDistance).coerceIn(0f, 1f)

        if (lifted >= pullDistance) {
            held.state = WeedState.PULLED
            held.pulledAtMs = nowMs
            held.pullProgress = 1f
            pulledCount++
            return true
        }
        return false
    }

    private fun release(weed: WeedItem) {
        weed.state = WeedState.IDLE
        weed.grabFrames = 0
        weed.holderSide = null
        weed.holderPos = null
        weed.pullProgress = 0f
    }

    private fun inHitbox(w: WeedItem, hand: Vec2, s: Float): Boolean {
        val halfWidth = hitHalfWidthInS * s
        val height = weedHeightInS * s
        return abs(hand.x - w.pos.x) <= halfWidth &&
            hand.y >= w.pos.y - height &&
            hand.y <= w.pos.y + height * 0.3f
    }

    private fun snapshot(
        poseVisible: Boolean,
        hands: List<Hand> = emptyList(),
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
        val heldSide = weeds.firstOrNull { it.state == WeedState.GRABBED }?.holderSide
        val weedHeight = weedHeightInS * anchorS

        return GameSnapshot(
            weeds = weeds.mapNotNull { it.toView(weedHeight) },
            hands = hands.map {
                HandView(
                    pos = it.pos,
                    size = handSizeInS * anchorS,
                    grip = it.grip,
                    holding = it.side == heldSide,
                )
            },
            pulled = pulledCount,
            total = total,
            progress = progress,
            poseVisible = poseVisible,
            nudge = nudge,
            cleared = cleared,
            lastActiveAt = lastActiveAt,
        )
    }

    /** 뽑힌 잡초는 잠깐 위로 떠오르며 사라진다. 그 시간이 지나면 그리지 않는다. */
    private fun WeedItem.toView(height: Float): WeedView? {
        if (state == WeedState.PULLED) {
            if (pulledAtMs == 0L) return null
            val elapsed = nowMs - pulledAtMs
            if (elapsed >= PULL_FADE_MS) return null
            val fade = 1f - elapsed.toFloat() / PULL_FADE_MS
            return WeedView(
                id = id,
                pos = Vec2(pos.x, pos.y - (1f - fade) * height * 0.9f),
                height = height,
                state = WeedState.PULLED,
                handPos = holderPos,
                pullProgress = 1f,
                fade = fade,
            )
        }
        return WeedView(
            id = id,
            pos = pos,
            height = height,
            state = state,
            handPos = if (state == WeedState.GRABBED) holderPos else null,
            pullProgress = pullProgress,
        )
    }

    private companion object {
        const val SMOOTHING = 0.25f
        const val EPSILON = 1e-4f

        /** 쥔 정도 완충. 낮출수록 둔해지고 높일수록 민감해진다 */
        const val GRIP_SMOOTHING = 0.35f

        /** 구역 안에서 각도를 흔드는 폭(구역 너비 대비). 1 에 가까울수록 다시 겹칠 수 있다 */
        const val SECTOR_JITTER = 0.6f

        /** 겹침을 푸는 반복 횟수 */
        const val SEPARATION_PASSES = 3

        /** 뽑힌 잡초가 떠오르며 사라지는 시간(ms) */
        const val PULL_FADE_MS = 350L
    }
}
