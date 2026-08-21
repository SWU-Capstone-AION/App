package com.example.aion_app.ui.screen.password

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aion_app.ui.component.*
import com.example.aion_app.ui.theme.*

// 인증번호 단계는 두지 않는다.
// Firebase가 코드 메일 발송을 지원하지 않아, 이름 + 이메일 일치 여부로만 확인한다.
@Composable
fun IdFindScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onBackClick: () -> Unit = {},
    onFindSuccess: (name: String, email: String) -> Unit = { _, _ -> },
    onSwitchToPasswordFind: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    var nameBlankError by remember { mutableStateOf(false) }
    var emailBlankError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { AionTopBar(title = "아이디 찾기", onBackClick = onBackClick) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            // 탭 (아이디 찾기 / 비밀번호 찾기)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GrayBackground)
                    .padding(4.dp)
            ) {
                TabButton(
                    text = "아이디 찾기",
                    selected = true,
                    onClick = { },  // 현재 화면이라 클릭 무시
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    text = "비밀번호 찾기",
                    selected = false,
                    onClick = onSwitchToPasswordFind,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(32.dp))

            // 이름 입력
            Text("이름", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            AionTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameBlankError = false
                },
                isError = nameBlankError
            )
            if (nameBlankError) {
                Text(
                    "이름을 입력해 주세요.",
                    color = RedError,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(20.dp))

            // 이메일 입력
            Text("이메일", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            AionTextField(
                value = email,
                onValueChange = {
                    email = it
                    emailBlankError = false
                },
                isError = emailBlankError
            )
            if (emailBlankError) {
                Text(
                    "이메일을 입력해 주세요.",
                    color = RedError,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "가입할 때 등록한 이름과 이메일을 입력해 주세요.",
                color = GrayText,
                style = MaterialTheme.typography.labelSmall
            )

            // 조회 실패 메시지.
            // 이름과 이메일 중 어느 쪽이 틀렸는지는 알려주지 않는다.
            // (가입 정보를 추측당하지 않도록)
            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    errorMessage,
                    color = RedError,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.weight(1f))

            // 아이디 찾기 버튼
            AionPrimaryButton(
                text = if (isLoading) "찾는 중..." else "아이디 찾기",
                enabled = !isLoading,
                onClick = {
                    when {
                        name.isBlank() -> nameBlankError = true
                        email.isBlank() -> emailBlankError = true
                        else -> onFindSuccess(name, email)
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_7")
@Composable
fun IdFindScreenPreview() {
    AionTheme { IdFindScreen() }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Normal else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) White else GrayText,
            fontWeight = FontWeight.SemiBold
        )
    }
}