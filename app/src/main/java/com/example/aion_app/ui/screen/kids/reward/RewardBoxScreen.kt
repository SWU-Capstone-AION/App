package com.example.aion_app.ui.kids.reward

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aion_app.R

// 팀 Color.kt에 맞는 색이 있으면 아래 세 줄을 그걸로 바꿔주세요.
private val RewardTextColor = Color(0xFF3F3A33)
private val RewardButtonColor = Color(0xFF7B9FE0)
private val RewardDimColor = Color(0x99000000)

/**
 * 메인 홈에 얹어서 쓰는 보상 박스.
 *
 * 박스가 없으면(unopenedBoxes == 0) 아무것도 안 그린다.
 * 박스 탭 → 약 1.2초 흔들림 → 구슬 등장 화면으로 이어진다.
 *
 * 사용 예 (KidsHomeScreen 안에서):
 *   Box(Modifier.fillMaxSize()) {
 *       // ... 기존 홈 화면 내용 ...
 *       RewardBoxScreen(
 *           viewModel = rewardViewModel,
 *           modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp)
 *       )
 *   }
 */
@Composable
fun RewardBoxScreen(
    viewModel: RewardViewModel,
    modifier: Modifier = Modifier
) {
    val boxCount by viewModel.unopenedBoxes.collectAsStateWithLifecycle()
    val phase by viewModel.boxPhase.collectAsStateWithLifecycle()
    val marble by viewModel.openedMarble.collectAsStateWithLifecycle()

    // 열 박스가 없으면 화면에 아무것도 안 띄운다
    if (boxCount <= 0 && phase == BoxPhase.CLOSED) return

    Box(modifier = modifier) {
        // 1) 닫힌 박스 (흔들리는 단계까지)
        if (phase != BoxPhase.OPENED) {
            ClosedBox(
                phase = phase,
                boxCount = boxCount,
                onTap = { viewModel.onBoxTapped() },
                onShakeFinished = { viewModel.onShakeFinished() }
            )
        }
    }

    // 2) 박스가 열리면 화면 전체를 덮는 구슬 등장 화면
    AnimatedVisibility(
        visible = phase == BoxPhase.OPENED && marble != null,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(200))
    ) {
        marble?.let {
            MarbleRevealOverlay(
                marble = it,
                onConfirm = { viewModel.onRewardClosed() }
            )
        }
    }
}

/**
 * 닫힌 박스.
 * 평소엔 위아래로 살짝 둥실거리고, 탭하면 좌우로 흔들린다.
 */
@Composable
private fun ClosedBox(
    phase: BoxPhase,
    boxCount: Int,
    onTap: () -> Unit,
    onShakeFinished: () -> Unit
) {
    // 흔들림 각도
    val rotation = remember { Animatable(0f) }

    // 평소 둥실거리는 움직임 (흔드는 중에는 멈춘다)
    val floatTransition = rememberInfiniteTransition(label = "box_float")
    val floatOffset by floatTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "box_float_offset"
    )

    LaunchedEffect(phase) {
        if (phase == BoxPhase.SHAKING) {
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = repeatable(
                    iterations = 6,               // 6번 × 200ms = 약 1.2초
                    animation = keyframes {
                        durationMillis = 200
                        -6f at 50
                        6f at 150
                        0f at 200
                    }
                )
            )
            rotation.snapTo(0f)
            onShakeFinished()                     // 흔들림이 끝나면 ViewModel에 알림
        } else {
            rotation.snapTo(0f)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.box_close),
                contentDescription = "선물 상자",
                modifier = Modifier
                    .size(140.dp)
                    .offset(y = if (phase == BoxPhase.CLOSED) floatOffset.dp else 0.dp)
                    .graphicsLayer { rotationZ = rotation.value }
                    .clickable(
                        enabled = phase == BoxPhase.CLOSED,
                        // 아동용이라 누를 때 회색 물결 효과는 뺀다
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTap() }
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (phase == BoxPhase.SHAKING) {
                "열리는 중이에요!"
            } else if (boxCount > 1) {
                "상자 ${boxCount}개가 기다리고 있어요"
            } else {
                "상자를 눌러 보세요"
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = RewardTextColor
        )
    }
}

/**
 * 박스가 열리고 구슬이 나오는 화면.
 * 화면 전체를 어둡게 덮어서 구슬에만 집중되게 한다.
 */
@Composable
private fun MarbleRevealOverlay(
    marble: Marble,
    onConfirm: () -> Unit
) {
    // 구슬이 톡 튀어나오는 효과
    val scale = remember { Animatable(0.3f) }
    val lift = remember { Animatable(0f) }

    LaunchedEffect(marble) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    LaunchedEffect(marble) {
        lift.animateTo(
            targetValue = -40f,
            animationSpec = tween(500)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RewardDimColor)
            // 뒤쪽 화면이 눌리지 않도록 클릭을 여기서 막는다
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${marble.label}을 얻었어요!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(24.dp))

            Box(contentAlignment = Alignment.BottomCenter) {
                // 열린 박스
                Image(
                    painter = painterResource(R.drawable.box_open),
                    contentDescription = null,
                    modifier = Modifier.size(180.dp)
                )
                // 박스 위로 떠오르는 구슬
                Image(
                    painter = painterResource(marble.imageRes),
                    contentDescription = marble.label,
                    modifier = Modifier
                        .size(110.dp)
                        .offset(y = lift.value.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                        .alpha(scale.value.coerceIn(0f, 1f))
                )
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RewardButtonColor),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "주머니에 담기",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }
}