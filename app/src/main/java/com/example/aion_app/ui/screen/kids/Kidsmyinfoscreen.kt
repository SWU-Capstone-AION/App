package com.example.aion_app.ui.screen.kids

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.theme.AionTextValue
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.Darker
import com.example.aion_app.ui.theme.GreyLightHover
import com.example.aion_app.ui.theme.GreyNormalActive
import com.example.aion_app.ui.theme.LightHover

// ============================================================
// 아동용 내 정보 — 시안 2p (조회)
// ============================================================
// 세로 배치는 시안(930 x 906) 실측값 그대로다.
//   상단바 63 → 프로필 행(구분선 164) → 이름(241) → 성별(318) → 생년월일(396)
//   → 감각 자극 → 행동 특성 → 하단 고정 '수정하기'
//
// 시안 문서는 906/1197 처럼 세로로 길지만 실제 화면은 582 라
// 본문은 스크롤, 버튼은 하단 고정이다. (KidsScreenFrame 이 처리)
// ============================================================

// 구분선 다음 섹션 라벨까지 / 라벨에서 첫 알약까지 / 마지막 알약에서 구분선까지
private val SectionTopGap    = 28.dp
private val SectionLabelGap  = 8.dp
private val PillGap          = 10.dp
private val SectionBottomGap = 27.dp

@Composable
fun KidsMyInfoScreen(
    userName: String = "김슈니",
    userGender: String = "남자",
    userBirthDate: String = "2019.12.21",
    sensitiveStimuli: List<String> = listOf("시각", "청각"),
    behaviorTraits: List<String> = listOf("손이나 팔을 흔들어요", "박수치듯 손을 맞부딪혀요"),
    profileImageUri: Uri? = null,
    onBackClick: () -> Unit = {},
    onEditClick: () -> Unit = {}
) {
    KidsScreenFrame(
        topBar = { AionTopBar(title = "내 정보", onBackClick = onBackClick) },
        contentWidth = KidsInfoWidth,
        bottomButton = {
            KidsPrimaryButton(
                text = "수정하기",
                onClick = onEditClick,
                width = KidsInfoWidth
            )
        },
        bottomPadding = 20.dp
    ) {
        Spacer(Modifier.height(25.dp))

        // ===== 프로필 사진 =====
        Row(
            modifier = Modifier.fillMaxWidth().height(76.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KidsInfoLabel("프로필 사진", Modifier.weight(1f))
            KidsProfileAvatar(
                imageUri = profileImageUri,
                size = 68.dp,
                modifier = Modifier.padding(end = KidsInfoValueInset)
            )
        }
        KidsInfoDivider()

        // ===== 이름 / 성별 / 생년월일 =====
        KidsInfoRow(label = "이름", value = userName)
        KidsInfoDivider()

        KidsInfoRow(label = "성별", value = userGender)
        KidsInfoDivider()

        KidsInfoRow(label = "생년월일", value = userBirthDate)
        KidsInfoDivider()

        // ===== 감각 자극 =====
        Spacer(Modifier.height(SectionTopGap))
        KidsInfoLabel("민감하게 반응하는 감각 자극")
        Spacer(Modifier.height(SectionLabelGap))
        sensitiveStimuli.forEachIndexed { index, tag ->
            if (index > 0) Spacer(Modifier.height(PillGap))
            KidsInfoPill(text = tag)
        }
        Spacer(Modifier.height(SectionBottomGap))
        KidsInfoDivider()

        // ===== 행동 특성 =====
        Spacer(Modifier.height(SectionTopGap))
        KidsInfoLabel("주로 나타나는 행동 특성")
        Spacer(Modifier.height(SectionLabelGap))
        behaviorTraits.forEachIndexed { index, tag ->
            if (index > 0) Spacer(Modifier.height(PillGap))
            KidsInfoPill(text = tag)
        }

        // 하단 고정 버튼에 가리지 않도록 여백 확보
        Spacer(Modifier.height(100.dp))
    }
}

// ============================================================
// 조회 / 수정 화면이 함께 쓰는 부품
// ============================================================

/** 왼쪽 회색 라벨 */
@Composable
internal fun KidsInfoLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 15.sp,
        color = GreyNormalActive,
        modifier = modifier
    )
}

/** 라벨 + 오른쪽 값 한 줄 (77dp) */
@Composable
internal fun KidsInfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(KidsInfoRowHeight)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KidsInfoLabel(label, Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = AionTextValue,
            modifier = Modifier.padding(end = KidsInfoValueInset)
        )
    }
}

/**
 * 선택지 알약 (320 x 50)
 *
 * 회원가입의 KidsSelectablePill 과 배경색이 다르다.
 *   회원가입   미선택 #ECEEF0
 *   마이페이지 미선택 #F6F7F8   ← 시안 실측
 * 그래서 공용 부품을 고치지 않고 여기 따로 뒀다.
 *
 * onClick 이 null 이면 조회용(터치 불가).
 */
@Composable
internal fun KidsInfoPill(
    text: String,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .width(KidsInfoWidth)
            .height(KidsItemHeight)
            .clip(RoundedCornerShape(KidsPillCorner))
            .background(if (isSelected) LightHover else GreyLightHover)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = Darker
        )
    }
}

// ============================================================
// Preview — 시안 프레임(930 x 582)
// ============================================================
@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "2p 내 정보")
@Composable
private fun KidsMyInfoScreenPreview() {
    AionTheme { KidsMyInfoScreen() }
}