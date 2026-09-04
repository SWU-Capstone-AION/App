package com.example.aion_app.ui.screen.kids

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.screen.password.isValidPassword
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.GreyLight
import com.example.aion_app.ui.theme.GreyLightHover
import com.example.aion_app.ui.theme.GreyNormalActive
import com.example.aion_app.ui.theme.LightHover
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.RedError
import com.example.aion_app.ui.theme.White

// ============================================================
// 아동용 비밀번호 변경 (시안 4p ~ 15p)
// ============================================================
// 마이페이지 → '비밀번호 변경'으로 들어온다.
//
// 지금까지는 교사용 PasswordChangeCheckScreen / PasswordChangeScreen 을 그대로 썼는데,
// 그쪽은 폰 세로 기준 Scaffold + 하단 탭 구조라 태블릿에서 비율이 완전히 어긋났다.
// 아동용은 다른 아동 화면들과 같은 930x582 프레임 + KidsScreenFrame 으로 다시 그린다.
//
// 치수는 전부 PDF 벡터 실측값이다 (1pt = 1dp).
//   본문 폭      320  (x = 305 ~ 625)  ← 로그인의 329 가 아니라 마이페이지와 같은 320
//   입력칸/버튼  320 x 50
//   라벨         18sp Bold             ← 로그인의 15sp 보다 크다. 시안이 실제로 다름
//   안내 문구    10sp, 좌 여백 8
//   하단 버튼    y 496 ~ 546 (아래 여백 36)
//
// ⚠ 시안 상단바 제목이 4p~15p 에서도 "비밀번호 찾기"로 되어 있는데,
//   내용은 전부 '변경' 플로우다. 피그마 복붙 흔적으로 보여 "비밀번호 변경"으로 뒀다.
//   디자인 의도가 '찾기'가 맞다면 이 문자열만 바꾸면 된다.
//
// ⚠ 시안의 라벨 글자색은 #233453 (Darker) 인데, 다른 아동 화면들이 모두
//   AionTextDark(#2D3C4A) 를 쓰고 있어 맞췄다. 팀에서 통일하면 그때 같이 바꾼다.
// ============================================================

// ---------- 시안 실측 치수 ----------
private val KidsPwWidth        = 320.dp   // 본문 폭 (마이페이지와 동일)
private val KidsPwActionWidth  = 150.dp   // '다음에' / '변경' (9p 하단 2분할)
private val KidsPwActionGap    = 20.dp    // 두 버튼 사이 (455 → 475)
private val KidsPwBottomInset  = 36.dp    // 하단 버튼 아래 여백 (546 → 582)

// ============================================================
// 시안 4p ~ 8p — 본인 확인 (아이디 + 현재 비밀번호)
// ============================================================
@Composable
fun KidsPasswordChangeCheckScreen(
    onBackClick: () -> Unit = {},
    onNextClick: (id: String, currentPw: String) -> Unit = { _, _ -> }
) {
    var id by remember { mutableStateOf("") }
    var currentPw by remember { mutableStateOf("") }
    var pwError by remember { mutableStateOf(false) }
    var showIdNotFound by remember { mutableStateOf(false) }

    KidsScreenFrame(
        topBar = { AionTopBar(title = "비밀번호 변경", onBackClick = onBackClick) },
        contentWidth = KidsPwWidth,
        bottomPadding = KidsPwBottomInset,
        bottomButton = {
            KidsPasswordButton(
                text = "다음",
                background = Normal,
                textColor = White,
                onClick = {
                    when {
                        id.isBlank() -> showIdNotFound = true
                        !isValidPassword(currentPw) -> pwError = true
                        else -> onNextClick(id, currentPw)
                    }
                }
            )
        }
    ) {
        // 상단바 아래(63) → 첫 라벨(130)
        Spacer(Modifier.height(67.dp))

        KidsPasswordLabel("아이디")
        Spacer(Modifier.height(12.dp))
        KidsTextField(
            value = id,
            onValueChange = { id = it },
            width = KidsPwWidth
        )

        Spacer(Modifier.height(19.dp))

        KidsPasswordLabel("현재 비밀번호를 입력하세요")
        Spacer(Modifier.height(12.dp))
        KidsPasswordField(
            value = currentPw,
            onValueChange = {
                currentPw = it
                pwError = it.isNotEmpty() && !isValidPassword(it)
            },
            width = KidsPwWidth,
            trailingIcon = validCheckIcon(isValidPassword(currentPw))
        )

        Spacer(Modifier.height(8.dp))
        KidsPasswordHelper(
            text = "영문, 숫자 포함 8자 이상 입력해 주세요.",
            isError = pwError
        )

        // 하단 고정 버튼에 본문이 가리지 않도록
        Spacer(Modifier.height(120.dp))
    }

    // 시안 5p — 아이디가 없을 때
    if (showIdNotFound) {
        KidsPasswordDialog(
            message = "존재하지 않는 아이디입니다.",
            onConfirm = { showIdNotFound = false }
        )
    }
}

