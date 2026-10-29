package com.example.aion_app.minigame.weed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.aion_app.minigame.GameIntroStep
import com.example.aion_app.minigame.HandView
import com.example.aion_app.minigame.PoseGameHost
import com.example.aion_app.minigame.drawEmoji
import com.example.aion_app.ui.screen.kids.tutorial.ArtWeedGrip
import com.example.aion_app.ui.screen.kids.tutorial.ArtWeedPull
import com.example.aion_app.ui.screen.kids.tutorial.ArtWeedReach
import java.util.concurrent.atomic.AtomicBoolean

private val WeedGreen = Color(0xFF7FC97F)
private val WeedGrabbed = Color(0xFFB8E986)
private val RootBrown = Color(0xFFBE9B6A)

/** 편 손 / 쥔 손. 아이가 자기 손 모양을 화면에서 바로 알아보도록 크게 그린다. */
private const val OPEN_HAND = "✋"
private const val CLOSED_HAND = "✊"

/** 잎 세 장이 벌어지는 방향 */
private val BLADES = listOf(-0.45f to -0.82f, 0f to -1f, 0.45f to -0.82f)

/** 이만큼 당기면 뿌리가 보이기 시작한다 */
private const val ROOT_SHOW_FROM = 0.25f

/**
 * 잡초 뽑기 미니게임 화면.
 *
 * 카메라·포즈 연결과 진행바 같은 공통 부분은 [PoseGameHost] 가 처리하고,
 * 여기서는 엔진을 만들고 잡초와 손을 그리는 일만 한다.
 *
 * @param onExit X 버튼을 눌렀을 때 (홈으로 복귀)
 * @param onGameStateChanged 게임 진입/종료 알림. 상동행동 판정을 일시정지시키는 데 쓴다.
 *                           뽑는 동작이 팔 상하 반복이라 감지기에 그대로 걸리기 때문.
 * @param onCleared 잡초를 다 뽑았을 때 한 번만. 구슬 상자 지급에 쓴다.
 * @param showDebug 오류 메시지 표시. 배포 시 false.
 */
@Composable
fun WeedGameScreen(
    onExit: () -> Unit,
    onGameStateChanged: (Boolean) -> Unit = {},
    onCleared: () -> Unit = {},
    showDebug: Boolean = true,
) {
    val engine = remember { WeedGameEngine() }

    // 완료 보상은 한 번만. update 가 매 프레임 돌기 때문에 플래그가 없으면
    // 다 뽑은 뒤 화면에 머무는 동안 상자가 계속 쌓인다.
    // update 는 카메라 결과 스레드에서 돌아서 AtomicBoolean 을 쓴다.
    val rewarded = remember { AtomicBoolean(false) }

    PoseGameHost(
        title = "잡초를 전부 뽑아보자!",
        introSteps = listOf(
            GameIntroStep("손바닥을\n잡초에 대") { ArtWeedReach(it) },
            GameIntroStep("손을\n꼭 쥐어") { ArtWeedGrip(it) },
            GameIntroStep("쥔 채로\n위로 쑥!") { ArtWeedPull(it) },
        ),
        introTip = "손을 펴면 잡초를 놓쳐. 다시 쥐면 돼! 잡초 6개를 다 뽑으면 끝이야.",
        clearedMessage = "잡초를 다 뽑았어!",
        idleStartHint = "잡초에 손을 대고 손을 꼭 쥐어 볼까?",
        idleResumeHint = "잡초가 조금 남았어! 하나만 더 뽑아 볼까?",
        onExit = onExit,
        onForceClear = { engine.forceClear() },
        onGameStateChanged = onGameStateChanged,
        update = { pose, nowMs ->
            val snapshot = engine.update(pose, nowMs)
            if (snapshot.cleared && rewarded.compareAndSet(false, true)) onCleared()
            snapshot
        },
        draw = { scope, snapshot -> scope.drawWeedGame(snapshot) },
        showDebug = showDebug,
    )
}

