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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.component.AionPasswordField
import com.example.aion_app.ui.component.AionPrimaryButton
import com.example.aion_app.ui.component.AionTextField
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.GrayBackground
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

@Composable
fun SignUpScreen(
    onLoginClick: () -> Unit = {},
    onSignUpClick: (type: String, email: String, password: String) -> Unit = { _, _, _ -> }
) {
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
        // ===== 로고 헤더 =====
        Spacer(modifier = Modifier.height(120.dp))

        Image(
            painter = painterResource(R.drawable.logo_text),
            contentDescription = "AION",
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(120.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text("회원가입", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            "가입 유형 선택",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
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
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ===== 공용 AionTextField (필드/버튼은 헤더 스타일과 무관하게 통일) =====
        AionTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "이메일"
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "비밀번호를 입력하세요",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        AionPasswordField(
            value = password,
            onValueChange = { password = it },
            placeholder = "비밀번호"
        )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(40.dp))

        AionPrimaryButton(
            text = "로그인",
            onClick = onLoginClick,
            isPrimary = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        AionPrimaryButton(
            text = "회원가입",
            onClick = { onSignUpClick(selectedType, email, password) },
            isPrimary = false,
            enabled = email.isNotBlank() && password.isNotBlank()
        )

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
            .background(if (isSelected) BluePrimary else GrayBackground)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) White else TextPrimary,
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