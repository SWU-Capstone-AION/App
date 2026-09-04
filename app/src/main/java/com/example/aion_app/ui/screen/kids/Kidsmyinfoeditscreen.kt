package com.example.aion_app.ui.screen.kids

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.screen.mypage.MyInfo
import com.example.aion_app.ui.theme.AionTextValue
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.GreyLightHover
import com.example.aion_app.ui.theme.GreyNormalActive
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.White
import java.io.File

// ============================================================
// 아동용 내 정보 수정 — 시안 3p / 4p / 5p
// ============================================================
// 3p 기본형 / 4p 프로필 사진 바텀시트 / 5p 생년월일 휠 피커
// (셋 다 같은 화면이고 위에 뜨는 것만 다르다)
//
// 조회 화면(2p)과 세로 배치가 완전히 같고, 다음만 바뀐다.
//   프로필 사진 → 카메라 배지 + 탭하면 바텀시트
//   이름       → 입력 가능
//   성별       → 라디오 두 개
//   생년월일    → 탭하면 휠 피커
//   알약       → 전체 목록이 나오고 선택/해제 가능
//   버튼 문구   → '저장하기'
//
// 데이터 모델(MyInfo)과 ViewModel(MyInfoViewModel)은 교사용 것을 그대로 재사용한다.
// ============================================================

private val SectionTopGap    = 28.dp
private val SectionLabelGap  = 8.dp
private val PillGap          = 10.dp
private val SectionBottomGap = 27.dp

// 회원가입(KidsSignUpScreen)과 같은 목록이어야 한다.
// 한쪽만 바뀌면 가입 때 고른 값이 수정 화면에서 사라지므로 함께 고칠 것.
private val AllSensitiveStimuli = listOf("시각", "청각", "촉각")
private val AllBehaviorTraits = listOf(
    "손이나 팔을 흔들어요",
    "박수치듯 손을 맞부딪혀요",
    "몸을 앞뒤나 양옆으로 흔들어요",
    "제자리에서 위아래로 뛰어요",
    "제자리에서 빙글빙글 돌아요",
    "귀를 막거나 머리를 두드려요"
)

