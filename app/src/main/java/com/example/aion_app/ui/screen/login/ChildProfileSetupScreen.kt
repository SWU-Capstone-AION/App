package com.example.aion_app.ui.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.aion_app.ui.component.AionPrimaryButton
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.component.AionTextField
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.GrayBackground
import com.example.aion_app.ui.theme.LightHover
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.TextPrimary
// 색상

// ============================================
// 아동 프로필 데이터 (모든 단계의 입력값을 한곳에 저장)
// ============================================
// data class: 데이터 묶음을 한 번에 다루는 코틀린 문법.
// 단계별 입력값을 따로따로 변수로 두지 않고 하나의 객체에 모아둠.
data class ChildProfile(
    val name: String = "",
    val gender: String? = null,           // "남자" or "여자"
    // 아직 고르지 않은 상태를 구분하려고 nullable. 선택 전에는 "년도/월/일" placeholder 표시.
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val birthDay: Int? = null,
    val sensoryTraits: Set<String> = emptySet(),    // 다중 선택이라 Set
    val behaviors: Set<String> = emptySet()         // 다중 선택이라 Set
)

// ============================================
// 메인 화면 - 단계 관리
// ============================================
@Composable
fun ChildProfileSetupScreen(
    // 감각특성·상동행동은 아동에게만 해당하는 항목이라,
    // 교사 회원가입에서는 false로 넘겨 1단계(이름·성별·생년월일)만 받는다.
    includeChildSteps: Boolean = true,
    onBackClick: () -> Unit = {},     // 1단계에서 뒤로가기 누르면 호출 (이전 화면으로)
    onComplete: (ChildProfile) -> Unit = {}    // 마지막 단계 통과 시 호출
) {
    // 현재 단계 (1~3) — 시안 3p/4p/5p
    var step by remember { mutableIntStateOf(1) }
    // 입력값 누적
    var profile by remember { mutableStateOf(ChildProfile()) }

    // 단계 안에서는 뒤로가기 → 이전 단계로, 1단계면 바깥(이전 화면)으로
    val handleBack: () -> Unit = {
        if (step > 1) step -= 1 else onBackClick()
    }

    // 단계에 따라 다른 화면 보여주기
    when (step) {
        // 시안 3p: 이름 + 성별 + 생년월일이 한 화면
        1 -> BasicInfoStep(
            current = profile,
            onBackClick = handleBack,
            onNext = { updated ->
                profile = updated
                // 교사 가입이면 여기서 끝. 아동 가입이면 감각특성 단계로.
                if (includeChildSteps) step = 2 else onComplete(updated)
            }
        )
        // 시안 4p
        2 -> SensoryStep(
            currentTraits = profile.sensoryTraits,
            onBackClick = handleBack,
            onNext = { traits ->
                profile = profile.copy(sensoryTraits = traits)
                step = 3
            }
        )
        // 시안 5p
        3 -> BehaviorStep(
            currentBehaviors = profile.behaviors,
            onBackClick = handleBack,
            onNext = { behaviors ->
                profile = profile.copy(behaviors = behaviors)
                onComplete(profile)   // 모든 단계 완료 → 콜백 호출
            }
        )
    }
}

// ============================================
// 공용 부품 1: 화면 전체 레이아웃 (TopBar + 제목 + 내용 + 다음 버튼)
// ============================================
// 모든 단계가 비슷한 구조라서 이걸로 통일. TopBar는 공용 AionTopBar 사용.
// 상단바 아래 ~ 제목 사이 간격 (이 값만 바꾸면 5단계 전부 반영됨)
private val HeaderToTitleGap = 48.dp

// ============================================
// 한 줄 제목
// ============================================
// 단계 제목은 줄바꿈되면 어미('요?')가 다음 줄로 떨어져 보기 나쁘다.
// 폰 화면 폭(좌우 46dp 패딩 제외 시 약 320dp)에는 20sp 로 다 안 들어가는 문구가 있어
// 폭을 넓히는 것만으로는 좁은 기기에서 잘린다.
//
// 그래서 한 줄에 안 들어가면 글자 크기를 한 단계씩 줄여 맞춘다.
// 측정이 끝나기 전 프레임이 잠깐 큰 글씨로 깜빡이지 않도록,
// 크기가 확정될 때까지 drawWithContent 로 그리기를 미룬다.
@Composable
private fun SingleLineTitle(
    text: String,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 20.sp,
    minFontSize: TextUnit = 14.sp
) {
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    var measured by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontSize > minFontSize) {
                fontSize = fontSize * 0.95f
            } else {
                measured = true
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent { if (measured) drawContent() }
    )
}

