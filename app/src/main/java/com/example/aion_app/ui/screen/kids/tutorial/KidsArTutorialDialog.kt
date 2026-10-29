package com.example.aion_app.ui.screen.kids.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.screen.kids.KidsCorner
import com.example.aion_app.ui.screen.kids.KidsDialogScrim
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.AionTextValue
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.GreyDark
import com.example.aion_app.ui.theme.Light
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.White

// ============================================================
// AR 게임 튜토리얼 (아동 홈)
// ============================================================
// 홈의 '놀이 방법' 버튼으로 연다. 다섯 장을 넘겨 보며
// AR 게임이 무엇인지, 왜 하는지, 어떻게 준비하는지를 알려 준다.
// 게임마다 자세한 방법은 게임 시작 전 안내(PoseGameHost)가 따로 보여 준다.
//
// 시안 프레임(930x582) 안에서 그린다. KidsDesignScale 의 overlay 로 넘길 것.
// 넘기기: [다음]/[이전] 버튼 또는 좌우로 밀기.
// 바깥을 눌러도 닫히지 않는다 (아이가 실수로 닫지 않도록). X 또는 마지막 장의 [알겠어요] 로 닫는다.

private class TutorialPage(
    val title: String,
    val body: String,
    val art: @Composable (Modifier) -> Unit,
)

private val TutorialPages = listOf(
    TutorialPage(
        title = "AR 게임이 뭐야?",
        body = "화면에 내 모습이 나와. 내 주변에 잡초랑 칠판이 생겨!\n진짜 손을 움직이면 화면 속 손도 같이 움직여.",
        art = { ArtWhatIsAr(it) },
    ),
    TutorialPage(
        title = "왜 하는 거야?",
        body = "몸을 쭉쭉 움직이면 마음이 편해지고 집중도 더 잘 돼.\n잠깐 쉬고 싶을 때 놀이처럼 해 봐!",
        art = { ArtWhyPlay(it) },
    ),
    TutorialPage(
        title = "이렇게 준비해",
        body = "몸이 안 보이면 화면에서 \"몸이 보이도록 앉아 줘\"라고 알려 줄 거야.",
        art = { ArtGetReady(it) },
    ),
    TutorialPage(
        title = "어떤 놀이가 있어?",
        body = "잡초를 꽉 잡고 쑥 뽑거나, 손바닥으로 칠판을 쓱쓱 지워.\n자세한 방법은 시작할 때 또 알려 줄게.",
        art = { ArtTwoGames(it) },
    ),
    TutorialPage(
        title = "다 하면?",
        body = "끝까지 하면 구슬 상자를 받아! 그만하고 싶으면 ✕ 버튼을 누르면 돼.\n천천히 해도 괜찮아.",
        art = { ArtReward(it) },
    ),
)

// 손가락을 이만큼(dp) 밀면 한 장 넘긴다
private const val SwipeThresholdDp = 60f

@Composable
fun BoxScope.KidsArTutorialDialog(onClose: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = TutorialPages.lastIndex

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
                .width(460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(White)
        ) {
            Box {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val forward = targetState > initialState
                        slideInHorizontally { if (forward) it else -it } togetherWith
                            slideOutHorizontally { if (forward) -it else it }
                    },
                    label = "tutorialPage",
                    modifier = Modifier.pointerInput(Unit) {
                        val threshold = SwipeThresholdDp.dp.toPx()
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onHorizontalDrag = { _, amount -> total += amount },
                            onDragEnd = {
                                if (total < -threshold) page = (page + 1).coerceAtMost(last)
                                if (total > threshold) page = (page - 1).coerceAtLeast(0)
                            }
                        )
                    }
                ) { index ->
                    val item = TutorialPages[index]
                    Column {
                        item.art(Modifier.fillMaxWidth().background(Light))
                        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp)) {
                            Text(
                                text = "${index + 1} / ${TutorialPages.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Normal
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AionTextDark
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = item.body,
                                fontSize = 14.sp,
                                lineHeight = 23.sp,
                                color = AionTextValue,
                                // 장마다 문구 길이가 달라도 카드 높이가 출렁이지 않게 세 줄 자리를 잡아 둔다
                                minLines = 3
                            )
                        }
                    }
                }

                // 닫기
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(White)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "닫기",
                        tint = AionTextDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 지금 몇 번째 장인지
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TutorialPages.indices.forEach { i ->
                        Box(
                            Modifier
                                .height(7.dp)
                                .width(if (i == page) 18.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (i == page) Normal else LightActive)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                if (page > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(KidsCorner))
                            .clickable { page-- }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("이전", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GreyDark)
                    }
                    Spacer(Modifier.width(6.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(KidsCorner))
                        .background(Normal)
                        .clickable { if (page < last) page++ else onClose() }
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (page < last) "다음" else "알겠어요",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "AR 게임 튜토리얼")
@Composable
private fun KidsArTutorialDialogPreview() {
    AionTheme {
        Box(Modifier.fillMaxSize().background(Light)) {
            KidsArTutorialDialog(onClose = {})
        }
    }
}
