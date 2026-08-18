package com.example.aion_app.ui.screen.kids

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.Dark
import com.example.aion_app.ui.theme.GreyLightActive
import com.example.aion_app.ui.theme.GreyNormalActive
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.LightHover
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.White

// ============================================================
// 아동용 전용 부품 모음
// ============================================================
// 교사용 화면이 쓰는 AionTextField / AionPasswordField / AionPrimaryButton 은
// 높이가 각각 56dp / 52dp 로 고정돼 있어 아동용 시안(50dp)과 맞지 않는다.
// 공용 컴포넌트를 고치면 은서님 비밀번호 화면까지 영향이 가므로,
// 아동용만 쓰는 부품을 여기 따로 둔다. (공용 파일은 건드리지 않음)
//
// 시안 기준: 프레임 930 x 582 / 본문 폭 329
//
// 이 파일이 쓰는 색 (Color.kt 토큰)
//   입력칸·미선택 배경    GreyLightActive   #ECEEF0
//   플레이스홀더·안내문구  GreyNormalActive  #9BA0A4
//   선택 상태             LightHover        #E8EFFC
//   테두리·구체 글로우     LightActive       #CFDEF9
//   본문 텍스트           AionTextDark      #2D3C4A
// ============================================================

// ---------- 시안 사이즈 (여기 값만 바꾸면 아동용 전체에 반영됨) ----------
// 모두 '시안 프레임(930 x 582) 기준' 값이다.
// 실제 기기 크기에 맞추는 일은 KidsDesignScale 이 density 를 조정해 처리한다.
const val KidsDesignFrameWidth  = 930f   // 피그마 프레임 가로
const val KidsDesignFrameHeight = 582f   // 피그마 프레임 세로

val KidsContentWidth = 329.dp    // 아이디 입력칸 등 '긴 것'
val KidsItemHeight   = 50.dp     // 모든 입력칸·버튼 공통 높이
val KidsToggleWidth  = 150.dp    // 교사용 / 아동용 토글
val KidsToggleGap    = KidsContentWidth - (KidsToggleWidth * 2)   // = 29dp
val KidsConfirmWidth = 60.dp     // 회원가입 '확인' 버튼
val KidsCorner       = 10.dp     // 입력칸·버튼 모서리
val KidsPillCorner   = 25.dp     // 선택지(알약) 모서리 = 높이/2

// ============================================================
// 시안 스케일 래퍼
// ============================================================
// 시안은 930 x 582 프레임인데 실제 기기는 크기도 비율도 제각각이다.
// 그래서 "화면이 930 x 582 인 것처럼" density 를 바꿔 하위 트리에 내려준다.
// → 안에 쓰인 모든 dp / sp 가 알아서 시안 비율로 확대/축소되고,
//   화면 코드는 치수마다 * scale 을 곱할 필요 없이 시안 숫자를 그대로 쓰면 된다.
//
// 배율은 폭 기준과 높이 기준 중 '작은 쪽'을 쓴다 (contain).
// 폭만 보면 세로 화면에서 프레임이 아래로 늘어나 버튼이 화면 끝까지 내려간다.
// 작은 쪽을 쓰면 어떤 비율에서도 시안 모양이 유지되고 남는 부분은 여백으로 빠진다.
// 덕분에 회전을 강제로 막지 않아도 레이아웃이 깨지지 않는다.
// (targetSdk 36 부터 sw600dp 이상 기기에서는 회전 고정이 무시되므로 이 대비가 필요하다)
//
// fontScale 은 원래 값을 그대로 넘기므로 사용자의 '글꼴 크기' 접근성 설정도 계속 반영된다.
//
// 가운데 329dp 열이 필요한 화면(로그인/회원가입)은 KidsScreenFrame 을,
// 배경이 화면 전체를 덮는 화면(홈/호흡)은 이 함수를 직접 쓴다.
// 여백 색은 호출부에서 modifier 로 지정한다. (예: Modifier.background(Light))
@Composable
fun KidsDesignScale(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val base = LocalDensity.current

        // "1 디자인dp 당 몇 px" = 새 density
        val scaledDensity = Density(
            density = minOf(
                this.constraints.maxWidth / KidsDesignFrameWidth,
                this.constraints.maxHeight / KidsDesignFrameHeight
            ),
            fontScale = base.fontScale
        )

        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 시안 프레임과 정확히 같은 크기의 영역. 화면 가운데에 놓인다.
                Box(
                    modifier = Modifier.size(
                        width = KidsDesignFrameWidth.dp,
                        height = KidsDesignFrameHeight.dp
                    ),
                    content = content
                )
            }
        }
    }
}

