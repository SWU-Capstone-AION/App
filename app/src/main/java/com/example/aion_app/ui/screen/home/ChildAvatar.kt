package com.example.aion_app.ui.home

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

/**
 * 아이 이름에 맞는 임시 프로필 사진을 고른다.
 * 나중에 서버에서 사진을 받아오게 되면 이 함수만 고치면 된다.
 */
private fun tempChildPhoto(childName: String?): Int = when (childName) {
    "김지우" -> R.drawable.mypage_profile_default
    "이주미" -> R.drawable.child_jumi
    "전소미" -> R.drawable.child_somi
    else -> R.drawable.mypage_profile_default
}

@Composable
fun ChildAvatar(
    size: Dp,
    cornerRadius: Dp,
    childName: String? = null,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = tempChildPhoto(childName)),
        contentDescription = "아동 프로필 사진",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
    )
}