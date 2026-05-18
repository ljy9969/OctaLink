package com.unboundapex.octalink.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.avatarById
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.AvatarTile
import com.unboundapex.octalink.ui.components.HexagonSkillChart
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.ui.theme.AppTheme
import com.unboundapex.octalink.ui.theme.AppThemeViewModel
import java.time.LocalDate
import java.time.Period

private fun membershipLabel(joinDate: LocalDate, today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))): String {
    val period = Period.between(joinDate, today)
    return when {
        period.years > 0 && period.months > 0 -> "${period.years}년 ${period.months}개월 차"
        period.years > 0 -> "${period.years}년 차"
        period.months > 0 -> "${period.months}개월 차"
        else -> "이번 달 입관"
    }
}

private val commentDateFormatter = java.time.format.DateTimeFormatter.ofPattern("M/d")
private fun dayOfWeekKr(d: java.time.DayOfWeek): String = when (d) {
    java.time.DayOfWeek.MONDAY -> "월"; java.time.DayOfWeek.TUESDAY -> "화"
    java.time.DayOfWeek.WEDNESDAY -> "수"; java.time.DayOfWeek.THURSDAY -> "목"
    java.time.DayOfWeek.FRIDAY -> "금"; java.time.DayOfWeek.SATURDAY -> "토"
    java.time.DayOfWeek.SUNDAY -> "일"
}