@Composable
fun KidsMyInfoEditScreen(
    initialName: String = "김슈니",
    initialGender: String = "남자",
    initialBirthDate: String = "2019.12.21",
    initialSensitiveStimuli: List<String> = listOf("시각", "청각"),
    initialBehaviorTraits: List<String> = listOf("손이나 팔을 흔들어요", "박수치듯 손을 맞부딪혀요"),
    initialProfileImageUri: Uri? = null,
    onBackClick: () -> Unit = {},
    onSaveClick: (MyInfo) -> Unit = {}
) {
    var name by remember { mutableStateOf(initialName) }
    var gender by remember { mutableStateOf(initialGender) }
    var birthDate by remember { mutableStateOf(initialBirthDate) }
    var profileImageUri by remember { mutableStateOf(initialProfileImageUri) }
    val sensitiveStimuli = remember {
        mutableStateListOf<String>().apply { addAll(initialSensitiveStimuli) }
    }
    val behaviorTraits = remember {
        mutableStateListOf<String>().apply { addAll(initialBehaviorTraits) }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showImageSourceSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // 갤러리 (안드로이드 13+ Photo Picker)
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) profileImageUri = uri }

    // 카메라 — 미리 만들어둔 파일에 저장
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && pendingCameraUri != null) profileImageUri = pendingCameraUri
        pendingCameraUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            val uri = createKidsImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
        // 거부하면 아무것도 하지 않는다
    }

    KidsScreenFrame(
        topBar = { AionTopBar(title = "내 정보", onBackClick = onBackClick) },
        contentWidth = KidsInfoWidth,
        bottomButton = {
            KidsPrimaryButton(
                text = "저장하기",
                onClick = {
                    onSaveClick(
                        MyInfo(
                            name = name,
                            gender = gender,
                            birthDate = birthDate,
                            sensitiveStimuli = sensitiveStimuli.toList(),
                            behaviorTraits = behaviorTraits.toList(),
                            profileImageUri = profileImageUri
                        )
                    )
                },
                enabled = name.isNotBlank(),
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
            Box(
                modifier = Modifier
                    .padding(end = KidsInfoValueInset)
                    .size(68.dp)
                    .clickable { showImageSourceSheet = true }
            ) {
                KidsProfileAvatar(imageUri = profileImageUri, size = 68.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(GreyLightHover),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "사진 변경",
                        tint = GreyNormalActive,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        KidsInfoDivider()

        // ===== 이름 =====
        KidsEditNameRow(value = name, onValueChange = { name = it })
        KidsInfoDivider()

        // ===== 성별 =====
        KidsEditGenderRow(selected = gender, onSelect = { gender = it })
        KidsInfoDivider()

        // ===== 생년월일 =====
        KidsInfoRow(
            label = "생년월일",
            value = birthDate,
            onClick = { showDatePicker = true }
        )
        KidsInfoDivider()

        // ===== 감각 자극 (다중 선택) =====
        Spacer(Modifier.height(SectionTopGap))
        KidsInfoLabel("민감하게 반응하는 감각 자극")
        Spacer(Modifier.height(SectionLabelGap))
        AllSensitiveStimuli.forEachIndexed { index, option ->
            if (index > 0) Spacer(Modifier.height(PillGap))
            KidsInfoPill(
                text = option,
                isSelected = option in sensitiveStimuli,
                onClick = {
                    if (option in sensitiveStimuli) sensitiveStimuli.remove(option)
                    else sensitiveStimuli.add(option)
                }
            )
        }
        Spacer(Modifier.height(SectionBottomGap))
        KidsInfoDivider()

        // ===== 행동 특성 (다중 선택) =====
        Spacer(Modifier.height(SectionTopGap))
        KidsInfoLabel("주로 나타나는 행동 특성")
        Spacer(Modifier.height(SectionLabelGap))
        AllBehaviorTraits.forEachIndexed { index, option ->
            if (index > 0) Spacer(Modifier.height(PillGap))
            KidsInfoPill(
                text = option,
                isSelected = option in behaviorTraits,
                onClick = {
                    if (option in behaviorTraits) behaviorTraits.remove(option)
                    else behaviorTraits.add(option)
                }
            )
        }

        Spacer(Modifier.height(100.dp))
    }

    // ===== 시안 5p — 생년월일 휠 피커 =====
    if (showDatePicker) {
        val (y, m, d) = birthDate.toKidsYmd()
        KidsBirthDatePickerDialog(
            initialYear = y,
            initialMonth = m,
            initialDay = d,
            onDismiss = { showDatePicker = false },
            onConfirm = { year, month, day ->
                birthDate = "%04d.%02d.%02d".format(year, month, day)
                showDatePicker = false
            }
        )
    }

    // ===== 시안 4p — 프로필 사진 바텀시트 =====
    if (showImageSourceSheet) {
        KidsImageSourceBottomSheet(
            onDismiss = { showImageSourceSheet = false },
            onPickFromGallery = {
                showImageSourceSheet = false
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onTakePhoto = {
                showImageSourceSheet = false
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
    }
}

// ============================================================
// 이름 — 오른쪽 정렬 입력칸
// ============================================================
// 조회 화면과 똑같이 보이되 탭하면 바로 고칠 수 있어야 해서
// 배경 없는 BasicTextField 로 값 자리에 그대로 얹었다.
@Composable
private fun KidsEditNameRow(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(KidsInfoRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KidsInfoLabel("이름", Modifier.weight(1f))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AionTextValue,
                textAlign = TextAlign.End
            ),
            cursorBrush = SolidColor(Normal),
            modifier = Modifier
                .width(160.dp)
                .padding(end = KidsInfoValueInset)
        )
    }
}

// ============================================================
// 성별 — 라디오 두 개 (원 20 / 원-라벨 8 / 그룹 사이 34)
// ============================================================
@Composable
private fun KidsEditGenderRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(KidsInfoRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KidsInfoLabel("성별", Modifier.weight(1f))

        KidsGenderOption("남자", selected == "남자") { onSelect("남자") }
        Spacer(Modifier.width(34.dp))
        KidsGenderOption("여자", selected == "여자") { onSelect("여자") }

        Spacer(Modifier.width(KidsInfoValueInset))
    }
}

@Composable
private fun KidsGenderOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isSelected) Normal else GreyLightHover),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = AionTextValue
        )
    }
}

// ============================================================
// 시안 4p — 프로필 사진 바텀시트
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KidsImageSourceBottomSheet(
    onDismiss: () -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            KidsSheetRow("앨범에서 선택", Icons.Default.Image, onPickFromGallery)
            HorizontalDivider(color = GreyLightHover, thickness = 0.5.dp)
            KidsSheetRow("사진촬영", Icons.Default.PhotoCamera, onTakePhoto)
        }
    }
}

@Composable
private fun KidsSheetRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AionTextValue,
            modifier = Modifier.weight(1f)
        )
        Icon(imageVector = icon, contentDescription = null, tint = GreyNormalActive)
    }
}

// ============================================================
// 유틸
// ============================================================

/** "2019.12.21" → (2019, 12, 21). 형식이 깨졌으면 시안 기본값. */
private fun String.toKidsYmd(): Triple<Int, Int, Int> {
    val parts = split(".")
    if (parts.size != 3) return Triple(2019, 12, 21)
    return Triple(
        parts[0].toIntOrNull() ?: 2019,
        parts[1].toIntOrNull() ?: 12,
        parts[2].toIntOrNull() ?: 21
    )
}

/** 카메라 사진을 저장할 임시 파일 Uri */
private fun createKidsImageUri(context: Context): Uri {
    val imagesDir = File(context.cacheDir, "images").apply { if (!exists()) mkdirs() }
    val imageFile = File(imagesDir, "kids_profile_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}

// ============================================================
// Preview — 시안 프레임(930 x 582)
// ============================================================
@Preview(showBackground = true, widthDp = 930, heightDp = 582, name = "3p 내 정보 수정")
@Composable
private fun KidsMyInfoEditScreenPreview() {
    AionTheme { KidsMyInfoEditScreen() }
}