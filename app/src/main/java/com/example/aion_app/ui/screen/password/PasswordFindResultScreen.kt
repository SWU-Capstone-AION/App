package com.example.aion_app.ui.screen.password

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.component.*
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.BluePrimary

@Composable
fun PasswordFindResultScreen(
    nickname: String = "김슈니",
    maskedPassword: String = "Aion06**",
    onBackClick: () -> Unit = {},
    onChangePasswordClick: () -> Unit = {},
    onLoginClick: () -> Unit = {}
) {
    Scaffold(
        topBar = { AionTopBar(title = "비밀번호 찾기", onBackClick = onBackClick) },
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

            Text("${nickname}님의 비밀번호는", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                maskedPassword,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BluePrimary
            )
            Spacer(Modifier.height(8.dp))
            Text("입니다.", fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = onChangePasswordClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("비밀번호 변경하기")
            }
            Spacer(Modifier.height(12.dp))
            AionPrimaryButton(text = "로그인", onClick = onLoginClick)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun PasswordFindResultScreenPreview() {
    AionTheme { PasswordFindResultScreen() }
}
