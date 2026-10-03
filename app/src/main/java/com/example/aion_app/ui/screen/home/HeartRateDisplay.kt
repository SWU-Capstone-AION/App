package com.example.aion_app.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.Red
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.watch.HEART_RATE_DISPLAY_MS
import com.example.aion_app.watch.HeartRateWindow
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 교사 홈 카드의 "♥ 심박수 ∿∿ 78 bpm" 줄. 숫자가 바뀌는 순간만 짧게 넘겨서 변화가 눈에 띄게 한다.
 * @param bpm 화면에 보여줄 숫자. null이면 "--"(값 없음 / 20초 넘게 끊김)
 */
@Composable
fun HeartRateRow(bpm: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = if (bpm != null) Red else GrayText,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "심박수", fontSize = 14.sp, color = TextPrimary)
        Spacer(modifier = Modifier.width(12.dp))
        Image(
            painter = painterResource(id = R.drawable.heart_graph),
            contentDescription = null,
            modifier = Modifier.size(60.dp, 24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        AnimatedContent(
            targetState = bpm,
            transitionSpec = {
                // 오르면 아래에서 위로, 내리면 위에서 아래로 넘어간다
                val up = (targetState ?: 0) >= (initialState ?: 0)
                (slideInVertically(tween(300)) { h -> if (up) h else -h } + fadeIn(tween(300)))
                    .togetherWith(slideOutVertically(tween(300)) { h -> if (up) -h else h } + fadeOut(tween(300)))
            },
            label = "bpm"
        ) { value ->
            Text(
                text = value?.toString() ?: "--",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (value != null) TextPrimary else GrayText
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "bpm", fontSize = 13.sp, color = GrayText)
    }
}

// ---- 미리보기: ▶(Start Interactive Mode)를 누르면 움직임을 볼 수 있다 ----

/**
 * 1초마다 심박이 들어오고, 화면 숫자는 5초마다 바뀌는 모습.
 * 아래 작은 글씨는 확인용으로, 1초마다 실제로 들어오는 값을 보여준다.
 */
@Preview(showBackground = true, widthDp = 360, name = "1초마다 데이터 → 5초마다 숫자 변화")
@Composable
private fun HeartRateEvery5sPreview() {
    val window = remember { HeartRateWindow() }
    var incoming by remember { mutableIntStateOf(78) }
    var shown by remember { mutableStateOf<Int?>(null) }
    var seconds by remember { mutableLongStateOf(0) }

    // 1초마다 새 심박이 들어온다 (워치 → 태블릿 → 서버 흉내)
    LaunchedEffect(Unit) {
        while (true) {
            incoming = (incoming + Random.nextInt(-3, 4)).coerceIn(65, 120)
            window.add(incoming, System.currentTimeMillis())
            if (shown == null) shown = window.tick(System.currentTimeMillis())  // 첫 값은 바로 표시
            delay(1_000)
            seconds++
        }
    }
    // 화면 숫자는 5초마다 한 번만 바뀐다
    LaunchedEffect(Unit) {
        while (true) {
            delay(HEART_RATE_DISPLAY_MS)
            shown = window.tick(System.currentTimeMillis())
        }
    }

    MaterialTheme {
        Column {
            HeartRateRow(bpm = shown)
            Text(
                text = "들어온 값(1초마다): $incoming  ·  ${seconds}초",
                fontSize = 11.sp,
                color = GrayText,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "연결 확인 (20초 넘게 값 없음)")
@Composable
private fun HeartRateStalePreview() {
    MaterialTheme { HeartRateRow(bpm = null) }
}
