package com.example.aion_app.ui.screen.mypage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.component.AionBottomNavBar
import com.example.aion_app.ui.theme.BlueLight
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.GrayBackground
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

import android.net.Uri
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.graphics.painter.Painter

@Composable
fun MyInfoScreen(
    userName: String = "김슈니",
    userGender: String = "남자",
    userBirthDate: String = "2019.12.21",
    sensitiveStimuli: List<String> = listOf("시각", "청각"),
    behaviorTraits: List<String> = listOf("손이나 팔을 흔들어요", "박수치듯 손을 맞부딪혀요"),
    profileImageUri: Uri? = null,
    onBackClick: () -> Unit = {},
    onEditClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            AionTopBar(
                title = "내 정보",
                onBackClick = onBackClick
            )
        },
        bottomBar = { AionBottomNavBar(selected = "mypage") },
        containerColor = White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 스크롤 가능한 콘텐츠 영역
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // 프로필 사진
                InfoRowWithImage(label = "프로필 사진", imageUri = profileImageUri)
                InfoDivider()

                // 이름
                InfoRow(label = "이름", value = userName)
                InfoDivider()

                // 성별
                InfoRow(label = "성별", value = userGender)
                InfoDivider()

                // 생년월일
                InfoRow(label = "생년월일", value = userBirthDate)
                InfoDivider()

                Spacer(modifier = Modifier.height(16.dp))

                // 민감하게 반응하는 감각 자극
                TagSection(
                    title = "민감하게 반응하는 감각 자극",
                    tags = sensitiveStimuli
                )

                InfoDivider()

                Spacer(modifier = Modifier.height(16.dp))

                // 주로 나타나는 행동 특성
                TagSection(
                    title = "주로 나타나는 행동 특성",
                    tags = behaviorTraits
                )

                Spacer(modifier = Modifier.height(32.dp))
            }

            // 수정하기 버튼 (고정)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(White)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = onEditClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BluePrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "수정하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = GrayText,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun InfoRowWithImage(label: String, imageUri: Uri? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = GrayText,
            modifier = Modifier.weight(1f)
        )

        val painter: Painter = if (imageUri != null) {
            rememberAsyncImagePainter(model = imageUri)
        } else {
            painterResource(id = R.drawable.mypage_profile_default)
        }

        Image(
            painter = painter,
            contentDescription = "프로필 사진",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(BlueLight)
        )
    }
}

@Composable
private fun TagSection(title: String, tags: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = GrayText,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        tags.forEach { tag ->
            TagChip(text = tag)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(GrayBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = TextPrimary
        )
    }
}

@Composable
private fun InfoDivider() {
    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7")
@Composable
fun MyInfoScreenPreview() {
    MaterialTheme {
        MyInfoScreen()
    }
}