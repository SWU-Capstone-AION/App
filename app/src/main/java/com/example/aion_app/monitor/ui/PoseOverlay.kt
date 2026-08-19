package com.example.aion_app.monitor.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import com.example.aion_app.monitor.pose.ARM_POINTS
import com.example.aion_app.monitor.pose.BOX_IDX
import com.example.aion_app.monitor.pose.PoseIndex
import com.example.aion_app.monitor.pose.SKELETON_BONES
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val SkeletonColor = Color(0xFF34C6FF)
private val JointColor = Color(0xFFD6F0FF)
private val BoxColor = Color(0xFF34C6FF)
private val LeftHud = Color(0xFF00E0FF)
private val RightHud = Color(0xFF6DB4FF)
private const val VIS_MIN = 0.3f

/**
 * 포즈 스켈레톤 + 3D 타겟 박스 + 손목 HUD 마커를 카메라 프리뷰(FILL_CENTER) 위에 겹쳐 그린다.
 * HTML 원본(drawSkeleton / draw3DBox / drawWristCoords)을 Compose Canvas 로 이식.
 */
@Composable
fun PoseOverlay(
    result: PoseLandmarkerResult?,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "hud")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(100_000, easing = LinearEasing)),
        label = "t",
    )

    Canvas(modifier = modifier) {
        val landmarks = result?.landmarks()?.firstOrNull() ?: return@Canvas
        if (imageWidth == 0 || imageHeight == 0 || landmarks.size < 33) return@Canvas

        val scale = max(size.width / imageWidth, size.height / imageHeight)
        val ox = (size.width - imageWidth * scale) / 2f
        val oy = (size.height - imageHeight * scale) / 2f
        fun pt(i: Int) = Offset(
            landmarks[i].x() * imageWidth * scale + ox,
            landmarks[i].y() * imageHeight * scale + oy,
        )
        fun vis(i: Int) = landmarks[i].visibility().orElse(1f)

        draw3DBox(::pt, ::vis, t)
        drawSkeleton(::pt, ::vis)
        drawWristHud(landmarks, ::pt, t)
    }
}

private fun DrawScope.drawSkeleton(pt: (Int) -> Offset, vis: (Int) -> Float) {
    for ((a, b) in SKELETON_BONES) {
        if (vis(a) < VIS_MIN || vis(b) < VIS_MIN) continue
        val isArm = a in ARM_POINTS || b in ARM_POINTS
        drawLine(SkeletonColor, pt(a), pt(b), strokeWidth = if (isArm) 9f else 5f)
    }
    for (i in PoseIndex.DRAW_POINTS) {
        if (vis(i) < VIS_MIN) continue
        val isWrist = i == PoseIndex.LEFT_WRIST || i == PoseIndex.RIGHT_WRIST
        drawCircle(
            color = if (isWrist) SkeletonColor else JointColor,
            radius = if (isWrist) 14f else 10f,
            center = pt(i),
        )
    }
}

