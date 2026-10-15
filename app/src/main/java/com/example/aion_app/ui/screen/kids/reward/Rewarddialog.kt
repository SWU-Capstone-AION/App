package com.example.aion_app.ui.screen.kids.reward

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.screen.kids.KidsContentWidth
import com.example.aion_app.ui.screen.kids.KidsDialogScrim
import com.example.aion_app.ui.screen.kids.KidsItemHeight
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.AionTheme
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
// 시안의 네 장면을 그대로 잇는다.
//   1. "우와, 끝까지 해냈어! / 보물상자를 톡 눌러볼까?"  닫힌 상자
//   2. "두근두근..."                                    흔들림 (약 1.2초)
//   3. "짜잔! / 귀여운 구슬 친구가 태어났어!"            상자가 사라지고 구슬 등장
//   4. "구슬 주머니에 쏙 넣었어!"                        구슬은 그대로, 문구만 바뀌고 확인 버튼
//
// 1→2 만 아이가 누르고, 2→3→4 는 화면이 알아서 넘어간다.
// 아이가 눌러야 넘어가는 지점을 하나로 줄여서 헤매지 않게 했다.
//
// 미니게임에서 받은 상자는 게임 화면에서 열지 않고 홈으로 돌아온 뒤에 뜬다.
// 게임 화면은 카메라를 쓰고 있어서 그 위에 팝업을 띄우면 아이가 헷갈린다.

// 흔들림 길이·세기. 디자인 요청은 "1~2초 간 짧게" 였다.
private const val ShakeDurationMs = 1200
private const val ShakeCycles = 6f      // 좌우로 6번 왕복
private const val ShakeAngle = 6f       // 최대 기울기(도). 아동용이라 작게 둔다

// 상자가 열린 뒤 구슬이 나오기까지의 타이밍
private const val OpenedHoldMs = 350L   // 열린 상자를 보여주는 시간
private const val BoxFadeMs = 250       // 상자가 사라지는 시간
private const val MarbleHoldMs = 1400L  // 구슬을 보여준 뒤 마지막 장면으로

// 카드 폭. 시안 기준은 620 이었는데 실기기에서 커 보여서 줄였다.
// 진정 팝업·학급 초대와 같은 폭(KidsContentWidth)을 쓴다 —
// 아동용 팝업이 전부 같은 크기라 아이가 볼 때 일관된다.
//
// ⚠ 이보다 좁히지 말 것. 18sp 두 줄 문구가 한 줄에 약 270dp 를 쓰기 때문에
//   더 줄이면 "보물상자를 톡 / 눌러볼까?" 처럼 원하지 않는 자리에서 줄이 바뀐다.
private val RewardCardWidth = KidsContentWidth
private val BoxImageSize = 150.dp
private val MarbleSize = 110.dp
private val MarbleBottomGap = 20.dp     // 구슬을 바닥에서 얼마나 띄울지

// 제목 두 줄 + 그림이 들어가는 영역. 장면이 바뀌어도 이 높이는 고정이다.
private val TitleTopGap = 24.dp
private val TitleAreaHeight = 62.dp
private val TitleImageGap = 14.dp
private val ContentAreaHeight = TitleTopGap + TitleAreaHeight + TitleImageGap + BoxImageSize

// ------------------------------------------------------------
// 4번 장면 배치
// ------------------------------------------------------------
// 문구가 한 줄로 줄어들면 문구와 구슬 사이가 휑하고, 구슬 아래는 확인 버튼에 붙어
// 한쪽으로 쏠려 보였다 (디자인 피드백 10/15).
// 그래서 4번 장면에서는 "문구 한 줄 + 간격 + 구슬" 을 한 묶음으로 보고
// 영역 위아래 가운데에 놓는다. 위아래 여백이 같아진다.
//
// 아래 값들은 기존 배치에서 얼마나 옮길지를 계산으로 구한다.
// 크기 값을 바꿔도 자동으로 다시 가운데가 맞는다.
private val StoredTextHeight = 26.dp        // 18sp 한 줄 (lineHeight 26sp)
private val StoredTextMarbleGap = 20.dp     // 문구와 구슬 사이 — 간격 조절은 이 값만
private const val StoredMoveMs = 400        // 구슬이 제자리로 올라가는 시간

// 묶음이 가운데 올 때의 맨 윗자리
private val StoredGroupTop =
    (ContentAreaHeight - (StoredTextHeight + StoredTextMarbleGap + MarbleSize)) / 2

// 3번 장면에서 문구·구슬이 원래 있던 윗자리
private val TitleRestTop = TitleTopGap + (TitleAreaHeight - StoredTextHeight) / 2
private val MarbleRestTop =
    TitleTopGap + TitleAreaHeight + TitleImageGap + (BoxImageSize - MarbleBottomGap - MarbleSize)