// ============================================================
// 화면 뼈대 (로그인 / 회원가입용)
// ============================================================
// bottomButton 을 넘기면 버튼이 하단에 고정되고 본문만 스크롤된다.
// (시안 5p에서 선택지 목록이 '다음' 버튼 뒤로 잘려 들어가는 모습 그대로)
@Composable
fun KidsScreenFrame(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomButton: (@Composable () -> Unit)? = null,
    bottomPadding: Dp = 32.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    // 프레임 바깥 여백도 본문과 같은 흰색으로 채워, 세로 화면에서 배경이 비치지 않게 한다
    KidsDesignScale(modifier = modifier.background(White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(White)
        ) {
            topBar?.invoke()

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.width(KidsContentWidth),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = content
                    )
                }

                if (bottomButton != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            // 스크롤된 선택지가 버튼 뒤로 비치지 않도록 흰 배경을 깔아준다
                            .background(White)
                            .padding(top = 12.dp, bottom = bottomPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        bottomButton()
                    }
                }
            }
        }
    }
}

// ============================================================
// 입력칸 (329 x 50)
// ============================================================
// Material3 OutlinedTextField 는 최소 높이가 56dp 라 .height(50.dp) 를 줘도
// 글자가 잘린다. 그래서 BasicTextField 로 직접 그린다.
@Composable
fun KidsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    width: Dp = KidsContentWidth,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 15.sp, color = AionTextDark),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(BluePrimary),
        modifier = modifier
            .width(width)
            .height(KidsItemHeight)
    ) { innerTextField ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(KidsCorner))
                .background(GreyLightActive)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontSize = 15.sp, color = GreyNormalActive)
                }
                innerTextField()
            }
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                trailingIcon()
            }
        }
    }
}

// ============================================================
// 비밀번호 입력칸
// ============================================================
// 시안에는 눈 아이콘이 없어서 기본값을 false 로 뒀다.
// 표시/숨김 토글이 필요하면 showToggle = true 만 넘기면 된다.
@Composable
fun KidsPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    width: Dp = KidsContentWidth,
    showToggle: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }

    KidsTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        width = width,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (visible) VisualTransformation.None
        else PasswordVisualTransformation(),
        trailingIcon = if (!showToggle) null else {
            {
                Image(
                    painter = painterResource(
                        if (visible) R.drawable.pw_visible else R.drawable.pw_masked
                    ),
                    contentDescription = if (visible) "비밀번호 숨기기" else "비밀번호 보기",
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { visible = !visible }
                )
            }
        }
    )
}

// ============================================================
// 기본 버튼 (329 x 50)
// ============================================================
// isPrimary = true  → 파랑 배경 + 흰 글씨 (로그인 / 다음)
// isPrimary = false → 회색 배경 + 진한 글씨 (회원가입)
@Composable
fun KidsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true,
    width: Dp = KidsContentWidth
) {
    val background = when {
        !isPrimary -> GreyLightActive
        enabled -> BluePrimary
        else -> BluePrimary.copy(alpha = 0.6f)
    }
    val contentColor = when {
        !isPrimary && enabled -> AionTextDark
        !isPrimary -> GreyNormalActive
        else -> White
    }

    Box(
        modifier = modifier
            .width(width)
            .height(KidsItemHeight)
            .clip(RoundedCornerShape(KidsCorner))
            .background(background)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

// ============================================================
// 교사용 / 아동용 토글 (150 x 50, 사이 간격 29)
// ============================================================
@Composable
fun KidsTypeToggle(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.width(KidsContentWidth),
        horizontalArrangement = Arrangement.spacedBy(KidsToggleGap)
    ) {
        KidsToggleButton("교사용", selected == "교사용") { onSelect("교사용") }
        KidsToggleButton("아동용", selected == "아동용") { onSelect("아동용") }
    }
}

@Composable
private fun KidsToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(KidsToggleWidth)
            .height(KidsItemHeight)
            .clip(RoundedCornerShape(KidsCorner))
            .background(if (isSelected) LightHover else GreyLightActive)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = AionTextDark
        )
    }
}

// ============================================================
// 선택지 알약 (329 x 50, 완전 둥근 모서리)
// ============================================================
@Composable
fun KidsSelectablePill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = KidsContentWidth
) {
    Box(
        modifier = modifier
            .width(width)
            .height(KidsItemHeight)
            .clip(RoundedCornerShape(KidsPillCorner))
            .background(if (isSelected) LightHover else GreyLightActive)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = AionTextDark
        )
    }
}

// ============================================================
// 왼쪽 정렬 라벨 ("아이디", "이름을 입력해주세요." 등)
// ============================================================
@Composable
fun KidsSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = AionTextDark,
        modifier = modifier.fillMaxWidth()
    )
}

// ============================================================
// 홈 / 호흡 화면 공용 부품
// ============================================================

