package com.teamposse.striking.data

import androidx.compose.ui.graphics.Color

enum class Belt(val displayName: String, val ringColor: Color) {
    WHITE("화이트", Color(0xFFF5F2EC)),
    BLUE("블루", Color(0xFF1976D2)),
    PURPLE("퍼플", Color(0xFF7B1FA2)),
    BROWN("브라운", Color(0xFF6D4C41)),
    BLACK("블랙", Color(0xFF333333)),
    ;

    companion object {
        fun fromName(name: String): Belt = values().firstOrNull { it.displayName == name } ?: WHITE
    }
}