// ============================================================
// 시안 9p ~ 15p — 새 비밀번호 입력
// ============================================================
@Composable
fun KidsPasswordChangeScreen(
    onBackClick: () -> Unit = {},
    onChangeSuccess: () -> Unit = {}
) {
    var currentPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var currentPwError by remember { mutableStateOf(false) }
    var newPwError by remember { mutableStateOf(false) }
    var confirmPwError by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    KidsScreenFrame(
        topBar = { AionTopBar(title = "비밀번호 변경", onBackClick = onBackClick) },
        contentWidth = KidsPwWidth,
        bottomPadding = KidsPwBottomInset,
        bottomButton = {
            // 시안 9p: 150 + 20 + 150 = 320
            Row(
                modifier = Modifier.width(KidsPwWidth),
                horizontalArrangement = Arrangement.spacedBy(KidsPwActionGap)
            ) {
                KidsPasswordButton(
                    text = "다음에",
                    background = GreyLightHover,
                    textColor = AionTextDark,
                    width = KidsPwActionWidth,
                    onClick = onBackClick
                )
                KidsPasswordButton(
                    text = "변경",
                    background = LightHover,
                    textColor = AionTextDark,
                    width = KidsPwActionWidth,
                    onClick = {
                        val ok = isValidPassword(currentPw) &&
                                isValidPassword(newPw) &&
                                newPw == confirmPw
                        if (ok) {
                            showSuccess = true
                        } else {
                            if (!isValidPassword(currentPw)) currentPwError = true
                            if (!isValidPassword(newPw)) newPwError = true
                            if (newPw != confirmPw) confirmPwError = true
                        }
                    }
                )
            }
        }
    ) {
        // 상단바 아래(63) → 첫 라벨(110)
        Spacer(Modifier.height(47.dp))

        KidsPasswordLabel("현재 비밀번호를 입력하세요")
        Spacer(Modifier.height(12.dp))
        KidsPasswordField(
            value = currentPw,
            onValueChange = {
                currentPw = it
                currentPwError = it.isNotEmpty() && !isValidPassword(it)
            },
            width = KidsPwWidth,
            trailingIcon = validCheckIcon(isValidPassword(currentPw))
        )
        Spacer(Modifier.height(8.dp))
        KidsPasswordHelper(
            text = "영문, 숫자 포함 8자 이상 입력해 주세요.",
            isError = currentPwError
        )

        Spacer(Modifier.height(21.dp))

        KidsPasswordLabel("새 비밀번호를 입력하세요")
        Spacer(Modifier.height(12.dp))
        KidsPasswordField(
            value = newPw,
            onValueChange = {
                newPw = it
                newPwError = it.isNotEmpty() && !isValidPassword(it)
            },
            width = KidsPwWidth,
            trailingIcon = validCheckIcon(isValidPassword(newPw))
        )
        Spacer(Modifier.height(8.dp))
        KidsPasswordHelper(
            text = "영문, 숫자 포함 8자 이상 입력해 주세요.",
            isError = newPwError
        )

        Spacer(Modifier.height(21.dp))

        KidsPasswordLabel("새 비밀번호를 확인해 주세요")
        Spacer(Modifier.height(12.dp))
        KidsPasswordField(
            value = confirmPw,
            onValueChange = {
                confirmPw = it
                confirmPwError = it.isNotEmpty() && it != newPw
            },
            // 시안 9p 는 이 칸만 안내 문구가 칸 안에 들어간다 (10sp)
            placeholder = "변경한 비밀번호를 한 번 더 입력해 주세요",
            placeholderFontSize = 10.sp,
            width = KidsPwWidth,
            trailingIcon = validCheckIcon(confirmPw.isNotEmpty() && confirmPw == newPw)
        )
        if (confirmPwError) {
            Spacer(Modifier.height(8.dp))
            KidsPasswordHelper(text = "비밀번호를 다시 입력해 주세요", isError = true)
        }

        Spacer(Modifier.height(120.dp))
    }

    // 시안 15p — 변경 완료
    if (showSuccess) {
        KidsPasswordDialog(
            message = "비밀번호가 변경되었습니다.",
            onConfirm = {
                showSuccess = false
                onChangeSuccess()
            }
        )
    }
}

