package com.example.aion_app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AionTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconStartPadding: Dp = 0.dp,  // 뒤로가기 아이콘 오른쪽 미세 이동 (기본 0 → 기존 화면 영향 없음)
    // 하단 구분선 색·두께. 기본값(null / 0.5dp)이면 기존 화면들은 지금 모습 그대로다.
    // 알림센터처럼 "칩 위 스트로크"가 필요한 화면에서만 값을 넘긴다.
    dividerColor: Color? = null,
    dividerThickness: Dp = 0.5.dp
) {
    // ⚠ Scaffold 의 topBar 슬롯은 자식들을 모두 (0,0)에 겹쳐 배치한다.
    //   Box 와 HorizontalDivider 를 형제로 두면 구분선이 제목 위에 겹쳐 그려져
    //   화면에서 보이지 않는다. 반드시 Column 으로 한 덩어리로 묶어야 한다.
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = iconStartPadding)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "뒤로가기"
                )
            }
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (dividerColor != null) {
            HorizontalDivider(thickness = dividerThickness, color = dividerColor)
        } else {
            HorizontalDivider(thickness = dividerThickness)
        }
    }
}