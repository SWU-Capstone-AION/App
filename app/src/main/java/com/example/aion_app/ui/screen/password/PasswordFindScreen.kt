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
    onBackClick: () -> Unit = {},
    onFindSuccess: (id: String) -> Unit = {},
    onSwitchToIdFind: () -> Unit = {}
) {
    var id by remember { mutableStateOf("") }
    var idError by remember { mutableStateOf(false) }

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
                    idError = false
                },
                isError = idError
            )
            if (idError) {
                Text(
                    "존재하지 않는 아이디예요.",
                    color = RedError,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.weight(1f))

            AionPrimaryButton(
                text = "비밀번호 찾기",
                onClick = {
                    if (id.isBlank()) {
                        idError = true
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