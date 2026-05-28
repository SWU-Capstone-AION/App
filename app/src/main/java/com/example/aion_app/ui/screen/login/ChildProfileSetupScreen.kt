package com.example.aion_app.ui.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.ui.theme.AionBlue
import com.example.aion_app.ui.theme.AionOnBlue
import com.example.aion_app.ui.theme.AionFieldBg
import com.example.aion_app.ui.theme.AionSelected
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.AionTextGray
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
    onComplete: (ChildProfile) -> Unit = {}    // 마지막 단계 통과 시 호출
) {
    // 현재 단계 (1~5)
    var step by remember { mutableStateOf(1) }
    // 입력값 누적
    var profile by remember { mutableStateOf(ChildProfile()) }

    // 단계에 따라 다른 화면 보여주기
    when (step) {
        1 -> NameStep(
            currentName = profile.name,
            onNext = { name ->
                profile = profile.copy(name = name)   // copy: 일부만 바꿔서 새 객체 만들기
                step = 2
            }
        )
        2 -> GenderStep(
            currentGender = profile.gender,
            onNext = { gender ->
                profile = profile.copy(gender = gender)
                step = 3
            }
        )
        3 -> BirthDateStep(
            currentYear = profile.birthYear,
            currentMonth = profile.birthMonth,
            currentDay = profile.birthDay,
            onNext = { y, m, d ->
                profile = profile.copy(birthYear = y, birthMonth = m, birthDay = d)
                step = 4
            }
        )
        4 -> SensoryStep(
            currentTraits = profile.sensoryTraits,
            onNext = { traits ->
                profile = profile.copy(sensoryTraits = traits)
                step = 5
            }
        )
        5 -> BehaviorStep(
            currentBehaviors = profile.behaviors,
            onNext = { behaviors ->
                profile = profile.copy(behaviors = behaviors)
                onComplete(profile)   // 모든 단계 완료 → 콜백 호출
            }
        )
    }
}

