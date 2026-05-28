package com.example.aion_app.ui.screen.login  // ← 본인 패키지명

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
import com.example.aion_app.ui.theme.AionBlue
import com.example.aion_app.ui.theme.AionOnBlue
import com.example.aion_app.ui.theme.AionTextDark

@Composable
fun OnboardingCompleteScreen(
    onConfirmClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(120.dp))

        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            Text("AION", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AionBlue)
        }

        Spacer(modifier = Modifier.height(100.dp))

        Text(
            "아이와 함께 성장할 준비가\n완료되었습니다.",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AionTextDark,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onConfirmClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AionBlue,
                contentColor = AionOnBlue
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("확인", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun OnboardingCompleteScreenPreview() {
    OnboardingCompleteScreen()
}