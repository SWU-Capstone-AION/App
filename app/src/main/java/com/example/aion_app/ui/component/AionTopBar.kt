package com.example.aion_app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.theme.HeaderStroke

// ============================================================
// 뒤로가기(<) 아이콘 — 선 두께 3
// ============================================================
private val BackArrowThick: ImageVector = ImageVector.Builder(
    name = "BackArrowThick",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),   // 실제 색은 Icon 의 tint 가 정한다
        strokeLineWidth = 3f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(11f, 3f)
        lineTo(2f, 12f)
        lineTo(11f, 21f)
    }
}.build()

@Composable
fun AionTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconStartPadding: Dp = 0.dp,  // 뒤로가기 아이콘 오른쪽 미세 이동 (기본 0 → 기존 화면 영향 없음)
    // 하단 구분선 색·두께.
    // 특별한 화면이 아니면 값을 넘기지 않는다.
    dividerColor: Color = HeaderStroke,
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
                    imageVector = BackArrowThick,
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
        HorizontalDivider(thickness = dividerThickness, color = dividerColor)
    }
}
