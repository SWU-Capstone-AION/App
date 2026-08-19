package com.example.aion_app.monitor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BgTop = Color(0xFFF3F5FE)
private val BgBottom = Color(0xFFE7ECFB)
private val InkBlue = Color(0xFF3B4C82)
private val AccentBlue = Color(0xFF5E86E8)

private enum class ChildMode { CALM, PROMPT, BREATHING }

/**
 * 아동용 화면. 카메라/수치는 보이지 않고 차분한 구(orb)만 표시.
 * 상동행동 감지(alarm) 또는 "도움이 필요해요" → 진정 안내 팝업 → 호흡 가이드(4회) 흐름.
 */
@Composable
fun ChildScreen(
    alarm: Boolean,
    points: Int,
    onOpenTeacher: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(ChildMode.CALM) }

    // 상동행동 감지 시 자동으로 진정 팝업
    LaunchedEffect(alarm) {
        if (alarm && mode == ChildMode.CALM) mode = ChildMode.PROMPT
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (mode == ChildMode.BREATHING) {
            BreathingView(
                onDone = { mode = ChildMode.CALM },
                onHelp = onHelp,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CalmView(
                points = points,
                onOpenTeacher = onOpenTeacher,
                onHelp = { onHelp(); mode = ChildMode.PROMPT },
                modifier = Modifier.fillMaxSize(),
            )
            if (mode == ChildMode.PROMPT) {
                CalmPromptDialog(onOk = { mode = ChildMode.BREATHING })
            }
        }
    }
}

/** 평소 화면: 밝은 배경 + 광택 구 + "잘하고 있어요!" */
@Composable
private fun CalmView(
    points: Int,
    onOpenTeacher: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(
        initialValue = 0.96f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom))),
    ) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(points = points, dark = false, onOpenTeacher = onOpenTeacher)

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(228.dp)) {
                    Box(
                        modifier = Modifier.size(228.dp).background(
                            Brush.radialGradient(listOf(Color(0x556E93F0), Color(0x00FFFFFF))), CircleShape,
                        )
                    )
                    Box(
                        modifier = Modifier
                            .size(146.dp).scale(breathe).clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFFD6E2FF), Color(0xFF7C9BF2), Color(0xFF5075DC)),
                                    center = Offset(95f, 85f), radius = 340f,
                                )
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 26.dp, top = 22.dp).size(28.dp)
                                .clip(CircleShape).background(Color(0xB3FFFFFF))
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("잘하고 있어요!", color = InkBlue, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                Spacer(Modifier.height(22.dp))
                HelpButton(dark = false, onHelp = onHelp)
            }
        }
    }
}

/** 진정 안내 팝업 (감지/도움 시) */
@Composable
private fun CalmPromptDialog(onOk: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x22303A66)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFF4C9C0)),
                contentAlignment = Alignment.Center,
            ) { Text("❤️", fontSize = 30.sp) }
            Spacer(Modifier.height(20.dp))
            Text(
                "마음이 조금 두근두근해?\n선생님이랑 같이 숨을 한 번 크게 쉬어볼까?",
                color = Color(0xFF44506E), fontSize = 20.sp, textAlign = TextAlign.Center,
                lineHeight = 30.sp,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Spacer(Modifier.height(26.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth().background(AccentBlue)
                    .clickable(onClick = onOk).padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("좋아요", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
    }
}

/** 호흡 가이드: 진한 파랑 배경, 풍선(구)이 커졌다 작아지며 4회 호흡을 안내. */
@Composable
private fun BreathingView(onDone: () -> Unit, onHelp: () -> Unit, modifier: Modifier = Modifier) {
    val totalBreaths = 4
    var breath by remember { mutableIntStateOf(1) }
    var phase by remember { mutableStateOf("천천히 들이마셔요") }
    val scale = remember { Animatable(0.55f) }

    // 30초가 지나면 자동으로 원래(평소) 화면으로 복귀
    LaunchedEffect(Unit) {
        delay(30_000)
        onDone()
    }
    LaunchedEffect(Unit) {
        for (b in 1..totalBreaths) {
            breath = b
            phase = "천천히 들이마셔요"
            scale.animateTo(1f, tween(2900, easing = LinearEasing))
            phase = "잠깐 멈춰요"
            delay(1000)
            phase = "후- 내쉬어요"
            scale.animateTo(0.55f, tween(2900, easing = LinearEasing))
            delay(200)
        }
        onDone()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF52699F), Color(0xFF41598E)))),
    ) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(points = null, dark = true, onOpenTeacher = {})

            // 상단 안내
            Text(
                "풍선이 커질 때 숨을 들이마시고,\n작아질 때 천천히 내뱉어 보세요.",
                color = Color(0xFFD8E3F5), fontSize = 18.sp, textAlign = TextAlign.Center, lineHeight = 26.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            )

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 빛나는 호흡 풍선
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
                    Box(
                        modifier = Modifier.size(300.dp).scale(scale.value).background(
                            Brush.radialGradient(listOf(Color(0xCCFFFFFF), Color(0x00FFFFFF))), CircleShape,
                        )
                    )
                    Box(
                        modifier = Modifier
                            .size(150.dp).scale(scale.value).clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFBFD3FF), Color(0xFF9FBCFF)),
                                    center = Offset(70f, 60f), radius = 260f,
                                )
                            ),
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(phase, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 1..totalBreaths) {
                            Box(
                                modifier = Modifier.size(11.dp).clip(CircleShape)
                                    .background(if (i <= breath) Color.White else Color(0x59FFFFFF))
                            )
                        }
                    }
                    Text("$breath / ${totalBreaths}번째 호흡", color = Color(0xFFC7D5EE), fontSize = 16.sp)
                }
                Spacer(Modifier.height(28.dp))
                HelpButton(dark = true, onHelp = onHelp)
            }
        }
    }
}

/** 상단 바: AION 로고 + (포인트 배지) + 프로필(선생님 전환). */
@Composable
private fun TopBar(points: Int?, dark: Boolean, onOpenTeacher: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            "AION",
            style = TextStyle(
                brush = if (dark)
                    Brush.horizontalGradient(listOf(Color(0xFFBFD3FF), Color(0xFFFFE39A)))
                else
                    Brush.horizontalGradient(listOf(Color(0xFF4C7DF0), Color(0xFFF5B84A))),
                fontWeight = FontWeight.ExtraBold, fontSize = 30.sp,
            ),
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
        )
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (points != null) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🍬", fontSize = 18.sp)
                    Text("$points", color = InkBlue, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onOpenTeacher),
                contentAlignment = Alignment.Center,
            ) { Text("👤", fontSize = 26.sp) }
        }
    }
}

@Composable
private fun HelpButton(dark: Boolean, onHelp: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) Color(0x33FFFFFF) else Color(0xFFDDE6FB))
            .clickable(onClick = onHelp)
            .padding(horizontal = 40.dp, vertical = 16.dp),
    ) {
        Text(
            "✋  도움이 필요해요",
            color = if (dark) Color.White else InkBlue,
            fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
        )
    }
}
