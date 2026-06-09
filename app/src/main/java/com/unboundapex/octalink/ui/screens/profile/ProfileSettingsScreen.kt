package com.unboundapex.octalink.ui.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Shop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.BuildConfig
import com.unboundapex.octalink.data.GymInfo
import com.unboundapex.octalink.data.allWeeklyClassSlots
import com.unboundapex.octalink.data.classSlotKey
import com.unboundapex.octalink.data.schema.NotificationType
import com.unboundapex.octalink.data.schema.isMaster
import com.unboundapex.octalink.messaging.BatteryOptimizationHelper
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.ui.theme.AppTheme
import com.unboundapex.octalink.ui.theme.AppThemeStore
import com.unboundapex.octalink.ui.theme.Blood
import com.unboundapex.octalink.ui.theme.Bone
import com.unboundapex.octalink.ui.theme.Canvas
import com.unboundapex.octalink.ui.theme.Cloud
import com.unboundapex.octalink.ui.theme.Ink
import com.unboundapex.octalink.ui.theme.Paper
import com.unboundapex.octalink.ui.theme.Slate
import java.time.DayOfWeek

@Composable
fun ProfileSettingsScreen(
    sessionVm: SessionViewModel,
    onBack: () -> Unit,
    notifPrefsVm: NotificationPrefsViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val currentAppTheme by AppThemeStore.theme.collectAsState()
    val context = LocalContext.current

    val myMemberId = session.member?.id
    LaunchedEffect(myMemberId) {
        notifPrefsVm.observeFor(myMemberId)
    }
    val notifPrefs by notifPrefsVm.prefs.collectAsState()
    val classReminderSlots by notifPrefsVm.classReminderSlots.collectAsState()

    var notifDialogOpen by remember { mutableStateOf(false) }
    var classReminderDialogOpen by remember { mutableStateOf(false) }
    var batteryOptimDialogOpen by remember { mutableStateOf(false) }
    var leaveConfirmOpen by remember { mutableStateOf(false) }
    var pendingToggleType by remember { mutableStateOf<NotificationType?>(null) }

    // 배터리 최적화 제외 상태 — notifDialogOpen 가 토글될 때마다 재평가 (사용자가
    // OS 다이얼로그 다녀온 직후의 상태 반영).
    val isIgnoringBattery by remember(notifDialogOpen) {
        mutableStateOf(BatteryOptimizationHelper.isIgnoring(context))
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val type = pendingToggleType ?: return@rememberLauncherForActivityResult
        notifPrefsVm.setEnabled(type, true)
        if (!granted) {
            android.util.Log.w(
                "OctaLink.NotifPrefs",
                "POST_NOTIFICATIONS denied — toggle saved but no notifications will show",
            )
        }
        pendingToggleType = null
    }

    fun openExternal(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    PosseScreen(
        title = "설정",
        trailing = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "뒤로",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
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
                            val visiblePrefs = notifPrefs.filterKeys { it != NotificationType.SIGNUP_RESULT }
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

            item {
                ThemePickerCard(
                    current = currentAppTheme,
                    onSelect = { AppThemeStore.set(it) },
                )
            }

            item {
                PosseCard {
                    Text(
                        "정책 / 링크",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SocialCell(
                            icon = Icons.Outlined.PrivacyTip,
                            primary = "개인정보처리방침",
                            secondary = "외부 브라우저로 열기",
                            onClick = { openExternal(GymInfo.PRIVACY_POLICY_URL) },
                            modifier = Modifier.weight(1f),
                        )
                        SocialCell(
                            icon = Icons.Outlined.Shop,
                            primary = "Play 스토어",
                            secondary = "앱 페이지 열기",
                            onClick = { openExternal(GymInfo.PLAY_STORE_URL) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                PosseCard {
                    Text(
                        "앱 정보",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("버전", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("패키지", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(BuildConfig.APPLICATION_ID, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

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
                        color = Color(0xFFC8102E),
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
        AlertDialog(
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
                    // 표시 순서 — 사용 빈도/중요도 기준. SIGNUP_RESULT 는 PENDING 단계 전용이라 제외.
                    // 운영진(MASTER/CREATOR) 전용 알림 2종은 isMaster 일 때만 노출 — 회원에게 무의미.
                    val baseTypes = listOf(
                        NotificationType.COMMENT,
                        NotificationType.SKILL_UPDATED,
                        NotificationType.TOURNAMENT_DRAWN,
                        NotificationType.NEW_NOTICE,
                        NotificationType.NEW_POST_COMMENT,
                        NotificationType.MENTION,
                        NotificationType.CLASS_REMINDER,
                    )
                    val adminTypes = if (session.role.isMaster) listOf(
                        NotificationType.NEW_SIGNUP_PENDING,
                        NotificationType.NEW_SKILL_PROPOSAL,
                    ) else emptyList()
                    (baseTypes + adminTypes).forEach { type ->
                        if (type == NotificationType.CLASS_REMINDER) {
                            ClassReminderConfigRow(
                                selectedCount = classReminderSlots.size,
                                onClick = {
                                    notifDialogOpen = false
                                    classReminderDialogOpen = true
                                },
                            )
                            // CLASS_REMINDER 슬롯이 선택돼 있는데 배터리 최적화 제외가 안 돼 있으면
                            // 정시 발화 보장이 안 됨(Doze / 삼성 절전 앱). 안내 행 노출.
                            if (classReminderSlots.isNotEmpty() && !isIgnoringBattery) {
                                BatteryOptimizationRow(
                                    onClick = {
                                        notifDialogOpen = false
                                        batteryOptimDialogOpen = true
                                    },
                                )
                            }
                        } else {
                            NotificationToggleRow(
                                type = type,
                                enabled = notifPrefs[type] ?: type.defaultEnabled,
                                onToggle = { newValue ->
                                    if (newValue) {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                            val granted = ContextCompat.checkSelfPermission(
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
                notifDialogOpen = true
            },
            onSave = { newSelected ->
                notifPrefsVm.updateClassReminderSlots(newSelected)
                classReminderDialogOpen = false
                notifDialogOpen = true
            },
            onRequestPermissionIfNeeded = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        pendingToggleType = NotificationType.CLASS_REMINDER
                        notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            },
        )
    }

    if (batteryOptimDialogOpen) {
        BatteryOptimizationDialog(
            onDismiss = {
                batteryOptimDialogOpen = false
                notifDialogOpen = true
            },
            onGrant = {
                BatteryOptimizationHelper.launchRequest(context)
                batteryOptimDialogOpen = false
                // 다시 알림 설정 다이얼로그로 복귀 — 사용자가 OS 다이얼로그 마치고 돌아오면
                // isIgnoringBattery 가 재평가돼 행이 자동으로 사라짐(허용 시) 또는 유지(거부 시).
                notifDialogOpen = true
            },
        )
    }

    if (leaveConfirmOpen) {
        AlertDialog(
            onDismissRequest = { leaveConfirmOpen = false },
            title = { Text("정말 탈퇴하시겠습니까?", style = MaterialTheme.typography.titleLarge) },
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
                    color = Color(0xFFC8102E),
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

@Composable
private fun NotificationToggleRow(
    type: NotificationType,
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
            Text(type.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                type.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun ClassReminderConfigRow(
    selectedCount: Int,
    onClick: () -> Unit,
) {
    val type = NotificationType.CLASS_REMINDER
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(type.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (selectedCount == 0) "수업 미선택 · 탭하여 설정"
                else "${selectedCount}개 수업 알림 ON · 탭하여 변경",
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
 * 배터리 최적화 제외 안내 행 — CLASS_REMINDER 슬롯이 선택돼 있는데 권한 미부여 시 노출.
 * 노란/경고 톤으로 사용자 주의 환기 (정시 발화가 실패할 수 있음을 명시).
 */
@Composable
private fun BatteryOptimizationRow(
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "⚠ 정시 알림 보장 설정 필요",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "배터리 최적화 제외 권한이 없으면 절전 상태에서 수업 알림이 늦거나 안 옴 · 탭하여 설정",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "→",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * 배터리 최적화 제외 안내 + 권한 요청 다이얼로그.
 *
 * Android 표준 배터리 최적화는 시스템 다이얼로그로 해결되지만, OEM (특히 삼성 One UI) 의
 * "절전 앱" / "절대 절전 안 함 앱" 은 API 없음 — 사용자가 OS 설정에서 직접 처리해야 함.
 * 두 레이어 모두 명시.
 */
@Composable
private fun BatteryOptimizationDialog(
    onDismiss: () -> Unit,
    onGrant: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("정시 알림 보장 설정", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    "수업 30분 전 알림이 절전 상태에서도 정확한 시각에 도착하려면 두 가지 설정이 필요해요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "1. 배터리 최적화 제외 (아래 \"권한 요청\" 버튼)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Android 표준 절전 모드에서 OctaLink 의 알람을 정시에 깨우도록 시스템에 허용 요청.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "2. 삼성 폰: \"절대 절전 안 함 앱\" 에 OctaLink 추가",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "설정 → 디바이스 케어 → 배터리 → 백그라운드 사용 한도 → 절대 절전 안 함 앱 → OctaLink 추가. (API 없어 수동 설정 필요)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Text(
                "권한 요청",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable { onGrant() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
        dismissButton = {
            Text(
                "닫기",
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
private fun ClassReminderConfigDialog(
    initialSelected: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onRequestPermissionIfNeeded: () -> Unit,
) {
    var selected by remember(initialSelected) { mutableStateOf(initialSelected) }

    val weekdays = remember {
        listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }
    val groupSlots = remember {
        allWeeklyClassSlots()
            .filter { (day, slot) -> day == DayOfWeek.MONDAY && slot.name == "복싱 · 킥복싱 · MMA" }
            .map { it.second }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("수업 리마인더 설정", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    "참석할 수업을 선택하면 시작 30분 전에 알림을 보냅니다.\n수업 시간 / 요일 라벨을 탭하면 일괄 선택.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.width(50.dp))
                    weekdays.forEach { day ->
                        val colKeys = groupSlots
                            .map { classSlotKey(day, it) }
                            .toSet()
                        val allColOn = colKeys.all { it in selected }
                        Text(
                            dayOfWeekKr(day),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selected = if (allColOn) selected - colKeys else selected + colKeys
                                }
                                .padding(vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (allColOn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                groupSlots.forEach { slot ->
                    val timeLabel = "%02d:%02d".format(slot.start.hour, slot.start.minute)
                    val rowKeys = weekdays.map { classSlotKey(it, slot) }.toSet()
                    val allRowOn = rowKeys.all { it in selected }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            timeLabel,
                            modifier = Modifier
                                .width(50.dp)
                                .clickable {
                                    selected = if (allRowOn) selected - rowKeys else selected + rowKeys
                                }
                                .padding(vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (allRowOn) FontWeight.Bold else FontWeight.Medium,
                            color = if (allRowOn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        weekdays.forEach { day ->
                            val key = classSlotKey(day, slot)
                            val checked = key in selected
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selected = if (checked) selected - key else selected + key
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        selected = if (it) selected + key else selected - key
                                    },
                                )
                            }
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

private fun dayOfWeekKr(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "월"; DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"; DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"; DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

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
                background = Ink,
                surface = Canvas,
                accent = Blood,
                textColor = Bone,
            )
            ThemeOptionTile(
                option = AppTheme.LIGHT,
                selected = current == AppTheme.LIGHT,
                onClick = { onSelect(AppTheme.LIGHT) },
                modifier = Modifier.weight(1f),
                background = Cloud,
                surface = Paper,
                accent = Blood,
                textColor = Slate,
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

@Composable
private fun SocialCell(
    icon: ImageVector,
    primary: String,
    secondary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                primary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                secondary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
