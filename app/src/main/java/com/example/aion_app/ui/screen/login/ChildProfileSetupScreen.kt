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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.component.AionPrimaryButton
import com.example.aion_app.ui.component.AionTextField
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.GrayBackground
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
    val gender: String? = null,           // "남성" or "여성"
    val birthYear: Int = 2019,
    val birthMonth: Int = 12,
    val birthDay: Int = 21,
    val sensoryTraits: Set<String> = emptySet(),    // 다중 선택이라 Set
    val behaviors: Set<String> = emptySet()         // 다중 선택이라 Set
)

// ============================================
// 메인 화면 - 단계 관리
// ============================================
@Composable
fun ChildProfileSetupScreen(
    onBackClick: () -> Unit = {},     // 1단계에서 뒤로가기 누르면 호출 (이전 화면으로)
    onComplete: (ChildProfile) -> Unit = {}    // 마지막 단계 통과 시 호출
) {
    // 현재 단계 (1~5)
    var step by remember { mutableIntStateOf(1) }
    // 입력값 누적
    var profile by remember { mutableStateOf(ChildProfile()) }

    // 단계 안에서는 뒤로가기 → 이전 단계로, 1단계면 바깥(이전 화면)으로
    val handleBack: () -> Unit = {
        if (step > 1) step -= 1 else onBackClick()
    }

    // 단계에 따라 다른 화면 보여주기
    when (step) {
        1 -> NameStep(
            currentName = profile.name,
            onBackClick = handleBack,
            onNext = { name ->
                profile = profile.copy(name = name)   // copy: 일부만 바꿔서 새 객체 만들기
                step = 2
            }
        )
        2 -> GenderStep(
            currentGender = profile.gender,
            onBackClick = handleBack,
            onNext = { gender ->
                profile = profile.copy(gender = gender)
                step = 3
            }
        )
        3 -> BirthDateStep(
            currentYear = profile.birthYear,
            currentMonth = profile.birthMonth,
            currentDay = profile.birthDay,
            onBackClick = handleBack,
            onNext = { y, m, d ->
                profile = profile.copy(birthYear = y, birthMonth = m, birthDay = d)
                step = 4
            }
        )
        4 -> SensoryStep(
            currentTraits = profile.sensoryTraits,
            onBackClick = handleBack,
            onNext = { traits ->
                profile = profile.copy(sensoryTraits = traits)
                step = 5
            }
        )
        5 -> BehaviorStep(
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
@Composable
private fun StepScaffold(
    title: String,
    subtitle: String? = null,              // 작은 안내 (선택지 단계에만 있음)
    nextEnabled: Boolean,                   // 다음 버튼 활성화 여부
    onBackClick: () -> Unit,
    onNext: () -> Unit,
    content: @Composable ColumnScope.() -> Unit   // 가운데 들어갈 내용
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ===== 작은 뒤로가기 아이콘 + 로고 (단계 이동엔 뒤로가기가 필요해서 최소한으로 추가) =====
        Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "뒤로가기"
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ===== 로고 =====
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("AION", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ===== 제목 =====
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

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

// ============================================
// 공용 부품 2: 선택 가능한 알약(pill) 모양 항목
// ============================================
// 성별, 감각, 상동행동 단계에서 공통으로 씀
@Composable
private fun SelectablePill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            // 선택되면 연파랑, 아니면 기본 회색 (공용 색상 토큰 사용)
            .background(if (isSelected) BluePrimary.copy(alpha = 0.15f) else GrayBackground)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================
// 단계 1: 이름 입력
// ============================================
@Composable
private fun NameStep(currentName: String, onBackClick: () -> Unit, onNext: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }

    StepScaffold(
        title = "이름을 입력해 주세요",
        nextEnabled = name.isNotBlank(),    // 비어있으면 다음 비활성화
        onBackClick = onBackClick,
        onNext = { onNext(name) }
    ) {
        // ===== 공용 AionTextField로 통일 =====
        AionTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "이름"
        )
    }
}

// ============================================
// 단계 2: 성별 선택 (단일 선택)
// ============================================
@Composable
private fun GenderStep(currentGender: String?, onBackClick: () -> Unit, onNext: (String) -> Unit) {
    var selected by remember { mutableStateOf(currentGender) }

    StepScaffold(
        title = "성별을 선택해 주세요",
        nextEnabled = selected != null,
        onBackClick = onBackClick,
        onNext = { onNext(selected!!) }
    ) {
        SelectablePill(
            text = "남성",
            isSelected = selected == "남성",
            onClick = { selected = "남성" }
        )
        Spacer(modifier = Modifier.height(12.dp))
        SelectablePill(
            text = "여성",
            isSelected = selected == "여성",
            onClick = { selected = "여성" }
        )
    }
}

// ============================================
// 단계 3: 생년월일 선택 (진짜 휠 피커)
// ============================================
// LazyColumn + Compose 내장 snap fling만 사용 (외부 라이브러리/서버 호출 없음).
// 위아래로 굴리면 스냅되면서 가운데 값이 선택됨.
@Composable
private fun BirthDateStep(
    currentYear: Int,
    currentMonth: Int,
    currentDay: Int,
    onBackClick: () -> Unit,
    onNext: (Int, Int, Int) -> Unit
) {
    var year by remember { mutableIntStateOf(currentYear) }
    var month by remember { mutableIntStateOf(currentMonth) }
    var day by remember { mutableIntStateOf(currentDay) }

    // 월/년이 바뀌어서 해당 월의 마지막 날보다 day가 커지면 보정 (예: 31일 선택 후 2월로 바꾸면 28/29일로)
    LaunchedEffect(year, month) {
        val maxDay = daysInMonth(year, month)
        if (day > maxDay) day = maxDay
    }

    val years = remember { (1990..2026).toList() }
    val months = remember { (1..12).toList() }
    val days = remember(year, month) { (1..daysInMonth(year, month)).toList() }

    StepScaffold(
        title = "생년월일을 입력해 주세요",
        nextEnabled = true,
        onBackClick = onBackClick,
        onNext = { onNext(year, month, day) }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
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

            // 가운데 선택 영역 표시선 (위/아래 구분선)
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
@Preview(showBackground = true, device = "id:pixel_7", name = "1. 이름")
@Composable
private fun NameStepPreview() {
    NameStep(currentName = "", onBackClick = {}, onNext = {})
}

@Preview(showBackground = true, device = "id:pixel_7", name = "2. 성별")
@Composable
private fun GenderStepPreview() {
    GenderStep(currentGender = null, onBackClick = {}, onNext = {})
}

@Preview(showBackground = true, device = "id:pixel_7", name = "3. 생년월일")
@Composable
private fun BirthDateStepPreview() {
    BirthDateStep(2019, 12, 21, onBackClick = {}, onNext = { _, _, _ -> })
}

@Preview(showBackground = true, device = "id:pixel_7", name = "4. 감각")
@Composable
private fun SensoryStepPreview() {
    SensoryStep(currentTraits = emptySet(), onBackClick = {}, onNext = {})
}

@Preview(showBackground = true, device = "id:pixel_7", name = "5. 상동행동")
@Composable
private fun BehaviorStepPreview() {
    BehaviorStep(currentBehaviors = emptySet(), onBackClick = {}, onNext = {})
}

// 전체 플로우 (Interactive Mode로 단계 이동 테스트 가능)
@Preview(showBackground = true, device = "id:pixel_7", name = "전체 플로우")
@Composable
private fun ChildProfileSetupScreenPreview() {
    ChildProfileSetupScreen()
}