// 원래 자리 → 가운데 자리까지 옮길 거리 (양수면 아래로, 음수면 위로)
private val StoredTitleShift: Dp = StoredGroupTop - TitleRestTop
private val StoredMarbleShift: Dp =
    StoredGroupTop + StoredTextHeight + StoredTextMarbleGap - MarbleRestTop

/**
 * 상자 → 구슬 팝업.
 *
 * 호출부(KidsHomeScreen)가 "받아둔 상자가 있을 때만" 이 함수를 부른다.
 *
 * @param phase   CLOSED → SHAKING → OPENED → STORED
 * @param marble  OPENED 부터 보여줄 구슬. 그 전에는 null
 * @param onBoxTap        닫힌 상자를 탭했을 때 → 흔들림 시작
 * @param onShakeFinished 흔들림이 끝났을 때 → 여기서 구슬이 뽑힌다
 * @param onMarbleShown   구슬을 충분히 보여줬을 때 → 마지막 장면으로
 * @param onConfirm       '확인'
 */
@Composable
fun BoxScope.KidsRewardDialog(
    phase: BoxPhase,
    marble: Marble?,
    onBoxTap: () -> Unit,
    onShakeFinished: () -> Unit,
    onMarbleShown: () -> Unit,
    onConfirm: () -> Unit,
    cardWidth: Dp = RewardCardWidth
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
            .background(KidsDialogScrim)
            // 뒤쪽 버튼이 눌리지 않도록 클릭을 흡수만 하고 아무것도 안 한다
            .clickable(enabled = true) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(cardWidth)
                .graphicsLayer {
                    scaleX = appear.value
                    scaleY = appear.value
                }
                .clip(RoundedCornerShape(16.dp))
                .background(White),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ⚠ 네 장면의 세로 길이를 똑같이 맞춰 둔다.
            //   카드 높이가 달라지면 팝업이 화면 가운데 정렬이라
            //   장면이 넘어갈 때마다 카드가 위아래로 움찔한다.
            //   4번 장면도 카드 높이는 그대로 두고 안쪽 배치만 바꾼다.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ContentAreaHeight),
                contentAlignment = Alignment.Center
            ) {
                SceneWithImage(
                    phase = phase,
                    marble = marble,
                    onBoxTap = onBoxTap,
                    onShakeFinished = onShakeFinished,
                    onMarbleShown = onMarbleShown
                )
            }

            // 하단 영역. 마지막 장면에서만 버튼이 보이고, 그 전에는 빈 자리다.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KidsItemHeight)
                    .then(
                        if (phase == BoxPhase.STORED) {
                            Modifier
                                .background(Normal)
                                .clickable { onConfirm() }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (phase == BoxPhase.STORED) {
                    Text(
                        text = "확인",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------
// 제목 + 그림 (네 장면 모두)
// ------------------------------------------------------------
@Composable
private fun SceneWithImage(
    phase: BoxPhase,
    marble: Marble?,
    onBoxTap: () -> Unit,
    onShakeFinished: () -> Unit,
    onMarbleShown: () -> Unit
) {
    val opened = phase == BoxPhase.OPENED && marble != null
    val stored = phase == BoxPhase.STORED && marble != null

    // 4번 장면으로 옮겨 가는 정도. 0 = 원래 자리, 1 = 가운데 자리.
    // 4번이 될 때만 천천히 움직이고, 다음 상자로 넘어갈 때는 바로 원래 자리로 돌아온다.
    // (돌아올 때까지 천천히 하면 다음 상자의 제목이 잠깐 어긋나 보인다)
    val storedProgress by animateFloatAsState(
        targetValue = if (stored) 1f else 0f,
        animationSpec = if (stored) tween(StoredMoveMs) else snap(),
        label = "stored_move"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(TitleTopGap))

        // 제목 자리도 높이를 고정한다. 한 줄짜리("두근두근...")로 바뀔 때
        // 아래 그림이 위아래로 흔들리지 않게.
        Box(
            modifier = Modifier.height(TitleAreaHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    stored -> "구슬 주머니에 쏙 넣었어!"
                    opened -> "짜잔!\n귀여운 구슬 친구가 태어났어!"
                    phase == BoxPhase.SHAKING -> "두근두근..."
                    else -> "우와, 끝까지 해냈어!\n보물상자를 톡 눌러볼까?"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AionTextDark,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
                // 4번 장면에서만 가운데 자리로 옮긴다.
                // 자리를 옮기는 값은 graphicsLayer 안에서 읽어야 매 프레임 전체를 다시 계산하지 않는다.
                modifier = Modifier.graphicsLayer {
                    translationY = StoredTitleShift.toPx() * storedProgress
                }
            )
        }

        Spacer(Modifier.height(TitleImageGap))

        // 그림 영역. 상자는 바닥 기준으로 놓는다.
        // 닫힌 상자(150x115)와 열린 상자(150x141)는 이미지 비율이 달라서
        // 가운데 정렬하면 몸통 위치가 어긋난다. 바닥을 맞추면 뚜껑만 열리는 것처럼 보인다.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BoxImageSize),
            contentAlignment = Alignment.BottomCenter
        ) {
            when {
                stored && marble != null -> StoredMarble(
                    marble = marble,
                    progress = { storedProgress }
                )
                opened && marble != null -> OpenedBoxContent(
                    marble = marble,
                    onMarbleShown = onMarbleShown
                )
                else -> ClosedBoxContent(
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
private fun ClosedBoxContent(
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

    Image(
        painter = painterResource(R.drawable.box_close),
        contentDescription = "보물상자",
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
}

// ------------------------------------------------------------
// 열린 상자 → 구슬
// ------------------------------------------------------------
@Composable
private fun OpenedBoxContent(
    marble: Marble,
    onMarbleShown: () -> Unit
) {
    // 순서: 열린 상자를 잠깐 보여준다 → 상자가 사라진다 → 그 자리에 구슬이 톡 나온다
    //       → 잠시 보여준 뒤 마지막 장면으로 넘어간다.
    val boxAlpha = remember { Animatable(1f) }
    val marbleScale = remember { Animatable(0f) }

    LaunchedEffect(marble) {
        delay(OpenedHoldMs)
        boxAlpha.animateTo(0f, tween(BoxFadeMs))
        marbleScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        delay(MarbleHoldMs)
        onMarbleShown()
    }

    Image(
        painter = painterResource(R.drawable.box_open),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(BoxImageSize)
            .graphicsLayer { alpha = boxAlpha.value }
    )

    // 구슬은 상자 바닥보다 살짝 위에 뜨게 한다.
    // padding 을 size 보다 먼저 걸어야 그림이 눌리지 않고 위로 올라간다.
    Image(
        painter = painterResource(marble.imageRes),
        contentDescription = marble.label,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .padding(bottom = MarbleBottomGap)
            .size(MarbleSize)
            .graphicsLayer {
                scaleX = marbleScale.value
                scaleY = marbleScale.value
                alpha = marbleScale.value.coerceIn(0f, 1f)
            }
    )
}

// ------------------------------------------------------------
// 주머니에 넣은 뒤의 구슬 (4번 장면)
// ------------------------------------------------------------
// 3번 장면 마지막 모습(상자 없음, 같은 크기·같은 위치)에서 시작해
// 가운데 자리까지 부드럽게 올라간다.
// 다시 튀어나오는 효과는 넣지 않는다 — 같은 구슬이 그대로 있는 것처럼 보여야 한다.
@Composable
private fun StoredMarble(
    marble: Marble,
    progress: () -> Float
) {
    Image(
        painter = painterResource(marble.imageRes),
        contentDescription = marble.label,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .padding(bottom = MarbleBottomGap)
            .size(MarbleSize)
            .graphicsLayer {
                translationY = StoredMarbleShift.toPx() * progress()
            }
    )
}

// ============================================================
// Preview
// ============================================================
@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "보상 1 (닫힌 상자)")
@Composable
private fun KidsRewardClosedPreview() {
    AionTheme {
        Box(Modifier.fillMaxSize().background(Light)) {
            KidsRewardDialog(
                phase = BoxPhase.CLOSED,
                marble = null,
                onBoxTap = {},
                onShakeFinished = {},
                onMarbleShown = {},
                onConfirm = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "보상 3 (구슬 등장)")
@Composable
private fun KidsRewardOpenedPreview() {
    AionTheme {
        Box(Modifier.fillMaxSize().background(Light)) {
            KidsRewardDialog(
                phase = BoxPhase.OPENED,
                marble = Marble.PURPLE,
                onBoxTap = {},
                onShakeFinished = {},
                onMarbleShown = {},
                onConfirm = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "보상 4 (주머니에 쏙)")
@Composable
private fun KidsRewardStoredPreview() {
    AionTheme {
        Box(Modifier.fillMaxSize().background(Light)) {
            KidsRewardDialog(
                phase = BoxPhase.STORED,
                marble = Marble.PURPLE,
                onBoxTap = {},
                onShakeFinished = {},
                onMarbleShown = {},
                onConfirm = {}
            )
        }
    }
}