package com.unboundapex.octalink.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────
// Dark 팔레트 (기본 OctaLink 테마)
// ─────────────────────────────────────
val Ink = Color(0xFF0B0B0F)       // background — 거의 검정
val Canvas = Color(0xFF15161B)    // surface — 카드/시트
val Ash = Color(0xFF3A3A42)       // surfaceVariant — 보조 표면 / 비활성
val Bone = Color(0xFFF5F2EC)      // onBackground / onSurface — 본문 텍스트
val Blood = Color(0xFFC8102E)     // primary — 브랜드 빨강 (양 테마 공통)
val Mist = Color(0xFF8A8A93)      // onSurfaceVariant — 보조 텍스트

// ─────────────────────────────────────
// Light 팔레트 (신규 — 깔끔/미니멀)
// 톤: Notion / Linear 풍의 부드러운 화이트 + 다크 그레이 본문.
// 순수 백색(#FFFFFF) 은 글래어 강해서 카드만 백색, 배경은 off-white.
// ─────────────────────────────────────
val Paper = Color(0xFFFFFFFF)        // surface — 카드 (순백)
val Cloud = Color(0xFFFAFAFA)        // background — 본문 배경 (off-white)
val Frost = Color(0xFFF2F2F4)        // surfaceVariant — 비활성 칩/세컨더리 surface
val Slate = Color(0xFF1A1A1F)        // onBackground / onSurface — 본문 텍스트
val Pebble = Color(0xFF6A6A72)       // onSurfaceVariant — 보조 텍스트 / 라벨
val OutlineLight = Color(0xFFE1E1E6) // outline — 1dp border / divider
