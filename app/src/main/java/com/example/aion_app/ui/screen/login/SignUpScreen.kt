package com.example.aion_app.ui.screen.login  // ← 본인 패키지명

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.component.AionPasswordField
import com.example.aion_app.ui.component.AionPrimaryButton
import com.example.aion_app.ui.component.AionTextField
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.GrayBackground
import com.example.aion_app.ui.theme.LightHover
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

// 로고 크기 (프리뷰 보고 이 두 값만 조절하면 됨)
private val LogoSymbolWidth = 72.dp
private val LogoTextWidth = 76.dp

@Composable
fun SignUpScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onLoginClick: (userId: String, password: String) -> Unit = { _, _ -> },
    onSignUpClick: (type: String) -> Unit = { }
) {
    var selectedType by remember { mutableStateOf("아동용") }
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ===== 로고 헤더 =====
        Spacer(modifier = Modifier.height(120.dp))

        // ===== 로고: 심볼(∞) + 워드마크(AION) =====
        // logo_text.png 는 캔버스에 위아래 여백이 포함돼 있어(글자 390x106 / 캔버스 476x268)
        // 두 이미지 사이에 Spacer 를 두지 않아도 자연스러운 간격이 생긴다.
        Image(
            painter = painterResource(R.drawable.logo_symbol),
            contentDescription = "AION 로고",
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(LogoSymbolWidth)
        )
        Image(
            painter = painterResource(R.drawable.logo_text),
            contentDescription = null,   // 위 이미지가 이미 "AION 로고"로 읽힘
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(LogoTextWidth)
        )

        Spacer(modifier = Modifier.height(30.dp))

        // 시안 1p: 제목은 "로그인". "가입 유형 선택" 라벨 없이 버튼만 노출.
        Text("로그인", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        Spacer(modifier = Modifier.height(30.dp))

        // 이 토글은 '회원가입' 버튼을 눌렀을 때 어느 가입 플로우로 갈지만 정한다.
        // 로그인 자체는 서버에 저장된 역할을 따르므로 토글과 무관하다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TypeSelectButton("교사용", selectedType == "교사용", { selectedType = "교사용" }, Modifier.weight(1f))
            TypeSelectButton("아동용", selectedType == "아동용", { selectedType = "아동용" }, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "아이디",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ===== 공용 AionTextField (필드/버튼은 헤더 스타일과 무관하게 통일) =====
        AionTextField(
            value = userId,
            onValueChange = { userId = it },
            placeholder = ""
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "비밀번호",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        AionPasswordField(
            value = password,
            onValueChange = { password = it },
            placeholder = ""
        )

        // ===== 로그인 실패 메시지 =====
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(40.dp))

        AionPrimaryButton(
            text = if (isLoading) "로그인 중..." else "로그인",
            onClick = { onLoginClick(userId, password) },
            enabled = !isLoading && userId.isNotBlank() && password.isNotBlank(),
            isPrimary = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        AionPrimaryButton(
            text = "회원가입",
            // 회원가입 화면(시안 2p)으로 '이동'만 한다. 아이디/비번은 거기서 입력.
            onClick = { onSignUpClick(selectedType) },
            enabled = !isLoading,
            isPrimary = false
        )

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
fun SignUpAccountScreen(
    onBackClick: () -> Unit = {},
    onCheckDuplicate: (String) -> Unit = {},   // 아이디 중복확인 (백엔드 연결 지점)
    duplicateMessage: String? = null,          // 중복확인 결과 문구 (없으면 표시 안 함)
    onNext: (userId: String, email: String, password: String) -> Unit = { _, _, _ -> }
) {
    var userId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AionTopBar(title = "회원가입", onBackClick = onBackClick)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // ===== 아이디 + 중복확인 =====
            Text(
                "아이디를 입력해 주세요",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AionTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    placeholder = "",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (userId.isBlank()) GrayBackground else LightHover)
                        .clickable(enabled = userId.isNotBlank()) { onCheckDuplicate(userId) }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "확인",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            if (duplicateMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = duplicateMessage,
                    fontSize = 12.sp,
                    color = GrayText,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ===== 이메일 =====
            // 비밀번호를 잊었을 때 재설정 메일을 받을 주소라 실제로 쓰는 메일이어야 한다.
            Text(
                "이메일을 입력해 주세요",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            AionTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "example@email.com"
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "비밀번호를 잊었을 때 이 주소로 재설정 메일이 발송됩니다.",
                fontSize = 11.sp,
                color = GrayText,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ===== 비밀번호 =====
            Text(
                "비밀번호를 입력해 주세요",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            AionPasswordField(
                value = password,
                onValueChange = { password = it },
                placeholder = ""
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "영문, 숫자 포함 8자 이상 입력해 주세요.",
                fontSize = 11.sp,
                color = GrayText,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(40.dp))

            AionPrimaryButton(
                text = "다음",
                onClick = { onNext(userId, email, password) },
                enabled = userId.isNotBlank() && email.isNotBlank() && password.isNotBlank()
            )

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7")
@Composable
fun SignUpAccountScreenPreview() {
    AionTheme {
        SignUpAccountScreen()
    }
}

@Composable
private fun TypeSelectButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            // 선택 상태 = Blue Light:hover (#E8EFFC). 배경만 바뀌고 글자색은 동일.
            .background(if (isSelected) LightHover else GrayBackground)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_7")
@Composable
fun SignUpScreenPreview() {
    SignUpScreen()
}