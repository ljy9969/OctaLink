package com.unboundapex.octalink.ui.screens.curriculum

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unboundapex.octalink.data.CurriculumDay
import com.unboundapex.octalink.data.dayLabelKor
import com.unboundapex.octalink.data.weeklyCurriculum
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.ui.components.TagChip
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
            item { com.unboundapex.octalink.ui.components.AdBanner() }
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

