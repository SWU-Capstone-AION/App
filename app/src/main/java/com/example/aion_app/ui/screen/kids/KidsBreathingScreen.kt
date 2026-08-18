package com.example.aion_app.ui.screen.kids

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.Dark
import com.example.aion_app.ui.theme.White
import kotlinx.coroutines.delay

// ============================================================
// 호흡 타이밍 (미확정 — 팀/디자이너 확정되면 이 값만 바꾸면 됨)
// ============================================================
// 기존 monitor/ui/ChildScreen.kt 값을 그대로 옮겨왔다.
// 참고: '진정' 목적의 호흡은 날숨이 들숨보다 길 때 효과가 크다고 알려져 있어,
//       InhaleMs < ExhaleMs 로 바꾸는 것도 검토해볼 만하다.
const val KidsBreathCount = 4          // 총 호흡 횟수
const val KidsInhaleMs = 2900          // 들이마시기
const val KidsHoldMs = 1000L           // 멈춤
const val KidsExhaleMs = 2900          // 내쉬기
const val KidsBreathGapMs = 200L       // 사이클 사이 간격
const val KidsBreathTimeoutMs = 30_000L // 이 시간이 지나면 무조건 홈으로 복귀

// 구체 크기 범위 (시안 실측: 들이마시기 시작 107dp → 내쉬기 시작 244dp)
private const val OrbMinScale = 0.44f
private const val OrbMaxScale = 1.0f

// ============================================================
// 호흡 가이드
// ============================================================
// 안내문("풍선이 커질 때 숨을 들이마시고, 작아질 때 천천히 내뱉어 보세요") 기준으로
//   들이마시기 → 구체 확대
//   멈춤       → 최대 크기 유지
//   내쉬기     → 구체 축소
// 시안 이미지의 구체 크기는 각 단계의 스냅샷 시점이 제각각이라 안내문을 기준으로 삼았다.
@Composable
fun KidsBreathingScreen(
    onProfileClick: () -> Unit = {},
    onHelpRequest: () -> Unit = {},
    // completed = true 면 4회를 끝까지 마친 것, false 면 시간 초과로 빠져나온 것
    onFinish: (completed: Boolean) -> Unit = {}
) {
    var breath by remember { mutableIntStateOf(1) }
    var phaseText by remember { mutableStateOf("천천히 들이마셔요") }
    val scale = remember { Animatable(OrbMinScale) }

    // 안전장치: 어떤 이유로든 오래 머무르지 않도록 30초 후 자동 복귀
    LaunchedEffect(Unit) {
        delay(KidsBreathTimeoutMs)
        onFinish(false)
    }

    LaunchedEffect(Unit) {
        for (n in 1..KidsBreathCount) {
            breath = n

            phaseText = "천천히 들이마셔요"
            scale.animateTo(OrbMaxScale, tween(KidsInhaleMs, easing = FastOutSlowInEasing))

            phaseText = "잠깐 멈춰요"
            delay(KidsHoldMs)

            phaseText = "후- 내쉬어요"
            scale.animateTo(OrbMinScale, tween(KidsExhaleMs, easing = FastOutSlowInEasing))

            delay(KidsBreathGapMs)
        }
        onFinish(true)
    }

    // 배경 이미지는 프레임(930x582) 안쪽만 덮는다.
    // 회전 시 생기는 바깥 여백은 Dark 로 채워 이미지 가장자리와 자연스럽게 이어지게 했다.
    KidsDesignScale(modifier = Modifier.background(Dark)) {
        // 가장 먼저 그려야 나머지 요소가 그 위에 온다
        Image(
            painter = painterResource(R.drawable.kids_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        KidsHomeTopBar(
            points = null,          // 호흡 중에는 구슬주머니를 숨긴다 (시안 기준)
            onProfileClick = onProfileClick,
            onDark = true
        )

        Text(
            text = "풍선이 커질 때 숨을 들이마시고,\n작아질 때 천천히 내뱉어 보세요.",
            fontSize = 14.sp,
            color = Color(0xFFD8E3F5),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KidsOrb(scale = scale.value, onDark = true)

            Spacer(Modifier.height(18.dp))

            Text(
                text = phaseText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = White
            )

            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..KidsBreathCount) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (i <= breath) White else Color(0x59FFFFFF))
                        )
                    }
                }
                Text(
                    text = "$breath / ${KidsBreathCount}번째 호흡",
                    fontSize = 13.sp,
                    color = Color(0xFFC7D5EE)
                )
            }
        }

        KidsHelpButton(
            onClick = onHelpRequest,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 38.dp)
        )
    }
}

// ============================================================
// Preview
// ============================================================
// 애니메이션이 도는 화면이라 정지 프리뷰는 첫 프레임(들이마시기 시작)만 보인다.
// 단계별 모습은 Interactive Mode 로 확인.
@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "호흡")
@Composable
private fun KidsBreathingScreenPreview() {
    AionTheme { KidsBreathingScreen() }
}

@Preview(showBackground = true, device = "spec:width=1204dp,height=753dp,dpi=340", name = "호흡 (실기기)")
@Composable
private fun KidsBreathingScreenTabletPreview() {
    AionTheme { KidsBreathingScreen() }
}