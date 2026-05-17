package com.unboundapex.octalink.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen

/**
 * 로그인 본인의 출석 기록 캘린더 — 홈 "내 주간 출석률" 카드에서 진입.
 *
 * 다른 회원 데이터 일절 노출 없음 ([MyAttendanceHistoryViewModel] 가 session.member.id 로만 구독).
 * 월 단위 페이징: 올해 1월 ~ 이번 달. 범위 밖 버튼은 비활성.
 */
@Composable
fun MyAttendanceHistoryScreen(
    onBack: () -> Unit,
    sessionVm: SessionViewModel,
    vm: MyAttendanceHistoryViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val memberId = session.member?.id
    LaunchedEffect(memberId) { vm.observeFor(memberId) }

    val selectedMonth by vm.selectedMonth.collectAsState()
    val attendedDates by vm.attendedDatesInMonth.collectAsState()

    val canPrev = selectedMonth.isAfter(vm.earliestMonth)
    val canNext = selectedMonth.isBefore(vm.latestMonth)

    PosseScreen(
        title = "Attendance",
        subtitle = "내 출석 기록 · ${attendedDates.size}일",
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 상단 — 뒤로 버튼만. 월 navigation 은 캘린더 카드 내부 ◀/▶ 에 통합.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChipBtn(
                    text = "← 뒤로",
                    bg = MaterialTheme.colorScheme.surfaceVariant,
                    fg = MaterialTheme.colorScheme.onSurface,
                    onClick = onBack,
                )
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    PosseCard(padding = PaddingValues(12.dp)) {
                        MyMonthCalendar(
                            monthAnchor = selectedMonth,
                            today = vm.today,
                            attendedDates = attendedDates,
                            canGoPrev = canPrev,
                            canGoNext = canNext,
                            onPrevMonth = { vm.prevMonth() },
                            onNextMonth = { vm.nextMonth() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 본인 한 명에 대한 월 캘린더 — REVIEW MonthCalendar 와 동일 디자인 (좌우 ◀/▶ + 중앙 월 라벨).
 * 출석한 날 = primary 채워진 셀, 오늘 = primary 테두리, 빈 날 = 회색 숫자.
 */
@Composable
private fun MyMonthCalendar(
    monthAnchor: java.time.LocalDate,
    today: java.time.LocalDate,
    attendedDates: Set<java.time.LocalDate>,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val firstOfMonth = monthAnchor.withDayOfMonth(1)
    val daysInMonth = monthAnchor.lengthOfMonth()
    // 일요일 = 0번 열 (Korean convention). DayOfWeek: MON=1..SUN=7
    val leadingBlank = firstOfMonth.dayOfWeek.value % 7
    val totalCells = ((leadingBlank + daysInMonth + 6) / 7) * 7

    // 좌우 페이징 버튼 + 중앙 월 라벨. 하한/상한 도달 시 해당 버튼 비활성화.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PagingButton(text = "◀", enabled = canGoPrev, onClick = onPrevMonth)
        Text(
            "${monthAnchor.year}년 ${monthAnchor.monthValue}월",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        PagingButton(text = "▶", enabled = canGoNext, onClick = onNextMonth)
    }
    Spacer(Modifier.height(8.dp))

    val dayLabels = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(modifier = Modifier.fillMaxWidth()) {
        dayLabels.forEachIndexed { idx, label ->
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = when (idx) {
                    0 -> Color(0xFFE57373)  // 일요일 빨강
                    6 -> Color(0xFF64B5F6)  // 토요일 파랑
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(6.dp))

    val primary = MaterialTheme.colorScheme.primary
    for (week in 0 until totalCells / 7) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (col in 0..6) {
                val cellIdx = week * 7 + col
                val dayNum = cellIdx - leadingBlank + 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (dayNum in 1..daysInMonth) {
                        val date = monthAnchor.withDayOfMonth(dayNum)
                        val attended = date in attendedDates
                        val isToday = date == today
                        val bg = if (attended) primary else Color.Transparent
                        val border = if (isToday) Modifier.border(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(6.dp),
                        ) else Modifier
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .then(border),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = dayNum.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (attended || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    attended -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** REVIEW MonthCalendar 의 `PagingButton` 과 동일 — 좌우 화살표, disabled 시 옅게. */
@Composable
private fun PagingButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.25f
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ChipBtn(
    text: String,
    bg: Color,
    fg: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.Bold,
        )
    }
}
