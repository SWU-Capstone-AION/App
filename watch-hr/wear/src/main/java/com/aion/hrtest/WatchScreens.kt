package com.aion.hrtest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices

/*
 * 워치 화면 4가지 (예시 디자인 기준): 대기 · 측정 중 · 연결 끊김 · 착용 확인.
 * 아동이 볼 수도 있으므로 어두운 남색 배경에 색과 문구를 최소로 쓴다 (설계서 7장).
 */

/** 워치 화면 상태 */
enum class WatchScreen { IDLE, MEASURING, DISCONNECTED, NOT_WORN }

/** 측정 서비스 상태 → 보여줄 화면. 착용 확인이 연결 끊김보다 먼저다 (차지 않으면 보낼 값도 없음) */
fun screenOf(running: Boolean, worn: Boolean, connected: Boolean): WatchScreen = when {
    !running -> WatchScreen.IDLE
    !worn -> WatchScreen.NOT_WORN
    !connected -> WatchScreen.DISCONNECTED
    else -> WatchScreen.MEASURING
}

// ---- 색 (예시 이미지에서 뽑음) ----
private val Bg = Color(0xFF2B3452)          // 화면 배경 남색
private val SurfaceTone = Color(0xFF39436B) // 아이콘 원·측정 원
private val Accent = Color(0xFF7D93E6)      // 주 버튼 (시작·다시 연결)
private val Outline = Color(0xFF5B6690)     // 보조 버튼 테두리 (중지·다시 시도)
private val Label = Color(0xFF8FA2E0)       // "AION" 글자
private val SubText = Color(0xFFAAB3D0)     // 안내 문구
private val Heart = Color(0xFFE0806A)       // 하트
private val Warn = Color(0xFFE6B450)        // 경고 삼각형
private val Ok = Color(0xFF5CCB8F)          // 연결됨 점

@Composable
fun AionWatchApp(
    screen: WatchScreen,
    bpm: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReconnect: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Bg).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        when (screen) {
            WatchScreen.IDLE -> IdleScreen(onStart)
            WatchScreen.MEASURING -> MeasuringScreen(bpm, onStop)
            WatchScreen.DISCONNECTED -> DisconnectedScreen(onReconnect)
            WatchScreen.NOT_WORN -> NotWornScreen(onRetry)
        }
    }
}

// ---- 화면 ----

/** 앱을 열면 보이는 화면. 교사가 '시작'을 누르면 측정이 시작된다 */
@Composable
private fun IdleScreen(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconCircle { VectorIcon(Icons.Filled.Favorite, Heart, 22) }
        Spacer(Modifier.height(10.dp))
        Brand()
        Spacer(Modifier.height(2.dp))
        Title("심박수 측정")
        Spacer(Modifier.height(14.dp))
        PillButton("시작", filled = true, onClick = onStart)
    }
}

/** 평소 상태. 숫자는 워치가 잰 값 그대로이고, 기준선·위험도 계산은 태블릿이 한다 */
@Composable
private fun MeasuringScreen(bpm: Int, onStop: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Brand()
        Spacer(Modifier.height(4.dp))
        // 둥근 화면 위·아래에 글자와 버튼이 걸리지 않게 원 크기를 줄여 둔다
        Box(
            Modifier.size(90.dp).clip(CircleShape).background(SurfaceTone),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VectorIcon(Icons.Filled.Favorite, Heart, 9)
                    Spacer(Modifier.width(3.dp))
                    Text("측정 중", color = SubText, fontSize = 9.sp)
                }
                Text(
                    if (bpm > 0) "$bpm" else "--",
                    color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light
                )
                Text("bpm", color = SubText, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        // 태블릿 연결 상태 칩
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(SurfaceTone)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Ok))
            Spacer(Modifier.width(5.dp))
            Text("태블릿 연결됨", color = SubText, fontSize = 9.sp)
        }
        Spacer(Modifier.height(6.dp))
        PillButton("중지", filled = false, onClick = onStop, height = 34)
    }
}

/** 태블릿과 끊겼을 때. 태블릿은 이 동안 심박 위험도를 쓰지 않고 행동 감지만으로 판정한다 */
@Composable
private fun DisconnectedScreen(onReconnect: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconCircle { VectorIcon(Icons.Filled.Warning, Warn, 20) }
        Spacer(Modifier.height(10.dp))
        Title("태블릿 연결 끊김")
        Spacer(Modifier.height(4.dp))
        Sub("태블릿이 켜져 있는지 확인해 주세요")
        Spacer(Modifier.height(12.dp))
        PillButton("다시 연결", filled = true, onClick = onReconnect)
    }
}

/** 센서에 값이 안 잡힐 때. 아동이 볼 수도 있으므로 문구는 부드럽게 */
@Composable
private fun NotWornScreen(onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconCircle { WatchGlyph(Label) }
        Spacer(Modifier.height(10.dp))
        Title("심박이 잡히지 않아요")
        Spacer(Modifier.height(4.dp))
        Sub("워치를 손목에 조금 더 밀착해 주세요")
        Spacer(Modifier.height(12.dp))
        PillButton("다시 시도", filled = false, onClick = onRetry)
    }
}

// ---- 부품 ----

@Composable
private fun Brand() = Text(
    "A I O N", color = Label, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
)

@Composable
private fun Title(text: String) = Text(
    text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
)

@Composable
private fun Sub(text: String) = Text(
    text, color = SubText, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 12.sp
)

@Composable
private fun IconCircle(content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(SurfaceTone),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun VectorIcon(icon: ImageVector, tint: Color, sizeDp: Int) {
    Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(sizeDp.dp))
}

/** 손목시계 모양 (기본 아이콘 세트에 없어서 직접 그림) */
@Composable
private fun WatchGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f)
        // 시곗줄 위·아래
        drawRoundRect(color, Offset(w * 0.34f, 0f), Size(w * 0.32f, h * 0.22f), CornerRadius(w * 0.05f))
        drawRoundRect(color, Offset(w * 0.34f, h * 0.78f), Size(w * 0.32f, h * 0.22f), CornerRadius(w * 0.05f))
        // 본체
        drawRoundRect(color, Offset(w * 0.22f, h * 0.2f), Size(w * 0.56f, h * 0.6f), CornerRadius(w * 0.16f), style = stroke)
    }
}

@Composable
private fun PillButton(text: String, filled: Boolean, onClick: () -> Unit, height: Int = 38) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .width(112.dp).height(height.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(Accent) else Modifier.border(BorderStroke(1.dp, Outline), shape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ---- Android Studio 미리보기: Split / Design 탭에서 4화면을 바로 볼 수 있다 ----

@Composable
private fun PreviewOf(screen: WatchScreen) =
    AionWatchApp(screen, bpm = 82, onStart = {}, onStop = {}, onReconnect = {}, onRetry = {})

@Preview(name = "1 대기", device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun PreviewIdle() = PreviewOf(WatchScreen.IDLE)

@Preview(name = "2 측정 중", device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun PreviewMeasuring() = PreviewOf(WatchScreen.MEASURING)

@Preview(name = "3 연결 끊김", device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun PreviewDisconnected() = PreviewOf(WatchScreen.DISCONNECTED)

@Preview(name = "4 착용 확인", device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun PreviewNotWorn() = PreviewOf(WatchScreen.NOT_WORN)
