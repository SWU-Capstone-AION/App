package com.example.aion_app.ui.screen.mypage

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter

import android.Manifest
import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.R
import com.example.aion_app.ui.component.AionBottomNavBar
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.theme.BlueLight
import com.example.aion_app.ui.theme.BluePrimary
import com.example.aion_app.ui.theme.GrayBackground
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyInfoEditScreen(
    initialName: String = "김슈니",
    initialGender: String = "남자",
    initialBirthDate: String = "2019.12.21",
    initialSensitiveStimuli: List<String> = listOf("시각", "청각"),
    initialBehaviorTraits: List<String> = listOf("손이나 팔을 흔들어요", "박수치듯 손을 맞부딪혀요"),
    initialProfileImageUri: Uri? = null,
    // 감각 자극·행동 특성은 아동에게만 해당하는 항목이라 교사에게는 감춘다
    isTeacher: Boolean = false,
    onBackClick: () -> Unit = {},
    onSaveClick: (MyInfo) -> Unit = {}
) {
    // 편집 가능한 state
    var name by remember { mutableStateOf(initialName) }
    var gender by remember { mutableStateOf(initialGender) }
    var birthDate by remember { mutableStateOf(initialBirthDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var sensitiveStimuli by remember { mutableStateOf(initialSensitiveStimuli) }
    var behaviorTraits by remember { mutableStateOf(initialBehaviorTraits) }
    var profileImageUri by remember { mutableStateOf(initialProfileImageUri) }
    var showImageSourceSheet by remember { mutableStateOf(false) }

    // 카메라 촬영을 위해 미리 만들어둔 임시 파일 Uri
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // 전체 감각 자극 옵션
    val allSensitiveStimuli = listOf("시각", "청각", "촉각")

    // 전체 행동 특성 옵션
    val allBehaviorTraits = listOf(
        "손이나 팔을 흔들어요",
        "박수치듯 손을 맞부딪혀요",
        "몸을 앞뒤나 양옆으로 흔들어요",
        "제자리에서 위아래로 뛰어요",
        "제자리에서 빙글빙글 돌아요",
        "귀를 막거나 머리를 두드려요"
    )

    // 갤러리에서 사진 선택 launcher (안드로이드 13+ Photo Picker)
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            profileImageUri = uri
        }
    }

    // 카메라 launcher — 사진을 미리 정해둔 파일에 저장
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && pendingCameraUri != null) {
            profileImageUri = pendingCameraUri
        }
        pendingCameraUri = null
    }

// 카메라 권한 launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            val uri = createImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
        // 권한 거부 시 그냥 아무것도 안 함
    }

    Scaffold(
        topBar = {
            AionTopBar(
                title = "내 정보",
                onBackClick = onBackClick
            )
        },
        bottomBar = { AionBottomNavBar(selected = "mypage") },
        containerColor = White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 스크롤 영역
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // 프로필 사진
                EditRowProfileImage(
                    imageUri = profileImageUri,
                    onClick = { showImageSourceSheet = true }
                )
                EditDivider()

                // 이름
                EditRowTextField(
                    label = "이름",
                    value = name,
                    onValueChange = { name = it }
                )
                EditDivider()

                // 성별
                EditRowGender(
                    selectedGender = gender,
                    onGenderSelect = { gender = it }
                )
                EditDivider()

                // 생년월일
                EditRowClickable(
                    label = "생년월일",
                    value = birthDate,
                    onClick = { showDatePicker = true }
                )
                EditDivider()

                // ===== 아동 전용 항목 =====
                if (!isTeacher) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // 민감하게 반응하는 감각 자극
                    EditSelectableTagSection(
                        title = "민감하게 반응하는 감각 자극",
                        allOptions = allSensitiveStimuli,
                        selectedTags = sensitiveStimuli,
                        onTagToggle = { tag ->
                            sensitiveStimuli = if (sensitiveStimuli.contains(tag)) {
                                sensitiveStimuli - tag    // 이미 선택돼 있으면 → 빼기
                            } else {
                                sensitiveStimuli + tag    // 선택 안 돼 있으면 → 추가
                            }
                        }
                    )

                    EditDivider()

                    Spacer(modifier = Modifier.height(16.dp))

                    // 주로 나타나는 행동 특성
                    EditSelectableTagSection(
                        title = "주로 나타나는 행동 특성",
                        allOptions = allBehaviorTraits,
                        selectedTags = behaviorTraits,
                        onTagToggle = { tag ->
                            behaviorTraits = if (behaviorTraits.contains(tag)) {
                                behaviorTraits - tag
                            } else {
                                behaviorTraits + tag
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(White)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = {
                        val newInfo = MyInfo(
                            name = name,
                            gender = gender,
                            birthDate = birthDate,
                            sensitiveStimuli = sensitiveStimuli,
                            behaviorTraits = behaviorTraits,
                            profileImageUri = profileImageUri
                        )
                        onSaveClick(newInfo)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BluePrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "저장하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }
        }
    }

    // 날짜 선택 다이얼로그 (showDatePicker가 true일 때만 표시)
    if (showDatePicker) {
        BirthDatePickerDialog(
            initialDate = birthDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { newDate ->
                birthDate = newDate
                showDatePicker = false
            }
        )
    }

    // 프로필 사진 선택 바텀시트
    if (showImageSourceSheet) {
        ImageSourceBottomSheet(
            onDismiss = { showImageSourceSheet = false },
            onPickFromGallery = {
                showImageSourceSheet = false
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onTakePhoto = {
                showImageSourceSheet = false
                // 카메라 권한 요청 → 권한 받으면 자동으로 카메라 실행
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
    }
}

@Composable
private fun EditRowText(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = GrayText,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun EditRowTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = GrayText,
            modifier = Modifier.weight(1f)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.End
            ),
            singleLine = true,
            modifier = Modifier.widthIn(min = 80.dp)
        )
    }
}

@Composable
private fun EditRowGender(
    selectedGender: String,
    onGenderSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "성별",
            fontSize = 15.sp,
            color = GrayText,
            modifier = Modifier.weight(1f)
        )

        // 남자
        GenderOption(
            label = "남자",
            isSelected = selectedGender == "남자",
            onClick = { onGenderSelect("남자") }
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 여자
        GenderOption(
            label = "여자",
            isSelected = selectedGender == "여자",
            onClick = { onGenderSelect("여자") }
        )
    }
}

@Composable
private fun GenderOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) {
                Icons.Default.CheckCircle
            } else {
                Icons.Default.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = if (isSelected) BluePrimary else GrayText,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun EditRowWithImage(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = GrayText,
            modifier = Modifier.weight(1f)
        )
        Image(
            painter = painterResource(id = R.drawable.mypage_profile_default),
            contentDescription = "프로필 사진",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(BlueLight)
        )
    }
}

