package com.example.aion_app.minigame.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.aion_app.minigame.PoseGameHost

private val BoardGreen = Color(0xFF2E4A3A)
private val Chalk = Color(0xFFF2F2F2)

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
 * @param showDebug 손목 위치를 원으로 표시. 배포 시 false.
 */
@Composable
fun BoardGameScreen(
    onExit: () -> Unit,
    onGameStateChanged: (Boolean) -> Unit = {},
    showDebug: Boolean = true,
) {
    val engine = remember { BoardGameEngine() }

    PoseGameHost(
        title = "칠판을 깨끗하게 지워보자!",
        introMessage = "손바닥으로 칠판을 쓱쓱 문질러 줘!\n천천히 해도 괜찮아.",
        clearedMessage = "칠판이 반짝반짝해! 잘했어",
        onExit = onExit,
        onForceClear = { engine.forceClear() },
        onGameStateChanged = onGameStateChanged,
        update = { pose, nowMs -> engine.update(pose, nowMs) },
        draw = { scope, snapshot -> scope.drawBoard(snapshot) },
        showDebug = showDebug,
    )
}

private fun DrawScope.drawBoard(snapshot: BoardSnapshot) {
    snapshot.cells.forEach { cell -> drawCell(cell) }

    // 지우개 위치. 디버그가 아니라 아이에게 주는 피드백이라 항상 그린다.
    if (snapshot.eraserRadius > 0f) {
        snapshot.wrists.forEach { wrist ->
            drawCircle(
                color = Chalk.copy(alpha = 0.45f),
                radius = snapshot.eraserRadius,
                center = Offset(wrist.x, wrist.y),
                style = Stroke(width = 5f),
            )
        }
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