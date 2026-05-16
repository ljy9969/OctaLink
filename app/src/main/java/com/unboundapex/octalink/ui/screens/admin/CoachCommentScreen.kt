package com.unboundapex.octalink.ui.screens.admin

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.unboundapex.octalink.data.schema.CommentDoc
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 운영진 회원 한 줄 코멘트 작성 화면 — AdminScreen 운영진 공통 카드 진입점.
 *
 * 흐름:
 *  1. 회원 선택 안 됨: APPROVED MEMBER 풀 2열 그리드
 *  2. 회원 선택됨: 기존 코멘트 시계열 + "+ 코멘트 작성" 칩 → 다이얼로그 (text + classDate)
 *  3. 본인 작성 코멘트만 삭제 칩 노출 (운영진 ↔ 운영진 모더레이션은 별도)
 *
 * 권한: PosseApp 라우팅에서 isStaff 만 진입. Firestore Rules 가 byMasterId == auth.uid 강제.
 */
@Composable
fun CoachCommentScreen(
    onBack: () -> Unit,
    sessionVm: SessionViewModel,
    vm: CoachCommentViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val members by vm.members.collectAsState()
    val selectedId by vm.selectedMemberId.collectAsState()
    val comments by vm.selectedComments.collectAsState()

    val selected = remember(selectedId, members) { members.firstOrNull { it.id == selectedId } }
    var writeOpen by remember { mutableStateOf(false) }

    PosseScreen(
        title = "Comment",
        subtitle = if (selected == null) "회원 한 줄 코멘트 작성" else "${selected.name}에게 한 줄 코멘트",
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    Spacer(Modifier.weight(1f))
                    ActionChip(
                        text = "+ 코멘트 작성",
                        bg = MaterialTheme.colorScheme.primary,
                        fg = MaterialTheme.colorScheme.onPrimary,
                        onClick = { writeOpen = true },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (selected == null) {
                MemberPickerList(members = members, onPick = { vm.selectMember(it.id) })
            } else {
                CommentList(
                    member = selected,
                    items = comments,
                    myStaffId = session.member?.id,
                    onDelete = { vm.delete(selected.id, it) },
                )
            }
        }
    }

    if (writeOpen && selected != null) {
        WriteCommentDialog(
            member = selected,
            onDismiss = { writeOpen = false },
            onSubmit = { text, date ->
                val staff = session.member ?: return@WriteCommentDialog
                vm.submit(
                    toMemberId = selected.id,
                    byMasterId = staff.id,
                    byMasterName = staff.name,
                    text = text,
                    classDate = date,
                )
                writeOpen = false
            },
        )
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
                "코멘트 대상 회원이 없습니다.",
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
private fun CommentList(
    member: MemberDoc,
    items: List<CommentDoc>,
    myStaffId: String?,
    onDelete: (commentId: String) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            PosseCard(leftStripeColor = member.belt.ringColor) {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${member.weightClass.displayName} · ${member.belt.displayName} 벨트 · 코멘트 ${items.size}건",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (items.isEmpty()) {
            item {
                PosseCard {
                    Text(
                        "아직 작성된 코멘트가 없습니다. 상단 '+ 코멘트 작성' 으로 첫 코멘트 남기기.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(items, key = { it.id }) { c ->
                CommentRow(
                    comment = c,
                    canDelete = c.byMasterId == myStaffId,
                    onDelete = { onDelete(c.id) },
                )
            }
        }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("M/d")
private fun dayKr(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "월"; DayOfWeek.TUESDAY -> "화"; DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"; DayOfWeek.FRIDAY -> "금"; DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

@Composable
private fun CommentRow(
    comment: CommentDoc,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    PosseCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${comment.classDate.format(dateFmt)} ${dayKr(comment.classDate.dayOfWeek)} · ${comment.byMasterName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (canDelete) {
                ActionChip(
                    text = "삭제",
                    bg = Color(0xFF555560),
                    fg = Color.White,
                    onClick = onDelete,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(comment.text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WriteCommentDialog(
    member: MemberDoc,
    onDismiss: () -> Unit,
    onSubmit: (text: String, classDate: LocalDate) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val today = remember { LocalDate.now(java.time.ZoneId.of("Asia/Seoul")) }
    // 일자는 기본 today, MVP 에선 별도 picker 없음. 필요 시 후속에 DatePicker 추가.
    val classDate = today

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${member.name}에게 한 줄 코멘트") },
        text = {
            Column {
                Text(
                    "기준일: ${classDate.format(dateFmt)} ${dayKr(classDate.dayOfWeek)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(300) },
                    label = { Text("코멘트") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${text.length} / 300",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Text(
                "게시",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(enabled = text.isNotBlank()) { onSubmit(text, classDate) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
        dismissButton = {
            Text(
                "취소",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
    )
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
