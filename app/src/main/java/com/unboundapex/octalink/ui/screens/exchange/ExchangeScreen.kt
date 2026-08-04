package com.unboundapex.octalink.ui.screens.exchange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.schema.ExchangeMatchDoc
import com.unboundapex.octalink.data.schema.ExchangeMatchStatus
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeScreen(
    memberId: String,
    myGymId: String,
    isStaff: Boolean,
    vm: ExchangeViewModel = viewModel(),
) {
    LaunchedEffect(memberId, myGymId, isStaff) { vm.bind(memberId, myGymId, isStaff) }

    val otherGyms by vm.otherGyms.collectAsState()
    val targetGymId by vm.selectedTargetGymId.collectAsState()
    val directory by vm.directory.collectAsState()
    val myDuels by vm.myDuels.collectAsState()
    val gymDuels by vm.gymDuels.collectAsState()
    val message by vm.message.collectAsState()

    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    // 내 교류전 + (운영진) 우리 체육관 교류전 병합, id 중복 제거, 최신순.
    val duels = remember(myDuels, gymDuels) {
        (myDuels + gymDuels).associateBy { it.id }.values.sortedByDescending { it.createdAt }
    }
    // 이미 진행 중(미종료)인 상대 id 셋 — 중복 신청 버튼 비활성용.
    val activeOpponentIds = remember(myDuels) {
        myDuels.filter { it.status != ExchangeMatchStatus.COMPLETED
            && it.status != ExchangeMatchStatus.REJECTED
            && it.status != ExchangeMatchStatus.CANCELLED }
            .flatMap { listOf(it.requesterMemberId, it.opponentMemberId) }
            .toSet()
    }

    var scheduleTarget by remember { mutableStateOf<ExchangeMatchDoc?>(null) }
    var resultTarget by remember { mutableStateOf<ExchangeMatchDoc?>(null) }

    PosseScreen(title = "교류전", subtitle = "다른 체육관 관원과 결투") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ── 다른 체육관 명단 ──
            item {
                Text("다른 체육관 명단", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                GymPicker(
                    gyms = otherGyms,
                    selectedId = targetGymId,
                    onSelect = { vm.selectTargetGym(it) },
                )
            }
            if (targetGymId != null && directory.isEmpty()) {
                item { Text("이 체육관에 표시할 관원이 없어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(directory, key = { it.id }) { p ->
                ExchangeProfileCard(
                    profile = p,
                    trailing = {
                        if (p.id != memberId) {
                            Button(
                                onClick = { vm.request(p.id) },
                                enabled = p.id !in activeOpponentIds,
                            ) { Text(if (p.id in activeOpponentIds) "진행중" else "신청") }
                        }
                    },
                )
            }

            // ── 내/우리 체육관 교류전 ──
            item {
                Spacer(Modifier.height(8.dp))
                Text("교류전 현황", style = MaterialTheme.typography.titleMedium)
            }
            if (duels.isEmpty()) {
                item { Text("아직 교류전이 없어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(duels, key = { it.id }) { d ->
                DuelRow(
                    d = d,
                    memberId = memberId,
                    myGymId = myGymId,
                    isStaff = isStaff,
                    onApprove = { vm.approve(d.id) },
                    onReject = { vm.reject(d.id) },
                    onSchedule = { scheduleTarget = d },
                    onResult = { resultTarget = d },
                )
            }
        }
    }

    scheduleTarget?.let { d ->
        ScheduleDialog(
            onDismiss = { scheduleTarget = null },
            onConfirm = { date, time, place -> vm.schedule(d.id, date, time, place); scheduleTarget = null },
        )
    }
    resultTarget?.let { d ->
        ResultDialog(
            duel = d,
            onDismiss = { resultTarget = null },
            onConfirm = { winnerId, draw -> vm.recordResult(d.id, winnerId, draw); resultTarget = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GymPicker(
    gyms: List<com.unboundapex.octalink.data.schema.GymDoc>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = gyms.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected?.let { it.name + (it.branch?.let { b -> " · $b" } ?: "") } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("체육관 선택") },
            placeholder = { Text("다른 체육관을 선택하세요") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (gyms.isEmpty()) {
                DropdownMenuItem(text = { Text("다른 체육관이 없어요") }, onClick = { open = false }, enabled = false)
            }
            gyms.forEach { g ->
                DropdownMenuItem(
                    text = { Text(g.name + (g.branch?.let { " · $it" } ?: "")) },
                    onClick = { onSelect(g.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun DuelRow(
    d: ExchangeMatchDoc,
    memberId: String,
    myGymId: String,
    isStaff: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onSchedule: () -> Unit,
    onResult: () -> Unit,
) {
    // 내가 이 교류전에서 승인해야 할 주체인지 (상대 본인 / 우리 체육관 운영진).
    val iAmOpponent = memberId == d.opponentMemberId
    val iAmParticipant = iAmOpponent || memberId == d.requesterMemberId
    val staffOfReq = isStaff && myGymId == d.requesterGymId
    val staffOfOpp = isStaff && myGymId == d.opponentGymId
    val canApprove = d.status == ExchangeMatchStatus.REQUESTED && (
        (iAmOpponent && !d.opponentApproved)
            || (staffOfReq && !d.requesterGymApproved)
            || (staffOfOpp && !d.opponentGymApproved)
    )
    val canReject = d.status == ExchangeMatchStatus.REQUESTED && (iAmParticipant || staffOfReq || staffOfOpp)
    val canSchedule = d.status == ExchangeMatchStatus.APPROVED && (staffOfReq || staffOfOpp)
    val canResult = d.status == ExchangeMatchStatus.SCHEDULED && (staffOfReq || staffOfOpp)

    PosseCard {
        Column {
            Text(
                "${d.requesterName}(${d.requesterGymId}) vs ${d.opponentName}(${d.opponentGymId})",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(2.dp))
            Text(statusLine(d), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (d.status == ExchangeMatchStatus.REQUESTED) {
                Text(
                    "승인: 상대 ${chk(d.opponentApproved)} · 요청측 운영진 ${chk(d.requesterGymApproved)} · 상대측 운영진 ${chk(d.opponentGymApproved)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canApprove || canReject || canSchedule || canResult) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canApprove) Button(onClick = onApprove) { Text("승인") }
                    if (canSchedule) Button(onClick = onSchedule) { Text("일정 정하기") }
                    if (canResult) Button(onClick = onResult) { Text("결과 입력") }
                    if (canReject) OutlinedButton(onClick = onReject) { Text("반려/취소") }
                }
            }
        }
    }
}

private fun chk(b: Boolean) = if (b) "✓" else "…"

private fun statusLine(d: ExchangeMatchDoc): String = when (d.status) {
    ExchangeMatchStatus.REQUESTED -> "요청됨 — 승인 대기"
    ExchangeMatchStatus.APPROVED -> "승인 완료 — 일정 대기"
    ExchangeMatchStatus.SCHEDULED -> "일정 확정 · ${d.scheduledDate} ${d.scheduledTime} @ ${d.place}"
    ExchangeMatchStatus.COMPLETED -> if (d.isDraw) "종료 · 무승부" else "종료 · 승자 ${if (d.winnerMemberId == d.requesterMemberId) d.requesterName else d.opponentName}"
    ExchangeMatchStatus.REJECTED -> "반려됨"
    ExchangeMatchStatus.CANCELLED -> "취소됨"
}

@Composable
private fun ScheduleDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("교류전 일정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("날짜 (예: 2026-09-01)") }, singleLine = true)
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("시간 (예: 19:30)") }, singleLine = true)
                OutlinedTextField(value = place, onValueChange = { place = it }, label = { Text("장소") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(date.trim(), time.trim(), place.trim()) },
                enabled = date.isNotBlank() && time.isNotBlank() && place.isNotBlank(),
            ) { Text("확정") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun ResultDialog(duel: ExchangeMatchDoc, onDismiss: () -> Unit, onConfirm: (String?, Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("교류전 결과") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("승자를 선택하세요.")
                Button(onClick = { onConfirm(duel.requesterMemberId, false) }, modifier = Modifier.fillMaxWidth()) { Text("${duel.requesterName} 승") }
                Button(onClick = { onConfirm(duel.opponentMemberId, false) }, modifier = Modifier.fillMaxWidth()) { Text("${duel.opponentName} 승") }
                OutlinedButton(onClick = { onConfirm(null, true) }, modifier = Modifier.fillMaxWidth()) { Text("무승부") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
