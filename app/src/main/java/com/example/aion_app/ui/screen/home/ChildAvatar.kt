package com.example.aion_app.ui.home   // ← 지금 파일에 적힌 그대로

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.example.aion_app.R

@Composable
fun ChildAvatar(
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.mypage_profile_default),
        contentDescription = "아동 프로필 사진",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
    )
}