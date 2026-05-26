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

/**
 * 수업 / 스킬축 / 난이도 태그 색상 매핑 — 커리큘럼/홈 카드/공지/AI 보강 루틴 어디서나 동일 톤.
 *
 * 구역:
 *  - 수업 태그 (5종): 스트라이킹 / 킥복싱 / 그래플링 / MMA / 스파링
 *  - 6축 스킬 (6종): 스트라이킹(공유) / 그래플링(공유) / 체력 / 기술 / 멘탈 / 스피드
 *  - 난이도 (3종): 초급 / 중급 / 고급 (DifficultySelector 그라데이션의 메인 톤과 정합)
 */
fun tagColor(tag: String): Color = when (tag) {
    // 수업 태그 + 공유 스킬축
    "스트라이킹" -> Color(0xFFFF6B35) // 오렌지
    "킥복싱" -> Color(0xFF1E88E5)     // 블루 (복싱 + 킥복싱 통합)
    "그래플링" -> Color(0xFF6D4C41)   // 브라운 (도복/땀 느낌)
    "MMA" -> Color(0xFFC8102E)        // 블러드 (브랜드 컬러)
    "스파링" -> Color(0xFF27AE60)     // 그린
    // 6축 추가 (스트라이킹 / 그래플링은 위 수업 태그와 공유)
    "체력" -> Color(0xFF7C3AED)       // 바이올렛 (스태미나)
    "기술" -> Color(0xFF0891B2)       // 시안 (정밀함)
    "멘탈" -> Color(0xFFEC4899)       // 마젠타 (심리)
    "스피드" -> Color(0xFFEAB308)     // 옐로 (모션)
    // 난이도 (DifficultySelector 그라데이션의 시작 색 톤)
    "초급" -> Color(0xFF10B981)       // 에메랄드
    "중급" -> Color(0xFFF59E0B)       // 앰버
    "고급" -> Color(0xFFDC2626)       // 레드
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
