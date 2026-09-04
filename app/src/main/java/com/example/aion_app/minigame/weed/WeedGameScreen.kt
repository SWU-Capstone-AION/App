package com.example.aion_app.minigame.weed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.aion_app.minigame.PoseGameHost

private val WeedGreen = Color(0xFF7FC97F)
private val WeedGrabbed = Color(0xFFB8E986)

/**
 * 잡초 뽑기 미니게임 화면.
 *
 * 카메라·포즈 연결과 진행바 같은 공통 부분은 [PoseGameHost] 가 처리하고,
 * 여기서는 엔진을 만들고 잡초를 그리는 일만 한다.
 *
 * @param onExit X 버튼을 눌렀을 때 (홈으로 복귀)
 * @param onGameStateChanged 게임 진입/종료 알림. 상동행동 판정을 일시정지시키는 데 쓴다.
 *                           뽑는 동작이 팔 상하 반복이라 감지기에 그대로 걸리기 때문.
 * @param showDebug 손목 위치를 원으로 표시. 배포 시 false.
 */
@Composable
fun WeedGameScreen(
    onExit: () -> Unit,
    onGameStateChanged: (Boolean) -> Unit = {},
    showDebug: Boolean = true,
) {
    val engine = remember { WeedGameEngine() }

    PoseGameHost(
        title = "잡초를 전부 뽑아보자!",
        introMessage = "잡초를 손으로 잡고 위로 쑥 뽑아 줘!\n천천히 해도 괜찮아.",
        clearedMessage = "다 뽑았어! 잘했어",
        onExit = onExit,
        onForceClear = { engine.forceClear() },
        onGameStateChanged = onGameStateChanged,
        update = { pose, nowMs -> engine.update(pose, nowMs) },
        draw = { scope, snapshot -> scope.drawWeeds(snapshot) },
        showDebug = showDebug,
    )
}

private fun DrawScope.drawWeeds(snapshot: GameSnapshot) {
    snapshot.weeds.forEach { weed ->
        if (weed.state != WeedState.PULLED) drawWeed(weed)
    }
}

/** 잡초 한 포기를 잎 세 장으로 그린다. */
private fun DrawScope.drawWeed(weed: WeedView) {
    val height = weed.height
    if (height <= 1f) return

    val color = if (weed.state == WeedState.GRABBED) WeedGrabbed else WeedGreen
    val baseX = weed.pos.x
    val baseY = weed.pos.y
    val blades = listOf(-0.45f to -0.82f, 0f to -1f, 0.45f to -0.82f)

    blades.forEach { (dx, dy) ->
        val path = Path().apply {
            moveTo(baseX, baseY)
            quadraticBezierTo(
                baseX + dx * height * 0.15f,
                baseY + dy * height * 0.5f,
                baseX + dx * height,
                baseY + dy * height,
            )
        }
        drawPath(path, color, style = Stroke(width = height * 0.13f, cap = StrokeCap.Round))
    }
}