@Composable
private fun StepScaffold(
    title: String? = null,                  // null 이면 큰 제목 없이 본문만 (시안 3p)
    subtitle: String? = null,              // 작은 안내 (선택지 단계에만 있음)
    nextEnabled: Boolean,                   // 다음 버튼 활성화 여부
    onBackClick: () -> Unit,
    onNext: () -> Unit,
    content: @Composable ColumnScope.() -> Unit   // 가운데 들어갈 내용
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ===== 상단바 =====
        // 시안 2~5p: 회원가입 단계 화면엔 로고가 없고 "회원가입" 타이틀 바가 들어간다.
        // 상단바는 화면 끝까지 닿아야 해서 가로 패딩(46dp) 바깥에 둔다.
        AionTopBar(title = "회원가입", onBackClick = onBackClick)

        // ===== 본문 (기존과 동일하게 좌우 46dp) =====
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(HeaderToTitleGap))

            // ===== 제목 (있을 때만) =====
            if (title != null) {
                // 제목은 무조건 한 줄. 두 줄로 넘어가면 "...있나 / 요?" 처럼
                // 어미가 떨어져 나가서 디자인팀이 수정을 요청한 부분이다.
                SingleLineTitle(text = title)

                // ===== 부제목 (있을 때만) =====
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = GrayText
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // ===== 본문 (단계마다 다른 부분) =====
            content()

            // ===== 남는 공간 다 차지 (다음 버튼을 아래로) =====
            Spacer(modifier = Modifier.weight(1f))

            // ===== 공용 AionPrimaryButton으로 통일 =====
            AionPrimaryButton(
                text = "다음",
                onClick = onNext,
                enabled = nextEnabled
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ============================================
// 공용 부품 2: 선택 가능한 알약(pill) 모양 항목
// ============================================
// 성별, 감각, 상동행동 단계에서 공통으로 씀
@Composable
private fun SelectablePill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier          // 성별처럼 좌우로 나란히 놓을 때 weight 전달용
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            // 선택 상태 = Blue Light:hover (#E8EFFC) — 교사용/아동용 토글과 동일한 토큰
            .background(if (isSelected) LightHover else GrayBackground)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = TextPrimary,
            // 선택된 항목은 Bold 로 강조
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ============================================
// 단계 1: 기본 정보 (이름 + 성별 + 생년월일) — 시안 3p
// ============================================
// 시안에서 세 항목이 한 화면에 모여 있어, 큰 제목 없이 섹션 라벨만 왼쪽 정렬로 둔다.
@Composable
private fun BasicInfoStep(
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

    StepScaffold(
        title = null,                       // 시안 3p는 큰 제목이 없음
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
        SectionLabel("이름을 입력해주세요.")
        Spacer(modifier = Modifier.height(8.dp))
        AionTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "이름"
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ===== 성별 (좌우로 나란히) =====
        SectionLabel("성별을 선택해주세요.")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SelectablePill(
                text = "남자",
                isSelected = gender == "남자",
                onClick = { gender = "남자" },
                modifier = Modifier.weight(1f)
            )
            SelectablePill(
                text = "여자",
                isSelected = gender == "여자",
                onClick = { gender = "여자" },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ===== 생년월일 (칸을 누르면 피커 다이얼로그) =====
        SectionLabel("생년월일을 입력해주세요.")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DateFieldBox(
                text = year?.let { "${it}년" } ?: "년도",
                isPlaceholder = year == null,
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f)
            )
            DateFieldBox(
                text = month?.let { "${it}월" } ?: "월",
                isPlaceholder = month == null,
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f)
            )
            DateFieldBox(
                text = day?.let { "${it}일" } ?: "일",
                isPlaceholder = day == null,
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showDatePicker) {
        BirthDatePickerDialog(
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

// 섹션 라벨 (왼쪽 정렬)
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.fillMaxWidth()
    )
}

// 년도/월/일 표시 칸 — 누르면 피커가 열린다
@Composable
private fun DateFieldBox(
    text: String,
    isPlaceholder: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(GrayBackground)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.Bold,
            color = if (isPlaceholder) GrayText else TextPrimary
        )
    }
}

// ============================================
// 생년월일 피커 다이얼로그 (년/월/일 휠 3개 + 취소/확인)
// ============================================
@Composable
private fun BirthDatePickerDialog(
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
        val maxDay = daysInMonth(year, month)
        if (day > maxDay) day = maxDay
    }

    val years = remember { (1990..2026).toList() }
    val months = remember { (1..12).toList() }
    val days = remember(year, month) { (1..daysInMonth(year, month)).toList() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        WheelPicker(
                            items = years,
                            selectedValue = year,
                            onValueChange = { year = it },
                            valueToLabel = { "${it}년" },
                            modifier = Modifier.weight(1f)
                        )
                        WheelPicker(
                            items = months,
                            selectedValue = month,
                            onValueChange = { month = it },
                            valueToLabel = { "${it}월" },
                            modifier = Modifier.weight(1f)
                        )
                        WheelPicker(
                            items = days,
                            selectedValue = day,
                            onValueChange = { day = it },
                            valueToLabel = { "${it}일" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 가운데 선택 영역 표시선
                    val itemHeight = 44.dp
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .padding(bottom = itemHeight),
                        color = Color.LightGray
                    )
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .padding(top = itemHeight),
                        color = Color.LightGray
                    )
                }

                // ===== 취소 / 확인 =====
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(GrayBackground)
                            .clickable { onDismiss() }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("취소", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Normal)
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
private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    else -> 31
}