private fun DrawScope.draw3DBox(pt: (Int) -> Offset, vis: (Int) -> Float, t: Float) {
    val pts = BOX_IDX.filter { vis(it) > 0.35f }.map { pt(it) }
    if (pts.size < 3) return
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (p in pts) {
        minX = min(minX, p.x); maxX = max(maxX, p.x)
        minY = min(minY, p.y); maxY = max(maxY, p.y)
    }
    val padX = (maxX - minX) * 0.18f + 18f
    val padY = (maxY - minY) * 0.10f + 22f
    minX -= padX; maxX += padX; minY -= padY; maxY += padY
    val bw = maxX - minX; val bh = maxY - minY
    if (bw < 20 || bh < 20) return

    val depth = (min(bw, bh) * 0.22f) * (0.92f + 0.08f * sin(t * 1.2f))
    val dx = depth * 0.62f; val dy = -depth * 0.5f

    val f = arrayOf(
        Offset(minX, minY), Offset(maxX, minY), Offset(maxX, maxY), Offset(minX, maxY),
    )
    val b = Array(4) { Offset(f[it].x + dx, f[it].y + dy) }

    // 후면
    drawPolyline(b, BoxColor.copy(alpha = 0.35f), 1f, close = true)
    // 연결 모서리
    for (i in 0 until 4) drawLine(BoxColor.copy(alpha = 0.45f), f[i], b[i], 1f)
    // 전면 코너 브래킷
    val corner = min(bw, bh) * 0.16f
    val seg = { p: Offset, qx: Float, qy: Float ->
        drawLine(BoxColor.copy(alpha = 0.9f), p, Offset(qx, qy), 1.6f)
    }
    seg(f[0], f[0].x + corner, f[0].y); seg(f[0], f[0].x, f[0].y + corner)
    seg(f[1], f[1].x - corner, f[1].y); seg(f[1], f[1].x, f[1].y + corner)
    seg(f[2], f[2].x - corner, f[2].y); seg(f[2], f[2].x, f[2].y - corner)
    seg(f[3], f[3].x + corner, f[3].y); seg(f[3], f[3].x, f[3].y - corner)
    // 전면 외곽
    drawPolyline(f, BoxColor.copy(alpha = 0.5f), 1f, close = true)

    // 라벨
    val labelX = (f[0].x + b[1].x) / 2f
    val labelY = min(f[0].y, b[1].y) - 8f
    drawIntoCanvas {
        val paint = Paint().apply {
            color = android.graphics.Color.parseColor("#34C6FF")
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        it.nativeCanvas.drawText("TARGET_LOCK", labelX, labelY, paint)
    }
}

private fun DrawScope.drawPolyline(pts: Array<Offset>, color: Color, width: Float, close: Boolean) {
    val path = Path()
    pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
    if (close) path.close()
    drawPath(path, color, style = Stroke(width = width))
}

private fun DrawScope.drawWristHud(
    landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
    pt: (Int) -> Offset,
    t: Float,
) {
    val pulse = 0.55f + 0.45f * (0.5f + 0.5f * sin(t * 3f))
    val rotDeg = Math.toDegrees((t * 0.6).toDouble()).toFloat()

    fun drawOne(idx: Int, color: Color, label: String) {
        if (landmarks[idx].visibility().orElse(1f) < VIS_MIN) return
        val c = pt(idx)
        // 십자선
        val gap = 8f; val len = 12f
        val cross = color.copy(alpha = 0.9f * pulse)
        drawLine(cross, Offset(c.x, c.y - gap), Offset(c.x, c.y - gap - len), 1.6f)
        drawLine(cross, Offset(c.x, c.y + gap), Offset(c.x, c.y + gap + len), 1.6f)
        drawLine(cross, Offset(c.x - gap, c.y), Offset(c.x - gap - len, c.y), 1.6f)
        drawLine(cross, Offset(c.x + gap, c.y), Offset(c.x + gap + len, c.y), 1.6f)
        // 회전 코너 브래킷
        val r = 26f; val bl = 9f
        rotate(rotDeg, pivot = c) {
            for (k in 0 until 4) {
                rotate(90f * k, pivot = c) {
                    val br = color.copy(alpha = 0.85f)
                    drawLine(br, Offset(c.x + r - bl, c.y - r), Offset(c.x + r, c.y - r), 2f)
                    drawLine(br, Offset(c.x + r, c.y - r), Offset(c.x + r, c.y - r + bl), 2f)
                }
            }
        }
        // 중앙 점
        drawCircle(color, radius = 3.2f, center = c)

        // HUD 라벨 박스 + 좌표
        val boxW = 150f; val boxH = 40f; val ch = 7f
        val bx = c.x + 34f; val by = c.y - boxH / 2f - 18f
        // 리더선
        val lead = color.copy(alpha = 0.8f)
        val leadPath = Path().apply {
            moveTo(c.x + 14f, c.y); lineTo(bx, by + boxH); lineTo(bx + 14f, by + boxH)
        }
        drawPath(leadPath, lead, style = Stroke(width = 1.2f))
        // 박스 (좌상단 컷)
        val boxPath = Path().apply {
            moveTo(bx + ch, by); lineTo(bx + boxW, by)
            lineTo(bx + boxW, by + boxH - ch); lineTo(bx + boxW - ch, by + boxH)
            lineTo(bx, by + boxH); lineTo(bx, by + ch); close()
        }
        drawPath(boxPath, Color(0xD1020810))
        drawPath(boxPath, color, style = Stroke(width = 1.4f))
        // 구분선
        drawLine(color.copy(alpha = 0.35f), Offset(bx + 9f, by + 19f), Offset(bx + boxW - 9f, by + 19f), 1f)

        val xr = (landmarks[idx].x() * 1280).toInt()
        val yr = (landmarks[idx].y() * 720).toInt()
        drawIntoCanvas {
            val labelPaint = Paint().apply {
                this.color = color.toArgbInt()
                textSize = 20f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                isAntiAlias = true
            }
            it.nativeCanvas.drawText(label, bx + 9f, by + 15f, labelPaint)
            val coordPaint = Paint().apply {
                this.color = android.graphics.Color.parseColor("#E8FBFF")
                textSize = 24f
                typeface = Typeface.MONOSPACE
                isAntiAlias = true
            }
            it.nativeCanvas.drawText("X:%d Y:%d".format(xr, yr), bx + 9f, by + 36f, coordPaint)
        }
    }

    drawOne(PoseIndex.LEFT_WRIST, LeftHud, "L · WRIST")
    drawOne(PoseIndex.RIGHT_WRIST, RightHud, "R · WRIST")
}

private fun Color.toArgbInt(): Int {
    val a = (alpha * 255).toInt(); val r = (red * 255).toInt()
    val g = (green * 255).toInt(); val bl = (blue * 255).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or bl
}
