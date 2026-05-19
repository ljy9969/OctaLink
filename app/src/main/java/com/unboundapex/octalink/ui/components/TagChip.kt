package com.unboundapex.octalink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 수업 태그 색상 매핑 — 커리큘럼/홈 카드/공지 등 어디서나 동일 톤. */
fun tagColor(tag: String): Color = when (tag) {
    "스트라이킹" -> Color(0xFFFF6B35) // 오렌지
    "킥복싱" -> Color(0xFF1E88E5)     // 블루 (복싱 + 킥복싱 통합)
    "그래플링" -> Color(0xFF6D4C41)   // 브라운 (도복/땀 느낌)
    "MMA" -> Color(0xFFC8102E)        // 블러드 (브랜드 컬러)
    "스파링" -> Color(0xFF27AE60)     // 그린
    else -> Color(0xFF6E6E78)         // 폴백 ash
}

/** 수업 태그 칩 — 둥근 pill, 색은 [tagColor] 로 결정. */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tagColor(text))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
