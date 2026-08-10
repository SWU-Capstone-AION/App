package com.example.aion_app.ui.screen.kids

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.aion_app.R
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.screen.login.ChildProfile   // 교사용과 같은 데이터 모델을 그대로 사용
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.GreyLightActive
import com.example.aion_app.ui.theme.GreyNormalActive

// ============================================================
// 아동용 회원가입 — 시안 3p / 4p / 5p (한 화면에서 단계만 바뀜)
// ============================================================
// 데이터 모델(ChildProfile)과 ViewModel(SignUpViewModel)은 교사용 것을 그대로 재사용한다.
// 화면(레이아웃)만 아동용 시안 사이즈로 새로 그린 것.
@Composable
fun KidsProfileSetupScreen(
    onBackClick: () -> Unit = {},
    onComplete: (ChildProfile) -> Unit = {}
) {
    var step by remember { mutableIntStateOf(1) }
    var profile by remember { mutableStateOf(ChildProfile()) }

    // 단계 안에서는 뒤로가기 → 이전 단계로, 1단계면 바깥(이전 화면)으로
    val handleBack: () -> Unit = { if (step > 1) step -= 1 else onBackClick() }

    when (step) {
        1 -> KidsBasicInfoStep(
            current = profile,
            onBackClick = handleBack,
            onNext = { updated -> profile = updated; step = 2 }
        )
        2 -> KidsSensoryStep(
            currentTraits = profile.sensoryTraits,
            onBackClick = handleBack,
            onNext = { traits -> profile = profile.copy(sensoryTraits = traits); step = 3 }
        )
        3 -> KidsBehaviorStep(
            currentBehaviors = profile.behaviors,
            onBackClick = handleBack,
            onNext = { behaviors ->
                val done = profile.copy(behaviors = behaviors)
                profile = done
                onComplete(done)
            }
        )
    }
}

// ============================================================
// 단계 공용 뼈대 (상단바 + 제목 + 본문 + 하단 고정 '다음')
// ============================================================
@Composable
private fun KidsStepScaffold(
    title: String? = null,
    subtitle: String? = null,
    nextEnabled: Boolean,
    onBackClick: () -> Unit,
    onNext: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    KidsScreenFrame(
        topBar = { AionTopBar(title = "회원가입", onBackClick = onBackClick) },
        bottomButton = {
            KidsPrimaryButton(text = "다음", onClick = onNext, enabled = nextEnabled)
        }
    ) {
        Spacer(Modifier.height(48.dp))

        if (title != null) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AionTextDark,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(text = subtitle, fontSize = 12.sp, color = GreyNormalActive)
            }
            Spacer(Modifier.height(24.dp))
        }

        content()

        // 하단 고정 버튼에 가리지 않도록 여백 확보
        Spacer(Modifier.height(120.dp))
    }
}

