package com.example.aion_app.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.GrayText

@Composable
fun AionBottomNavBar(selected: String = "mypage",
                     onSelect: (String) -> Unit = {}) {
    NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
        NavigationBarItem(
            selected = selected == "home",
            onClick = {},
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("홈") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BluePrimary,
                unselectedIconColor = GrayText
            )
        )
        NavigationBarItem(
            selected = selected == "report",
            onClick = {},
            icon = { Icon(Icons.Default.Description, contentDescription = null) },
            label = { Text("리포트") }
        )
        NavigationBarItem(
            selected = selected == "mypage",
            onClick = {},
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            label = { Text("마이페이지") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BluePrimary,
                unselectedIconColor = GrayText
            )
        )
    }
}