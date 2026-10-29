package com.example.aion_app.minigame.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.aion_app.minigame.GameIntroStep
import com.example.aion_app.minigame.HandView
import com.example.aion_app.minigame.PoseGameHost
import com.example.aion_app.ui.screen.kids.tutorial.ArtBoardClean
import com.example.aion_app.ui.screen.kids.tutorial.ArtBoardRaise
import com.example.aion_app.ui.screen.kids.tutorial.ArtBoardRub
import java.util.concurrent.atomic.AtomicBoolean

private val BoardGreen = Color(0xFF2E4A3A)
private val Chalk = Color(0xFFF2F2F2)

/** 칠판 지우개 — 나무 손잡이 + 아래쪽 펠트 */
private val EraserWood = Color(0xFFD9A441)
private val EraserWoodDark = Color(0xFFB07F2C)
private val EraserFelt = Color(0xFF2F3A42)
private val EraserShadow = Color(0x4D000000)

/** 손에 들린 것처럼 살짝 기울여 둔다 */
private const val ERASER_TILT = -8f

/**
 * 칠판 지우기 미니게임 화면.
 *
 * 카메라 영상 위에 칠판을 덮어 두고, 손으로 문지르면 그 자리가 지워지면서
 * 자기 모습이 드러난다. 지운 만큼 바로 보이니까 아이가 결과를 알기 쉽다.
 *
 * 카메라·포즈 연결과 진행바 같은 공통 부분은 [PoseGameHost] 가 처리한다.
 *
 * @param onExit X 버튼을 눌렀을 때 (홈으로 복귀)
 * @param onGameStateChanged 게임 진입/종료 알림. 상동행동 판정을 일시정지시키는 데 쓴다.
 *                           문지르는 동작이 팔 좌우 반복이라 감지기에 그대로 걸리기 때문.
 * @param onCleared 칠판을 다 지웠을 때 한 번만. 구슬 상자 지급에 쓴다.
 * @param showDebug 손목 위치를 원으로 표시. 배포 시 false.
 */
@Composable
fun BoardGameScreen(
    onExit: () -> Unit,
    onGameStateChanged: (Boolean) -> Unit = {},
    onCleared: () -> Unit = {},
    showDebug: Boolean = true,
) {
    val engine = remember { BoardGameEngine() }

    // 완료 보상은 한 번만. (WeedGameScreen 과 같은 이유)
    val rewarded = remember { AtomicBoolean(false) }

    PoseGameHost(
        title = "칠판을 깨끗하게 지워보자!",
        introSteps = listOf(
            GameIntroStep("손을\n칠판 쪽으로 들어") { ArtBoardRaise(it) },
            GameIntroStep("손바닥으로\n쓱쓱 문질러") { ArtBoardRub(it) },
            GameIntroStep("얼룩이 사라지면\n반짝!") { ArtBoardClean(it) },
        ),
        introTip = "구석이 조금 남아도 괜찮아. 거의 다 지우면 끝이야.",
        clearedMessage = "칠판이 반짝반짝해졌어!",
        idleStartHint = "손바닥을 들어 칠판을 쓱쓱 문질러 볼까?",
        idleResumeHint = "얼룩이 조금 남았어! 손을 들어 마저 지워 볼까?",
        onExit = onExit,
        onForceClear = { engine.forceClear() },
        onGameStateChanged = onGameStateChanged,
        update = { pose, nowMs ->
            val snapshot = engine.update(pose, nowMs)
            if (snapshot.cleared && rewarded.compareAndSet(false, true)) onCleared()
            snapshot
        },
        draw = { scope, snapshot -> scope.drawBoard(snapshot) },
        showDebug = showDebug,
    )
}

private fun DrawScope.drawBoard(snapshot: BoardSnapshot) {
    snapshot.cells.forEach { cell -> drawCell(cell) }

    // 손 자리에는 손 대신 칠판 지우개를 그린다.
    // 디버그 표시가 아니라 "이걸로 문지르면 된다"는 아이에게 주는 안내라 항상 그린다.
    snapshot.hands.forEach { drawEraser(it) }
}

/**
 * 칠판 지우개 한 개.
 * 위쪽은 나무 손잡이, 아래쪽은 펠트. 펠트 끝에 분필 가루를 한 줄 얹어
 * 어느 쪽으로 문지르는 것인지 바로 보이게 했다.
 */
private fun DrawScope.drawEraser(hand: HandView) {
    val width = hand.size
    if (width <= 1f) return

    val height = width * 0.62f
    val left = hand.pos.x - width / 2f
    val top = hand.pos.y - height / 2f
    val corner = CornerRadius(height * 0.22f, height * 0.22f)

    rotate(degrees = ERASER_TILT, pivot = Offset(hand.pos.x, hand.pos.y)) {
        drawRoundRect(
            color = EraserShadow,
            topLeft = Offset(left, top + height * 0.1f),
            size = Size(width, height),
            cornerRadius = corner,
        )
        // 나무 손잡이
        drawRoundRect(
            color = EraserWood,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = corner,
        )
        // 손잡이 아래쪽 그늘 — 두께가 있어 보이게
        drawRoundRect(
            color = EraserWoodDark,
            topLeft = Offset(left, top + height * 0.38f),
            size = Size(width, height * 0.24f),
            cornerRadius = CornerRadius(height * 0.08f, height * 0.08f),
        )
        // 펠트
        drawRoundRect(
            color = EraserFelt,
            topLeft = Offset(left, top + height * 0.55f),
            size = Size(width, height * 0.45f),
            cornerRadius = corner,
        )
        // 분필 가루
        drawLine(
            color = Chalk.copy(alpha = 0.55f),
            start = Offset(left + width * 0.12f, top + height * 0.95f),
            end = Offset(left + width * 0.88f, top + height * 0.95f),
            strokeWidth = height * 0.07f,
            cap = StrokeCap.Round,
        )
    }
}

/** 칸 하나 = 칠판 바탕 + 분필 자국. 지울수록 둘 다 옅어진다. */
private fun DrawScope.drawCell(cell: ChalkCell) {
    val alpha = cell.amount.coerceIn(0f, 1f)
    val left = cell.centerX - cell.width / 2f
    val top = cell.centerY - cell.height / 2f

    drawRect(
        color = BoardGreen.copy(alpha = alpha),
        topLeft = Offset(left, top),
        size = Size(cell.width, cell.height),
    )

    // 분필 자국. 칸마다 방향을 바꿔 손으로 쓴 낙서처럼 보이게 한다.
    val inset = cell.width * 0.18f
    val insetY = cell.height * 0.22f
    val (start, end) = when (cell.id % 3) {
        0 -> Offset(left + inset, top + insetY) to
                Offset(left + cell.width - inset, top + cell.height - insetY)
        1 -> Offset(left + inset, top + cell.height - insetY) to
                Offset(left + cell.width - inset, top + insetY)
        else -> Offset(left + inset, cell.centerY) to
                Offset(left + cell.width - inset, cell.centerY)
    }

    drawLine(
        color = Chalk.copy(alpha = alpha * 0.35f),
        start = start,
        end = end,
        strokeWidth = cell.height * 0.09f,
        cap = StrokeCap.Round,
    )
}