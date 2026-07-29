package com.example.aion_app.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.GrayText

@Composable
fun AionBottomNavBar(
    selected: String = "mypage",
    onSelect: (String) -> Unit = {}
) {
    // 활성화 시 아이콘+텍스트 모두 메인 컬러(#6495ED), 선택 알약 배경은 제거
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = BluePrimary,
        selectedTextColor = BluePrimary,
        unselectedIconColor = GrayText,
        unselectedTextColor = GrayText,
        indicatorColor = Color.Transparent
    )
    // 아이콘 3개 동일 크기 + 조금 키움
    val iconSize = 28.dp

    NavigationBar(containerColor = Color.White) {
        NavigationBarItem(
            selected = selected == "home",
            onClick = { onSelect("home") },
            icon = {
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(iconSize))
            },
            label = { Text("홈") },
            colors = itemColors
        )
        NavigationBarItem(
            selected = selected == "report",
            onClick = { onSelect("report") },
            icon = {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(iconSize))
            },
            label = { Text("리포트") },
            colors = itemColors
        )
        NavigationBarItem(
            selected = selected == "mypage",
            onClick = { onSelect("mypage") },
            icon = {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(iconSize))
            },
            label = { Text("마이페이지") },
            colors = itemColors
        )
    }
}
