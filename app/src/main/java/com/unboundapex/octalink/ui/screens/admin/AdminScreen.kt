package com.unboundapex.octalink.ui.screens.admin

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.isCreator
import com.unboundapex.octalink.data.schema.isMaster
import com.unboundapex.octalink.data.schema.isStaff
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.ui.screens.home.SaveState
import com.unboundapex.octalink.ui.screens.home.WeeklyMissionViewModel

/**
 * 운영진/관장/창조자 전용 페이지 — 하단 nav 탭 "운영" 진입점.
 *
 * 권한별 카드 가시성:
 * - 운영진 (COACH+): 회원 코멘트 / 출결 검토 / 토너먼트 / 공지 / 스킬 입력 (미구현 placeholder)
 * - 관장 (MASTER+): 회원 가입 승인 큐 (활성) + 스킬 검토 (placeholder)
 * - 창조자 (CREATOR): 회원 역할 부여 (별도 [com.unboundapex.octalink.ui.screens.creator.CreatorScreen])
 *
 * 회원(MEMBER)에게는 탭 자체가 안 보임 (PosseApp 의 동적 tabs 처리).
 */
@Composable
fun AdminScreen(
    sessionVm: SessionViewModel,
    onOpenCreator: () -> Unit,
    onOpenCoachComment: () -> Unit = {},
    onOpenBracket: () -> Unit = {},
    onOpenSkillScorePropose: () -> Unit = {},
    approvalVm: MemberApprovalViewModel = viewModel(),
    weeklyMissionVm: WeeklyMissionViewModel = viewModel(),
    skillReviewVm: SkillScoreReviewViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val pendingMembers by approvalVm.pending.collectAsState()
    val unknownBeltMembers by approvalVm.unknownBelt.collectAsState()
    val approvedMembers by approvalVm.approvedMembers.collectAsState()
    val weeklyMission by weeklyMissionVm.mission.collectAsState()
    val missionSaveState by weeklyMissionVm.saveState.collectAsState()
    val pendingSkillScores by skillReviewVm.pending.collectAsState()
    val role = session.role

    // 스킬 점수 편집 다이얼로그 — null 이면 닫힘.
    var skillEditTarget by remember { mutableStateOf<MemberDoc?>(null) }
    // 주간 미션 작성/수정 다이얼로그 토글.
    var missionDialogOpen by remember { mutableStateOf(false) }

    val subtitle = when {
        role.isCreator -> "운영 전체 + 권한 부여 (창조자)"
        role.isMaster -> "운영 전체 (관장)"
        role.isStaff -> "일상 운영 (코치)"
        else -> ""
    }

    PosseScreen(title = "Admin", subtitle = subtitle) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // 운영진 공통 (COACH + MASTER + CREATOR) — 미구현 placeholder들
            // 스트라이프: 코치 블루 (운영진 공통의 가장 낮은 권한 색)
            if (role.isStaff) {
                item {
                    PosseCard(leftStripeColor = StripeStaff) {
                        Text("운영진 공통", style = MaterialTheme.typography.titleMedium, color = StripeStaff)
                        Spacer(Modifier.height(4.dp))
                        StaffAction("회원 한 줄 코멘트 작성") { onOpenCoachComment() }
                        StaffAction("토너먼트 추첨 / 대진 관리") { onOpenBracket() }
                        StaffAction("스킬 점수 입력 (제안 → 관장 검토)") { onOpenSkillScorePropose() }
                        StaffAction(
                            if (weeklyMission == null) "주간 미션 작성"
                            else "주간 미션 수정"
                        ) { missionDialogOpen = true }
                        Spacer(Modifier.height(4.dp))
                        FootnoteText("출결 검토는 출석 탭의 운영진 모드, 공지 작성은 커뮤니티 탭에서 진입.")
                    }
                }
            }

            // 관장 — 회원 가입 승인 큐 (액션 필요 카드 — 앰버 스트라이프)
            if (role.isMaster) {
                item {
                    PosseCard(leftStripeColor = StripeApprovalQueue) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "회원 가입 승인",
                                style = MaterialTheme.typography.titleMedium,
                                color = StripeApprovalQueue,
                                modifier = Modifier.weight(1f),
                            )
                            PendingBadge(count = pendingMembers.size)
                        }
                        Spacer(Modifier.height(4.dp))
                        if (pendingMembers.isEmpty()) {
                            Text(
                                "현재 대기 중인 가입 신청이 없습니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            pendingMembers.forEach { m ->
                                PendingMemberRow(
                                    member = m,
                                    onApprove = { approvalVm.approve(m.id) },
                                    onReject = { approvalVm.reject(m.id) },
                                )
                            }
                        }
                    }
                }
                // 미등급 벨트(UNKNOWN) 회원 — 관장이 정식 벨트 지정 (체급은 회원 본인이 변경)
                item {
                    PosseCard(leftStripeColor = StripeUnknownBelt) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "미등급 벨트 지정",
                                style = MaterialTheme.typography.titleMedium,
                                color = StripeUnknownBelt,
                                modifier = Modifier.weight(1f),
                            )
                            PendingBadge(count = unknownBeltMembers.size)
                        }
                        Spacer(Modifier.height(4.dp))
                        if (unknownBeltMembers.isEmpty()) {
                            Text(
                                "미등급 회원이 없습니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            unknownBeltMembers.forEach { m ->
                                UnknownBeltRow(
                                    member = m,
                                    onAssignBelt = { b -> approvalVm.assignBelt(m.id, b) },
                                )
                            }
                        }
                    }
                }
                // 관장 전용 — 코치/관장 본인 입력 PROPOSED 스킬 점수 검토 큐
                item {
                    PosseCard(leftStripeColor = StripeSkillReview) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "스킬 점수 검토",
                                style = MaterialTheme.typography.titleMedium,
                                color = StripeSkillReview,
                                modifier = Modifier.weight(1f),
                            )
                            PendingBadge(count = pendingSkillScores.size)
                        }
                        Spacer(Modifier.height(4.dp))
                        if (pendingSkillScores.isEmpty()) {
                            Text(
                                "대기 중인 스킬 점수 제안이 없습니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val myStaffId = session.member?.id
                            pendingSkillScores.forEach { item ->
                                PendingSkillScoreRow(
                                    item = item,
                                    onApprove = {
                                        myStaffId?.let { skillReviewVm.approve(item, it) }
                                    },
                                    onReject = {
                                        myStaffId?.let { skillReviewVm.reject(item, it) }
                                    },
                                )
                            }
                        }
                    }
                }
                // 관장 전용 — 회원 스킬 점수 입력 (HexagonSkillChart 데이터)
                item {
                    PosseCard(leftStripeColor = StripeMaster) {
                        Text(
                            "회원 스킬 점수 입력",
                            style = MaterialTheme.typography.titleMedium,
                            color = StripeMaster,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "탭하면 프로필 차트 (6축) 점수를 0~100 슬라이더로 설정",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (approvedMembers.isEmpty()) {
                            Text(
                                "승인된 회원이 없습니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // 2열 그리드 — 회원 수가 늘면 카드가 너무 길어지지 않도록.
                            approvedMembers.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    pair.forEach { m ->
                                        SkillTargetRow(
                                            member = m,
                                            onClick = { skillEditTarget = m },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    // 홀수 인원의 마지막 행 — 빈 칸으로 폭 균형 유지
                                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 창조자 단독 (CREATOR) — 회원 역할 부여 페이지 진입.
            // 블랙홀 그라데이션 스트라이프 (보라→블랙→어선 디스크 주황) — 가장 강한 권한의 시각 시그니처
            if (role.isCreator) {
                item {
                    PosseCard(leftStripeBrush = StripeCreatorBrush) {
                        Text("창조자 전용", style = MaterialTheme.typography.titleMedium, color = StripeCreatorAccent)
                        Spacer(Modifier.height(4.dp))
                        StaffAction("권한 부여 페이지 (회원 → 코치 / 관장 승격)") {
                            onOpenCreator()
                        }
                        Spacer(Modifier.height(4.dp))
                        FootnoteText("관장 계정 탈취 시 권한 상승 공격 차단 위해 분리됨")
                    }
                }
            }
        }
    }

    skillEditTarget?.let { target ->
        SkillEditDialog(
            member = target,
            onDismiss = { skillEditTarget = null },
            onSave = { skills ->
                val staff = session.member ?: return@SkillEditDialog
                approvalVm.assignSkills(target.id, byUserId = staff.id, skills = skills)
                skillEditTarget = null
            },
        )
    }

    if (missionDialogOpen) {
        WeeklyMissionDialog(
            initialText = weeklyMission?.text.orEmpty(),
            saveState = missionSaveState,
            onSave = { text ->
                val staff = session.member ?: return@WeeklyMissionDialog
                weeklyMissionVm.save(text, staff.name)
            },
            onDismiss = {
                missionDialogOpen = false
                weeklyMissionVm.resetSaveState()
            },
        )
        LaunchedEffect(missionSaveState) {
            if (missionSaveState is SaveState.Done) {
                missionDialogOpen = false
                weeklyMissionVm.resetSaveState()
            }
        }
    }
}

@Composable
private fun WeeklyMissionDialog(
    initialText: String,
    saveState: SaveState,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // initialText 가 바뀌면(첫 진입 / 다른 doc) prefill 갱신.
    var text by remember(initialText) { mutableStateOf(initialText) }
    val isSaving = saveState is SaveState.Saving

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (initialText.isBlank()) "주간 미션 작성" else "주간 미션 수정") },
        text = {
            Column {
                Text(
                    "모든 회원의 홈 카드에 즉시 표시됩니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    label = { Text("미션 내용 (불릿 가능, 최대 500자)") },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${text.length} / 500",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (saveState is SaveState.Error) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        saveState.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            if (isSaving) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(16.dp).height(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "저장 중…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            } else {
                Text(
                    "저장",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = text.isNotBlank()) { onSave(text) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        },
        dismissButton = {
            Text(
                "취소",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable(enabled = !isSaving) { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
    )
}

@Composable
private fun SkillEditDialog(
    member: MemberDoc,
    onDismiss: () -> Unit,
    onSave: (SkillSet) -> Unit,
) {
    val initial = member.skills ?: SkillSet.EMPTY
    var striking by remember(member.id) { mutableStateOf(initial.striking) }
    var grappling by remember(member.id) { mutableStateOf(initial.grappling) }
    var stamina by remember(member.id) { mutableStateOf(initial.stamina) }
    var technique by remember(member.id) { mutableStateOf(initial.technique) }
    var mental by remember(member.id) { mutableStateOf(initial.mental) }
    var speed by remember(member.id) { mutableStateOf(initial.speed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${member.name} 스킬 평가") },
        text = {
            Column {
                SkillSlider("스트라이킹", striking) { striking = it }
                SkillSlider("그래플링", grappling) { grappling = it }
                SkillSlider("체력", stamina) { stamina = it }
                SkillSlider("기술", technique) { technique = it }
                SkillSlider("멘탈", mental) { mental = it }
                SkillSlider("스피드", speed) { speed = it }
            }
        },
        confirmButton = {
            Text(
                "저장",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable {
                        onSave(SkillSet(striking, grappling, stamina, technique, mental, speed))
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
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
    )
}

@Composable
private fun SkillSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    // Slider 가 다이얼로그 폭을 가득 채우면서 active track 색이 카드 배경 톤과 비슷해
    // 슬라이더 인식이 어려운 문제 — (1) 가로 padding 으로 길이 단축, (2) inactive track
    // 을 밝은 회색으로 두꺼이 보이게, (3) thumb 을 흰색으로 강조.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(64.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color(0xFF555560),
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(
            "${(value * 100).toInt()}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(36.dp),
        )
    }
}

// AdminScreen 카드 스트라이프 색상 — 권한 계층 + 작업 종류로 시각 구분.
// 각 카드는 고유 색이어야 함 (중복 금지).
private val StripeStaff = Color(0xFF1E88E5)           // 코치 블루 — 운영진 공통 카드
private val StripeApprovalQueue = Color(0xFFFBC02D)   // 앰버 — 회원 가입 승인 큐
private val StripeUnknownBelt = Color(0xFF00897B)     // 티얼 — 미등급 벨트 지정 (앰버 회피)
private val StripeMaster = Color(0xFFC8102E)          // 관장 빨강 — 스킬 점수 입력
private val StripeSkillReview = Color(0xFFEC407A)     // 핑크 — 스킬 점수 검토 큐 (관장 빨강과 구분)

// 창조자 전용 — 블랙홀 그라데이션 (앰버 인접색 회피)
// 위에서부터: 이벤트 호라이즌 보라 → 특이점 검정 → 어센션 디스크 주황. 8dp 폭 vertical gradient
private val StripeCreatorBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color(0xFF7B1FA2),  // 보라 (event horizon)
        0.35f to Color(0xFF1A0033),  // 거의 검정 (보라 끝)
        0.55f to Color(0xFF000000),  // 순검정 (특이점)
        0.75f to Color(0xFF3E0606),  // 어두운 적색 (디스크 안쪽)
        1.00f to Color(0xFFFF6F00),  // 주황 (accretion disk)
    )
)
private val StripeCreatorAccent = Color(0xFFAB47BC)   // 보라 — 타이틀 텍스트 (그라데이션 상단과 매칭)

private val reviewSeoulZone = java.time.ZoneId.of("Asia/Seoul")
private val reviewDateFmt = java.time.format.DateTimeFormatter.ofPattern("MM/dd")

/** 검토 큐 한 행 — 회원 이름 + 6축 점수 평균 + 제안자 + 평가일 + 승인/반려 칩. */
@Composable
private fun PendingSkillScoreRow(
    item: PendingReviewItem,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val s = item.score
    val avg = (((s.striking + s.grappling + s.stamina + s.technique + s.mental + s.speed) / 6f) * 100).toInt()
    val date = s.evaluatedAt.atZone(reviewSeoulZone).toLocalDate().format(reviewDateFmt)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${item.memberName} · 평균 ${avg}점",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${date} · 제안: ${item.byUserName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ActionChip(
                label = "승인",
                bg = MaterialTheme.colorScheme.primary,
                fg = MaterialTheme.colorScheme.onPrimary,
                onClick = onApprove,
            )
            Spacer(Modifier.width(6.dp))
            ActionChip(
                label = "반려",
                bg = MaterialTheme.colorScheme.surfaceVariant,
                fg = MaterialTheme.colorScheme.onSurface,
                onClick = onReject,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "스트라이킹 ${(s.striking * 100).toInt()} · 그래플링 ${(s.grappling * 100).toInt()} · 체력 ${(s.stamina * 100).toInt()}\n" +
                "기술 ${(s.technique * 100).toInt()} · 멘탈 ${(s.mental * 100).toInt()} · 스피드 ${(s.speed * 100).toInt()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PendingMemberRow(
    member: MemberDoc,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(member.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${member.weightClass.displayName} · ${member.belt.displayName} 벨트",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionChip(
            label = "승인",
            bg = MaterialTheme.colorScheme.primary,
            fg = MaterialTheme.colorScheme.onPrimary,
            onClick = onApprove,
        )
        Spacer(Modifier.width(6.dp))
        ActionChip(
            label = "거부",
            bg = MaterialTheme.colorScheme.surfaceVariant,
            fg = MaterialTheme.colorScheme.onSurface,
            onClick = onReject,
        )
    }
}

/**
 * UNKNOWN 벨트 회원 한 줄 — 5단 벨트 칩으로 즉시 지정.
 * 체급(weightClass)은 회원 본인 권한 — 여기엔 표시만, 변경 UI 없음.
 */
@Composable
private fun UnknownBeltRow(
    member: MemberDoc,
    onAssignBelt: (Belt) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            "${member.name} · ${member.weightClass.displayName}",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        // 벨트 칩은 동일 너비(weight 1f)로 정렬 — 텍스트 길이로 크기 흔들리지 않게.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Belt.gradedValues.forEach { b ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(b.ringColor)
                        .clickable { onAssignBelt(b) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = b.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (b == Belt.WHITE) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * 스킬 점수 편집 진입 셀 — 회원 이름 + 평균 점수 한 줄. 2열 그리드 1셀 차지.
 * 평균 점수가 있는 회원만 점수를 노출하지만 단일 Row 라 행 높이는 항상 동일.
 */
@Composable
private fun SkillTargetRow(
    member: MemberDoc,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avg = member.skills?.let { s ->
        val sum = s.striking + s.grappling + s.stamina + s.technique + s.mental + s.speed
        (sum / 6f * 100).toInt()
    }
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            member.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (avg != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                "${avg}점",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PendingBadge(count: Int) {
    if (count <= 0) return
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun ActionChip(
    label: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun StaffAction(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "▸ $label",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FootnoteText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
