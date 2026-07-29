package com.example.aion_app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.GrayBackground
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

@Composable
fun AionPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) BluePrimary else GrayBackground,
            contentColor = if (isPrimary) White else TextPrimary,
            // 보조 버튼(isPrimary = false)은 비활성일 때도 회색을 유지한다.
            // 이전에는 두 경우 모두 파랑 60%라, 회색이어야 할 '회원가입' 버튼이
            // 비활성 상태에서 연한 파랑으로 보였다.
            disabledContainerColor = if (isPrimary) BluePrimary.copy(alpha = 0.6f) else GrayBackground,
            disabledContentColor = if (isPrimary) White else GrayText
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(text = text)
    }
}