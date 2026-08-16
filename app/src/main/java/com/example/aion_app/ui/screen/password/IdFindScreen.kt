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

@Composable
fun IdFindScreen(
    onBackClick: () -> Unit = {},
    onFindSuccess: (email: String) -> Unit = {},
    onSwitchToPasswordFind: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }

    var nameError by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }
    var codeError by remember { mutableStateOf(false) }
    var isEmailVerified by remember { mutableStateOf(false) }

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
                    nameError = false
                },
                isError = nameError
            )
            if (nameError) {
                Text(
                    "존재하지 않는 이름이에요.",
                    color = RedError,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(20.dp))

            // 이메일 + 인증 버튼
            Text("이메일", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AionTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            emailError = false
                        },
                        isError = emailError
                    )
                }
                Button(
                    onClick = {
                        // 가짜 인증 로직 - 이메일이 비어있지 않으면 성공
                        if (email.isNotBlank()) {
                            isEmailVerified = true
                        } else {
                            emailError = true
                        }
                    },
                    modifier = Modifier.height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEmailVerified) GrayBackground else BluePrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "인증",
                        color = if (isEmailVerified) GrayText else White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (emailError) {
                Text(
                    "존재하지 않는 이메일이에요.",
                    color = RedError,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(20.dp))

            // 인증번호 입력
            Text("인증번호", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            AionTextField(
                value = verificationCode,
                onValueChange = {
                    verificationCode = it
                    codeError = false
                },
                isError = codeError
            )
            if (codeError) {
                Text(
                    "인증번호가 틀렸습니다.",
                    color = RedError,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.weight(1f))

            // 아이디 찾기 버튼
            AionPrimaryButton(
                text = "아이디 찾기",
                onClick = {
                    // 가짜 검증 - 필드가 비었을 때만 에러
                    when {
                        name.isBlank() -> nameError = true
                        email.isBlank() -> emailError = true
                        verificationCode.isBlank() -> codeError = true
                        else -> onFindSuccess(email)
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
            .background(if (selected) BluePrimary else Color.Transparent)
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