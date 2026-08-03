package com.example.aion_app.monitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.aion_app.monitor.pose.StereotypyDetector
import com.example.aion_app.monitor.pose.StereotypyDetector.TimelinePoint
import com.example.aion_app.monitor.pose.StereotypyDetector.WristPoint

private val LeftColor = Color(0xFF34C6FF)
private val RightColor = Color(0xFF3B6DFF)
private val HeadColor = Color(0xFF16D0C0)
private val BodyColor = Color(0xFFB47CFF)
private val ThresholdColor = Color(0xFFFF5A3C)
private val GridColor = Color(0x1A34C6FF)

/** 이상 행동 빈도 추이 (좌/우 누적활성 시간, 최근 30초). */
@Composable
fun TimelineChart(timeline: List<TimelinePoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val yMax = StereotypyDetector.GLOBAL_WINDOW_S
        fun yOf(v: Double) = (h - (v / yMax) * (h - 4) - 2).toFloat()

        // 그리드 (0, 1.5, 3s)
        for (tv in listOf(0.0, yMax * 0.5, yMax)) {
            val y = yOf(tv)
            drawLine(GridColor, Offset(0f, y), Offset(w, y), 1f)
        }

        if (timeline.size < 2) return@Canvas
        val tEnd = timeline.last().t
        val tStart = (tEnd - StereotypyDetector.TIMELINE_WINDOW_S).coerceAtLeast(0.0)
        val span = (tEnd - tStart).takeIf { it > 0 } ?: 1.0
        fun xOf(t: Double) = (((t - tStart) / span) * w).toFloat()

        // 알람 구간 음영
        var runStart: Double? = null
        for (i in timeline.indices) {
            val p = timeline[i]
            if (p.alarm && runStart == null) runStart = p.t
            if ((!p.alarm || i == timeline.size - 1) && runStart != null) {
                val endT = if (p.alarm) p.t else timeline[i - 1].t
                val x0 = xOf(runStart!!)
                drawRect(
                    color = Color(0x28FF4630),
                    topLeft = Offset(x0, 0f),
                    size = androidx.compose.ui.geometry.Size((xOf(endT) - x0).coerceAtLeast(1f), h),
                )
                runStart = null
            }
        }

        // 임계선 (점선)
        val thy = yOf(StereotypyDetector.DURATION_THRESHOLD)
        drawLine(
            ThresholdColor, Offset(0f, thy), Offset(w, thy), 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )

        // 부위별 파형 (좌팔·우팔·머리·몸통)
        for ((sel, color) in listOf<Pair<(TimelinePoint) -> Double, Color>>(
            { p: TimelinePoint -> p.leftArm } to LeftColor,
            { p: TimelinePoint -> p.rightArm } to RightColor,
            { p: TimelinePoint -> p.head } to HeadColor,
            { p: TimelinePoint -> p.body } to BodyColor,
        )) {
            val path = Path()
            timeline.forEachIndexed { i, p ->
                val x = xOf(p.t); val y = yOf(sel(p))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2f))
        }
    }
}

/** 실시간 손목 세로위치 그래프 (왼팔/오른팔, 최근 6초, 자동 스케일). */
@Composable
fun WristGraph(wristTrace: List<WristPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        for (i in 1 until 4) {
            val y = h * i / 4f
            drawLine(GridColor, Offset(0f, y), Offset(w, y), 1f)
        }

        if (wristTrace.size < 2) return@Canvas

        var mn = 1.0; var mx = 0.0; var has = false
        for (s in wristTrace) for (v in listOf(s.ly, s.ry)) {
            if (v != null) { mn = minOf(mn, v); mx = maxOf(mx, v); has = true }
        }
        if (!has) { mn = 0.0; mx = 1.0 }
        if (mx - mn < 0.12) { val c = (mn + mx) / 2; mn = c - 0.06; mx = c + 0.06 }
        val pad = (mx - mn) * 0.15; mn -= pad; mx += pad
        val range = (mx - mn).takeIf { it != 0.0 } ?: 1.0

        val tNow = wristTrace.last().t
        val t0 = tNow - StereotypyDetector.WRIST_WIN
        fun xOf(t: Double) = (((t - t0) / StereotypyDetector.WRIST_WIN) * w).toFloat()
        fun yOf(v: Double) = (((v - mn) / range) * (h - 8) + 4).toFloat()

        fun drawTrace(sel: (WristPoint) -> Double?, color: Color) {
            val path = Path()
            var pen = false
            for (s in wristTrace) {
                val v = sel(s)
                if (v == null) { pen = false; continue }
                val x = xOf(s.t); val y = yOf(v)
                if (!pen) { path.moveTo(x, y); pen = true } else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 3f))
        }
        drawTrace({ it.ly }, LeftColor)
        drawTrace({ it.ry }, RightColor)
    }
}