@Composable
fun ProfileScreen(
    sessionVm: SessionViewModel,
    onOpenTournamentHistory: () -> Unit = {},
    commentsVm: MyCommentsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    skillsVm: MySkillsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    recordVm: MyTournamentRecordViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    notifPrefsVm: NotificationPrefsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    appThemeVm: AppThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val currentAppTheme by appThemeVm.theme.collectAsState()
    var leaveConfirmOpen by remember { mutableStateOf(false) }
    val avatar = avatarById(session.avatarId)
    val belt = session.belt

    // 본인 회원 id 가 set 되면 코멘트/스킬/전적/알림 prefs 구독 시작
    val myMemberId = session.member?.id
    androidx.compose.runtime.LaunchedEffect(myMemberId) {
        commentsVm.observeFor(myMemberId)
        skillsVm.observeFor(myMemberId)
        recordVm.observeForMember(myMemberId)
        notifPrefsVm.observeFor(myMemberId)
    }
    val notifPrefs by notifPrefsVm.prefs.collectAsState()

    // POST_NOTIFICATIONS 런타임 권한 launcher — 토글 ON 시 권한 없으면 요청.
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingToggleType by remember { mutableStateOf<com.unboundapex.octalink.data.schema.NotificationType?>(null) }
    val notifPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val type = pendingToggleType ?: return@rememberLauncherForActivityResult
        // 권한 결과와 무관하게 사용자 의도(ON 토글)는 저장 — 거부 시 알림은 안 뜨지만 prefs 는 유지.
        notifPrefsVm.setEnabled(type, true)
        if (!granted) {
            android.util.Log.w("OctaLink.NotifPrefs", "POST_NOTIFICATIONS denied — toggle saved but no notifications will show")
        }
        pendingToggleType = null
    }
    var notifDialogOpen by remember { mutableStateOf(false) }
    var classReminderDialogOpen by remember { mutableStateOf(false) }
    val coachComments by commentsVm.myComments.collectAsState()
    val classReminderSlots by notifPrefsVm.classReminderSlots.collectAsState()
    // 차트는 [SkillScoreDoc] 컬렉션을 직접 구독 — 콘솔에서 점수 doc 삭제/수정해도 즉시 반영.
    // [MemberDoc.skills] 스냅샷은 다른 화면(슬라이더 기준선 등) 의 빠른 접근용으로만 유지.
    val skillSet by skillsVm.skills.collectAsState()
    val skills = skillSet.toStats()
    val tournamentRecord by recordVm.record.collectAsState()
    // 실제 도장 입관일 — Firestore `members/{uid}.joinDate` 에서 (가입 폼에서 사용자가 입력).
    // 아직 회원 doc 이 없는 LOADING 단계 폴백은 오늘 (이번 달 입관 표시).
    val joinDate = session.member?.joinDate ?: LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
    val membership = remember(joinDate) { membershipLabel(joinDate) }

    PosseScreen(title = "Profile", subtitle = "${session.name} · ${belt.displayName} 벨트 · $membership") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                PosseCard(leftStripeColor = belt.ringColor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarTile(
                            avatar = avatar,
                            size = 88.dp,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 20.dp)
                        ) {
                            Text(session.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${avatar.displayName} · ${belt.displayName} 벨트",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                // 차트 + 평균 점수. skills 6축 평균(0..1) → 0..100 정수.
                val avgScore = (skills.sumOf { it.value.toDouble() } / skills.size * 100).toInt()
                PosseCard(padding = PaddingValues(4.dp)) {
                    // 차트의 하단 axis 라벨("기술") 아래 빈 공간 때문에 평균 행이 멀어 보임.
                    // offset 으로 위로 끌어올려 차트와 시각적으로 결합.
                    HexagonSkillChart(
                        skills = skills,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-20).dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "평균",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$avgScore",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "점",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                // 토너먼트 전적 — 완료된 토너먼트(`finishedAt != null`) 누적. 탭하면 히스토리 진입.
                PosseCard(modifier = Modifier.clickable { onOpenTournamentHistory() }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                "우승",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${tournamentRecord.wins}회",
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (tournamentRecord.participated == 0) {
                                Text(
                                    "참가한 토너먼트 없음",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "히스토리 보기 →",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    "토너먼트 ${tournamentRecord.participated}전 · 준우승 ${tournamentRecord.runnerUps}회",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "히스토리 보기 →",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            item {
                PosseCard {
                    Text("관장님 한 줄 코멘트", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    if (coachComments.isEmpty()) {
                        Text(
                            "아직 받은 코멘트가 없습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        coachComments.forEach { c ->
                            val dateLabel = c.classDate.format(commentDateFormatter) +
                                " " + dayOfWeekKr(c.classDate.dayOfWeek)
                            Text(
                                "$dateLabel · ${c.byMasterName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(c.text, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            // 알림 설정 — 카드 탭 시 모달로 토글 리스트 노출.
            item {
                PosseCard(modifier = Modifier.clickable { notifDialogOpen = true }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "알림 설정",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            // SIGNUP_RESULT 제외 (Profile 진입자에겐 무의미). 토글 다이얼로그와 카운트 일치.
                            val visiblePrefs = notifPrefs.filterKeys {
                                it != com.unboundapex.octalink.data.schema.NotificationType.SIGNUP_RESULT
                            }
                            val onCount = visiblePrefs.count { it.value }
                            Text(
                                "$onCount / ${visiblePrefs.size} 종 켜짐 · 탭하여 변경",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "→",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // ─────────────────────────────────────
            // UI 테마 (다크 / 라이트) — 본인 device-local 설정. SharedPreferences 영속.
            // ─────────────────────────────────────
            item {
                ThemePickerCard(
                    current = currentAppTheme,
                    onSelect = { appThemeVm.set(it) },
                )
            }

            // 운영진/관장/창조자 전용 작업은 하단 nav "운영" 탭으로 이전 (AdminScreen 참조)
            item {
                PosseCard(modifier = Modifier.clickable { sessionVm.signOut() }) {
                    Text(
                        "로그아웃",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "세션 종료. 데이터는 보존되며 다음 로그인 시 복원",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                PosseCard(modifier = Modifier.clickable { leaveConfirmOpen = true }) {
                    Text(
                        "회원 탈퇴",
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color(0xFFC8102E),
                    )
                    Text(
                        "앱 이용 중단. 도장 명단 완전 삭제는 관장님께 별도 요청",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (notifDialogOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { notifDialogOpen = false },
            title = { Text("알림 설정", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        "푸시 알림 종류별로 켜고 끌 수 있습니다.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    // SIGNUP_RESULT 는 PENDING 상태에서만 의미 있는 알림 — Profile 진입자는 이미
                    // APPROVED 라 토글이 무의미해 UI 에서 제외. 데이터 모델/CF 트리거는 그대로 유지.
                    com.unboundapex.octalink.data.schema.NotificationType.values()
                        .filter { it != com.unboundapex.octalink.data.schema.NotificationType.SIGNUP_RESULT }
                        .forEach { type ->
                        if (type == com.unboundapex.octalink.data.schema.NotificationType.CLASS_REMINDER) {
                            // 수업 리마인더는 슬롯별 세분화 — 단순 Switch 가 아닌 설정 진입 row.
                            ClassReminderConfigRow(
                                selectedCount = classReminderSlots.size,
                                onClick = {
                                    notifDialogOpen = false
                                    classReminderDialogOpen = true
                                },
                            )
                        } else {
                            NotificationToggleRow(
                                type = type,
                                enabled = notifPrefs[type] ?: type.defaultEnabled,
                                onToggle = { newValue ->
                                    if (newValue) {
                                        // ON 으로 전환 — Android 13+ 면 권한 확인.
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.POST_NOTIFICATIONS,
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                notifPrefsVm.setEnabled(type, true)
                                            } else {
                                                pendingToggleType = type
                                                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        } else {
                                            notifPrefsVm.setEnabled(type, true)
                                        }
                                    } else {
                                        notifPrefsVm.setEnabled(type, false)
                                    }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Text(
                    "닫기",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable { notifDialogOpen = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }

    if (classReminderDialogOpen) {
        ClassReminderConfigDialog(
            initialSelected = classReminderSlots,
            onDismiss = {
                classReminderDialogOpen = false
                notifDialogOpen = true // 알림 다이얼로그로 복귀
            },
            onSave = { newSelected ->
                notifPrefsVm.updateClassReminderSlots(newSelected)
                classReminderDialogOpen = false
                notifDialogOpen = true
            },
            onRequestPermissionIfNeeded = {
                // 슬롯 1개 이상 선택 시 Android 13+ POST_NOTIFICATIONS 권한 확보.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        pendingToggleType = com.unboundapex.octalink.data.schema.NotificationType.CLASS_REMINDER
                        notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            },
        )
    }

    if (leaveConfirmOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { leaveConfirmOpen = false },
            title = {
                Text(
                    "정말 탈퇴하시겠습니까?",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    "• 즉시 앱 이용이 중단됩니다\n" +
                        "• 출석/스킬/한 줄 코멘트 등 과거 기록은 운영 자료로 보존됩니다\n" +
                        "• 도장 명단에서 완전 삭제는 관장님께 별도 요청해주세요\n" +
                        "• 같은 카카오 계정으로 재가입 시 새 회원으로 처리됩니다",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Text(
                    "탈퇴",
                    color = androidx.compose.ui.graphics.Color(0xFFC8102E),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable {
                            leaveConfirmOpen = false
                            sessionVm.leaveMembership()
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
                        .clickable { leaveConfirmOpen = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }

}

/** 알림 종류 한 줄 — 타이틀/설명 + Material3 Switch. */
@Composable
private fun NotificationToggleRow(
    type: com.unboundapex.octalink.data.schema.NotificationType,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                type.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                type.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(
            checked = enabled,
            onCheckedChange = onToggle,
        )
    }
}

/**
 * 수업 30분 전 리마인더 — 슬롯별 세분화 설정 진입 row.
 * 단순 Switch 대신 "N개 선택됨 · 설정 →" 안내 + 탭 시 슬롯 선택 다이얼로그 오픈.
 */
@Composable
private fun ClassReminderConfigRow(
    selectedCount: Int,
    onClick: () -> Unit,
) {
    val type = com.unboundapex.octalink.data.schema.NotificationType.CLASS_REMINDER
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                type.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (selectedCount == 0) "비활성화 · 탭하여 슬롯 선택"
                else "$selectedCount 개 슬롯 활성화 · 탭하여 변경",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "→",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * 수업 리마인더 슬롯 선택 다이얼로그.
 * weekly schedule 의 모든 (요일, 수업) 슬롯을 요일별 그룹으로 LazyColumn 에 나열, 체크박스로 토글.
 * 저장 시 선택 셋을 [NotificationPrefsViewModel.updateClassReminderSlots] 로 일괄 push.
 */
@Composable
private fun ClassReminderConfigDialog(
    initialSelected: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onRequestPermissionIfNeeded: () -> Unit,
) {
    // 다이얼로그 열린 동안 로컬 편집 상태 — 저장 시점에 외부로 commit.
    var selected by remember(initialSelected) { mutableStateOf(initialSelected) }
    val allSlots = remember { com.unboundapex.octalink.data.allWeeklyClassSlots() }
    val grouped = remember(allSlots) {
        allSlots.groupBy({ it.first }, { it.second })
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("수업 리마인더 설정", style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(modifier = Modifier.height(420.dp)) {
                item {
                    Text(
                        "참석할 수업을 선택하면 시작 30분 전에 알림을 보냅니다.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                grouped.forEach { (day, slots) ->
                    item {
                        // 요일 헤더 + 일괄 선택/해제 토글
                        val dayKeys = slots.map { com.unboundapex.octalink.data.classSlotKey(day, it) }.toSet()
                        val allOn = dayKeys.isNotEmpty() && dayKeys.all { it in selected }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                dayOfWeekKr(day),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (allOn) "전체 해제" else "전체 선택",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        selected = if (allOn) selected - dayKeys else selected + dayKeys
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(slots) { slot ->
                        val key = com.unboundapex.octalink.data.classSlotKey(day, slot)
                        val checked = key in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - key else selected + key
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selected = if (it) selected + key else selected - key
                                },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${slot.timeRangeText} · ${slot.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                "저장",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        if (selected.isNotEmpty()) onRequestPermissionIfNeeded()
                        onSave(selected)
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
                    .clickable { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
    )
}

/**
 * 테마 선택 카드 — 두 가지 옵션을 시각적 swatch + 라벨 한 행으로 비교.
 * 각 스와치는 그 테마의 background / surface / primary 3색을 미니 미리보기로 노출 →
 * 토글 후 바로 적용되니 사용자가 결과를 미리 보고 선택 가능.
 */
@Composable
private fun ThemePickerCard(
    current: AppTheme,
    onSelect: (AppTheme) -> Unit,
) {
    PosseCard {
        Text("UI 테마", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "탭하여 다크 / 라이트 즉시 전환. 기기별 선호로 저장됩니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeOptionTile(
                option = AppTheme.DARK,
                selected = current == AppTheme.DARK,
                onClick = { onSelect(AppTheme.DARK) },
                modifier = Modifier.weight(1f),
                background = com.unboundapex.octalink.ui.theme.Ink,
                surface = com.unboundapex.octalink.ui.theme.Canvas,
                accent = com.unboundapex.octalink.ui.theme.Blood,
                textColor = com.unboundapex.octalink.ui.theme.Bone,
            )
            ThemeOptionTile(
                option = AppTheme.LIGHT,
                selected = current == AppTheme.LIGHT,
                onClick = { onSelect(AppTheme.LIGHT) },
                modifier = Modifier.weight(1f),
                background = com.unboundapex.octalink.ui.theme.Cloud,
                surface = com.unboundapex.octalink.ui.theme.Paper,
                accent = com.unboundapex.octalink.ui.theme.Blood,
                textColor = com.unboundapex.octalink.ui.theme.Slate,
            )
        }
    }
}

@Composable
private fun ThemeOptionTile(
    option: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color,
    surface: Color,
    accent: Color,
    textColor: Color,
) {
    val borderColor = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        // 미니 미리보기 — 배경 위에 카드(surface) + 액센트 도트.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(background)
                .padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(surface),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(accent),
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .fillMaxWidth(0.7f)
                            .background(textColor.copy(alpha = 0.7f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                option.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (selected) {
                Text(
                    "✓",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
