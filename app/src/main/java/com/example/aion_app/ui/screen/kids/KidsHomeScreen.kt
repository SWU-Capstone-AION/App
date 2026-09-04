package com.example.aion_app.ui.screen.kids

import android.os.SystemClock
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
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.data.auth.TeacherInvite
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.DarkHover
import com.example.aion_app.ui.theme.GreyLightHover
import com.example.aion_app.ui.theme.Light
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.Red
import com.example.aion_app.ui.theme.White

// ============================================================
// 아동용 홈
// ============================================================
// 시안 3장(홈 / 진정 제안 팝업 / 호흡)이 사실상 한 화면의 3가지 상태다.
// 호흡은 30초 안에 끝나고 홈으로 돌아오는 흐름이라 라우트를 나누지 않고
// 내부 상태로 전환한다.
//
//   CALM           평소 화면
//   PROMPT_DETECTED 상동행동이 감지되어 뜬 팝업
//   PROMPT_HELP     아이가 "도움이 필요해요"를 눌러서 뜬 팝업
//   BREATHING       호흡 가이드 (KidsBreathingScreen.kt)
//
// 팝업을 둘로 나눈 이유
//   감지는 아이가 아무것도 안 했는데 화면이 먼저 말을 거는 상황이다.
//   자기가 흔들고 있다는 걸 모를 수 있어서 몸 상태를 단정하지 않고 권하기만 한다.
//   반대로 도움 요청은 아이가 스스로 누른 것이라, 그 행동을 먼저 받아준다.
private enum class KidsHomeMode { CALM, PROMPT_DETECTED, PROMPT_HELP, BREATHING }

// 팝업이 떠 있는 상태인지. (감지/도움 둘 다)
private val KidsHomeMode.isPrompt: Boolean
    get() = this == KidsHomeMode.PROMPT_DETECTED || this == KidsHomeMode.PROMPT_HELP

// ============================================================
// 진정 제안 팝업의 두 가지 표시 스타일
// ============================================================
// 아이가 팝업을 보는 순간 '내가 부른 것'인지 '화면이 먼저 말 건 것'인지
// 읽히도록 문구·아이콘·색을 함께 나눈다.
//
// ⚠ 대비를 세게 주지는 않는다. 진정시키려고 띄우는 화면이라
//   색이 튀면 그 자체가 자극이 된다. 아이콘 원과 버튼만 바꾸고
//   카드 배경(흰색)과 레이아웃은 둘 다 그대로 둔다.
private class KidsPromptStyle(
    val message: String,
    val icon: ImageVector,
    val iconBackground: Color,
    val iconTint: Color,
    val buttonColor: Color,
)

// 감지: 아이는 자기가 흔들고 있다는 걸 모를 수 있다.
// 상태를 단정하지 않고("두근두근해?" 같은 말) 같이 하자고만 권한다.
// 오탐이어도 어색하지 않아야 한다.
//
// 앱 기본 파란 계열로 두어 평소 화면과 이어지는 느낌을 준다.
private val KidsPromptDetectedStyle = KidsPromptStyle(
    message = "잠깐 쉬어갈까?\n나랑 같이 숨을 크게 쉬어보자.",
    icon = Icons.Filled.Favorite,
    iconBackground = LightActive,   // Blue 계열의 옅은 배경
    iconTint = Normal,
    buttonColor = Normal,
)

// 도움 요청: 아이가 버튼을 눌러 선생님을 부른 뒤다.
// 선생님이 오고 있다는 사실을 먼저 알려주고, 기다리는 동안 할 일을 준다.
//
// 감지와 구분되도록 빨간 계열로 둔다.
// 아이가 직접 부른 상황이라 평소 화면과 확실히 달라 보이는 편이 낫다.
//
// ⚠ 버튼은 Red 를 그대로 쓰지 않는다.
//   아이콘 원(42dp)과 달리 버튼은 카드 폭을 꽉 채우는 큰 면적이라
//   같은 색이라도 훨씬 세게 보인다. 진정시키려고 띄우는 화면에서
//   그 정도 채도는 오히려 자극이 된다.
//   Red 를 흰 카드 위에 약 78% 로 얹은 값으로 톤을 낮췄다.
//   더 옅게 하려면 KidsPromptHelpButton 값을 올리면 되는데,
//   흰 글씨 대비가 떨어지므로 프리뷰로 확인하면서 조정할 것.
private val KidsPromptHelpButton = Color(0xFFC97C6B)

private val KidsPromptHelpStyle = KidsPromptStyle(
    message = "선생님이 곧 오실 거야.\n그동안 같이 숨을 쉬어보자.",
    icon = Icons.Filled.WavingHand,
    iconBackground = Color(0xFFF7DAD5),   // Red 계열의 옅은 배경
    iconTint = Red,                        // 작은 면적이라 원색 그대로 써도 세지 않다
    buttonColor = KidsPromptHelpButton,
)

