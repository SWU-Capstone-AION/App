package com.example.aion_app.ui.screen.password

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aion_app.ui.component.*
import com.example.aion_app.ui.theme.AionTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.aion_app.ui.theme.LightHover
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.White

@Composable
fun PasswordChangeScreen(
    onBackClick: () -> Unit = {},
    onChangeSuccess: () -> Unit = {}
) {
    var currentPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var currentPwError by remember { mutableStateOf(false) }
    var newPwError by remember { mutableStateOf(false) }
    var confirmPwError by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { AionTopBar(title = "비밀번호 변경", onBackClick = onBackClick) },
        bottomBar = { AionBottomNavBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(40.dp))

            Text("현재 비밀번호를 입력하세요", style = MaterialTheme.typography.titleMedium)
            AionPasswordField(
                value = currentPw,
                onValueChange = {
                    currentPw = it
                    currentPwError = it.isNotEmpty() && !isValidPassword(it)
                },
                isError = currentPwError,
                showValidCheck = isValidPassword(currentPw)
            )

            Text("새 비밀번호를 입력하세요", style = MaterialTheme.typography.titleMedium)
            AionPasswordField(
                value = newPw,
                onValueChange = {
                    newPw = it
                    newPwError = it.isNotEmpty() && !isValidPassword(it)
                },
                isError = newPwError,
                showValidCheck = isValidPassword(newPw)
            )

            Text("새 비밀번호를 확인해 주세요", style = MaterialTheme.typography.titleMedium)
            AionPasswordField(
                value = confirmPw,
                onValueChange = {
                    confirmPw = it
                    confirmPwError = it.isNotEmpty() && it != newPw
                },
                isError = confirmPwError,
                errorMessage = "비밀번호를 다시 입력해 주세요",
                helperText = null,
                placeholder = "변경한 비밀번호를 한 번 더 입력해 주세요",
                placeholderColor = Color(0xFFB4B4B4),
                placeholderFontSize = 13.sp,
                showValidCheck = confirmPw.isNotEmpty() && confirmPw == newPw
            )

            Spacer(Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AionPrimaryButton(
                    text = "다음에",
                    onClick = onBackClick,
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )
                AionPrimaryButton(
                    text = "변경",
                    onClick = {
                        if (isValidPassword(currentPw) &&
                            isValidPassword(newPw) &&
                            newPw == confirmPw
                        ) {
                            showSuccessDialog = true
                        } else {
                            if (!isValidPassword(currentPw)) currentPwError = true
                            if (!isValidPassword(newPw)) newPwError = true
                            if (newPw != confirmPw) confirmPwError = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { },
                confirmButton = {
                    Button(
                        onClick = {
                            showSuccessDialog = false
                            onChangeSuccess()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Normal  // 연한 파란색 배경
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "확인",
                            color = LightHover,  // 진한 파란 텍스트
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "비밀번호가 변경되었습니다.",
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = White
            )
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_7")
@Composable
fun PasswordChangeScreenPreview() {
    AionTheme { PasswordChangeScreen() }
}