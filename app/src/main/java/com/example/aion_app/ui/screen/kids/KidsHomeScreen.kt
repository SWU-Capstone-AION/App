package com.example.aion_app.ui.screen.kids

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.DarkHover
import com.example.aion_app.ui.theme.Light
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.White

// ============================================================
// 아동용 홈
// ============================================================
// 시안 3장(홈 / 진정 제안 팝업 / 호흡)이 사실상 한 화면의 3가지 상태다.
// 호흡은 30초 안에 끝나고 홈으로 돌아오는 흐름이라 라우트를 나누지 않고
// 내부 상태로 전환한다.
//
//   CALM      평소 화면
//   PROMPT    "마음이 조금 두근두근해?" 팝업
//   BREATHING 호흡 가이드 (KidsBreathingScreen.kt)
private enum class KidsHomeMode { CALM, PROMPT, BREATHING }

@Composable
fun KidsHomeScreen(
    // ===== 상동행동 감지 연결 지점 ★ =====
    // feature/stereotypy-monitor 의 StereotypyDetector.State.anyAlarm 을 그대로 넘기면 된다.
    // 지금은 호출부에서 가짜 값을 주고 있어 카메라 없이도 화면 확인이 가능하다.
    stereotypyDetected: Boolean = false,
    points: Int = 0,
    onProfileClick: () -> Unit = {},
    // "도움이 필요해요"를 눌렀을 때 (교사에게 알림 전송 등)
    onHelpRequest: () -> Unit = {},
    // 호흡 4회를 끝까지 마쳤을 때 (포인트 지급 등)
    onBreathingComplete: () -> Unit = {}
) {
    var mode by remember { mutableStateOf(KidsHomeMode.CALM) }

    // 감지되면 자동으로 진정 제안 팝업. 이미 팝업/호흡 중이면 방해하지 않는다.
    LaunchedEffect(stereotypyDetected) {
        if (stereotypyDetected && mode == KidsHomeMode.CALM) {
            mode = KidsHomeMode.PROMPT
        }
    }

    if (mode == KidsHomeMode.BREATHING) {
        KidsBreathingScreen(
            onProfileClick = onProfileClick,
            onHelpRequest = onHelpRequest,
            onFinish = { completed ->
                if (completed) onBreathingComplete()
                mode = KidsHomeMode.CALM
            }
        )
        return
    }

    KidsDesignScale(modifier = Modifier.background(Light)) {
        KidsHomeTopBar(
            points = points,
            onProfileClick = onProfileClick
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 아주 느린 숨쉬기 애니메이션 (평소에도 살아있는 느낌)
            val transition = rememberInfiniteTransition(label = "orb")
            val breathe by transition.animateFloat(
                initialValue = KidsOrbHomeScale - 0.03f,
                targetValue = KidsOrbHomeScale + 0.03f,
                animationSpec = infiniteRepeatable(
                    tween(3500, easing = LinearEasing),
                    RepeatMode.Reverse
                ),
                label = "breathe"
            )

            KidsOrb(scale = breathe)

            Spacer(Modifier.height(18.dp))

            // 8월 디자인 수정: AionTextDark → DarkHover (#3C598E)
            Text(
                text = "잘하고 있어요!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DarkHover
            )
        }

        KidsHelpButton(
            onClick = {
                onHelpRequest()
                mode = KidsHomeMode.PROMPT
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 38.dp)
        )

        if (mode == KidsHomeMode.PROMPT) {
            KidsCalmPromptDialog(
                onConfirm = { mode = KidsHomeMode.BREATHING }
            )
        }
    }
}

// ============================================================
// 진정 제안 팝업
// ============================================================
// 시안 기준 폭 329, '좋아요' 버튼은 카드 하단을 꽉 채운다.
// 아이가 실수로 닫아버리지 않도록 바깥 탭으로는 닫히지 않게 했다.
@Composable
private fun BoxScope.KidsCalmPromptDialog(
    onConfirm: () -> Unit
) {
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
                .clip(RoundedCornerShape(16.dp))
                .background(White),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // TODO: 하트 아이콘 에셋(heart_icon.png) 적용 여부 디자인팀 확인 후 교체
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF7DAD5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFC05C47),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "마음이 조금 두근두근해?\n선생님이랑 같이 숨을 한 번 크게 쉬어볼까?",
                fontSize = 14.sp,
                color = AionTextDark,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
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
                    text = "좋아요",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }
        }
    }
}

// ============================================================
// Preview
// ============================================================
@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "홈 (평소)")
@Composable
private fun KidsHomeScreenPreview() {
    AionTheme { KidsHomeScreen(points = 20) }
}

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "홈 (감지됨 → 팝업)")
@Composable
private fun KidsHomePromptPreview() {
    AionTheme { KidsHomeScreen(stereotypyDetected = true, points = 20) }
}

@Preview(showBackground = true, device = "spec:width=1204dp,height=753dp,dpi=340", name = "홈 (실기기)")
@Composable
private fun KidsHomeScreenTabletPreview() {
    AionTheme { KidsHomeScreen(points = 20) }
}