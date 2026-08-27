package com.example.aion_app.minigame.board

import com.example.aion_app.minigame.MinigameStatus
import com.example.aion_app.minigame.Nudge
import com.example.aion_app.minigame.PoseInput
import com.example.aion_app.minigame.Vec2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 칠판 지우기 게임의 순수 로직.
 * 안드로이드 의존성이 전혀 없으므로 JVM 단위 테스트로 검증할 수 있다.
 *
 * 좌표계: 화면 픽셀 (x 오른쪽 +, y 아래쪽 +)
 * 스케일 기준: S = 두 어깨 사이의 픽셀 거리
 *
 * 칠판은 화면 전체가 아니라 **몸 주변**에 놓인다. 앉은 아이의 팔이 닿는 범위를 벗어나면
 * 아무리 문질러도 지울 수 없는 칸이 생기기 때문이다. 잡초 뽑기와 같은 이유로
 * 크기·위치를 전부 S 배수로 잡는다.
 */

/** 칠판 얼룩 한 칸. [amount] 0f = 다 지워짐, 1f = 그대로. */
data class ChalkCell(
    val id: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val amount: Float,
)

data class BoardSnapshot(
    val cells: List<ChalkCell> = emptyList(),
    /** 지우개 반경(픽셀). 아이가 어디를 문지르는지 보여주는 데 쓴다. */
    val eraserRadius: Float = 0f,
    override val wrists: List<Vec2> = emptyList(),
    override val progress: Float = 0f,
    override val poseVisible: Boolean = false,
    override val nudge: Nudge = Nudge.NONE,
    override val cleared: Boolean = false,
) : MinigameStatus