// ============================================
// 공용 부품 3: 휠 피커 (위아래로 굴려서 값 선택)
// ============================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(
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

    // 화면 가운데(중앙선)에 가장 가까운 항목의 index를 계산 (스크롤 중에도 실시간 반영)
    // 주의: viewportCenter는 (끝-시작)/2가 아니라 (시작+끝)/2 — content padding이 있으면
    // viewportStartOffset이 음수가 되므로, 크기가 아니라 두 좌표의 "평균"을 구해야 진짜 중심이 됨.
    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item -> kotlin.math.abs((item.offset + item.size / 2) - viewportCenter) }
                ?.index ?: 0
        }
    }

    // 스크롤(드래그/플링)이 멈췄을 때 = 값 확정되는 시점. 가운데 항목을 실제 선택값으로 반영.
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            items.getOrNull(centerIndex)?.let { value ->
                if (value != selectedValue) onValueChange(value)
            }
        }
    }

    // 바깥에서 selectedValue가 바뀐 경우(예: 2/30일 보정) 휠도 같이 이동
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = valueToLabel(value),
                    fontSize = if (isCenter) 22.sp else 16.sp,
                    fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCenter) Color.Black else Color.LightGray
                )
            }
        }
    }
}

// ============================================
// 단계 4: 감각 특성 (다중 선택)
// ============================================
@Composable
private fun SensoryStep(currentTraits: Set<String>, onBackClick: () -> Unit, onNext: (Set<String>) -> Unit) {
    // toMutableSet으로 변경 가능한 Set 만들기
    val selected = remember { mutableStateListOf<String>().apply { addAll(currentTraits) } }

    val options = listOf("시각", "청각", "촉각")

    StepScaffold(
        title = "어떤 감각 자극에 민감하게 반응하나요?",
        subtitle = "한 가지 이상 선택해 주세요.",
        nextEnabled = selected.isNotEmpty(),
        onBackClick = onBackClick,
        onNext = { onNext(selected.toSet()) }
    ) {
        options.forEach { option ->
            SelectablePill(
                text = option,
                isSelected = option in selected,
                onClick = {
                    if (option in selected) selected.remove(option)
                    else selected.add(option)
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ============================================
// 단계 5: 상동 행동 (다중 선택)
// ============================================
@Composable
private fun BehaviorStep(currentBehaviors: Set<String>, onBackClick: () -> Unit, onNext: (Set<String>) -> Unit) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(currentBehaviors) } }

    val options = listOf(
        "손이나 팔을 흔들어요",
        "박수치듯 손을 맞부딪혀요",
        "몸을 앞뒤나 양옆으로 흔들어요",
        "제자리에서 위아래로 뛰어요",
        "제자리에서 빙글빙글 돌아요",
        "귀를 막거나 머리를 두드려요"
    )

    StepScaffold(
        title = "감정을 표현할 때 자주 보이는 행동이 있나요?",
        subtitle = "한 가지 이상 선택해 주세요.",
        nextEnabled = selected.isNotEmpty(),
        onBackClick = onBackClick,
        onNext = { onNext(selected.toSet()) }
    ) {
        options.forEach { option ->
            SelectablePill(
                text = option,
                isSelected = option in selected,
                onClick = {
                    if (option in selected) selected.remove(option)
                    else selected.add(option)
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// ============================================
// Preview - 각 단계별 미리보기
// ============================================
@Preview(showBackground = true, device = "id:pixel_7", name = "1. 기본 정보")
@Composable
private fun BasicInfoStepPreview() {
    AionTheme {
        BasicInfoStep(current = ChildProfile(), onBackClick = {}, onNext = {})
    }
}

@Preview(showBackground = true, device = "id:pixel_7", name = "2. 감각")
@Composable
private fun SensoryStepPreview() {
    AionTheme {
        SensoryStep(currentTraits = emptySet(), onBackClick = {}, onNext = {})
    }
}

@Preview(showBackground = true, device = "id:pixel_7", name = "3. 상동행동")
@Composable
private fun BehaviorStepPreview() {
    AionTheme {
        BehaviorStep(currentBehaviors = emptySet(), onBackClick = {}, onNext = {})
    }
}

// 전체 플로우 (Interactive Mode로 단계 이동 테스트 가능)
@Preview(showBackground = true, device = "id:pixel_7", name = "전체 플로우 (아동)")
@Composable
private fun ChildProfileSetupScreenPreview() {
    AionTheme {
        ChildProfileSetupScreen()
    }
}

// 교사 가입 — 1단계에서 바로 완료
@Preview(showBackground = true, device = "id:pixel_7", name = "전체 플로우 (교사)")
@Composable
private fun TeacherProfileSetupScreenPreview() {
    AionTheme {
        ChildProfileSetupScreen(includeChildSteps = false)
    }
}