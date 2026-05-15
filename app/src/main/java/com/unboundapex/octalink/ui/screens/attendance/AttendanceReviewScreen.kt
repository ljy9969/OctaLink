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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import java.time.DayOfWeek
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
                    onPick = { vm.selectMember(it.id) },
                )
            } else {
                AttendanceList(
                    member = selected,
                    items = attendance,
                    onToggleVerified = { date, v ->
                        vm.toggleVerified(selected.id, date, v)
                    },
                    onDelete = { date -> vm.deleteAttendance(selected.id, date) },
                )
            }
        }
    }
}

@Composable
private fun MemberPickerList(
    members: List<MemberDoc>,
    onPick: (MemberDoc) -> Unit,
) {
    if (members.isEmpty()) {
        PosseCard {
            Text(
                "승인된 회원이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
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
                        Text(m.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${m.weightClass.displayName} · ${m.belt.displayName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AttendanceList(
    member: MemberDoc,
    items: List<AttendanceDoc>,
    onToggleVerified: (java.time.LocalDate, Boolean) -> Unit,
    onDelete: (java.time.LocalDate) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            PosseCard(leftStripeColor = member.belt.ringColor) {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${member.weightClass.displayName} · ${member.belt.displayName} 벨트 · 총 ${items.size}회 출석",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    onToggleVerified = { v -> onToggleVerified(att.classDate, v) },
                    onDelete = { onDelete(att.classDate) },
                )
            }
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val seoul = ZoneId.of("Asia/Seoul")

private fun dayOfWeekKr(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "월"; DayOfWeek.TUESDAY -> "화"; DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"; DayOfWeek.FRIDAY -> "금"; DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

@Composable
private fun AttendanceRow(
    att: AttendanceDoc,
    onToggleVerified: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    PosseCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${att.classDate.format(dateFormatter)} (${dayOfWeekKr(att.classDate.dayOfWeek)})",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "체크인 " + att.checkInAt.atZone(seoul).toLocalTime().format(timeFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "확인",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(
                    checked = att.verified,
                    onCheckedChange = onToggleVerified,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Spacer(Modifier.width(8.dp))
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
