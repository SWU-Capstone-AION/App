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

// ============================================================
// GreyScale (피그마 'GreyScale' 팔레트)
// ============================================================
// ⚠ Blue 쪽이 이미 Light / Normal / Dark / Darker 라는 이름을 쓰고 있어서
//   그레이스케일에는 Grey 접두사를 붙였다. (Blue 토큰명은 팀 화면들이 쓰는 중이라 유지)
//
// 어두움 → 밝음 순서. hover / active 는 Blue 와 같은 규칙으로 기준색보다 한 단계씩 어둡다.
val GreyBlack        = Color(0xFF171D1B)  // Black
val GreyDarker       = Color(0xFF444648)  // Darker
val GreyDarkActive   = Color(0xFF575A5C)  // Dark :active
val GreyDarkHover    = Color(0xFF74787B)  // Dark :hover
val GreyDark         = Color(0xFF92969A)  // Dark
val GreyNormalActive = Color(0xFF9BA0A4)  // Normal :active
val GreyNormalHover  = Color(0xFFAFB4B9)  // Normal :hover
val GreyNormal       = Color(0xFFC2C8CD)  // Normal
val GreyLightActive  = Color(0xFFECEEF0)  // Light :active
val GreyLightHover   = Color(0xFFF6F7F8)  // Light :hover
val GreyLight        = Color(0xFFF9FAFA)  // Light
val GreyWhite        = Color(0xFFFFFFFF)  // White  ※ 팔레트 12번째 — 디자인팀 확인 필요

// ---------- 기존 코드 호환 별칭 (팀 화면들이 아직 사용 중 → 유지) ----------
val BluePrimary = Normal   // = Blue Normal (0xFF6495ED)
val BlueLight   = Light    // = Blue Light  (0xFFF0F4FD)

// ============================================================
// 보조 색 — 그레이스케일 도입 전에 각자 만들어 쓰던 값들
// ============================================================
// 새 화면에는 위 Grey* 토큰을 쓰고, 아래는 기존 화면 호환용으로만 남긴다.
// 화면별로 하나씩 Grey* 로 옮긴 뒤 최종적으로 삭제하는 게 목표.
//
//   GrayBackground (#F5F5F5) → GreyLightHover (#F6F7F8)   거의 동일
//   GrayText       (#9E9E9E) → GreyNormalActive (#9BA0A4)
//   GrayDark       (#575A5C) → GreyDarkActive   (#575A5C)  ★ 완전 일치
//   AionFieldBg    (#F6F7F8) → GreyLightHover   (#F6F7F8)  ★ 완전 일치
//   AionTextGray   (#8E8E8E) → GreyDark         (#92969A)
//   TextPrimary    (#222222) → AionTextDark     (#2D3C4A)  ※ 본문 텍스트는 그레이스케일이 아니라
//                                                             파랑 계열 다크(#2D3C4A)가 시안 값
val GrayBackground = Color(0xFFF5F5F5)
val GrayText       = Color(0xFF9E9E9E)
val GrayDark       = Color(0xFF575A5C)
val RedError       = Color(0xFFE53935)   // 팀 에러 색
val GreenSuccess   = Color(0xFF4CAF50)   // 팀 성공 색
val TextPrimary    = Color(0xFF222222)
val White          = Color(0xFFFFFFFF)

// ---------- AION 공통 ----------
val AionBlue     = Normal              // = Blue Normal
val AionOnBlue   = Color(0xFFF2F7FB)   // 파랑 기 도는 배경 — 그레이스케일 아님
val AionFieldBg  = GreyLightHover      // = Light :hover (#F6F7F8)
val AionSelected = LightHover          // = Blue Light :hover
val AionTextDark = Color(0xFF2D3C4A)   // 본문 텍스트
val AionTextGray = Color(0xFF8E8E8E)