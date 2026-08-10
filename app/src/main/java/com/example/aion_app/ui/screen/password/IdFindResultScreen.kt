package com.example.aion_app.ui.screen.password

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.component.*
import com.example.aion_app.ui.theme.*

@Composable
fun IdFindResultScreen(
    nickname: String = "김슈니",
    userId: String = "swuaion2026",
    onBackClick: () -> Unit = {},
    onPasswordFindClick: () -> Unit = {},
    onLoginClick: () -> Unit = {}
) {
    Scaffold(
        topBar = { AionTopBar(title = "아이디 찾기", onBackClick = onBackClick) },
        bottomBar = { AionBottomNavBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Text(
                "${nickname}님의 아이디는",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                userId,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = BluePrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "입니다.",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.weight(1f))

            // 비밀번호 찾기 버튼 - 회색 배경
            Button(
                onClick = onPasswordFindClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFECEEF0)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "비밀번호 찾기",
                    color = Darker,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))

            // 로그인 버튼 - 파란 배경
            Button(
                onClick = onLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BluePrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "로그인",
                    color = White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_7")
@Composable
fun IdFindResultScreenPreview() {
    AionTheme { IdFindResultScreen() }
}