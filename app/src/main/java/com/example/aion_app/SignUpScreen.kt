package com.example.aion_app  // ← 본인 패키지명

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== 팀 공통 색상 (회의 확정) =====
private val AionBlue = Color(0xFF6495ED)        // 로그인 버튼
private val AionOnBlue = Color(0xFFF2F7FB)      // 로그인 버튼 텍스트
private val AionFieldBg = Color(0xFFF6F7F8)     // 입력칸 & 회원가입 버튼 배경
private val AionTextDark = Color(0xFF2D3C4A)    // 기본 텍스트
private val AionSelected = Color(0xFFE8EFFC)    // 선택/hover 색
private val AionTextGray = Color(0xFF8E8E8E)    // 안내 텍스트 (회색)

@Composable
fun SignUpScreen() {
    var selectedType by remember { mutableStateOf("아동용") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(120.dp))

        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            Text("AION", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AionBlue)
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text("회원가입", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AionTextDark)

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            "가입 유형 선택",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = AionTextDark,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TypeSelectButton("교사용", selectedType == "교사용", { selectedType = "교사용" }, Modifier.weight(1f))
            TypeSelectButton("아동용", selectedType == "아동용", { selectedType = "아동용" }, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "이메일을 입력하세요",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = AionTextDark,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        SignUpField(value = email, onValueChange = { email = it }, isPassword = false)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "비밀번호를 입력하세요",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = AionTextDark,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        SignUpField(value = password, onValueChange = { password = it }, isPassword = true)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "영문, 숫자 포함 8자 이상 입력해 주세요.",
            fontSize = 11.sp,
            color = AionTextGray,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(40.dp))

        // 로그인 버튼 (파란색)
        Button(
            onClick = { /* TODO: 로그인 화면 이동 */ },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AionBlue,
                contentColor = AionOnBlue
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("로그인", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 회원가입 버튼 (연회색)
        Button(
            onClick = { /* TODO: 회원가입 처리 */ },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AionFieldBg,
                contentColor = AionTextDark
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("회원가입", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(60.dp))
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
            // 선택되면 파란색, 아니면 연회색
            .background(if (isSelected) AionBlue else AionFieldBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) AionOnBlue else AionTextDark,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SignUpField(
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = AionFieldBg,
            unfocusedContainerColor = AionFieldBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = AionTextDark,
            unfocusedTextColor = AionTextDark
        )
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun SignUpScreenPreview() {
    SignUpScreen()
}