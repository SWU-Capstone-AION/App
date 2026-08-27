package com.example.aion_app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.aion_app.R
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.GrayText

@Composable
fun AionBottomNavBar(
    selected: String = "mypage",
    onSelect: (String) -> Unit = {}
) {
    // 라벨 색만 지정 (아이콘은 커스텀 PNG라 원본 색 사용), 선택 알약 배경 제거
    val itemColors = NavigationBarItemDefaults.colors(
        selectedTextColor = Normal,
        unselectedTextColor = GrayText,
        indicatorColor = Color.Transparent
    )
    val iconSize = 26.dp

    NavigationBar(containerColor = Color.White) {
        NavigationBarItem(
            selected = selected == "home",
            onClick = { onSelect("home") },
            icon = {
                Image(
                    painter = painterResource(
                        if (selected == "home") R.drawable.home_active else R.drawable.home_nonactive
                    ),
                    contentDescription = "홈",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(iconSize)
                )
            },
            label = { Text("홈") },
            colors = itemColors
        )
        NavigationBarItem(
            selected = selected == "report",
            onClick = { onSelect("report") },
            icon = {
                Image(
                    painter = painterResource(
                        if (selected == "report") R.drawable.report_active else R.drawable.report_nonactive
                    ),
                    contentDescription = "리포트",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(iconSize)
                )
            },
            label = { Text("리포트") },
            colors = itemColors
        )
        NavigationBarItem(
            selected = selected == "mypage",
            onClick = { onSelect("mypage") },
            icon = {
                Image(
                    painter = painterResource(
                        if (selected == "mypage") R.drawable.mypage_active else R.drawable.mypage_nonactive
                    ),
                    contentDescription = "마이페이지",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(iconSize)
                )
            },
            label = { Text("마이페이지") },
            colors = itemColors
        )
    }
}