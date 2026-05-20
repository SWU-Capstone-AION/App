package com.example.aion_app

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================
// 색상 정의 (시안에서 추출)
// ============================================
// 시안의 푸른 톤을 대략적으로 맞춘 값. 정확한 색은 디자이너에게 hex 코드 받으세요.
private val AionBlue = Color(0xFF8AB4DB)        // 선택된 버튼/로그인 버튼의 파란색
private val AionLightGray = Color(0xFFEDEDED)   // 선택 안 된 버튼 배경
private val AionFieldBg = Color(0xFFEEF3F8)     // 입력칸 배경 (연한 푸른빛)
private val AionTextGray = Color(0xFF8E8E8E)    // 안내 텍스트 색

// ============================================
// 회원가입 화면
// ============================================
@Composable
fun SignUpScreen() {

    // ----- 상태 -----
    // 선택된 유형: "교사용" or "아동용" (null이면 아무것도 선택 안 됨)
    // 시안에서 아동용이 기본 선택된 것처럼 보여서 그렇게 둠. 변경 원하면 "교사용"으로.
    var selectedType by remember { mutableStateOf("아동용") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // 전체 화면 컨테이너
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            // 키보드 올라올 때 스크롤되게 (입력칸 가려지지 않도록)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ===== 상단 여백 =====
        Spacer(modifier = Modifier.height(80.dp))

        // ===== 로고 영역 =====
        // TODO: 로고 이미지 파일 받으면 아래 두 줄을 이걸로 교체:
        // Image(
        //     painter = painterResource(R.drawable.logo_aion),
        //     contentDescription = "AION 로고",
        //     modifier = Modifier.size(120.dp)
        // )
        // 일단 임시 텍스트:
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AION",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AionBlue
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 제목 =====
        Text(
            text = "회원가입",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(40.dp))

        // ===== 가입 유형 선택 =====
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "가입 유형 선택",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 두 버튼을 가로로 배치
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)  // 버튼 사이 간격
            ) {
                // 교사용 버튼
                TypeSelectButton(
                    text = "교사용",
                    isSelected = selectedType == "교사용",
                    onClick = { selectedType = "교사용" },
                    modifier = Modifier.weight(1f)  // 가로 절반 차지
                )
                // 아동용 버튼
                TypeSelectButton(
                    text = "아동용",
                    isSelected = selectedType == "아동용",
                    onClick = { selectedType = "아동용" },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ===== 이메일 입력 =====
        InputLabel(text = "이메일을 입력하세요")
        Spacer(modifier = Modifier.height(8.dp))
        CustomTextField(
            value = email,
            onValueChange = { email = it },
            isPassword = false
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ===== 비밀번호 입력 =====
        InputLabel(text = "비밀번호를 입력하세요")
        Spacer(modifier = Modifier.height(8.dp))
        CustomTextField(
            value = password,
            onValueChange = { password = it },
            isPassword = true
        )
        Spacer(modifier = Modifier.height(6.dp))
        // 비밀번호 안내 텍스트
        Text(
            text = "영문, 숫자 포함 8자 이상 입력해 주세요.",
            fontSize = 11.sp,
            color = AionTextGray,
            modifier = Modifier.align(Alignment.Start)  // 왼쪽 정렬
        )

        // ===== 버튼들과의 간격 (남는 공간을 모두 차지) =====
        Spacer(modifier = Modifier.height(60.dp))

        // ===== 로그인 버튼 (채워진) =====
        Button(
            onClick = { /* TODO: 로그인 화면 이동 */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AionBlue,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "로그인", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== 회원가입 버튼 (테두리만) =====
        OutlinedButton(
            onClick = { /* TODO: 실제 회원가입 처리 */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "회원가입", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ============================================
// 가입 유형 선택 버튼 (재사용 컴포넌트)
// ============================================
// 같은 모양 두 개를 다 적기 귀찮으니 함수로 빼둠
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
            .background(if (isSelected) AionBlue else AionLightGray)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.7f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================
// 입력칸 위 라벨 (재사용)
// ============================================
@Composable
private fun InputLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Black,
        modifier = Modifier.fillMaxWidth()
    )
}

// ============================================
// 커스텀 입력칸 (재사용)
// ============================================
@Composable
private fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        singleLine = true,
        // 비밀번호면 ●●● 처리
        visualTransformation = if (isPassword) PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(8.dp),
        // 시안의 연한 푸른빛 배경 + 밑줄 없애기
        colors = TextFieldDefaults.colors(
            focusedContainerColor = AionFieldBg,
            unfocusedContainerColor = AionFieldBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

// ============================================
// Preview
// ============================================
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun SignUpScreenPreview() {
    SignUpScreen()
}