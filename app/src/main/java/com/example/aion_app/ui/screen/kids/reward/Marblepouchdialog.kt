package com.example.aion_app.ui.screen.kids.reward

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.aion_app.ui.screen.kids.KidsItemHeight
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.GreyNormalActive
import com.example.aion_app.ui.theme.Light
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.White

// ============================================================
// 구슬 주머니 팝업
// ============================================================
// 홈 오른쪽 위 사탕 배지를 누르면 열린다. 별도 화면이 아니라 팝업이다.
//
// ⚠ 초기 기획안이다. 디자인 확정 전이라 값들을 한곳에 모아 뒀다.
//   모은 구슬을 진열장 선반에 올려둔 것처럼 쭉 늘어놓는 형태.
//   색깔별로 묶지 않고 얻은 개수만큼 하나씩 놓는다 — 많이 모을수록
//   선반이 채워지는 게 보여야 계속 모으고 싶어진다.
//
// 선반이 넘치면 카드 안에서 위아래로 스크롤된다.

private const val MarblesPerShelf = 6      // 선반 한 칸에 올라가는 구슬 수
private val PouchCardWidth = 460.dp
private val PouchMarbleSize = 56.dp
private val ShelfMaxHeight = 300.dp        // 이보다 길어지면 스크롤

/**
 * @param counts 색깔별 개수
 * @param total  모은 구슬 총 개수 (counts 합과 같다)
 */
@Composable
fun BoxScope.KidsMarblePouchDialog(
    counts: Map<Marble, Int>,
    total: Int,
    onClose: () -> Unit
) {
    // 색깔별 개수를 "구슬 한 알씩" 목록으로 펼친다.
    // 예: 연두 2개, 분홍 1개 → [연두, 연두, 분홍]
    val marbles = Marble.entries.flatMap { marble ->
        List(counts[marble] ?: 0) { marble }
    }

    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color(0x33303A66))
            // 뒤쪽 버튼이 눌리지 않도록 클릭을 흡수만 하고 아무것도 안 한다
            .clickable(enabled = true) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(PouchCardWidth)
                .clip(RoundedCornerShape(16.dp))
                .background(White)
                // 카드 안쪽을 눌렀을 때는 닫히지 않게 클릭을 흡수한다.
                // 흡수용이라 눌린 효과(회색 물결)는 끈다.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = "구슬 주머니",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AionTextDark
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = if (total == 0) {
                    "활동을 마치면 구슬을 모을 수 있어요"
                } else {
                    "구슬 ${total}개를 모았어요!"
                },
                fontSize = 14.sp,
                color = GreyNormalActive,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ShelfMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (marbles.isEmpty()) {
                    // 아직 하나도 없을 때도 빈 선반을 보여준다.
                    // "여기에 채우는 거구나" 가 보여야 모으고 싶어진다.
                    repeat(2) { Shelf(marbles = emptyList()) }
                } else {
                    marbles.chunked(MarblesPerShelf).forEach { row ->
                        Shelf(marbles = row)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KidsItemHeight)
                    .background(Normal)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "닫기",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }
        }
    }
}

// ------------------------------------------------------------
// 선반 한 칸
// ------------------------------------------------------------
// 구슬을 왼쪽부터 채우고, 그 아래에 선반 판을 그린다.
@Composable
private fun Shelf(marbles: List<Marble>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PouchMarbleSize),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            marbles.forEach { marble ->
                Image(
                    painter = painterResource(marble.imageRes),
                    contentDescription = marble.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(PouchMarbleSize)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // 선반 판
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(LightActive)
        )
    }
}

// ============================================================
// Preview
// ============================================================
@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "주머니 (모은 구슬 있음)")
@Composable
private fun KidsMarblePouchPreview() {
    AionTheme {
        Box(Modifier.fillMaxSize().background(Light)) {
            KidsMarblePouchDialog(
                counts = mapOf(
                    Marble.GREEN to 3,
                    Marble.BLUE to 2,
                    Marble.CREAM to 1,
                    Marble.PINK to 2,
                    Marble.PURPLE to 1
                ),
                total = 9,
                onClose = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "주머니 (비어 있음)")
@Composable
private fun KidsMarblePouchEmptyPreview() {
    AionTheme {
        Box(Modifier.fillMaxSize().background(Light)) {
            KidsMarblePouchDialog(counts = emptyMap(), total = 0, onClose = {})
        }
    }
}