package com.example.aion_app.ui.screen.kids

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.aion_app.R
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.AionTextValue
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.DarkHover
import com.example.aion_app.ui.theme.GreyNormal
import com.example.aion_app.ui.theme.Light
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.White

// ============================================================
// 아동용 마이페이지 — 시안 1p ("내 정보")
// ============================================================
// 시안 프레임 930 x 582. 치수는 전부 PDF 벡터에서 실측한 값이다.
//
// 교사용 MyPageScreen 과 항목 구성은 비슷하지만
//   - 프레임(930x582) / 본문 폭(320) / 행 높이(77) 가 전부 다르고
//   - 하단 탭이 없다 (아동용에는 하단 내비게이션 자체가 없음)
// 그래서 아동용/교사용 분리 원칙대로 별도 화면으로 둔다.
//
// ⚠ 시안 1p 에는 '아이디/비밀번호 찾기'가 있지만 넣지 않았다.
//   아이디 찾기·비밀번호 재설정은 가입 이메일로 진행되는데
//   아동 계정에는 아이가 쓸 수 있는 이메일이 없다. (교사용 화면에만 둔다)
//   → 아동용 메뉴는 비밀번호 변경 / 로그아웃 두 개.
//
// 이 파일이 쓰는 색 (Color.kt 토큰)
//   구분선        GreyNormal       #C2C8CD
//   라벨          GreyNormalActive #9BA0A4
//   값 / 이름     AionTextValue    #3A4D5F
//   메뉴 텍스트   AionTextDark     #2D3C4A
//   화살표        DarkHover        #3C598E
//   버튼          Normal           #6495ED
// ============================================================

// ---------- 마이페이지 공통 치수 (시안 실측) ----------
// 로그인/회원가입의 KidsContentWidth(329) 와 값이 다르다. 시안이 실제로 다름.
internal val KidsInfoWidth      = 320.dp   // 본문 폭 (x = 305 ~ 625)
internal val KidsInfoRowHeight  = 77.dp    // 이름/성별/생년월일 한 줄
internal val KidsInfoMenuHeight = 76.dp    // 1p 메뉴 한 줄 (+ 구분선 1dp = 77)

// 값(오른쪽)은 구분선 끝(625)이 아니라 601 에서 끝난다 — 시안 실측값.
internal val KidsInfoValueInset = 24.dp

// ============================================================
// 시안 1p — 마이페이지 메인
// ============================================================
@Composable
fun KidsMyPageScreen(
    userName: String = "김지우",
    userGender: String = "남",
    userAge: Int = 7,
    profileImageUri: Uri? = null,
    onBackClick: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    onChangePasswordClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {
    KidsScreenFrame(
        topBar = { AionTopBar(title = "내 정보", onBackClick = onBackClick) },
        contentWidth = KidsInfoWidth
    ) {
        Spacer(Modifier.height(28.dp))

        KidsProfileAvatar(imageUri = profileImageUri, size = 108.dp)

        Spacer(Modifier.height(20.dp))

        Text(
            text = userName,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = AionTextValue
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "$userGender · ${userAge}세",
            fontSize = 14.sp,
            color = AionTextValue
        )

        Spacer(Modifier.height(20.dp))

        // 내 정보 수정 (100 x 35)
        Box(
            modifier = Modifier
                .size(width = 100.dp, height = 35.dp)
                .clip(RoundedCornerShape(KidsCorner))
                .background(Normal)
                .clickable { onEditProfileClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "내 정보 수정",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = White
            )
        }

        Spacer(Modifier.height(30.dp))

        // ===== 메뉴 =====
        // 시안에는 첫 항목 위 구분선이 없다 (교사용과 다른 점)
        KidsMenuItem("비밀번호 변경", onChangePasswordClick)
        KidsInfoDivider()
        KidsMenuItem("로그아웃", onLogoutClick)
        KidsInfoDivider()

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KidsMenuItem(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(KidsInfoMenuHeight)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = AionTextDark,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = DarkHover,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ============================================================
// 마이페이지 3개 화면이 함께 쓰는 부품
// ============================================================

/** 원형 프로필 사진. 사진이 없으면 기본 이미지. */
@Composable
internal fun KidsProfileAvatar(
    imageUri: Uri?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val painter: Painter = if (imageUri != null) {
        rememberAsyncImagePainter(model = imageUri)
    } else {
        painterResource(id = R.drawable.mypage_profile_default)
    }

    Image(
        painter = painter,
        contentDescription = "프로필 사진",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Light)
    )
}

/** 본문 폭 전체를 가로지르는 구분선 */
@Composable
internal fun KidsInfoDivider() {
    HorizontalDivider(color = GreyNormal, thickness = 1.dp)
}

// ============================================================
// Preview — 시안 프레임(930 x 582)
// ============================================================
@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "1p 마이페이지")
@Composable
private fun KidsMyPageScreenPreview() {
    AionTheme { KidsMyPageScreen() }
}