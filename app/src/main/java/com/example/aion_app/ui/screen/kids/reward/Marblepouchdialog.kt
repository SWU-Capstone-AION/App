package com.example.aion_app.ui.screen.kids.reward

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.screen.kids.KidsDialogScrim
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.Light
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.White

// ============================================================
// 구슬 주머니 팝업
// ============================================================
// 홈 오른쪽 위 사탕 배지를 누르면 열린다. 별도 화면이 아니라 팝업이다.
//
// 시안 기준 구성
//   - 제목 "구슬주머니", 오른쪽 위에 닫기 아이콘
//   - 한 쪽에 5칸 x 2줄 = 10개. 아직 못 채운 자리는 점선 원으로 보여준다
//     ("여기를 채우는 거구나" 가 보여야 계속 모으고 싶어진다)
//   - 아래쪽에 페이지 번호와 좌우 이동 버튼 (교사 앱 리포트 화면과 같은 모양)
//   - 구슬을 누르면 뒤집혀서 얻은 날짜가 보인다
//
// 정렬(획득순/색깔순) 버튼은 디자인 피드백으로 뺐다.
// 구슬은 언제나 얻은 순서대로 놓인다.

private const val MarblesPerRow = 5
private const val RowsPerPage = 2
private const val MarblesPerPage = MarblesPerRow * RowsPerPage

private val PouchCardWidth = 620.dp
private val PouchMarbleSize = 76.dp
private val MarbleGapHorizontal = 28.dp   // 시안 피드백대로 기존(8dp)보다 20dp 넓혔다
private val MarbleGapVertical = 24.dp

private const val FlipDurationMs = 500    // 구슬이 돌아가는 시간

/**
 * @param history 얻은 순서대로 늘어놓은 구슬 목록 (색 + 얻은 날짜)
 */
@Composable
fun BoxScope.KidsMarblePouchDialog(
    history: List<MarbleRecord>,
    onClose: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }

    // 지금 뒤집어 놓은 구슬의 번호(전체 목록 기준). 아무것도 안 뒤집었으면 null.
    // 한 번에 하나만 뒤집힌다 — 여러 개가 날짜를 달고 있으면 아이가 헷갈린다.
    var flipped by remember { mutableStateOf<Int?>(null) }

    // 구슬이 없어도 빈 칸이 보이도록 최소 한 쪽은 만든다
    val pageCount = maxOf(1, (history.size + MarblesPerPage - 1) / MarblesPerPage)
    val safePage = page.coerceIn(0, pageCount - 1)

    Box(
        modifier = Modifier
            .matchParentSize()
            .background(KidsDialogScrim)
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
                ) { flipped = null },   // 빈 곳을 누르면 뒤집힌 구슬이 돌아온다
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ----- 제목 + 닫기 -----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, start = 20.dp, end = 20.dp)
            ) {
                Text(
                    text = "구슬주머니",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AionTextDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                )
                Image(
                    painter = painterResource(R.drawable.cancel_icon_round),
                    contentDescription = "닫기",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(30.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onClose() }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ----- 구슬 칸 -----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(MarbleGapVertical)
            ) {
                repeat(RowsPerPage) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MarbleGapHorizontal)
                    ) {
                        repeat(MarblesPerRow) { column ->
                            // 전체 목록에서 이 칸에 해당하는 번호
                            val index =
                                safePage * MarblesPerPage + row * MarblesPerRow + column

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                val record = history.getOrNull(index)
                                if (record == null) {
                                    EmptySlot()
                                } else {
                                    MarbleSlot(
                                        record = record,
                                        flipped = flipped == index,
                                        onClick = {
                                            flipped = if (flipped == index) null else index
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ----- 페이지 이동 -----
            // 교사 앱 리포트 화면(DateNavigator)과 같은 아이콘·색을 쓴다.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        page = safePage - 1
                        flipped = null
                    },
                    enabled = safePage > 0
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "이전",
                        tint = if (safePage > 0) AionTextDark else AionTextDark.copy(alpha = 0.3f)
                    )
                }

                Text(
                    text = "${safePage + 1}/$pageCount",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = AionTextDark
                )

                IconButton(
                    onClick = {
                        page = safePage + 1
                        flipped = null
                    },
                    enabled = safePage < pageCount - 1
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = "다음",
                        tint = if (safePage < pageCount - 1) {
                            AionTextDark
                        } else {
                            AionTextDark.copy(alpha = 0.3f)
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ------------------------------------------------------------
// 구슬 한 알
// ------------------------------------------------------------
// 누르면 세로축을 중심으로 돌아가면서 뒷면이 나오고, 거기에 얻은 날짜가 있다.
// 다시 누르면 앞면으로 돌아온다.
//
// 예전 방식으로 저장돼 날짜를 모르는 구슬은 뒷면에 "?" 가 뜬다.
// 뒤집히지도 않게 하면 아이는 고장 난 줄 안다.
@Composable
private fun MarbleSlot(
    record: MarbleRecord,
    flipped: Boolean,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(FlipDurationMs),
        label = "marble_flip"
    )

    Box(
        modifier = Modifier
            .size(PouchMarbleSize)
            .graphicsLayer {
                rotationY = rotation
                // 이 값이 없으면 돌아갈 때 원근이 과해서 그림이 우그러진다
                cameraDistance = 12f * density
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // 90도를 넘어가는 순간 앞뒤가 바뀐다
        if (rotation <= 90f) {
            Image(
                painter = painterResource(record.marble.imageRes),
                contentDescription = record.marble.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(PouchMarbleSize)
            )
        } else {
            // 뒷면은 이미 180도 돌아간 면이라, 그대로 두면 그림과 글씨가 거울처럼 뒤집힌다.
            // 한 번 더 돌려서 바로 세운다.
            Box(
                modifier = Modifier.graphicsLayer { rotationY = 180f },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(record.marble.backImageRes),
                    contentDescription = record.marble.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(PouchMarbleSize)
                )
                Text(
                    text = record.shortDate ?: "?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AionTextDark
                )
            }
        }
    }
}

// ------------------------------------------------------------
// 아직 못 채운 자리
// ------------------------------------------------------------
@Composable
private fun EmptySlot() {
    Canvas(modifier = Modifier.size(PouchMarbleSize)) {
        val stroke = 1.5.dp.toPx()
        drawCircle(
            color = LightActive,
            radius = size.minDimension / 2 - stroke,
            style = Stroke(
                width = stroke,
                // 점선 원. 4dp 선 + 4dp 빈칸
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                )
            )
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
                    MarbleRecord(Marble.PURPLE, "2026-09-10"),
                    MarbleRecord(Marble.GREEN, "2026-09-11"),
                    MarbleRecord(Marble.BLUE, "2026-09-13"),
                    MarbleRecord(Marble.BLUE, "2026-09-14")
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