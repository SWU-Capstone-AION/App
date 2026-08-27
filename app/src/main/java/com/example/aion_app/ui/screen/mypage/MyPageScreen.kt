package com.example.aion_app.ui.screen.mypage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.component.AionBottomNavBar
import com.example.aion_app.ui.theme.Light
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White
import android.net.Uri
import androidx.compose.ui.graphics.painter.Painter
import coil.compose.rememberAsyncImagePainter
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.DarkHover

@Composable
fun MyPageScreen(
    userName: String = "김슈니",
    userGender: String = "남",
    userAge: Int = 7,
    // 교사는 성별·나이 대신 역할을 보여준다
    isTeacher: Boolean = false,
    profileImageUri: Uri? = null,
    onEditProfileClick: () -> Unit = {},
    onFindIdPasswordClick: () -> Unit = {},
    onChangePasswordClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onTabSelect: (String) -> Unit = {}
) {
    Scaffold(
        topBar = { MyPageTopBar() },
        bottomBar = { AionBottomNavBar(selected = "mypage", onSelect = onTabSelect) },
        containerColor = White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            val painter: Painter = if (profileImageUri != null) {
                rememberAsyncImagePainter(model = profileImageUri)
            } else {
                painterResource(id = R.drawable.mypage_profile_default)
            }

            Image(
                painter = painter,
                contentDescription = "프로필 이미지",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Light)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 이름
            Text(
                text = userName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 교사: "교사" / 아동: 성별 · 나이
            Text(
                text = if (isTeacher) "교사" else "$userGender · ${userAge}세",
                fontSize = 14.sp,
                color = GrayText
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 내 정보 수정 버튼
            Button(
                onClick = onEditProfileClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Normal
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "내 정보 수정",
                    fontSize = 13.sp,
                    color = White
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)

            // 메뉴 리스트
            MenuItem(text = "아이디/비밀번호 찾기", onClick = onFindIdPasswordClick)
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)

            MenuItem(text = "비밀번호 변경", onClick = onChangePasswordClick)
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)

            MenuItem(text = "로그아웃", onClick = onLogoutClick)
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
        }
    }
}

@Composable
private fun MyPageTopBar() {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(White)
        ) {
            Text(
                text = "마이페이지",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
    }
}

@Composable
private fun MenuItem(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = DarkHover
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7", name = "교사")
@Composable
fun MyPageScreenTeacherPreview() {
    MaterialTheme {
        MyPageScreen(userName = "김지연", isTeacher = true)
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7", name = "아동")
@Composable
fun MyPageScreenPreview() {
    MaterialTheme {
        MyPageScreen()
    }
}