package com.example.aion_app.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.GreyLightHover
import com.example.aion_app.ui.theme.GreyNormalActive
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

// ============================================
// 아동 등록하기 바텀시트
// ============================================
// 홈에서 [+ 등록하기]를 누르면 화면 아래에서 올라온다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildRegisterBottomSheet(
    onDismiss: () -> Unit,
    onSearchByIdClick: () -> Unit,
    onCreateAccountClick: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "아동 등록하기",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            RegisterOptionItem(
                icon = Icons.Default.Search,
                title = "아이디로 찾기",
                description = "아동이 쓰고 있는 아이디를 검색해서 연결해요.",
                onClick = onSearchByIdClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            RegisterOptionItem(
                icon = Icons.Default.PersonAdd,
                title = "새 계정 만들기",
                description = "선생님이 대신 가입해줄 수 있어요.",
                onClick = onCreateAccountClick
            )
        }
    }
}

@Composable
private fun RegisterOptionItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GreyLightHover)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 아이콘 (흰 동그라미 안에)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Normal,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = GreyNormalActive
            )
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_7")
@Composable
private fun ChildRegisterBottomSheetPreview() {
    AionTheme {
        ChildRegisterBottomSheet(
            onDismiss = {},
            onSearchByIdClick = {},
            onCreateAccountClick = {}
        )
    }
}