private fun DrawScope.drawWeedGame(snapshot: GameSnapshot) {
    snapshot.weeds.forEach { drawWeed(it) }
    snapshot.hands.forEach { drawHand(it) }
}

/**
 * 손을 그린다. 펴면 ✋, 쥐면 ✊ 로 **매 프레임 바뀐다.**
 * 잡초를 쥐고 있는 동안에는 조금 크게 그려서 "지금 내가 뭔가를 잡고 있다"가 드러나게 한다.
 */
private fun DrawScope.drawHand(hand: HandView) {
    val closed = hand.holding || hand.grip >= GRIP_SHOW_CLOSED
    val size = if (hand.holding) hand.size * 1.15f else hand.size
    drawEmoji(if (closed) CLOSED_HAND else OPEN_HAND, hand.pos, size)
}

/**
 * 잡초 한 포기.
 *
 * 상태마다 모양이 다르다.
 *   가만히  — 잎 세 장이 위로 곧게 선다
 *   잡음    — 잎이 손 쪽으로 쏠리고 색이 밝아진다
 *   당기는 중 — 당긴 만큼 줄기가 늘어나고 뿌리가 드러난다
 *   뽑힘    — 잠깐 위로 떠오르며 사라진다
 */
private fun DrawScope.drawWeed(weed: WeedView) {
    val height = weed.height
    if (height <= 1f) return

    val alpha = if (weed.state == WeedState.PULLED) weed.fade.coerceIn(0f, 1f) else 1f
    if (alpha <= 0.01f) return

    val baseX = weed.pos.x
    val baseY = weed.pos.y

    // 당긴 만큼 줄기가 늘어나고, 잡은 손 쪽으로 쏠린다.
    val stretch = 1f + weed.pullProgress * 0.55f
    val leanX: Float
    val leanY: Float
    val hand = weed.handPos
    if (hand != null) {
        val follow = 0.3f + weed.pullProgress * 0.45f
        leanX = (hand.x - baseX) * follow
        leanY = (hand.y - baseY) * follow
    } else {
        leanX = 0f
        leanY = 0f
    }

    if (weed.pullProgress > ROOT_SHOW_FROM) {
        drawRoots(baseX, baseY, height, weed.pullProgress, alpha)
    }

    val color = (if (weed.state == WeedState.IDLE) WeedGreen else WeedGrabbed).copy(alpha = alpha)
    BLADES.forEach { (dx, dy) ->
        val path = Path().apply {
            moveTo(baseX, baseY)
            quadraticBezierTo(
                baseX + dx * height * 0.15f + leanX * 0.3f,
                baseY + dy * height * 0.5f + leanY * 0.3f,
                baseX + dx * height * stretch + leanX,
                baseY + dy * height * stretch + leanY,
            )
        }
        drawPath(path, color, style = Stroke(width = height * 0.13f, cap = StrokeCap.Round))
    }
}

/** 뽑히기 직전에 밑동 아래로 드러나는 뿌리. 당길수록 길어진다. */
private fun DrawScope.drawRoots(
    baseX: Float,
    baseY: Float,
    height: Float,
    pullProgress: Float,
    alpha: Float,
) {
    val length = height * 0.3f * pullProgress
    if (length <= 1f) return

    val color = RootBrown.copy(alpha = alpha * pullProgress)
    listOf(-0.4f, 0f, 0.4f).forEach { dx ->
        drawLine(
            color = color,
            start = Offset(baseX, baseY),
            end = Offset(baseX + dx * length, baseY + length),
            strokeWidth = height * 0.07f,
            cap = StrokeCap.Round,
        )
    }
}

/** 손 그림을 주먹으로 바꾸는 기준. 잡기 판정보다 살짝 낮춰 손 모양이 먼저 따라오게 한다. */
private const val GRIP_SHOW_CLOSED = 0.45f
