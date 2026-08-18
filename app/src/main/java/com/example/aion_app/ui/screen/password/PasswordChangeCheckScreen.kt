package com.example.aion_app.ui.screen.password

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aion_app.ui.component.*
import com.example.aion_app.ui.theme.AionTheme

@Composable
fun PasswordChangeCheckScreen(
    onBackClick: () -> Unit = {},
    onNextClick: (id: String, currentPw: String) -> Unit = { _, _ -> }
) {
    var id by remember { mutableStateOf("") }
    var currentPw by remember { mutableStateOf("") }
    var pwError by remember { mutableStateOf(false) }
    var showIdNotFoundDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { AionTopBar(title = "비밀번호 변경", onBackClick = onBackClick) },
        bottomBar = { AionBottomNavBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(40.dp))

            Text("아이디", style = MaterialTheme.typography.titleMedium)
            AionTextField(value = id, onValueChange = { id = it })

            Text("현재 비밀번호를 입력하세요", style = MaterialTheme.typography.titleMedium)
            AionPasswordField(
                value = currentPw,
                onValueChange = {
                    currentPw = it
                    pwError = it.isNotEmpty() && !isValidPassword(it)
                },
                isError = pwError,
                errorMessage = "영문, 숫자 포함 8자 이상 입력해 주세요.",
                showValidCheck = isValidPassword(currentPw)
            )

            Spacer(Modifier.weight(1f))

            AionPrimaryButton(
                text = "다음",
                onClick = {
                    if (id.isBlank()) {
                        showIdNotFoundDialog = true
                    } else if (!isValidPassword(currentPw)) {
                        pwError = true
                    } else {
                        onNextClick(id, currentPw)
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
        }

        if (showIdNotFoundDialog) {
            AlertDialog(
                onDismissRequest = { showIdNotFoundDialog = false },
                confirmButton = {
                    AionPrimaryButton(
                        text = "확인",
                        onClick = { showIdNotFoundDialog = false }
                    )
                },
                text = { Text("존재하지 않는 아이디입니다.") }
            )
        }
    }
}

fun isValidPassword(pw: String): Boolean {
    return pw.length >= 8 &&
            pw.any { it.isLetter() } &&
            pw.any { it.isDigit() }
}

@Preview(showBackground = true, device = "id:pixel_7")
@Composable
fun PasswordChangeCheckScreenPreview() {
    AionTheme { PasswordChangeCheckScreen() }
}