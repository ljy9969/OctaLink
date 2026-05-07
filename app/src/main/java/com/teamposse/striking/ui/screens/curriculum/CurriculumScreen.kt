package com.teamposse.striking.ui.screens.curriculum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamposse.striking.data.CurriculumDay
import com.teamposse.striking.data.dayLabelKor
import com.teamposse.striking.data.weeklyCurriculum
import com.teamposse.striking.ui.components.PosseCard
import com.teamposse.striking.ui.components.PosseScreen
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun CurriculumScreen() {
    val today = remember { LocalDate.now(ZoneId.of("Asia/Seoul")) }
    val subtitle = remember(today) {
        "${today.monthValue}/${today.dayOfMonth} ${dayLabelKor(today.dayOfWeek)}"
    }

    PosseScreen(title = "Curriculum", subtitle = subtitle) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(weeklyCurriculum) { day ->
                CurriculumCard(
                    day = day,
                    isToday = day.dayOfWeek == today.dayOfWeek,
                )
            }
        }
    }
}

@Composable
private fun CurriculumCard(day: CurriculumDay, isToday: Boolean) {
    PosseCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DayBadge(label = dayLabelKor(day.dayOfWeek), highlight = isToday)
            Spacer(Modifier.padding(start = 12.dp))
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        day.theme,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TagChip(day.tag)
                }
                Text(
                    day.coach,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        day.drills.forEach { drill ->
            Text(
                "• $drill",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DayBadge(label: String, highlight: Boolean) {
    val bg = if (highlight) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (highlight) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = fg,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

private fun tagColor(tag: String): Color = when (tag) {
    "스트라이킹" -> Color(0xFFFF6B35) // 오렌지
    "킥복싱" -> Color(0xFF1E88E5)     // 블루 (복싱 + 킥복싱 통합)
    "그래플링" -> Color(0xFF6D4C41)   // 브라운 (도복/땀 느낌)
    "MMA" -> Color(0xFFC8102E)        // 블러드 (브랜드 컬러)
    "스파링" -> Color(0xFF27AE60)     // 그린
    else -> Color(0xFF6E6E78)         // 폴백 ash
}

@Composable
private fun TagChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tagColor(text))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