// 진정 팝업을 띄운 뒤 다음 팝업까지 최소 간격.
//
// StereotypyDetector 의 anyAlarm 은 흔들림이 이어지는 동안 계속 true 로 남는다.
// 쿨다운이 없으면 호흡을 마치고 홈으로 돌아오자마자 팝업이 다시 떠서
// 아이가 화면에 갇히게 된다.
//
// ⚠ 30초는 임시값이다. 실제 아동 반응을 보고 팀에서 정할 것.
//   (KidsBreathTimeoutMs 와 함께 조정 필요)
const val KidsPromptCooldownMs = 30_000L

// 구체 + 문구 묶음을 화면 정중앙보다 위로 올리는 양.
//
// 정중앙(Alignment.Center)에 두면 시안 프레임(930x582) 기준으로
//   구체+문구    143 ~ 439
//   미니게임 버튼 432 ~ 482
// 라서 문구가 버튼 위로 파고든다. 글로우는 requiredSize 라 레이아웃에 잡히지도 않고
// 그대로 버튼 위까지 번진다.
//
// 위로 50 올리면 문구 하단이 389 가 되어 버튼까지 43dp 여유가 생긴다.
private val KidsHomeOrbOffsetY = (-50).dp

@Composable
fun KidsHomeScreen(
    // ===== 상동행동 감지 =====
    // StereotypyDetectionHost 가 넘겨주는 StereotypyDetector.State.anyAlarm.
    // 기본값 false 라 프리뷰/에뮬레이터에서는 카메라 없이도 화면 확인이 된다.
    stereotypyDetected: Boolean = false,
    points: Int = 0,
    // 선생님이 보낸 학급 초대. null 이 아니면 팝업이 뜬다.
    invite: TeacherInvite? = null,
    isRespondingToInvite: Boolean = false,
    onInviteRespond: (accept: Boolean) -> Unit = {},
    onProfileClick: () -> Unit = {},
    // "도움이 필요해요"를 눌렀을 때 (교사에게 알림 전송 등)
    onHelpRequest: () -> Unit = {},
    // 호흡 4회를 끝까지 마쳤을 때 (포인트 지급 등)
    onBreathingComplete: () -> Unit = {},
    // 잡초 뽑기 미니게임으로 이동
    onWeedGameClick: () -> Unit = {},
    // 칠판 지우기 미니게임으로 이동
    onBoardGameClick: () -> Unit = {},
    // 상동행동 모니터링 화면으로 이동
    onMonitorClick: () -> Unit = {}
) {
    var mode by remember { mutableStateOf(KidsHomeMode.CALM) }

    // 마지막으로 팝업을 띄운 시각. 쿨다운 판단에만 쓴다.
    var lastPromptAt by remember { mutableLongStateOf(0L) }

    // 감지되면 자동으로 진정 제안 팝업. 이미 팝업/호흡 중이면 방해하지 않는다.
    //
    // mode 도 키에 넣어야 한다. 호흡을 마치고 CALM 으로 돌아왔을 때
    // 아직 흔들림이 이어지고 있으면(anyAlarm 계속 true) 다시 판단해야 하기 때문.
    // 대신 쿨다운으로 연속 재발동을 막는다.
    LaunchedEffect(stereotypyDetected, mode) {
        if (!stereotypyDetected || mode != KidsHomeMode.CALM) return@LaunchedEffect

        val now = SystemClock.elapsedRealtime()
        if (now - lastPromptAt < KidsPromptCooldownMs) return@LaunchedEffect

        lastPromptAt = now
        mode = KidsHomeMode.PROMPT_DETECTED
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

    KidsDesignScale(
        modifier = Modifier.background(Light),
        // 팝업은 overlay 로 넘긴다.
        // content 안에 두면 딤이 시안 프레임(930x582) 안까지만 깔려서
        // 기기 비율이 다를 때 화면 가장자리가 안 덮인다.
        overlay = {
            if (mode.isPrompt) {
                KidsCalmPromptDialog(
                    style = if (mode == KidsHomeMode.PROMPT_DETECTED) {
                        KidsPromptDetectedStyle
                    } else {
                        KidsPromptHelpStyle
                    },
                    onConfirm = { mode = KidsHomeMode.BREATHING }
                )
            }

            // 학급 초대는 진정 흐름을 방해하지 않도록 평소 화면일 때만 띄운다
            if (invite != null && mode == KidsHomeMode.CALM) {
                KidsTeacherInviteDialog(
                    invite = invite,
                    isResponding = isRespondingToInvite,
                    onRespond = onInviteRespond
                )
            }
        }
    ) {
        KidsHomeTopBar(
            points = points,
            onProfileClick = onProfileClick
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = KidsHomeOrbOffsetY),
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

            Text(
                text = "잘하고 있어요!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DarkHover
            )
        }

        // 미니게임 진입. 도움 버튼 바로 위에 둘을 나란히 놓는다.
        // (38dp + 버튼 높이 50dp + 간격 12dp = 100dp)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .width(KidsContentWidth),
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            KidsMinigameButton(
                text = "잡초 뽑기",
                onClick = onWeedGameClick,
                modifier = Modifier.weight(1f)
            )
            KidsMinigameButton(
                text = "칠판 지우기",
                onClick = onBoardGameClick,
                modifier = Modifier.weight(1f)
            )
        }

        // 모니터링 진입. 아이가 늘 쓰는 기능이 아니라서 로고 아래 작게 둔다.
        KidsMonitorButton(
            onClick = onMonitorClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = KidsScreenPadding, top = 64.dp)
        )

        KidsHelpButton(
            onClick = {
                onHelpRequest()
                mode = KidsHomeMode.PROMPT_HELP
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 38.dp)
        )

    }
}

