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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.component.*
import com.example.aion_app.ui.theme.*

// 아이디 찾기는 로그인 전에 쓰는 기능이라 users 컬렉션을 로그인 없이 조회해야 한다.
// 그런데 그걸 허용하면 전체 회원 정보(아동 프로필 포함)가 노출되므로
// 보안 규칙에서 막아두었다.
//
// 서버(Django)에 조회 API가 생기면 아래 TODO 자리를 되살리면 된다.
// 그때까지는 검색을 막고 안내만 보여준다.
private const val ID_FIND_READY = false

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
                    .background(GreyLightHover)
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

            if (!ID_FIND_READY) {
                PreparingCard(onSwitchToPasswordFind = onSwitchToPasswordFind)
                return@Column
            }

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

// ============================================
// 준비 중 안내
// ============================================
@Composable
private fun PreparingCard(onSwitchToPasswordFind: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = "아이디 찾기는\n준비 중입니다",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "조금만 기다려 주세요.\n비밀번호 찾기는 지금 이용하실 수 있어요.",
            fontSize = 14.sp,
            color = GrayText,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.weight(1f))

        AionPrimaryButton(
            text = "비밀번호 찾기",
            onClick = onSwitchToPasswordFind
        )

        Spacer(Modifier.height(16.dp))
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