package com.example.aion_app.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.aion_app.data.auth.LinkedChild
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.GreyLightHover
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.Red
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

// ============================================
// 담당 아동 목록
// ============================================
// 마이페이지에서 진입. 연결된 아동을 보고 연결을 해제할 수 있다.
@Composable
fun ChildListScreen(
    children: List<LinkedChild> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onBackClick: () -> Unit = {},
    onUnlink: (LinkedChild) -> Unit = {},
    onAddChildClick: () -> Unit = {},
) {
    // 삭제 확인 대화상자에 띄울 아동. null이면 안 뜬다.
    var childToUnlink by remember { mutableStateOf<LinkedChild?>(null) }

    Scaffold(
        topBar = { AionTopBar(title = "아동 목록", onBackClick = onBackClick) },
        containerColor = White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "우리반 아이들",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${children.size}명",
                    fontSize = 13.sp,
                    color = GrayText,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Normal,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                children.isEmpty() -> {
                    Text(
                        text = "아직 등록된 아동이 없어요",
                        fontSize = 14.sp,
                        color = GrayText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                    )
                }

                else -> {
                    children.forEach { child ->
                        ChildListItem(
                            child = child,
                            onUnlinkClick = { childToUnlink = child }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    fontSize = 12.sp,
                    color = Red
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            AddChildRow(onClick = onAddChildClick)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ===== 연결 해제 확인 =====
    val target = childToUnlink
    if (target != null) {
        UnlinkConfirmDialog(
            child = target,
            onConfirm = {
                onUnlink(target)
                childToUnlink = null
            },
            onDismiss = { childToUnlink = null }
        )
    }
}

@Composable
private fun ChildListItem(
    child: LinkedChild,
    onUnlinkClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .border(
                width = 1.dp,
                color = LightActive,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 프로필 자리
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE0E0E0))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = child.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${child.gender} · ${child.age}세",
                    fontSize = 12.sp,
                    color = GrayText,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = child.loginId,
                fontSize = 12.sp,
                color = GrayText
            )
        }

        // 연결 해제 버튼
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GreyLightHover)
                .clickable(onClick = onUnlinkClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "연결 해제",
                tint = GrayText,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AddChildRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .border(
                width = 1.dp,
                color = LightActive,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+ 아동 추가",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Normal
        )
    }
}

// ============================================
// 연결 해제 확인
// ============================================
// 아동 계정은 지워지지 않는다는 걸 분명히 알려준다.
@Composable
private fun UnlinkConfirmDialog(
    child: LinkedChild,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(White),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "${child.name} 학생을\n목록에서 삭제할까요?",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "아이의 계정은 그대로 남아 있어요.\n나중에 다시 연결할 수 있습니다.",
                fontSize = 12.sp,
                color = GrayText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .background(GreyLightHover)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "취소",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .background(Red)
                        .clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "삭제",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7", name = "목록")
@Composable
private fun ChildListScreenPreview() {
    AionTheme {
        ChildListScreen(
            children = listOf(
                LinkedChild("uid1", "Jiwoo_0517", "김지우", "남", 9),
                LinkedChild("uid2", "Jumi_0203", "이주미", "여", 9),
            )
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7", name = "비어 있음")
@Composable
private fun ChildListScreenEmptyPreview() {
    AionTheme { ChildListScreen(children = emptyList()) }
}