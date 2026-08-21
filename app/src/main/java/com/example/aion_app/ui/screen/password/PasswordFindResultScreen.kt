package com.example.aion_app.ui.screen.password

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.component.*
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.Normal
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.White

// ============================================================
// 비밀번호 찾기 결과 — 재설정 메일 발송 안내
// ============================================================
// 비밀번호는 암호화되어 저장되므로 서버도 원본을 알 수 없다.
// 그래서 기존 비밀번호를 보여주는 대신, 메일 속 링크에서 새로 설정하게 한다.
// 링크를 누르면 Firebase가 제공하는 재설정 페이지가 열리므로
// 앱 안에 비밀번호 변경 화면은 두지 않는다.
@Composable
fun PasswordFindResultScreen(
    onBackClick: () -> Unit = {},
    onLoginClick: () -> Unit = {}
) {
    Scaffold(
        topBar = { AionTopBar(title = "비밀번호 찾기", onBackClick = onBackClick) },
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
                "비밀번호 재설정 메일을",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "보냈습니다.",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = Normal,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "메일함에서 링크를 눌러\n새 비밀번호를 설정해 주세요.",
                fontSize = 15.sp,
                color = GrayText,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "메일이 오지 않으면 스팸함을 확인해 주세요.",
                fontSize = 13.sp,
                color = GrayText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            // 로그인 버튼 - 파란 배경
            Button(
                onClick = onLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Normal
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
fun PasswordFindResultScreenPreview() {
    AionTheme { PasswordFindResultScreen() }
}