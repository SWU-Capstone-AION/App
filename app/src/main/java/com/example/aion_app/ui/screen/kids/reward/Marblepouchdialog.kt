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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.aion_app.ui.theme.GreyLightHover
import com.example.aion_app.ui.theme.GreyNormalActive
import com.example.aion_app.ui.theme.Light
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.LightHover
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
 * 구슬 정렬 방식.
 *
 *   ACQUIRED 얻은 순서대로 (먼저 얻은 것이 앞)
 *   COLOR    같은 색끼리 모아서 (Marble 에 적은 순서)
 *
 * ⚠ 버튼 글자는 아이가 읽는 말이다. '획득순' 같은 한자어는 쓰지 않는다.
 *   팝업의 "구슬을 얻었어요!" 와 같은 단어를 써서 이어지게 했다.
 */
private enum class MarbleSort(val label: String) {
    ACQUIRED("얻은 순서로"),
    COLOR("색깔 별로")
}

/**
 * @param history 얻은 순서대로 늘어놓은 구슬 목록
 */
@Composable
fun BoxScope.KidsMarblePouchDialog(
    history: List<Marble>,
    onClose: () -> Unit
) {
    // 정렬은 팝업을 여는 동안만 쓰는 값이라 저장하지 않는다.
    // 다시 열면 기본값(획득순)으로 돌아온다.
    var sort by remember { mutableStateOf(MarbleSort.ACQUIRED) }

    val marbles = when (sort) {
        MarbleSort.ACQUIRED -> history
        MarbleSort.COLOR -> history.sortedBy { it.ordinal }
    }

    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color(0x33303A66))
            // 바깥을 누르면 닫힌다
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
                text = if (history.isEmpty()) {
                    "활동을 마치면 구슬을 모을 수 있어요"
                } else {
                    "구슬 ${history.size}개를 모았어요!"
                },
                fontSize = 14.sp,
                color = GreyNormalActive,
                textAlign = TextAlign.Center
            )

            // 정렬 버튼. 구슬이 하나도 없으면 고를 게 없으니 숨긴다.
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarbleSort.entries.forEach { option ->
                        SortChip(
                            text = option.label,
                            selected = sort == option,
                            onClick = { sort = option }
                        )
                    }
                }
            }

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
// 정렬 버튼
// ------------------------------------------------------------
// 회원가입 선택지(KidsSelectablePill)와 같은 알약 모양이지만
// 훨씬 작아서 따로 그린다. 색 규칙은 같게 맞췄다.
@Composable
private fun SortChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) LightHover else GreyLightHover)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) AionTextDark else GreyNormalActive
        )
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
                history = listOf(
                    Marble.PINK, Marble.GREEN, Marble.PINK, Marble.PURPLE,
                    Marble.BLUE, Marble.GREEN, Marble.CREAM, Marble.GREEN
                ),
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
            KidsMarblePouchDialog(history = emptyList(), onClose = {})
        }
    }
}