@Composable
private fun EditTagSection(title: String, tags: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = GrayText,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        tags.forEach { tag ->
            EditTagChip(text = tag)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun EditTagChip(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(GrayBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = TextPrimary
        )
    }
}

@Composable
private fun EditDivider() {
    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7", name = "교사")
@Composable
fun MyInfoEditScreenTeacherPreview() {
    MaterialTheme {
        MyInfoEditScreen(
            initialName = "김지연",
            initialGender = "여자",
            initialBirthDate = "1999.05.12",
            isTeacher = true
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7", name = "아동")
@Composable
fun MyInfoEditScreenPreview() {
    MaterialTheme {
        MyInfoEditScreen()
    }
}

@Composable
private fun EditRowClickable(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = GrayText,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePickerDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val initialMillis = parseDateToMillis(initialDate)
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        onConfirm(formatMillisToDate(selectedMillis))
                    }
                }
            ) {
                Text("확인", color = BluePrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = GrayText)
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

// "2019.12.21" 형식 문자열 → milliseconds
private fun parseDateToMillis(dateString: String): Long {
    return try {
        val format = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
        format.parse(dateString)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}

// milliseconds → "2019.12.21" 형식 문자열
private fun formatMillisToDate(millis: Long): String {
    val format = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
    return format.format(Date(millis))
}

@Composable
private fun EditSelectableTagSection(
    title: String,
    allOptions: List<String>,
    selectedTags: List<String>,
    onTagToggle: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = GrayText,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        allOptions.forEach { option ->
            SelectableTagChip(
                text = option,
                isSelected = selectedTags.contains(option),
                onClick = { onTagToggle(option) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SelectableTagChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (isSelected) BlueLight else GrayBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = TextPrimary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun EditRowProfileImage(
    imageUri: Uri?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "프로필 사진",
            fontSize = 15.sp,
            color = GrayText,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.BottomEnd
        ) {
            // 사진
            val painter: Painter = if (imageUri != null) {
                rememberAsyncImagePainter(model = imageUri)
            } else {
                painterResource(id = R.drawable.mypage_profile_default)
            }

            Image(
                painter = painter,
                contentDescription = "프로필 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(BlueLight)
            )

            // 우하단 카메라 아이콘
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "사진 변경",
                    tint = GrayText,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageSourceBottomSheet(
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // 앨범에서 선택
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickFromGallery)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "앨범에서 선택",
                    fontSize = 16.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = GrayText
                )
            }

            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)

            // 사진촬영
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTakePhoto)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "사진촬영",
                    fontSize = 16.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = GrayText
                )
            }
        }
    }
}

// 카메라 사진을 저장할 임시 파일 Uri 생성
private fun createImageUri(context: Context): Uri {
    val imagesDir = File(context.cacheDir, "images").apply {
        if (!exists()) mkdirs()
    }
    val imageFile = File(imagesDir, "profile_${System.currentTimeMillis()}.jpg")

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}