package com.example.aion_app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 시안 색상 (회원가입 화면이랑 동일)
private val AionBlue = Color(0xFF8AB4DB)

// ============================================
// 온보딩 완료 화면 (교사용/아동용 공통으로 쓸 수 있게 만듦)
// ============================================
// userType 파라미터로 텍스트만 살짝 다르게:
//   "교사용" → "아이와 함께 성장할 준비가 완료되었습니다."
//   "아동용" → 다른 문구 (나중에 결정)
@Composable
fun OnboardingCompleteScreen(
    userType: String = "교사용",
    onConfirmClick: () -> Unit = {}  // 확인 버튼 눌렀을 때 동작 (나중에 화면 이동 연결)
) {

    // 사용자 유형에 따라 메시지 다르게
    val message = when (userType) {
        "교사용" -> "아이와 함께 성장할 준비가\n완료되었습니다."
        "아동용" -> "AION과 함께\n시작할 준비가 완료되었어요!"  // 임시 문구, 시안 받으면 교체
        else -> "준비가 완료되었습니다."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ===== 상단 여백 =====
        Spacer(modifier = Modifier.height(120.dp))

        // ===== 로고 =====
        // TODO: 로고 이미지 받으면 Image()로 교체
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AION",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AionBlue
            )
        }

        // ===== 중간 여백 =====
        Spacer(modifier = Modifier.height(100.dp))

        // ===== 완료 메시지 =====
        Text(
            text = message,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center,
            lineHeight = 30.sp  // 줄 간격
        )

        // ===== 남는 공간 다 차지 (확인 버튼을 아래로 밀기) =====
        Spacer(modifier = Modifier.weight(1f))

        // ===== 확인 버튼 =====
        Button(
            onClick = onConfirmClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AionBlue,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "확인", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ============================================
// Preview - 교사용
// ============================================
@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "교사용 완료")
@Composable
fun OnboardingCompleteScreenTeacherPreview() {
    OnboardingCompleteScreen(userType = "교사용")
}

// ============================================
// Preview - 아동용
// ============================================
@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "아동용 완료")
@Composable
fun OnboardingCompleteScreenChildPreview() {
    OnboardingCompleteScreen(userType = "아동용")
}