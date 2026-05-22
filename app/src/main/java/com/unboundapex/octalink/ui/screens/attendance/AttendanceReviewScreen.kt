package com.unboundapex.octalink.ui.screens.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.schema.AttendanceDoc
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 운영진 출결 검토 — `AttendanceScreen` 운영진 모드 카드 → 진입.
 *
 * 흐름:
 *  1. 회원 선택 안 된 상태: APPROVED 회원 list 표시 → 탭하면 선택
 *  2. 회원 선택 된 상태: 상단에 선택 회원 카드(다른 회원으로 전환 버튼) + attendance 시계열
 *  3. 각 attendance 행: 일자 + 시각 + verified 토글 + 삭제 칩
 *
 * 권한: PosseApp 라우팅에서 isStaff 만 진입 가능. Firestore Rules 가 동일 검증을 강제.
 */
@Composable
fun AttendanceReviewScreen(
    onBack: () -> Unit,
    vm: AttendanceReviewViewModel = viewModel(),
) {
    val members by vm.approvedMembers.collectAsState()
    val selectedId by vm.selectedMemberId.collectAsState()
    val attendance by vm.selectedAttendance.collectAsState()
    val weeklyCount by vm.weeklyCountByMember.collectAsState()
    val monthlyByDate by vm.monthlyCountByDate.collectAsState()
    val displayedMonth by vm.displayedMonth.collectAsState()

    val sortedMembers = remember(members) {
        members.sortedBy { it.name }
    }
    val selected = remember(selectedId, members) {
        members.firstOrNull { it.id == selectedId }
    }

    PosseScreen(
        title = "Review",
        subtitle = if (selected == null) "회원 출결 검토" else "${selected.name} 출결 검토",
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 상단 액션 바 — 뒤로 / 회원 변경
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionChip(
                    text = "← 뒤로",
                    bg = MaterialTheme.colorScheme.surfaceVariant,
                    fg = MaterialTheme.colorScheme.onSurface,
                    onClick = onBack,
                )
                if (selected != null) {
                    ActionChip(
                        text = "회원 변경",
                        bg = MaterialTheme.colorScheme.primary,
                        fg = MaterialTheme.colorScheme.onPrimary,
                        onClick = { vm.clearSelection() },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (selected == null) {
                MemberPickerList(
                    members = sortedMembers,
                    weeklyCount = weeklyCount,
                    displayedMonth = displayedMonth,
                    canGoPrev = displayedMonth.isAfter(vm.minMonth),
                    canGoNext = displayedMonth.isBefore(vm.maxMonth),
                    onPrevMonth = { vm.goPrevMonth() },
                    onNextMonth = { vm.goNextMonth() },
                    today = java.time.LocalDate.now(seoul),
                    monthlyByDate = monthlyByDate,
                    onPick = { vm.selectMember(it.id) },
                )
            } else {
                AttendanceList(
                    member = selected,
                    items = attendance,
                    onDelete = { date -> vm.deleteAttendance(selected.id, date) },
                )
            }
        }
    }
}

@Composable
private fun MemberPickerList(
    members: List<MemberDoc>,
    weeklyCount: Map<String, Int>,
    displayedMonth: java.time.LocalDate,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    today: java.time.LocalDate,
    monthlyByDate: Map<java.time.LocalDate, Int>,
    onPick: (MemberDoc) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // 최상단 — 도장 전체 일자별 출석 활성도 히트맵. 좌우 페이징 버튼으로 월 이동.
        item {
            Text(
                "월별 도장 활성도",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp),
            )
        }
        item {
            MonthCalendar(
                monthAnchor = displayedMonth,
                today = today,
                countsByDate = monthlyByDate,
                totalMemberCount = members.size,
                canGoPrev = canGoPrev,
                canGoNext = canGoNext,
                onPrevMonth = onPrevMonth,
                onNextMonth = onNextMonth,
            )
        }
        if (members.isNotEmpty()) {
            item {
                Text(
                    "회원별 주간 출석률",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                )
            }
        }
        if (members.isEmpty()) {
            item {
                PosseCard {
                    Text(
                        "승인된 회원이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(members.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { m ->
                        PosseCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPick(m) },
                            padding = PaddingValues(12.dp),
                            leftStripeColor = m.belt.ringColor,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 이름(위) + 체급(아래) 세로 배치 — 옆 출석률 badge 와 폭 충돌 없게.
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        m.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        m.weightClass.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                WeeklyRateBadge(count = weeklyCount[m.id] ?: 0)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AttendanceList(
    member: MemberDoc,
    items: List<AttendanceDoc>,
    onDelete: (java.time.LocalDate) -> Unit,
) {
    // 우발 클릭 방지 — 삭제는 confirm 다이얼로그 거침
    var deleteTarget by remember { mutableStateOf<AttendanceDoc?>(null) }

    val today = remember { java.time.LocalDate.now(seoul) }
    val countThisMonth = remember(items, today) {
        items.count { it.classDate.year == today.year && it.classDate.month == today.month }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            PosseCard(leftStripeColor = member.belt.ringColor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${member.weightClass.displayName} · 총 ${items.size}회 출석 · 이번 달 ${countThisMonth}회",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (items.isEmpty()) {
            item {
                PosseCard {
                    Text(
                        "출석 기록이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(items, key = { it.id }) { att ->
                AttendanceRow(
                    att = att,
                    onDelete = { deleteTarget = att },
                )
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("출석 기록 삭제") },
            text = {
                Text(
                    "${formatShortDate(target.classDate)} 출석 기록을 삭제하시겠습니까? 되돌릴 수 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Text(
                    "삭제",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            onDelete(target.classDate)
                            deleteTarget = null
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
            dismissButton = {
                Text(
                    "취소",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable { deleteTarget = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }
}

// 앱 전체 통일 포맷 — 날짜 "5/20(수)", 시간 "오후 8:59".
private val dateFormatter = DateTimeFormatter.ofPattern("M/d(EEE)", java.util.Locale.KOREAN)
private val timeFormatter = DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN)
private val seoul = ZoneId.of("Asia/Seoul")

private fun formatShortDate(d: java.time.LocalDate): String = d.format(dateFormatter)

@Composable
private fun AttendanceRow(
    att: AttendanceDoc,
    onDelete: () -> Unit,
) {
    PosseCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        // 날짜+시간 좌측, 삭제 우측. 카드 자체 padding 만 사용 — 내부 Row 추가 padding 없이 균형 맞춤.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatShortDate(att.classDate),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "체크인 " + att.checkInAt.atZone(seoul).toLocalTime().format(timeFormatter),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            ActionChip(
                text = "삭제",
                bg = Color(0xFF555560),
                fg = Color.White,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ActionChip(
    text: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
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

/**
 * 회원 카드 우측 — 이번 주 출석률.
 * 상단: % (체육관 6일 운영 기준), 하단: "N / 6" 일수.
 * 출석 0이면 회색 비활성 톤, 1+ 이면 primary.
 */
@Composable
private fun WeeklyRateBadge(count: Int) {
    val capped = count.coerceAtMost(GYM_DAYS_PER_WEEK)
    val pct = (capped * 100 / GYM_DAYS_PER_WEEK)
    val active = count > 0
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.titleMedium,
            color = fg,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "$capped / $GYM_DAYS_PER_WEEK",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}

/**
 * 한 달 캘린더 — 일요일 시작 7열 그리드. 도장 전체 회원의 일자별 출석 건수 heatmap.
 * 출석 0건: 빈 셀 / 1+ 건: primary alpha 비례 채움 (활성도 시각화).
 * 오늘 셀은 1.5dp border 강조.
 *
 * 휴무 / 공휴일 등 별도 표기는 없음 — MVP.
 */
@Composable
private fun MonthCalendar(
    monthAnchor: java.time.LocalDate,
    today: java.time.LocalDate,
    countsByDate: Map<java.time.LocalDate, Int>,
    /** 표시 대상 회원 풀 크기 — heatmap alpha 분모 (예: 6명 중 1명 → ratio ≈ 0.17). */
    totalMemberCount: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val firstOfMonth = monthAnchor.withDayOfMonth(1)
    val daysInMonth = monthAnchor.lengthOfMonth()
    // 일요일을 0번 컬럼으로 — Korean calendar convention. DayOfWeek: MON=1..SUN=7
    val leadingBlank = firstOfMonth.dayOfWeek.value % 7
    val totalCells = ((leadingBlank + daysInMonth + 6) / 7) * 7
    // heatmap alpha 스케일 — 도장 승인 회원 수 기준의 절대 출석률.
    // (예전엔 "이 달 최대 출석 일수" 분모였지만, 6명 중 1명만 나온 날도 100% 진하게 표시되는 문제가
    // 있어 부정확. 이제 6명 중 K명 → K/6 비율로 색 진해짐.)
    val denominator = totalMemberCount.coerceAtLeast(1)

    PosseCard(padding = PaddingValues(12.dp)) {
        // 좌우 페이징 버튼 + 중앙 월 라벨. 하한/상한 도달 시 해당 버튼 비활성화.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PagingButton(text = "◀", enabled = canGoPrev, onClick = onPrevMonth)
            Text(
                "${monthAnchor.year}년 ${monthAnchor.monthValue}월",
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                        0 -> Color(0xFFE57373)
                        6 -> Color(0xFF64B5F6)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        (0 until totalCells).toList().chunked(7).forEach { weekCells ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                weekCells.forEach { cellIdx ->
                    val dayNum = cellIdx - leadingBlank + 1
                    val cellDate = if (dayNum in 1..daysInMonth) firstOfMonth.withDayOfMonth(dayNum) else null
                    val count = cellDate?.let { countsByDate[it] } ?: 0
                    CalendarCell(
                        date = cellDate,
                        count = count,
                        intensityRatio = count.toFloat() / denominator,
                        isToday = cellDate == today,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(
    date: java.time.LocalDate?,
    count: Int,
    intensityRatio: Float,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val cellShape = RoundedCornerShape(6.dp)
    // 0건: 투명 / 1+ 건: 0.2~0.85 사이로 alpha 보간 (최저 가시성 확보 + 최고 채도)
    val alpha = if (count <= 0) 0f else 0.2f + 0.65f * intensityRatio.coerceIn(0f, 1f)
    val bg = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    val fg = when {
        date == null -> Color.Transparent
        count > 0 && intensityRatio > 0.5f -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .padding(2.dp)
            .height(32.dp)
            .clip(cellShape)
            .background(bg)
            .let { m ->
                if (isToday) m.border(1.5.dp, MaterialTheme.colorScheme.primary, cellShape)
                else m
            },
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Text(
                text = "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = if (count > 0 || isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
