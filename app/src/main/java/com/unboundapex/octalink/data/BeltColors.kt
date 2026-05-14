package com.unboundapex.octalink.data

import androidx.compose.ui.graphics.Color

enum class Belt(val displayName: String, val ringColor: Color) {
    WHITE("화이트", Color(0xFFF5F2EC)),
    BLUE("블루", Color(0xFF1976D2)),
    PURPLE("퍼플", Color(0xFF7B1FA2)),
    BROWN("브라운", Color(0xFF6D4C41)),
    BLACK("블랙", Color(0xFF333333)),

    /** 벨트 미정 — 본인이 모르거나, 아직 부여받지 않은 신규 회원용.
     *  추첨 풀 필터에는 노출되지 않고, 운영자가 정확한 벨트로 갱신할 때까지 임시 상태.
     *  Profile/Signup 등에서 "Unknown 벨트" 로 표시 — "모름 벨트" 어색함 회피. */
    UNKNOWN("Unknown", Color(0xFF9E9E9E)),
    ;

    /** 정식 5단계 벨트 (필터/매치 표시 등 — UNKNOWN 제외) */
    companion object {
        val gradedValues: List<Belt> = listOf(WHITE, BLUE, PURPLE, BROWN, BLACK)
        fun fromName(name: String): Belt = values().firstOrNull { it.displayName == name } ?: WHITE
    }
}