// ============================================================
// 학급 초대 팝업
// ============================================================
// 선생님이 연결 요청을 보내면 아이가 수락/거절을 고른다.
// 수락해야 담당 교사로 확정되고 그때부터 알림이 전달된다.
@Composable
private fun BoxScope.KidsTeacherInviteDialog(
    invite: TeacherInvite,
    isResponding: Boolean,
    onRespond: (accept: Boolean) -> Unit
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
            Spacer(Modifier.height(28.dp))

            Text(
                text = "${invite.teacherName} 선생님이\n학급에 초대했어요",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AionTextDark,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(28.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                // 거절
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(KidsItemHeight)
                        .background(GreyLightHover)
                        .clickable(enabled = !isResponding) { onRespond(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "거절",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AionTextDark
                    )
                }

                // 수락
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(KidsItemHeight)
                        .background(Normal)
                        .clickable(enabled = !isResponding) { onRespond(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "수락",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }
        }
    }
}

// ============================================================
// 미니게임 진입 버튼
// ============================================================
// 높이는 도움 버튼과 같은 50, 폭은 둘이 나란히 서서 329를 채운다.
@Composable
private fun KidsMinigameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(KidsContentWidth)
            .height(KidsItemHeight)
            .clip(RoundedCornerShape(KidsCorner))
            .background(Normal)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = White
        )
    }
}

// ============================================================
// 모니터링 진입 버튼
// ============================================================
// 미니게임 버튼과 달리 아이가 직접 고르는 놀이가 아니라 관찰 모드로 들어가는 입구다.
// 그래서 하단 놀이 버튼들과 섞지 않고 위쪽에 작게 뒀다.
@Composable
private fun KidsMonitorButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(White)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "모니터링",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Normal
        )
    }
}

// ============================================================
// 진정 제안 팝업
// ============================================================
// 시안 기준 폭 329, '좋아요' 버튼은 카드 하단을 꽉 채운다.
// 아이가 실수로 닫아버리지 않도록 바깥 탭으로는 닫히지 않게 했다.
@Composable
private fun BoxScope.KidsCalmPromptDialog(
    // 감지로 떴는지 도움 요청으로 떴는지에 따라 문구·아이콘·색이 다르다.
    // (KidsPromptDetectedStyle / KidsPromptHelpStyle)
    style: KidsPromptStyle,
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

            // TODO: 아이콘 이미지 에셋으로 교체할지 디자인팀 확인 필요 (수정 요청 목록에 없었음)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(style.iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = style.message,
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
                    .background(style.buttonColor)
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

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "홈 (학급 초대)")
@Composable
private fun KidsHomeInvitePreview() {
    AionTheme {
        KidsHomeScreen(
            points = 20,
            invite = TeacherInvite(teacherUid = "uid", teacherName = "박서연")
        )
    }
}

@Preview(showBackground = true, device = "spec:width=1204dp,height=753dp,dpi=340", name = "홈 (실기기)")
@Composable
private fun KidsHomeScreenTabletPreview() {
    AionTheme { KidsHomeScreen(points = 20) }
}

// ============================================================
// 진정 제안 팝업 — 두 스타일 비교용
// ============================================================
// 홈 전체를 띄우는 위 프리뷰와 달리 팝업만 따로 본다.
// 두 개를 나란히 놓고 아이콘 대비 · 버튼 색 · 문구 줄바꿈을 비교할 때 쓴다.
//
// 확인 포인트
//   1. 감지: 파란 하트가 LightActive 배경 위에서 충분히 보이는가
//      (흐리면 iconTint 를 Normal → Dark 로 내린다)
//   2. 도움: 빨간 버튼이 진정 화면에서 과하지 않은가
//      (세면 KidsPromptHelpButton 값을 더 옅게)
//   3. 두 문구 모두 폭 329dp 안에서 두 줄로 떨어지는가

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "팝업 (감지)")
@Composable
private fun KidsCalmPromptDetectedPreview() {
    AionTheme {
        Box(Modifier.fillMaxSize().background(Light)) {
            KidsCalmPromptDialog(
                style = KidsPromptDetectedStyle,
                onConfirm = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "팝업 (도움 요청)")
@Composable
private fun KidsCalmPromptHelpPreview() {
    AionTheme {
        Box(Modifier.fillMaxSize().background(Light)) {
            KidsCalmPromptDialog(
                style = KidsPromptHelpStyle,
                onConfirm = {}
            )
        }
    }
}