// ============================================
// 공용 부품 1: 화면 전체 레이아웃 (로고 + 제목 + 내용 + 다음 버튼)
// ============================================
// 모든 단계가 비슷한 구조라서 이걸로 통일
@Composable
private fun StepScaffold(
    title: String,
    subtitle: String? = null,              // 작은 안내 (선택지 단계에만 있음)
    nextEnabled: Boolean,                   // 다음 버튼 활성화 여부
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
        // ===== 상단 여백 =====
        Spacer(modifier = Modifier.height(80.dp))

        // ===== 로고 =====
        // TODO: 이미지 받으면 교체
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("AION", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AionBlue)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ===== 제목 =====
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center
        )

        // ===== 부제목 (있을 때만) =====
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = AionTextGray
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ===== 본문 (단계마다 다른 부분) =====
        content()

        // ===== 남는 공간 다 차지 (다음 버튼을 아래로) =====
        Spacer(modifier = Modifier.weight(1f))

        // ===== 다음 버튼 =====
        Button(
            onClick = onNext,
            enabled = nextEnabled,           // 입력 안 됐으면 비활성화
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AionBlue,
                contentColor = Color.White,
                disabledContainerColor = AionBlue.copy(alpha = 0.5f)   // 비활성 시 흐리게
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("다음", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

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
            // 선택되면 연파랑(E8EFFC), 아니면 기본 회색(F6F7F8)
            .background(if (isSelected) AionSelected else AionFieldBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = AionTextDark,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================
// 단계 1: 이름 입력
// ============================================
@Composable
private fun NameStep(currentName: String, onNext: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }

    StepScaffold(
        title = "이름을 입력해 주세요",
        nextEnabled = name.isNotBlank(),    // 비어있으면 다음 비활성화
        onNext = { onNext(name) }
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AionFieldBg,
                unfocusedContainerColor = AionFieldBg,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}

// ============================================
// 단계 2: 성별 선택 (단일 선택)
// ============================================
@Composable
private fun GenderStep(currentGender: String?, onNext: (String) -> Unit) {
    var selected by remember { mutableStateOf(currentGender) }

    StepScaffold(
        title = "성별을 선택해 주세요",
        nextEnabled = selected != null,
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
// 단계 3: 생년월일 선택 (임시 구현 - 실제 스크롤 X)
// ============================================
// 진짜 휠 피커는 외부 라이브러리 필요. 일단 모양만 흉내냄.
@Composable
private fun BirthDateStep(
    currentYear: Int,
    currentMonth: Int,
    currentDay: Int,
    onNext: (Int, Int, Int) -> Unit
) {
    val year by remember { mutableStateOf(currentYear) }
    val month by remember { mutableStateOf(currentMonth) }
    val day by remember { mutableStateOf(currentDay) }

    StepScaffold(
        title = "생년월일을 입력해 주세요",
        nextEnabled = true,
        onNext = { onNext(year, month, day) }
    ) {
        // 휠 피커 흉내 - 위/아래 흐리고 가운데 진하게
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 위 (흐림)
            DateRow(year - 1, if (month == 1) 12 else month - 1, day - 1, isCenter = false)

            Spacer(modifier = Modifier.height(8.dp))

            // 가운데 구분선
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(0.9f),
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 가운데 (진함 - 선택된 값)
            DateRow(year, month, day, isCenter = true)

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(0.9f),
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 아래 (흐림)
            DateRow(year + 1, if (month == 12) 1 else month + 1, day + 1, isCenter = false)
        }
    }
}

// 생년월일 한 줄
@Composable
private fun DateRow(year: Int, month: Int, day: Int, isCenter: Boolean) {
    val textColor = if (isCenter) Color.Black else Color.LightGray
    val fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal
    val fontSize = if (isCenter) 24.sp else 22.sp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${year}년", fontSize = fontSize, color = textColor, fontWeight = fontWeight)
        Text("${month}월", fontSize = fontSize, color = textColor, fontWeight = fontWeight)
        Text("${day}일", fontSize = fontSize, color = textColor, fontWeight = fontWeight)
    }
}

// ============================================
// 단계 4: 감각 특성 (다중 선택)
// ============================================
@Composable
private fun SensoryStep(currentTraits: Set<String>, onNext: (Set<String>) -> Unit) {
    // toMutableSet으로 변경 가능한 Set 만들기
    val selected = remember { mutableStateListOf<String>().apply { addAll(currentTraits) } }

    val options = listOf("시각", "청각", "촉각")

    StepScaffold(
        title = "어떤 감각 자극에 민감하게 반응하나요?",
        subtitle = "한 가지 이상 선택해 주세요.",
        nextEnabled = selected.isNotEmpty(),
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
private fun BehaviorStep(currentBehaviors: Set<String>, onNext: (Set<String>) -> Unit) {
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
    NameStep(currentName = "", onNext = {})
}

@Preview(showBackground = true, device = "id:pixel_7", name = "2. 성별")
@Composable
private fun GenderStepPreview() {
    GenderStep(currentGender = null, onNext = {})
}

@Preview(showBackground = true, device = "id:pixel_7", name = "3. 생년월일")
@Composable
private fun BirthDateStepPreview() {
    BirthDateStep(2019, 12, 21, onNext = { _, _, _ -> })
}

@Preview(showBackground = true, device = "id:pixel_7", name = "4. 감각")
@Composable
private fun SensoryStepPreview() {
    SensoryStep(currentTraits = emptySet(), onNext = {})
}

@Preview(showBackground = true, device = "id:pixel_7", name = "5. 상동행동")
@Composable
private fun BehaviorStepPreview() {
    BehaviorStep(currentBehaviors = emptySet(), onNext = {})
}

// 전체 플로우 (Interactive Mode로 단계 이동 테스트 가능)
@Preview(showBackground = true, device = "id:pixel_7", name = "전체 플로우")
@Composable
private fun ChildProfileSetupScreenPreview() {
    ChildProfileSetupScreen()
}