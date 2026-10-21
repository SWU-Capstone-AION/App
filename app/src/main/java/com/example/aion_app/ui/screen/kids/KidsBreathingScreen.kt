package com.example.aion_app.ui.screen.kids

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
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
import com.example.aion_app.watch.BreathingCalmTracker
import com.example.aion_app.watch.HrState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "AionHr"

// ============================================================
// 호흡 타이밍 (미확정 — 팀/디자이너 확정되면 이 값만 바꾸면 됨)
// ============================================================
// 기존 monitor/ui/ChildScreen.kt 값을 그대로 옮겨왔다.
// 참고: '진정' 목적의 호흡은 날숨이 들숨보다 길 때 효과가 크다고 알려져 있어,
//       InhaleMs < ExhaleMs 로 바꾸는 것도 검토해볼 만하다.
const val KidsBreathCount = 4          // 총 호흡 횟수 (심박으로 끝내지 않을 때)
const val KidsInhaleMs = 2900          // 들이마시기
const val KidsHoldMs = 1000L           // 멈춤
const val KidsExhaleMs = 2900          // 내쉬기
const val KidsBreathGapMs = 200L       // 사이클 사이 간격
const val KidsBreathTimeoutMs = 30_000L // 이 시간이 지나면 무조건 홈으로 복귀

// 심박으로 끝낼 때(시작할 때 심박이 올라가 있었을 때) 최대로 기다리는 시간.
// 심박이 평소로 돌아오는 데 1~2분이 걸려서 30초로는 부족하다.
const val KidsBreathHeartRateMaxMs = 120_000L

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
//
// 끝내는 조건
//   - 시작할 때 워치 심박이 올라가 있었으면(위험도 0.3 이상) 심박이 평소 범위로 내려와
//     10초 이어진 뒤, 하던 호흡이 끝나는 시점에 홈으로 돌아간다. 최대 2분.
//   - 심박이 내려가는 것을 확인할 수 없으면(워치 끊김, 기준선 준비 전, 처음부터 평소 범위)
//     기존처럼 4회 뒤에 끝낸다. 호흡 중에 워치가 끊겨도 4회를 채웠으면 끝낸다.
//   - 아동 화면에는 심박 숫자·색을 보여주지 않는다.
@Composable
fun KidsBreathingScreen(
    onProfileClick: () -> Unit = {},
    onHelpRequest: () -> Unit = {},
    // 워치 심박 (WatchHeartRate.hrState). null 이면 심박 없이 4회만 한다 (프리뷰 등)
    heartRate: StateFlow<HrState>? = null,
    // completed = true 면 진정(심박이 내려감 또는 4회 완료), false 면 시간 초과로 빠져나온 것
    onFinish: (completed: Boolean) -> Unit = {}
) {
    var breath by remember { mutableIntStateOf(1) }
    var phaseText by remember { mutableStateOf("천천히 들이마셔요") }
    val scale = remember { Animatable(OrbMinScale) }
    val finish by rememberUpdatedState(onFinish)

    // 화면에 들어온 순간의 심박으로 "심박으로 끝낼지"를 정한다
    val tracker = remember {
        BreathingCalmTracker(heartRate?.value, System.currentTimeMillis())
    }
    val useHeartRate = tracker.usesHeartRate
    var calm by remember { mutableStateOf(false) }
    var heartRateAvailable by remember { mutableStateOf(useHeartRate) }
    var finished by remember { mutableStateOf(false) }

    fun end(completed: Boolean, reason: String) {
        if (finished) return
        finished = true
        Log.d(TAG, "호흡 종료: $reason (호흡 ${breath}회, 완료=$completed)")
        finish(completed)
    }

    LaunchedEffect(Unit) {
        Log.d(TAG, "호흡 시작: 시작 위험도=${tracker.startRisk}, 심박으로 끝내기=$useHeartRate")
    }

    // 심박은 1초마다 들어오므로 1초마다 진정 여부를 갱신한다
    if (heartRate != null && useHeartRate) {
        LaunchedEffect(Unit) {
            while (true) {
                val now = System.currentTimeMillis()
                val state = heartRate.value
                calm = tracker.update(state, now)
                heartRateAvailable = tracker.heartRateAvailable(state, now)
                delay(1_000)
            }
        }
    }

    // 안전장치: 어떤 이유로든 오래 머무르지 않도록 자동 복귀 (심박 모드 2분, 그 외 30초)
    LaunchedEffect(Unit) {
        delay(if (useHeartRate) KidsBreathHeartRateMaxMs else KidsBreathTimeoutMs)
        end(false, "시간 초과")
    }

    LaunchedEffect(Unit) {
        var n = 0
        while (!finished) {
            n += 1
            breath = n

            phaseText = "천천히 들이마셔요"
            scale.animateTo(OrbMaxScale, tween(KidsInhaleMs, easing = FastOutSlowInEasing))

            phaseText = "잠깐 멈춰요"
            delay(KidsHoldMs)

            phaseText = "후- 내쉬어요"
            scale.animateTo(OrbMinScale, tween(KidsExhaleMs, easing = FastOutSlowInEasing))

            delay(KidsBreathGapMs)

            // 호흡 한 번이 끝날 때만 판단한다 (숨을 내쉬는 도중에 화면이 바뀌지 않게)
            when {
                !useHeartRate && n >= KidsBreathCount -> end(true, "${KidsBreathCount}회 완료")
                useHeartRate && calm -> end(true, "심박이 평소 범위로 내려감")
                useHeartRate && !heartRateAvailable && n >= KidsBreathCount ->
                    end(true, "워치 심박 끊김 → ${KidsBreathCount}회 완료")
            }
        }
    }

    // 배경은 시안 프레임(930x582)이 아니라 '화면 전체'를 덮는다.
    //
    // KidsDesignScale 은 프레임을 화면 안에 다 넣는 contain 방식이라,
    // 기기 비율이 프레임 비율(약 1.6)과 다르면 남는 쪽에 여백이 생긴다.
    // (시스템바 여백이 높이를 깎으면 화면이 더 가로로 길어져 좌우에 여백이 남는다)
    // 배경을 프레임 안에 넣으면 그 여백이 그대로 보이므로 프레임 밖에 그린다.
    //
    // Modifier.paint 는 KidsDesignScale 의 바깥 Box(=화면 전체)에 그림을 그린다.
    // Crop 이라 여백 없이 꽉 차고, 대신 이미지 가장자리가 조금 잘린다.
    // background(Dark) 는 이미지가 로드되기 전 한 프레임을 위한 바탕색.
    KidsDesignScale(
        modifier = Modifier
            .background(Dark)
            .paint(
                painter = painterResource(R.drawable.kids_background),
                contentScale = ContentScale.Crop
            )
    ) {
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
                // 심박으로 끝낼 때는 횟수가 정해져 있지 않아 점(진행 표시)을 숨긴다
                if (!useHeartRate) {
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
                }
                Text(
                    text = if (useHeartRate) "${breath}번째 호흡" else "$breath / ${KidsBreathCount}번째 호흡",
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