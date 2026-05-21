package com.unboundapex.octalink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.unboundapex.octalink.data.WeightClass

/**
 * 체급별 색상 그라데이션 — 페더(밝은 오렌지/레드) → 헤비(딥 마룬) 점진적으로 진해짐.
 * BracketDrawScreen 의 필터 칩과 TournamentHistory 카드의 체급 라벨이 동일 톤 공유.
 */
fun weightGradient(weightClass: WeightClass): Brush {
    val palette = listOf(
        Color(0xFFFF6B35) to Color(0xFFE53935),  // 페더
        Color(0xFFE53935) to Color(0xFFC8102E),  // 라이트
        Color(0xFFC8102E) to Color(0xFFAD1A1A),  // 웰터
        Color(0xFFAD1A1A) to Color(0xFF7B1010),  // 미들
        Color(0xFF7B1010) to Color(0xFF3E0606),  // 헤비
    )
    val idx = weightClass.ordinal.coerceIn(palette.indices)
    val (start, end) = palette[idx]
    return Brush.horizontalGradient(listOf(start, end))
}

/**
 * 체급 그라데이션 칩 — 라벨은 [WeightClass.displayName] (예: "페더급"). 선택/필터 상태 없음
 * (필터용은 [BracketDrawScreen] 의 로컬 WeightChip 사용). 단순 표시 전용.
 */
@Composable
fun WeightClassChip(
    weightClass: WeightClass,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(weightGradient(weightClass))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = weightClass.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
