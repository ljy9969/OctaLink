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
// Light 팔레트 — Apple iOS systemGroupedBackground / Notion 풍.
// 핵심: 배경 < surfaceVariant < surface 단계로 명도 분리. 균일 화이트면 카드 구분 안 됨.
//   - 배경: 약 6% 어두운 그레이 (#EDEDF0) → 카드(흰색)가 명확히 떠 보임
//   - surfaceVariant: 칩/비활성 영역 (#E1E1E6) → 본문 카드와 다른 단계 표현
//   - outline: 카드 경계 1dp 보더 시인성 충분히 (#CFCFD5)
// ─────────────────────────────────────
val Paper = Color(0xFFFFFFFF)        // surface — 카드 (순백, 떠올라 보이는 plane)
val Cloud = Color(0xFFE9E9ED)        // background — 본문 배경. 너무 옅음(#EDEDF0)/너무 진함(#E5E5EA) 중간 톤
val Frost = Color(0xFFDCDCE1)        // surfaceVariant — 칩/세컨더리 surface (배경보다 한 단계 진함, Cloud 와 동일 비율 보간)
val Slate = Color(0xFF1A1A1F)        // onBackground / onSurface — 본문 텍스트
val Pebble = Color(0xFF6A6A72)       // onSurfaceVariant — 보조 텍스트 / 라벨
val OutlineLight = Color(0xFFCFCFD5) // outline — 1dp border / divider (배경/카드 모두에서 보임)
