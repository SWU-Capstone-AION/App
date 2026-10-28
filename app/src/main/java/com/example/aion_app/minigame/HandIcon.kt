package com.example.aion_app.minigame

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/**
 * 이모지 그리기용 Paint. 프레임마다 새로 만들면 낭비라 한 번만 만들어 재사용한다.
 * 그리기는 UI 스레드에서만 일어나므로 공유해도 안전하다.
 */
private val emojiPaint = android.graphics.Paint().apply {
    isAntiAlias = true
    textAlign = android.graphics.Paint.Align.CENTER
}

/**
 * 이모지를 한 점에 중심을 맞춰 그린다.
 * 카메라 영상이 밝든 어둡든 보이도록 그림자를 깔아 둔다.
 */
fun DrawScope.drawEmoji(emoji: String, center: Vec2, size: Float) {
    if (size <= 1f) return

    emojiPaint.textSize = size
    emojiPaint.setShadowLayer(
        size * 0.12f,
        0f,
        size * 0.03f,
        android.graphics.Color.argb(140, 0, 0, 0),
    )
    val metrics = emojiPaint.fontMetrics
    val baseline = center.y - (metrics.ascent + metrics.descent) / 2f

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(emoji, center.x, baseline, emojiPaint)
    }
}
