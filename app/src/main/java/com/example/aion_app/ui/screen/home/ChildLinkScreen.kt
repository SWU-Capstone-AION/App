package com.example.aion_app.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.data.auth.ChildSearchResult
import com.example.aion_app.ui.component.AionTopBar
import com.example.aion_app.ui.theme.AionTheme
import com.example.aion_app.ui.theme.GrayText
import com.example.aion_app.ui.theme.GreyLightHover
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.LightHover
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.RedError
import com.example.aion_app.ui.theme.TextPrimary
import com.example.aion_app.ui.theme.White

// ============================================
// 아이디로 아동 찾기
// ============================================
// 아이디를 검색해 아동 계정을 찾고 연결을 요청한다.
// 요청 후 아동이 태블릿에서 수락해야 실제로 연결된다.
@Composable
fun ChildLinkScreen(
    isSearching: Boolean = false,
    hasSearched: Boolean = false,
    searchResult: ChildSearchResult? = null,
    isRequesting: Boolean = false,
    errorMessage: String? = null,
    onBackClick: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    onRequestLink: (ChildSearchResult) -> Unit = {},
    onCreateAccountClick: () -> Unit = {},
) {
    var loginId by remember { mutableStateOf("") }
    // 검색 결과 카드를 눌러 선택해야 연결 요청 버튼이 뜬다
    var isSelected by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { AionTopBar(title = "아이디로 찾기", onBackClick = onBackClick) },
        containerColor = White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // ===== 검색창 =====
            SearchField(
                value = loginId,
                onValueChange = {
                    loginId = it
                    isSelected = false
                },
                enabled = !isSearching,
                onSearch = { onSearch(loginId) }
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    fontSize = 12.sp,
                    color = RedError
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            when {
                isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Normal,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                searchResult != null -> {
                    ChildResultCard(
                        child = searchResult,
                        isSelected = isSelected,
                        onClick = { isSelected = !isSelected }
                    )

                    // 이미 연결된 아이는 요청을 보낼 수 없다
                    if (searchResult.alreadyLinked || searchResult.hasPendingRequest) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchResult.alreadyLinked)
                                "이미 다른 선생님에게 등록된 아이예요."
                            else "이미 연결 요청을 기다리고 있어요.",
                            fontSize = 12.sp,
                            color = GrayText,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 검색은 했는데 결과가 없는 경우
                hasSearched -> {
                    NotFoundCard(onCreateAccountClick = onCreateAccountClick)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ===== 연결 요청 버튼 =====
            // 카드를 선택했고, 아직 아무 데도 연결되지 않은 아이일 때만 보인다
            val canRequest = searchResult != null &&
                    isSelected &&
                    !searchResult.alreadyLinked &&
                    !searchResult.hasPendingRequest

            if (canRequest) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isRequesting) LightActive else Normal)
                        .clickable(enabled = !isRequesting) {
                            onRequestLink(searchResult)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRequesting) "요청 중..." else "연결 요청",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ============================================
// 검색창 (입력칸 + 돋보기 버튼)
// ============================================
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = LightActive,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = TextPrimary
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSearch() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 돋보기 버튼
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Normal)
                .clickable(enabled = enabled && value.isNotBlank()) { onSearch() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "검색",
                tint = White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ============================================
// 검색 결과 카드
// ============================================
@Composable
private fun ChildResultCard(
    child: ChildSearchResult,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) LightHover else White)
            .border(
                width = 1.dp,
                color = if (isSelected) Normal else LightActive,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        // 프로필 + 이름 + 아이디
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE0E0E0))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = child.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = child.loginId,
                    fontSize = 12.sp,
                    color = GrayText
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = LightActive, thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        InfoRow(label = "성별", value = child.gender)
        Spacer(modifier = Modifier.height(8.dp))
        InfoRow(label = "생년월일", value = child.birthDateText)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = GrayText,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}

// ============================================
// 검색 결과가 없을 때
// ============================================
@Composable
private fun NotFoundCard(onCreateAccountClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GreyLightHover)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "찾는 아이가 없나요?",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "아직 가입하지 않은 아이라면 새로 만들어 주세요.",
            fontSize = 12.sp,
            color = GrayText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(White)
                .border(
                    width = 1.dp,
                    color = LightActive,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onCreateAccountClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "새 계정 만들어주기",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7", name = "검색 전")
@Composable
private fun ChildLinkScreenPreview() {
    AionTheme { ChildLinkScreen() }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7", name = "검색 결과")
@Composable
private fun ChildLinkScreenResultPreview() {
    AionTheme {
        ChildLinkScreen(
            hasSearched = true,
            searchResult = ChildSearchResult(
                uid = "uid",
                loginId = "Jiwoo_0517",
                name = "김지우",
                gender = "남",
                birthDateText = "2019.05.17",
                alreadyLinked = false,
                hasPendingRequest = false,
            )
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7", name = "결과 없음")
@Composable
private fun ChildLinkScreenNotFoundPreview() {
    AionTheme {
        ChildLinkScreen(hasSearched = true, searchResult = null)
    }
}