// ============================================================
// 시안 3p — 이름 / 성별 / 생년월일
// ============================================================
@Composable
private fun KidsBasicInfoStep(
    current: ChildProfile,
    onBackClick: () -> Unit,
    onNext: (ChildProfile) -> Unit
) {
    var name by remember { mutableStateOf(current.name) }
    var gender by remember { mutableStateOf(current.gender) }
    var year by remember { mutableStateOf(current.birthYear) }
    var month by remember { mutableStateOf(current.birthMonth) }
    var day by remember { mutableStateOf(current.birthDay) }
    var showDatePicker by remember { mutableStateOf(false) }

    val birthChosen = year != null && month != null && day != null

    // 329 = 113 + 8 + 100 + 8 + 100
    val yearWidth = 113.dp
    val monthWidth = 100.dp
    val dayWidth = 100.dp

    KidsStepScaffold(
        nextEnabled = name.isNotBlank() && gender != null && birthChosen,
        onBackClick = onBackClick,
        onNext = {
            onNext(
                current.copy(
                    name = name,
                    gender = gender,
                    birthYear = year,
                    birthMonth = month,
                    birthDay = day
                )
            )
        }
    ) {
        // ===== 이름 =====
        KidsSectionLabel("이름을 입력해주세요.")
        Spacer(Modifier.height(8.dp))
        KidsTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "이름"
        )

        Spacer(Modifier.height(24.dp))

        // ===== 성별 =====
        KidsSectionLabel("성별을 선택해주세요.")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.width(KidsContentWidth),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val halfWidth = (KidsContentWidth - 12.dp) / 2
            KidsSelectablePill(
                text = "남자",
                isSelected = gender == "남자",
                onClick = { gender = "남자" },
                width = halfWidth
            )
            KidsSelectablePill(
                text = "여자",
                isSelected = gender == "여자",
                onClick = { gender = "여자" },
                width = halfWidth
            )
        }

        Spacer(Modifier.height(24.dp))

        // ===== 생년월일 (칸을 누르면 휠 피커) =====
        KidsSectionLabel("생년월일을 입력해주세요.")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.width(KidsContentWidth),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KidsDateFieldBox(
                text = year?.let { "${it}년" } ?: "년도",
                isPlaceholder = year == null,
                width = yearWidth,
                onClick = { showDatePicker = true }
            )
            KidsDateFieldBox(
                text = month?.let { "${it}월" } ?: "월",
                isPlaceholder = month == null,
                width = monthWidth,
                onClick = { showDatePicker = true }
            )
            KidsDateFieldBox(
                text = day?.let { "${it}일" } ?: "일",
                isPlaceholder = day == null,
                width = dayWidth,
                onClick = { showDatePicker = true }
            )
        }
    }

    if (showDatePicker) {
        KidsBirthDatePickerDialog(
            initialYear = year ?: 2019,
            initialMonth = month ?: 12,
            initialDay = day ?: 21,
            onDismiss = { showDatePicker = false },
            onConfirm = { y, m, d ->
                year = y; month = m; day = d
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun KidsDateFieldBox(
    text: String,
    isPlaceholder: Boolean,
    width: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(KidsItemHeight)
            .clip(RoundedCornerShape(KidsCorner))
            .background(GreyLightActive)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.Bold,
            color = if (isPlaceholder) GreyNormalActive else AionTextDark
        )
    }
}

// ============================================================
// 시안 4p — 감각 자극 (다중 선택)
// ============================================================
@Composable
private fun KidsSensoryStep(
    currentTraits: Set<String>,
    onBackClick: () -> Unit,
    onNext: (Set<String>) -> Unit
) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(currentTraits) } }
    val options = listOf("시각", "청각", "촉각")

    KidsStepScaffold(
        title = "어떤 감각 자극에 민감하게 반응하나요?",
        subtitle = "한 가지 이상 선택해 주세요.",
        nextEnabled = selected.isNotEmpty(),
        onBackClick = onBackClick,
        onNext = { onNext(selected.toSet()) }
    ) {
        options.forEach { option ->
            KidsSelectablePill(
                text = option,
                isSelected = option in selected,
                onClick = {
                    if (option in selected) selected.remove(option) else selected.add(option)
                }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ============================================================
// 시안 5p — 감정 표현 행동 (다중 선택)
// ============================================================
@Composable
private fun KidsBehaviorStep(
    currentBehaviors: Set<String>,
    onBackClick: () -> Unit,
    onNext: (Set<String>) -> Unit
) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(currentBehaviors) } }
    val options = listOf(
        "손이나 팔을 흔들어요",
        "박수치듯 손을 맞부딪혀요",
        "몸을 앞뒤나 양옆으로 흔들어요",
        "제자리에서 위아래로 뛰어요",
        "제자리에서 빙글빙글 돌아요",
        "귀를 막거나 머리를 두드려요"
    )

    KidsStepScaffold(
        title = "감정을 표현할 때 자주 보이는 행동이 있나요?",
        subtitle = "한 가지 이상 선택해 주세요.",
        nextEnabled = selected.isNotEmpty(),
        onBackClick = onBackClick,
        onNext = { onNext(selected.toSet()) }
    ) {
        options.forEach { option ->
            KidsSelectablePill(
                text = option,
                isSelected = option in selected,
                onClick = {
                    if (option in selected) selected.remove(option) else selected.add(option)
                }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ============================================================
// 시안 6p — 가입 완료
// ============================================================
// 시안 버튼 문구가 '다음'이라 교사용('확인')과 다르게 맞춰뒀다.
@Composable
fun KidsOnboardingCompleteScreen(
    isSubmitting: Boolean = false,
    onNextClick: () -> Unit = {}
) {
    KidsScreenFrame(
        bottomButton = {
            KidsPrimaryButton(
                text = if (isSubmitting) "저장 중..." else "다음",
                onClick = onNextClick,
                enabled = !isSubmitting
            )
        }
    ) {
        Spacer(Modifier.height(150.dp))

        Image(
            painter = painterResource(R.drawable.logo_symbol),
            contentDescription = "AION 로고",
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(79.dp)
        )
        Image(
            painter = painterResource(R.drawable.logo_text),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(75.dp)
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = "아이와 함께 성장할 준비가\n완료되었습니다.",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AionTextDark,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp
        )

        Spacer(Modifier.height(120.dp))
    }
}

// ============================================================
// 생년월일 휠 피커
// ============================================================
// 교사용 ChildProfileSetupScreen 안의 것과 같은 로직이지만 그쪽이 private 이라
// (그 파일을 건드리지 않기로 해서) 아동용에 같은 구조로 따로 뒀다.
// 나중에 공유하고 싶으면 교사용 쪽 private → internal 한 단어만 바꾸면 된다.
@Composable
private fun KidsBirthDatePickerDialog(
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit
) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }
    var day by remember { mutableIntStateOf(initialDay) }

    // 2월 30일 같은 조합이 되지 않도록 보정
    LaunchedEffect(year, month) {
        val maxDay = kidsDaysInMonth(year, month)
        if (day > maxDay) day = maxDay
    }

    val years = remember { (1990..2026).toList() }
    val months = remember { (1..12).toList() }
    val days = remember(year, month) { (1..kidsDaysInMonth(year, month)).toList() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(modifier = Modifier.width(KidsContentWidth)) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        KidsWheelPicker(years, year, { year = it }, { "${it}년" }, Modifier.weight(1f))
                        KidsWheelPicker(months, month, { month = it }, { "${it}월" }, Modifier.weight(1f))
                        KidsWheelPicker(days, day, { day = it }, { "${it}일" }, Modifier.weight(1f))
                    }

                    // 가운데 선택 영역 표시선
                    val itemHeight = 44.dp
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(0.95f).padding(bottom = itemHeight),
                        color = Color.LightGray
                    )
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(0.95f).padding(top = itemHeight),
                        color = Color.LightGray
                    )
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(GreyLightActive)
                            .clickable { onDismiss() }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("취소", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AionTextDark)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(BluePrimary)
                            .clickable { onConfirm(year, month, day) }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("확인", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

// 해당 연/월의 마지막 날짜 (윤년 포함)
private fun kidsDaysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    else -> 31
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KidsWheelPicker(
    items: List<Int>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    valueToLabel: (Int) -> String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 44.dp,
    visibleItemCount: Int = 3
) {
    val initialIndex = items.indexOf(selectedValue).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val paddingCount = visibleItemCount / 2

    // 중앙선에 가장 가까운 항목 index (contentPadding 때문에 viewportStartOffset 이 음수가 될 수 있어
    // '크기'가 아니라 시작/끝 좌표의 '평균'으로 중심을 구해야 한다)
    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item -> kotlin.math.abs((item.offset + item.size / 2) - viewportCenter) }
                ?.index ?: 0
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            items.getOrNull(centerIndex)?.let { value ->
                if (value != selectedValue) onValueChange(value)
            }
        }
    }

    LaunchedEffect(selectedValue, items) {
        val targetIndex = items.indexOf(selectedValue)
        if (targetIndex >= 0 && targetIndex != listState.firstVisibleItemIndex) {
            listState.scrollToItem(targetIndex)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier.height(itemHeight * visibleItemCount),
        contentPadding = PaddingValues(vertical = itemHeight * paddingCount)
    ) {
        itemsIndexed(items) { index, value ->
            val isCenter = index == centerIndex
            Box(
                modifier = Modifier.fillMaxWidth().height(itemHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = valueToLabel(value),
                    fontSize = if (isCenter) 22.sp else 16.sp,
                    fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCenter) AionTextDark else Color.LightGray
                )
            }
        }
    }
}

// ============================================================
// Preview — 시안 프레임(930 x 582)
// ============================================================
@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "3p 기본 정보")
@Composable
private fun KidsBasicInfoStepPreview() {
    AionTheme { KidsBasicInfoStep(ChildProfile(), {}, {}) }
}

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "4p 감각")
@Composable
private fun KidsSensoryStepPreview() {
    AionTheme { KidsSensoryStep(emptySet(), {}, {}) }
}

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "5p 행동")
@Composable
private fun KidsBehaviorStepPreview() {
    AionTheme { KidsBehaviorStep(emptySet(), {}, {}) }
}

@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "6p 완료")
@Composable
private fun KidsOnboardingCompleteScreenPreview() {
    AionTheme { KidsOnboardingCompleteScreen() }
}