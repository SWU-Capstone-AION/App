package com.example.aion_app.ui.screen.kids

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.GreyLightActive
import com.example.aion_app.ui.theme.GreyNormalActive
import com.example.aion_app.ui.theme.LightHover

// 로고 크기 (프리뷰 보고 이 두 값만 조절하면 됨)
private val LogoSymbolWidth = 79.dp
private val LogoTextWidth = 75.dp

// ============================================================
// 시안 1p — 아동용 로그인
// ============================================================
// 교사용 SignUpScreen 과 달리 "로그인" 큰 제목이 없고, 로고 바로 아래 토글이 온다.
// 토글에서 '교사용'을 누르면 교사용 로그인 화면으로 넘어간다(onTeacherClick).
@Composable
fun KidsLoginScreen(
    onTeacherClick: () -> Unit = {},
    onLoginClick: (userId: String, password: String) -> Unit = { _, _ -> },
    onSignUpClick: () -> Unit = {}
) {
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    KidsScreenFrame {
        Spacer(Modifier.height(32.dp))

        // ===== 로고: 심볼(∞) + 워드마크(AION) =====
        // logo_text.png 는 캔버스에 위아래 여백이 포함돼 있어 Spacer 없이도 간격이 생긴다.
        Image(
            painter = painterResource(R.drawable.logo_symbol),
            contentDescription = "AION 로고",
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(LogoSymbolWidth)
        )
        Image(
            painter = painterResource(R.drawable.logo_text),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(LogoTextWidth)
        )

        Spacer(Modifier.height(32.dp))

        // ===== 교사용 / 아동용 =====
        KidsTypeToggle(
            selected = "아동용",
            onSelect = { type -> if (type == "교사용") onTeacherClick() }
        )

        Spacer(Modifier.height(20.dp))

        KidsSectionLabel("아이디")
        Spacer(Modifier.height(8.dp))
        KidsTextField(
            value = userId,
            onValueChange = { userId = it },
            placeholder = ""
        )

        Spacer(Modifier.height(20.dp))

        KidsSectionLabel("비밀번호")
        Spacer(Modifier.height(8.dp))
        KidsPasswordField(
            value = password,
            onValueChange = { password = it },
            placeholder = ""
        )

        Spacer(Modifier.height(32.dp))

        KidsPrimaryButton(
            text = "로그인",
            onClick = { onLoginClick(userId, password) },
            isPrimary = true
        )

        Spacer(Modifier.height(20.dp))

        KidsPrimaryButton(
            text = "회원가입",
            onClick = onSignUpClick,
            isPrimary = false
        )

        Spacer(Modifier.height(38.dp))
    }
}

// ============================================================
// 시안 2p — 회원가입: 아이디 + 비밀번호
// ============================================================
// 아이디 줄 = 입력칸 261 + 간격 8 + 확인 60 = 329
@Composable
fun KidsSignUpAccountScreen(
    onBackClick: () -> Unit = {},
    onCheckDuplicate: (String) -> Unit = {},   // 아이디 중복확인 (백엔드 연결 지점)
    duplicateMessage: String? = null,          // 중복확인 결과 문구 (없으면 표시 안 함)
    onNext: (userId: String, password: String) -> Unit = { _, _ -> }
) {
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val idFieldWidth = KidsContentWidth - KidsConfirmWidth - 8.dp   // = 261dp

    KidsScreenFrame(
        topBar = { AionTopBar(title = "회원가입", onBackClick = onBackClick) },
        bottomButton = {
            KidsPrimaryButton(
                text = "다음",
                onClick = { onNext(userId, password) },
                enabled = userId.isNotBlank() && password.isNotBlank()
            )
        }
    ) {
        Spacer(Modifier.height(48.dp))

        // ===== 아이디 + 확인 =====
        KidsSectionLabel("아이디를 입력해 주세요")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.width(KidsContentWidth),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KidsTextField(
                value = userId,
                onValueChange = { userId = it },
                placeholder = "",
                width = idFieldWidth
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(KidsConfirmWidth)
                    .height(KidsItemHeight)
                    .clip(RoundedCornerShape(KidsCorner))
                    .background(if (userId.isBlank()) GreyLightActive else LightHover)
                    .clickable(enabled = userId.isNotBlank()) { onCheckDuplicate(userId) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "확인",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AionTextDark
                )
            }
        }

        if (duplicateMessage != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = duplicateMessage,
                fontSize = 12.sp,
                color = GreyNormalActive,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(24.dp))

        // ===== 비밀번호 =====
        KidsSectionLabel("비밀번호를 입력해 주세요")
        Spacer(Modifier.height(8.dp))
        KidsPasswordField(
            value = password,
            onValueChange = { password = it },
            placeholder = ""
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "영문, 숫자 포함 8자 이상 입력해 주세요.",
            fontSize = 11.sp,
            color = GreyNormalActive,
            modifier = Modifier.fillMaxWidth()
        )

        // 하단 고정 버튼에 가리지 않도록 여백 확보
        Spacer(Modifier.height(120.dp))
    }
}

// ============================================================
// Preview — 시안 프레임(930 x 582)에 맞춘 가로 크기
// ============================================================
@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "1p 아동용 로그인")
@Composable
private fun KidsLoginScreenPreview() {
    AionTheme { KidsLoginScreen() }
}

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "2p 아이디/비밀번호")
@Composable
private fun KidsSignUpAccountScreenPreview() {
    AionTheme { KidsSignUpAccountScreen() }
}

// 실기기(갤럭시탭 S9 FE+ 가로) 크기에서 확인용
// 위 930x582 프리뷰와 '똑같이' 보이면 스케일이 정상 동작하는 것.
@Preview(showBackground = true, device = "spec:width=1204dp,height=753dp,dpi=340", name = "1p 실기기")
@Composable
private fun KidsLoginScreenTabletPreview() {
    AionTheme { KidsLoginScreen() }
}