// ============================================================
// 공통 부품
// ============================================================

// 라벨 (18sp Bold, 왼쪽 정렬)
// KidsSectionLabel 은 로그인/회원가입 기준 15sp 라 여기서는 쓰지 않는다.
@Composable
private fun KidsPasswordLabel(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = AionTextDark,
        modifier = Modifier.fillMaxWidth()
    )
}

// 입력칸 아래 안내 문구. 시안 x = 313 → 본문 왼쪽에서 8 들여쓴다.
@Composable
private fun KidsPasswordHelper(text: String, isError: Boolean) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = if (isError) RedError else GreyNormalActive,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp)
    )
}

// 조건을 만족하면 칸 오른쪽에 초록 체크 (시안 15p)
private fun validCheckIcon(show: Boolean): (@Composable () -> Unit)? =
    if (!show) null else {
        {
            Image(
                painter = painterResource(R.drawable.pw_correct),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }

// 하단 버튼. 시안 색이 KidsPrimaryButton 의 두 가지(파랑/GreyLightHover)와 달라
// 배경·글자색을 직접 받는다.
//   다음   Normal(#6495ED) + 흰 글씨
//   다음에 GreyLightHover(#F6F7F8) + 진한 글씨
//   변경   LightHover(#E8EFFC) + 진한 글씨
@Composable
private fun KidsPasswordButton(
    text: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = KidsPwWidth
) {
    Box(
        modifier = modifier
            .width(width)
            .height(KidsItemHeight)
            .clip(RoundedCornerShape(KidsCorner))
            .background(background)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// 안내 팝업 (시안 5p / 15p)
// 카드 320 폭, 본문 영역 + 하단에 꽉 찬 '확인' 버튼. 바깥 탭으로는 닫히지 않는다.
@Composable
private fun KidsPasswordDialog(
    message: String,
    onConfirm: () -> Unit
) {
    // 딤이 화면 끝까지 닿도록 overlay 슬롯을 쓴다.
    // content 안에 두면 시안 프레임(930x582) 안까지만 깔린다.
    KidsDesignScale(overlay = {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0x33303A66))
                // 뒤쪽 입력칸이 눌리지 않도록 클릭을 흡수만 한다
                .clickable(enabled = true) { },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(KidsPwWidth)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GreyLight),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(129.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message,
                        fontSize = 16.sp,
                        color = AionTextDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(KidsItemHeight)
                        .background(Normal)
                        .clickable { onConfirm() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "확인",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }
        }
    })
}

// ============================================================
// Preview
// ============================================================
@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "본인 확인 (4p)")
@Composable
private fun KidsPasswordChangeCheckPreview() {
    AionTheme { KidsPasswordChangeCheckScreen() }
}

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "새 비밀번호 (9p)")
@Composable
private fun KidsPasswordChangePreview() {
    AionTheme { KidsPasswordChangeScreen() }
}

@Preview(showBackground = true, device = "spec:width=1204dp,height=753dp,dpi=340", name = "새 비밀번호 (실기기)")
@Composable
private fun KidsPasswordChangeTabletPreview() {
    AionTheme { KidsPasswordChangeScreen() }
}