// 시안 실측값 (930 x 582 기준)
val KidsScreenPadding = 20.dp     // 로고·마이페이지 바깥 여백
val KidsOrbMaxSize    = 244.dp    // 구체 최대 지름 (호흡 '내쉬어요' 시점)
val KidsOrbHomeScale  = 0.78f     // 홈 화면 구체 크기 비율

// 글로우가 구체보다 몇 배 넓게 퍼지는지. 퍼짐 정도는 이 값 하나로 조절한다.
private const val KidsOrbGlowRatio = 2.1f

// ------------------------------------------------------------
// 구체(풍선)
// ------------------------------------------------------------
// 본체는 sphere.png, 글로우만 코드로 그린다.
// 호흡 화면에서 구체 크기가 실시간으로 변하는데 글로우까지 PNG 로 만들면
// 확대할 때 가장자리가 뭉개지기 때문이다.
//
// 글로우 색: 홈 = LightActive(#CFDEF9) / 호흡 = 흰색
@Composable
fun KidsOrb(
    scale: Float,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    size: Dp = KidsOrbMaxSize
) {
    val glowColor = if (onDark) White else LightActive

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // requiredSize 라서 부모(size)보다 커도 잘리지 않고,
        // 바깥 레이아웃(아래 텍스트 위치 등)도 밀지 않는다.
        Box(
            modifier = Modifier
                .requiredSize(size * KidsOrbGlowRatio)
                .scale(scale)
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to glowColor.copy(alpha = if (onDark) 0.55f else 0.95f),
                            0.30f to glowColor.copy(alpha = if (onDark) 0.34f else 0.55f),
                            0.55f to glowColor.copy(alpha = if (onDark) 0.16f else 0.26f),
                            0.78f to glowColor.copy(alpha = 0.06f),
                            1.00f to Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Image(
            painter = painterResource(R.drawable.sphere),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .scale(scale)
        )
    }
}

// ------------------------------------------------------------
// 상단 바 (AION 로고 + 구슬주머니 + 마이페이지)
// ------------------------------------------------------------
// points 가 null 이면 구슬주머니를 숨긴다 (호흡 화면).
// onDark 는 배경이 어두운 호흡 화면용 — 마이페이지 아이콘을 흰색 버전으로 바꾼다.
@Composable
fun BoxScope.KidsHomeTopBar(
    points: Int?,
    onProfileClick: () -> Unit,
    onDark: Boolean = false
) {
    // 로고와 마이페이지를 한 Row 에 묶어야 로고 높이가 얼마든 세로 가운데가 맞는다.
    // (각각 top padding 을 주면 로고 에셋이 바뀔 때마다 다시 맞춰야 한다)
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .padding(horizontal = KidsScreenPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Image(
            painter = painterResource(R.drawable.logo_text),
            contentDescription = "AION",
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(72.dp)
        )

        Image(
            painter = painterResource(
                if (onDark) R.drawable.mypage_white else R.drawable.mypage_active
            ),
            contentDescription = "내 정보",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(26.dp)
                .clickable { onProfileClick() }
        )
    }

    if (points != null) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 30.dp, top = 78.dp),
            contentAlignment = Alignment.Center
        ) {
            // 배지 뒤 그라데이션 블러. requiredSize 로 배지보다 넓게 퍼진다.
            Box(
                modifier = Modifier
                    .requiredSize(width = 210.dp, height = 130.dp)
                    .background(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                0.00f to Normal.copy(alpha = 0.30f),
                                0.40f to Normal.copy(alpha = 0.15f),
                                0.70f to Normal.copy(alpha = 0.05f),
                                1.00f to Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(White)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // TODO: 젤리 아이콘 에셋 받으면 Text(이모지) → Image 로 교체
                Text("🍬", fontSize = 20.sp)
                Text(
                    text = "$points",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Dark
                )
            }
        }
    }
}

// ------------------------------------------------------------
// "도움이 필요해요" 버튼 (329 x 50 — 다른 버튼과 동일)
// ------------------------------------------------------------
// 배경과 테두리는 홈/호흡 공통이고, onDark 는 글자색만 구분한다.
//
// ⚠ 홈 화면 배경색은 디자인 시트에 값이 비어 있어 호흡 화면과 같은 LightHover 로 뒀다.
//   디자인팀 확인 후 아래 background 한 줄만 바꾸면 된다.
@Composable
fun KidsHelpButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false
) {
    Row(
        modifier = modifier
            .width(KidsContentWidth)
            .height(KidsItemHeight)
            .clip(RoundedCornerShape(KidsCorner))
            .background(LightHover)
            .border(1.dp, LightActive, RoundedCornerShape(KidsCorner))
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.hand_icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "도움이 필요해요",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (onDark) Dark else Normal
        )
    }
}