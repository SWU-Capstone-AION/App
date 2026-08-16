package com.example.aion_app.ui.screen.password

import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable

@Composable
fun PasswordFindScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onBackClick: () -> Unit = {},
    onFindSuccess: (id: String) -> Unit = {},
    onSwitchToIdFind: () -> Unit = {}
) {
    var id by remember { mutableStateOf("") }
    var idBlankError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { AionTopBar(title = "비밀번호 찾기", onBackClick = onBackClick) },
        bottomBar = { AionBottomNavBar() }
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
                    selected = false,
                    onClick = onSwitchToIdFind,
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    text = "비밀번호 찾기",
                    selected = true,
                    onClick = { },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(40.dp))

            Text("가입한 아이디", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            AionTextField(
                value = id,
                onValueChange = {
                    id = it
                    idBlankError = false
                },
                isError = idBlankError || errorMessage != null
            )

            // 아이디가 존재하는지 여부는 알려주지 않는다.
            // (가입된 계정을 추측당하지 않도록)
            val message = when {
                idBlankError -> "아이디를 입력해 주세요."
                errorMessage != null -> errorMessage
                else -> null
            }
            if (message != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    message,
                    color = RedError,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "가입할 때 등록한 이메일로 비밀번호 재설정 링크를 보내드립니다.",
                color = GrayText,
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(Modifier.weight(1f))

            AionPrimaryButton(
                text = if (isLoading) "메일 보내는 중..." else "비밀번호 찾기",
                enabled = !isLoading,
                onClick = {
                    if (id.isBlank()) {
                        idBlankError = true
                    } else {
                        onFindSuccess(id)
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
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

@Preview(showBackground = true, device = "id:pixel_7")
@Composable
fun PasswordFindScreenPreview() {
    AionTheme { PasswordFindScreen() }
}