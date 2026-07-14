package com.example.aion_app.ui.screen.login  // ← 본인 패키지명

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.example.aion_app.ui.component.AionPrimaryButton
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.TextPrimary

@Composable
fun OnboardingCompleteScreen(
    isSubmitting: Boolean = false,
    onConfirmClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(120.dp))

        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            Text("AION", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
        }

        Spacer(modifier = Modifier.height(100.dp))

        Text(
            "아이와 함께 성장할 준비가\n완료되었습니다.",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        // ===== 공용 AionPrimaryButton으로 통일 + 제출 중 로딩 표시 =====
        AionPrimaryButton(
            text = if (isSubmitting) "저장 중..." else "확인",
            onClick = onConfirmClick,
            enabled = !isSubmitting
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Preview(showBackground = true, device = "id:pixel_7")
@Composable
fun OnboardingCompleteScreenPreview() {
    OnboardingCompleteScreen()
}