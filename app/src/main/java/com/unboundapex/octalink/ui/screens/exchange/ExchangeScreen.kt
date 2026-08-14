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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import com.unboundapex.octalink.data.dayLabelKor
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
    val allGyms by vm.allGyms.collectAsState()
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
        fun gymLabel(id: String) = allGyms.firstOrNull { it.id == id }
            ?.let { it.name + (it.branch?.let { b -> " · $b" } ?: "") } ?: id
        val placeOptions = listOf(gymLabel(d.requesterGymId), gymLabel(d.opponentGymId)).distinct()
        ScheduleDialog(
            placeOptions = placeOptions,
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
    ExchangeMatchStatus.SCHEDULED -> "일정 확정 · ${schedDateLabel(d.scheduledDate)} ${schedTimeLabel(d.scheduledTime)} @ ${d.place}"
    ExchangeMatchStatus.COMPLETED -> if (d.isDraw) "종료 · 무승부" else "종료 · 승자 ${if (d.winnerMemberId == d.requesterMemberId) d.requesterName else d.opponentName}"
    ExchangeMatchStatus.REJECTED -> "반려됨"
    ExchangeMatchStatus.CANCELLED -> "취소됨"
}

/** 저장된 ISO 일정("yyyy-MM-dd") → 표시용 "YY/MM/DD (요일)". 파싱 실패 시 원본. */
private fun schedDateLabel(iso: String?): String {
    val d = iso?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return iso.orEmpty()
    return "%02d/%02d/%02d (%s)".format(d.year % 100, d.monthValue, d.dayOfMonth, dayLabelKor(d.dayOfWeek))
}

/** 저장된 "HH:mm" → 표시용 "오전/오후 h:mm". 파싱 실패 시 원본. */
private fun schedTimeLabel(hhmm: String?): String {
    val t = hhmm?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() } ?: return hhmm.orEmpty()
    return t.format(java.time.format.DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(
    placeOptions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    var hour by remember { mutableStateOf<Int?>(null) }
    var minute by remember { mutableStateOf<Int?>(null) }
    var place by remember { mutableStateOf(placeOptions.firstOrNull().orEmpty()) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var placeOpen by remember { mutableStateOf(false) }

    // DatePicker 는 UTC 자정 millis 를 주므로 UTC 로 날짜 추출(로컬 변환 시 하루 밀림 방지).
    // 표시 포맷: YY/MM/DD (요일) — 예: "26/08/14 (금)".
    val dateText = dateMillis?.let {
        val ld = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        "%02d/%02d/%02d (%s)".format(ld.year % 100, ld.monthValue, ld.dayOfMonth, dayLabelKor(ld.dayOfWeek))
    } ?: ""
    // 표시 포맷: 오전/오후 h:mm — 예: "오후 7:30".
    val timeText = if (hour != null && minute != null) {
        java.time.LocalTime.of(hour!!, minute!!)
            .format(java.time.format.DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
    } else ""
    // 저장은 파싱 가능한 ISO 로(배지의 일정 당일 비교·정렬용). 표시는 위 dateText/timeText.
    val isoDate = dateMillis?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
    } ?: ""
    val isoTime = if (hour != null && minute != null) "%02d:%02d".format(hour!!, minute!!) else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("교류전 일정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (dateText.isBlank()) "📅  날짜 선택" else "📅  $dateText")
                }
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (timeText.isBlank()) "🕐  시간 선택" else "🕐  $timeText")
                }
                ExposedDropdownMenuBox(expanded = placeOpen, onExpandedChange = { placeOpen = it }) {
                    OutlinedTextField(
                        value = place,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("장소") },
                        placeholder = { Text("체육관 선택") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placeOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = placeOpen, onDismissRequest = { placeOpen = false }) {
                        placeOptions.forEach { opt ->
                            DropdownMenuItem(text = { Text(opt) }, onClick = { place = opt; placeOpen = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(isoDate, isoTime, place) },
                enabled = isoDate.isNotBlank() && isoTime.isNotBlank() && place.isNotBlank(),
            ) { Text("확정") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )

    if (showDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = { dateMillis = state.selectedDateMillis; showDate = false }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }
    if (showTime) {
        val state = rememberTimePickerState(is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = { hour = state.hour; minute = state.minute; showTime = false }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("취소") } },
            text = { Column { TimePicker(state = state) } },
        )
    }
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
