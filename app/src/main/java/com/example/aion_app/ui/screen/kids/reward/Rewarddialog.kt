package com.example.aion_app.ui.screen.kids.reward

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.screen.kids.KidsContentWidth
import com.example.aion_app.ui.screen.kids.KidsItemHeight
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.GreyNormalActive
import com.example.aion_app.ui.theme.Light
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.White
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

// ============================================================
// 보상 상자 팝업
// ============================================================
// 홈에 상시로 놓여 있는 물건이 아니다.
// 활동(호흡 가이드 · 잡초 뽑기 · 칠판 지우기)을 마치면 그때 잠깐 나타난다.
//
//   활동 완료 → 상자 팝업 등장 → 탭 → 흔들림(약 1.2초) → 열림 + 구슬 등장
//   → '주머니에 담기' → 팝업 사라짐 (받아둔 상자가 더 있으면 바로 다음 상자)
//
// 미니게임에서 받은 상자는 게임 화면에서 열지 않고 홈으로 돌아온 뒤에 뜬다.
// 게임 화면은 카메라를 쓰고 있어서 그 위에 팝업을 띄우면 아이가 헷갈린다.
//
// 진정 팝업(KidsCalmPromptDialog)과 같은 모양으로 맞췄다.
// 흰 카드 329 · 모서리 16 · 하단을 꽉 채우는 버튼 — 아이가 이미 익숙한 형태다.

// 흔들림 길이·세기. 디자인 요청은 "1~2초 간 짧게" 였다.
private const val ShakeDurationMs = 1200
private const val ShakeCycles = 6f      // 좌우로 6번 왕복
private const val ShakeAngle = 6f       // 최대 기울기(도). 아동용이라 작게 둔다

// 상자가 열린 뒤 구슬이 나오기까지의 타이밍
private const val OpenedHoldMs = 350L   // 열린 상자를 보여주는 시간
private const val BoxFadeMs = 250       // 상자가 사라지는 시간

private val BoxImageSize = 150.dp
private val MarbleSize = 88.dp

/**
 * 상자 → 구슬 팝업.
 *
 * 호출부(KidsHomeScreen)가 "받아둔 상자가 있을 때만" 이 함수를 부른다.
 *
 * @param phase   CLOSED(닫힘) → SHAKING(흔들림) → OPENED(구슬 등장)
 * @param marble  OPENED 일 때 보여줄 구슬. 그 전에는 null
 * @param onBoxTap        닫힌 상자를 탭했을 때 → 흔들림 시작
 * @param onShakeFinished 흔들림이 끝났을 때 → 여기서 구슬이 뽑힌다
 * @param onConfirm       '주머니에 담기'
 */
@Composable
fun BoxScope.KidsRewardDialog(
    phase: BoxPhase,
    marble: Marble?,
    onBoxTap: () -> Unit,
    onShakeFinished: () -> Unit,
    onConfirm: () -> Unit
) {
    // 카드가 톡 나타나는 효과. 갑자기 뜨는 것보다 시선이 따라가기 쉽다.
    val appear = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) {
        appear.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color(0x33303A66))
            // 뒤쪽 버튼이 눌리지 않도록 클릭을 흡수만 하고 아무것도 안 한다
            .clickable(enabled = true) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(KidsContentWidth)
                .graphicsLayer {
                    scaleX = appear.value
                    scaleY = appear.value
                }
                .clip(RoundedCornerShape(16.dp))
                .background(White),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            if (phase == BoxPhase.OPENED && marble != null) {
                OpenedBoxContent(marble = marble, onConfirm = onConfirm)
            } else {
                ClosedBoxContent(
                    isShaking = phase == BoxPhase.SHAKING,
                    onTap = onBoxTap,
                    onShakeFinished = onShakeFinished
                )
            }
        }
    }
}

