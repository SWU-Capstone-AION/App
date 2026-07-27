package com.example.aion_app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// 디자인 시스템 컬러 (Figma '디자인 시스템(최종)' 기준)
// 토큰 이름 = 피그마 이름 (Light / Normal / Dark + :hover / :active)
// ============================================================

// ---------- Blue (앱 메인 컬러) ----------
val Light        = Color(0xFFF0F4FD)  // Light
val LightHover   = Color(0xFFE8EFFC)  // Light :hover
val LightActive  = Color(0xFFCFDEF9)  // Light :active
val Normal       = Color(0xFF6495ED)  // Normal         ← 메인 포인트 컬러
val NormalHover  = Color(0xFF5A86D5)  // Normal :hover
val NormalActive = Color(0xFF5077BE)  // Normal :active
val Dark         = Color(0xFF4B70B2)  // Dark
val DarkHover    = Color(0xFF3C598E)  // Dark :hover
val DarkActive   = Color(0xFF2D436B)  // Dark :active
val Darker       = Color(0xFF233453)  // Darker

// ---------- Point / Accent (피그마 포인트 3색) ----------
val Red    = Color(0xFFC05C47)  // 위험(danger)
val Orange = Color(0xFFCC8D42)  // 주의(caution)
val Green  = Color(0xFF629F7D)  // 안전/안정(safe)

// ---------- 기존 코드 호환 별칭 (팀 화면들이 아직 사용 중 → 유지) ----------
val BluePrimary = Normal   // = Blue Normal (0xFF6495ED)
val BlueLight   = Light    // = Blue Light  (0xFFF0F4FD)

// ---------- 보조 색 (GreyScale 는 추후 통일 예정) ----------
val GrayBackground = Color(0xFFF5F5F5)
val GrayText       = Color(0xFF9E9E9E)
val RedError       = Color(0xFFE53935)   // 팀 에러 색
val GreenSuccess   = Color(0xFF4CAF50)   // 팀 성공 색
val TextPrimary    = Color(0xFF222222)
val White          = Color(0xFFFFFFFF)

// ---------- AION 공통 ----------
val AionBlue     = Normal              // = Blue Normal
val AionOnBlue   = Color(0xFFF2F7FB)
val AionFieldBg  = Color(0xFFF6F7F8)
val AionSelected = LightHover          // = Blue Light :hover
val AionTextDark = Color(0xFF2D3C4A)
val AionTextGray = Color(0xFF8E8E8E)