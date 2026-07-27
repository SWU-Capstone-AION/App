package com.example.aion_app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// 디자인 시스템 컬러 (Figma '디자인 시스템(최종)' 기준)
// 이름은 피그마 구조와 통일 (Hue + 상태: Light / Normal / Dark, hover / active)
// ============================================================

// ---------- Blue (앱 메인 컬러) ----------
val BlueLight        = Color(0xFFF0F4FD)  // Light          (기존 BlueLight #E8F0F8 → 통일)
val BlueLightHover   = Color(0xFFE8EFFC)  // Light :hover
val BlueLightActive  = Color(0xFFCFDEF9)  // Light :active
val BlueNormal       = Color(0xFF6495ED)  // Normal         ← 메인 포인트 컬러
val BlueNormalHover  = Color(0xFF5A86D5)  // Normal :hover
val BlueNormalActive = Color(0xFF5077BE)  // Normal :active
val BlueDark         = Color(0xFF4B70B2)  // Dark
val BlueDarkHover    = Color(0xFF3C598E)  // Dark :hover
val BlueDarkActive   = Color(0xFF2D436B)  // Dark :active
val BlueDarker       = Color(0xFF233453)  // Darker

// ---------- Point / Accent (피그마 포인트 3색) ----------
// 피그마엔 hex만 표기돼 있어서 hue 기준으로 이름 지음 — 정식 명칭 있으면 알려주면 그대로 바꿈
val Red    = Color(0xFFC05C47)  // 위험(danger) 강조
val Orange = Color(0xFFCC8D42)  // 주의(caution) 강조
val Green  = Color(0xFF629F7D)  // 안전/안정(safe) 강조

// ---------- 기존 코드 호환 별칭 (이름 유지 → 앱 전체 자동 반영) ----------
// BluePrimary: 이전 0xFF8AB4E0 → Figma Normal(0xFF6495ED) 로 통일
val BluePrimary = BlueNormal

// ---------- 보조 색 ----------
val GrayBackground = Color(0xFFF5F5F5)     // 비활성 버튼   (GreyScale 통일 예정)
val GrayText       = Color(0xFF9E9E9E)     // 보조 텍스트   (GreyScale 통일 예정)
val RedError       = Color(0xFFE53935)     // 에러 메시지/테두리 (팀 error 색 — 필요 시 Red 로 통일 가능)
val GreenSuccess   = Color(0xFF4CAF50)     // 통과 체크 아이콘   (필요 시 Green 으로 통일 가능)
val TextPrimary    = Color(0xFF222222)
val White          = Color(0xFFFFFFFF)

// ---------- AION 공통 (기존 유지 · Blue 스케일과 정합) ----------
val AionBlue     = BlueNormal              // = Blue Normal (0xFF6495ED)
val AionOnBlue   = Color(0xFFF2F7FB)
val AionFieldBg  = Color(0xFFF6F7F8)
val AionSelected = BlueLightHover          // = Blue Light :hover (0xFFE8EFFC)
val AionTextDark = Color(0xFF2D3C4A)
val AionTextGray = Color(0xFF8E8E8E)