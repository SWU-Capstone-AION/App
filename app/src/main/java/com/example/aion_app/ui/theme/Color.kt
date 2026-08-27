package com.example.aion_app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// 디자인 시스템 컬러
// ============================================================
// 피그마 '디자인 시스템(최종)' 팔레트를 그대로 옮긴 파일이다.
// 토큰 이름 = 피그마 이름 (Light / Normal / Dark + :hover / :active)
//
// 구성
//   1. 팔레트   — 피그마에 정의된 색. 지금 안 쓰는 값도 정의만 해둔다.
//   2. 별칭     — 화면 코드가 쓰는 이름. 팔레트로 옮기는 중이라 남아 있다.
//
// 새 화면은 1번(팔레트)만 쓰고, 2번은 쓰지 않는다.
// ============================================================


// ============================================================
// 1. 팔레트
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

// ---------- GreyScale ----------
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
// White 는 아래 별칭 구역의 White 를 그대로 쓴다 (값 동일, 이름 중복을 피함)


// ============================================================
// 2. 별칭 — 화면 코드가 쓰는 이름
// ============================================================
// 그레이스케일 도입 전에 각자 만들어 쓰던 이름들. 아직 참조가 많아 지우지 못한다.
// 화면을 하나씩 팔레트 토큰으로 옮긴 뒤 최종적으로 삭제하는 게 목표다.
//
// 값이 같아 단순 치환이 가능했던 것들은 이미 팔레트로 옮겼다.
//   BluePrimary → Normal / BlueLight → Light / GrayDark → GreyDarkActive / GreyWhite → White
//
// 남은 것 (괄호 안은 현재 참조 수) — 옮기면 색이 미세하게 바뀌므로 화면 확인이 필요하다
//   GrayBackground (29) → GreyLightHover    #F5F5F5 → #F6F7F8
//   GrayText       (69) → GreyNormalActive  #9E9E9E → #9BA0A4
//
// TextPrimary(62) / AionTextDark(25) 는 둘 다 본문 텍스트인데 값이 다르다.
// 어느 쪽으로 통일할지 팀에서 정해야 한다. (시안 값은 파랑 계열 다크인 #2D3C4A)

// ---------- 회색 계열 별칭 ----------
val GrayBackground = Color(0xFFF5F5F5)
val GrayText       = Color(0xFF9E9E9E)
val White          = Color(0xFFFFFFFF)   // 팔레트의 White 이기도 하다

// ---------- 텍스트 ----------
val TextPrimary  = Color(0xFF222222)   // 팀 화면들이 쓰는 본문 색
val AionTextDark = Color(0xFF2D3C4A)   // 시안 기준 본문 색
val AionTextValue = Color(0xFF3A4D5F)  // 시안 기준 '값' 색 (마이페이지 이름·성별·생년월일)

// ---------- 상태 ----------
val RedError = Color(0xFFE53935)   // 팀 에러 색