// ------------------------------------------------------------
// 닫힌 상자 — 누르면 흔들린다
// ------------------------------------------------------------
@Composable
private fun ColumnScope.ClosedBoxContent(
    isShaking: Boolean,
    onTap: () -> Unit,
    onShakeFinished: () -> Unit
) {
    // 흔들림. 0 → 1 로 가는 동안 sin 으로 좌우 왕복시킨다.
    // 끝값이 정확히 0도로 떨어져서 멈출 때 기울어진 채 남지 않는다.
    val shake = remember { Animatable(0f) }

    // 평소 둥실거리는 움직임. "누를 수 있는 것" 이라는 신호다.
    val floatTransition = rememberInfiniteTransition(label = "box_float")
    val floatOffset by floatTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "box_float_offset"
    )

    LaunchedEffect(isShaking) {
        if (!isShaking) {
            shake.snapTo(0f)
            return@LaunchedEffect
        }
        shake.snapTo(0f)
        shake.animateTo(1f, tween(ShakeDurationMs, easing = LinearEasing))
        onShakeFinished()
    }

    Text(
        text = "선물이 도착했어요!",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = AionTextDark,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(12.dp))

    Image(
        painter = painterResource(R.drawable.box_close),
        contentDescription = "선물 상자",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(BoxImageSize)
            // 움직이는 값(shake, floatOffset)은 graphicsLayer 안에서 읽는다.
            // 밖에서 읽으면 매 프레임 화면 전체가 다시 계산된다.
            .graphicsLayer {
                if (isShaking) {
                    rotationZ = sin(shake.value * ShakeCycles * 2f * PI.toFloat()) * ShakeAngle
                } else {
                    translationY = floatOffset * density   // dp → px
                }
            }
            .clickable(
                enabled = !isShaking,
                // 아동용이라 안드로이드 기본 회색 물결 효과는 끈다
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() }
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = if (isShaking) "열리는 중이에요!" else "상자를 눌러 보세요",
        fontSize = 14.sp,
        color = GreyNormalActive,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(28.dp))
}

// ------------------------------------------------------------
// 열린 상자 + 구슬
// ------------------------------------------------------------
@Composable
private fun ColumnScope.OpenedBoxContent(
    marble: Marble,
    onConfirm: () -> Unit
) {
    // 순서: 열린 상자를 잠깐 보여준다 → 상자가 사라진다 → 그 자리에 구슬이 톡 나온다.
    // 둘을 겹쳐 놓고 상자는 흐려지고 구슬은 커지게 한다.
    val boxAlpha = remember { Animatable(1f) }
    val marbleScale = remember { Animatable(0f) }

    LaunchedEffect(marble) {
        delay(OpenedHoldMs)                          // 열린 상자를 알아볼 시간
        boxAlpha.animateTo(0f, tween(BoxFadeMs))     // 상자가 사라지고
        marbleScale.animateTo(                       // 구슬이 나온다
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // 높이를 상자 크기로 고정한다.
    // 안 그러면 상자가 사라질 때 카드 높이가 줄었다가 다시 늘어나면서 화면이 튄다.
    Box(
        modifier = Modifier.height(BoxImageSize),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.box_open),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(BoxImageSize)
                .graphicsLayer { alpha = boxAlpha.value }
        )
        Image(
            painter = painterResource(marble.imageRes),
            contentDescription = marble.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(MarbleSize)
                .graphicsLayer {
                    scaleX = marbleScale.value
                    scaleY = marbleScale.value
                    alpha = marbleScale.value.coerceIn(0f, 1f)
                }
        )
    }

    Spacer(Modifier.height(10.dp))

    Text(
        text = "${marble.label}을 얻었어요!",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = AionTextDark,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(24.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(KidsItemHeight)
            .background(Normal)
            .clickable { onConfirm() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "주머니에 담기",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = White
        )
    }
}

// ============================================================
// Preview
// ============================================================
@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "상자 (닫힘)")
@Composable
private fun KidsRewardClosedPreview() {
    AionTheme {
        Box(Modifier.fillMaxSize().background(Light)) {
            KidsRewardDialog(
                phase = BoxPhase.CLOSED,
                marble = null,
                onBoxTap = {},
                onShakeFinished = {},
                onConfirm = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "상자 (구슬 등장)")
@Composable
private fun KidsRewardOpenedPreview() {
    AionTheme {
        Box(Modifier.fillMaxSize().background(Light)) {
            KidsRewardDialog(
                phase = BoxPhase.OPENED,
                marble = Marble.PINK,
                onBoxTap = {},
                onShakeFinished = {},
                onConfirm = {}
            )
        }
    }
}