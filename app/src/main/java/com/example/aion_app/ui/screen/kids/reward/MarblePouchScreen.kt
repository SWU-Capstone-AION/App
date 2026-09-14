package com.example.aion_app.ui.kids.reward

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aion_app.R

// 팀 Color.kt에 맞는 색이 있으면 아래 네 줄을 그걸로 바꿔주세요.
private val PouchBackground = Color(0xFFFDF8F0)
private val PouchCardColor = Color(0xFFFFFFFF)
private val PouchTextColor = Color(0xFF3F3A33)
private val PouchSubTextColor = Color(0xFF9A938A)

/**
 * 구슬 주머니 화면.
 * 모은 구슬을 색깔별로 보여준다. 아직 못 모은 색은 흐리게 표시해서
 * "이 색도 있구나" 하고 다음 활동 동기가 생기게 한다.
 */
@Composable
fun MarblePouchScreen(
    viewModel: RewardViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val counts by viewModel.marbleCounts.collectAsStateWithLifecycle()
    val total by viewModel.totalMarbles.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PouchBackground)
            .padding(horizontal = 40.dp, vertical = 28.dp)
    ) {
        // 상단 — 뒤로 가기 + 제목
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PouchCardColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "←",
                    fontSize = 24.sp,
                    color = PouchTextColor
                )
            }

            Spacer(Modifier.width(16.dp))

            Text(
                text = "내 구슬 주머니",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = PouchTextColor
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (total == 0) {
                "활동을 마치면 구슬을 모을 수 있어요"
            } else {
                "지금까지 구슬 ${total}개를 모았어요!"
            },
            fontSize = 18.sp,
            color = PouchSubTextColor,
            modifier = Modifier.padding(start = 64.dp)
        )

        Spacer(Modifier.height(28.dp))

        // 구슬 5색을 한 줄에 나란히
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(Marble.values()) { marble ->
                MarbleCard(
                    marble = marble,
                    count = counts[marble] ?: 0
                )
            }
        }
    }
}

/**
 * 구슬 하나를 보여주는 카드.
 * 아직 못 모은 색은 흐리게.
 */
@Composable
private fun MarbleCard(
    marble: Marble,
    count: Int
) {
    val hasMarble = count > 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PouchCardColor)
            .padding(vertical = 20.dp, horizontal = 8.dp)
    ) {
        Image(
            painter = painterResource(marble.imageRes),
            contentDescription = marble.label,
            modifier = Modifier
                .size(84.dp)
                .alpha(if (hasMarble) 1f else 0.25f)
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = marble.label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (hasMarble) PouchTextColor else PouchSubTextColor
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = if (hasMarble) "${count}개" else "아직 없어요",
            fontSize = 17.sp,
            fontWeight = if (hasMarble) FontWeight.Bold else FontWeight.Normal,
            color = if (hasMarble) PouchTextColor else PouchSubTextColor
        )
    }
}