class BoardGameEngine(
    /** 칸 개수. 너무 잘게 나누면 지운 자국이 지저분해 보인다. */
    private val columns: Int = 7,
    private val rows: Int = 5,
    /** 칠판 크기 (S 배수) */
    private val boardWidthInS: Float = 3.0f,
    private val boardHeightInS: Float = 2.0f,
    /** 칠판 중심을 어깨 중심에서 위로 올리는 양 (S 배수). 앉은 자세면 아래쪽은 책상에 가린다. */
    private val boardLiftInS: Float = 0.45f,
    /** 지우개 반경 (S 배수). 손 하나가 덮는 크기. */
    private val eraserRadiusInS: Float = 0.3f,
    /** 지우개 한가운데가 닿았을 때 초당 지워지는 양. 낮추면 여러 번 문질러야 한다. */
    private val erasePerSecond: Float = 1.8f,
    /** 이 비율만큼 지우면 완료. 구석 한 칸까지 강요하지 않는다. */
    private val clearThreshold: Float = 0.93f,
    /** 이만큼 아무 진전이 없으면 남은 얼룩이 저절로 옅어진다 (수업 중 막힘 방지) */
    private val assistAfterIdleMs: Long = 30_000L,
    private val nudgeKeepGoingAfterMs: Long = 60_000L,
    private val nudgeHowToQuitAfterMs: Long = 180_000L,
) {

    private class BoardRect(val left: Float, val top: Float, val width: Float, val height: Float)

    /** 칸별 남은 얼룩. 인덱스 = row * columns + column */
    private val dirt = FloatArray(columns * rows) { 1f }

    // 어깨 기준점은 랜드마크 노이즈 때문에 그대로 쓰면 칠판이 떨린다. EMA로 완충.
    private var anchorCenter: Vec2? = null
    private var anchorS = 0f

    private var lastUpdateMs = 0L
    private var idleElapsedMs = 0L

    fun reset() {
        dirt.fill(1f)
        anchorCenter = null
        anchorS = 0f
        lastUpdateMs = 0L
        idleElapsedMs = 0L
    }

    /** 조명이 나쁘거나 인식이 안 될 때 빠져나가기 위한 수동 클리어. */
    fun forceClear() {
        dirt.fill(0f)
    }

    fun update(pose: PoseInput, nowMs: Long): BoardSnapshot {
        val dtMs = if (lastUpdateMs == 0L) 0L else (nowMs - lastUpdateMs).coerceAtLeast(0L)
        lastUpdateMs = nowMs
        // 프레임이 크게 튀어도 한 번에 다 지워지지 않도록 상한을 둔다.
        val dt = min(dtMs / 1000f, 0.2f)

        val ls = pose.leftShoulder
        val rs = pose.rightShoulder

        // 어깨가 안 잡히면 아이가 자리에 없는 것. 게임을 멈추고 무반응 타이머도 세지 않는다.
        if (ls == null || rs == null) return snapshot(poseVisible = false)

        updateAnchor(ls, rs)
        val center = anchorCenter ?: return snapshot(poseVisible = false)
        val s = anchorS
        if (s <= 1f) return snapshot(poseVisible = false)

        val rect = boardRect(center, s, pose.viewWidth, pose.viewHeight)
        val wrists = listOfNotNull(pose.leftWrist, pose.rightWrist)
        val eraserRadius = eraserRadiusInS * s

        val erased = erase(wrists, rect, eraserRadius, dt)
        idleElapsedMs = if (erased) 0L else idleElapsedMs + dtMs

        applyAssist(dt)

        return snapshot(
            poseVisible = true,
            wrists = wrists,
            rect = rect,
            eraserRadius = eraserRadius,
        )
    }

    // ---------------------------------------------------------------- 내부

    private fun updateAnchor(ls: Vec2, rs: Vec2) {
        val center = Vec2((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f)
        val s = hypot(ls.x - rs.x, ls.y - rs.y)

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
     * 칠판을 몸 기준으로 놓되, 화면 밖으로 나가면 안으로 밀어 넣는다.
     * 화면보다 크면 화면 크기에 맞춰 줄인다 — 아이가 카메라에 바짝 붙어 앉는 경우.
     */
    private fun boardRect(center: Vec2, s: Float, viewWidth: Float, viewHeight: Float): BoardRect {
        var width = boardWidthInS * s
        var height = boardHeightInS * s
        if (viewWidth > 0f) width = min(width, viewWidth)
        if (viewHeight > 0f) height = min(height, viewHeight)

        var left = center.x - width / 2f
        var top = center.y - boardLiftInS * s - height / 2f
        if (viewWidth > 0f) left = left.coerceIn(0f, max(0f, viewWidth - width))
        if (viewHeight > 0f) top = top.coerceIn(0f, max(0f, viewHeight - height))

        return BoardRect(left, top, width, height)
    }

    /**
     * 손목 주변을 지운다. 지우개 한가운데가 가장 빨리 지워지고 가장자리로 갈수록 느려진다.
     * 문지르는 속도가 아니라 머문 시간으로 계산하므로 FPS 가 달라져도 체감이 같다.
     */
    private fun erase(
        wrists: List<Vec2>,
        rect: BoardRect,
        eraserRadius: Float,
        dt: Float,
    ): Boolean {
        if (wrists.isEmpty() || dt <= 0f || eraserRadius <= 0f) return false

        val cellWidth = rect.width / columns
        val cellHeight = rect.height / rows
        var erased = false

        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val index = row * columns + column
                if (dirt[index] <= 0f) continue

                val cellX = rect.left + (column + 0.5f) * cellWidth
                val cellY = rect.top + (row + 0.5f) * cellHeight

                val nearest = wrists.minOf { hypot(it.x - cellX, it.y - cellY) }
                if (nearest > eraserRadius) continue

                val falloff = 1f - nearest / eraserRadius
                val next = max(0f, dirt[index] - erasePerSecond * dt * falloff)
                if (next < dirt[index] - ERASE_EPSILON) erased = true
                dirt[index] = next
            }
        }
        return erased
    }

    /** 오래 진전이 없으면 남은 얼룩을 서서히 옅게 만들어 막힘을 푼다. */
    private fun applyAssist(dt: Float) {
        if (idleElapsedMs < assistAfterIdleMs || dt <= 0f) return
        for (i in dirt.indices) {
            if (dirt[i] > 0f) dirt[i] = max(0f, dirt[i] - ASSIST_PER_SECOND * dt)
        }
    }

    private fun snapshot(
        poseVisible: Boolean,
        wrists: List<Vec2> = emptyList(),
        rect: BoardRect? = null,
        eraserRadius: Float = 0f,
    ): BoardSnapshot {
        val remaining = dirt.sum()
        val progress = 1f - remaining / dirt.size
        val cleared = progress >= clearThreshold

        val cells = if (rect == null) {
            emptyList()
        } else {
            val cellWidth = rect.width / columns
            val cellHeight = rect.height / rows
            buildList {
                for (row in 0 until rows) {
                    for (column in 0 until columns) {
                        val index = row * columns + column
                        val amount = dirt[index]
                        if (amount <= VISIBLE_MIN) continue
                        add(
                            ChalkCell(
                                id = index,
                                centerX = rect.left + (column + 0.5f) * cellWidth,
                                centerY = rect.top + (row + 0.5f) * cellHeight,
                                width = cellWidth,
                                height = cellHeight,
                                amount = amount,
                            )
                        )
                    }
                }
            }
        }

        val nudge = when {
            cleared || !poseVisible -> Nudge.NONE
            idleElapsedMs >= nudgeHowToQuitAfterMs -> Nudge.HOW_TO_QUIT
            idleElapsedMs >= nudgeKeepGoingAfterMs -> Nudge.KEEP_GOING
            else -> Nudge.NONE
        }

        return BoardSnapshot(
            cells = cells,
            eraserRadius = eraserRadius,
            wrists = wrists,
            progress = progress.coerceIn(0f, 1f),
            poseVisible = poseVisible,
            nudge = nudge,
            cleared = cleared,
        )
    }

    private companion object {
        const val SMOOTHING = 0.25f
        /** 이보다 옅어지면 그리지 않는다 */
        const val VISIBLE_MIN = 0.02f
        /** 지웠다고 인정하는 최소 변화량 (무반응 타이머 판정용) */
        const val ERASE_EPSILON = 0.001f
        const val ASSIST_PER_SECOND = 0.05f
    }
}
