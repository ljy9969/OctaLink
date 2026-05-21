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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.SkillScoreDoc
import com.unboundapex.octalink.data.schema.SkillScoreStatus
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 운영진 스킬 점수 제안 화면 — AdminScreen 의 "스킬 점수 입력 (제안 → 관장 검토)" 진입점.
 *
 * 흐름:
 *  1. 회원 선택 안 됨: 평가 대상 풀 2열 그리드 (CoachCommentScreen 와 동일 패턴)
 *  2. 회원 선택됨: 기존 점수 시계열 (status 칩 색으로 PROPOSED/APPROVED/REJECTED 구분) + "+ 새 점수 제안" 다이얼로그
 *  3. 다이얼로그: 6축 슬라이더 → status=PROPOSED 로 doc 생성. 관장 리뷰 대기.
 *
 * 권한: PosseApp 라우팅에서 isStaff 만 진입. Rules 가 byUserId + status='PROPOSED' 강제.
 */
@Composable
fun SkillScoreProposeScreen(
    onBack: () -> Unit,
    sessionVm: SessionViewModel,
    vm: SkillScoreProposeViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val members by vm.members.collectAsState()
    val selectedId by vm.selectedMemberId.collectAsState()
    val scores by vm.selectedScores.collectAsState()
    val nameById by vm.nameById.collectAsState()

    val selected = remember(selectedId, members) { members.firstOrNull { it.id == selectedId } }
    var proposeOpen by remember { mutableStateOf(false) }

    // 이 화면은 *제안* 전용. 관장이라도 PROPOSED → 본인 검토 경유 (감사 추적 / 일관성).
    // 관장 직접 평가는 AdminScreen "회원 스킬 점수 입력" 카드(관장 전용) 에서 별도 진입.

    PosseScreen(
        title = "Skill",
        subtitle = if (selected == null) "스킬 점수 제안 (관장 검토 대기)"
        else "${selected.name} 스킬 점수 제안",
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChipBtn(
                    text = "← 뒤로",
                    bg = MaterialTheme.colorScheme.surfaceVariant,
                    fg = MaterialTheme.colorScheme.onSurface,
                    onClick = onBack,
                )
                if (selected != null) {
                    ChipBtn(
                        text = "회원 변경",
                        bg = MaterialTheme.colorScheme.primary,
                        fg = MaterialTheme.colorScheme.onPrimary,
                        onClick = { vm.clearSelection() },
                    )
                    Spacer(Modifier.weight(1f))
                    ChipBtn(
                        text = "+ 새 점수 제안",
                        bg = MaterialTheme.colorScheme.primary,
                        fg = MaterialTheme.colorScheme.onPrimary,
                        onClick = { proposeOpen = true },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (selected == null) {
                MemberPicker(members = members, onPick = { vm.selectMember(it.id) })
            } else {
                ScoreList(member = selected, items = scores, nameById = nameById)
            }
        }
    }

    if (proposeOpen && selected != null) {
        ProposeDialog(
            member = selected,
            onDismiss = { proposeOpen = false },
            onSubmit = { skills ->
                val staff = session.member ?: return@ProposeDialog
                vm.propose(memberId = selected.id, byUserId = staff.id, skills = skills)
                proposeOpen = false
            },
        )
    }
}

@Composable
private fun MemberPicker(
    members: List<MemberDoc>,
    onPick: (MemberDoc) -> Unit,
) {
    if (members.isEmpty()) {
        PosseCard {
            Text(
                "평가 대상 회원이 없습니다.",
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

private val seoul = ZoneId.of("Asia/Seoul")
// 앱 전체 통일 날짜 포맷 — "5/20(수)".
private val dateFmt = DateTimeFormatter.ofPattern("M/d(EEE)", java.util.Locale.KOREAN)

@Composable
private fun ScoreList(
    member: MemberDoc,
    items: List<SkillScoreDoc>,
    nameById: Map<String, String>,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            PosseCard(leftStripeColor = member.belt.ringColor) {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${member.weightClass.displayName} · ${member.belt.displayName} 벨트 · 점수 ${items.size}건",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (items.isEmpty()) {
            item {
                PosseCard {
                    Text(
                        "아직 제안된 점수가 없습니다.\n상단 '+ 새 점수 제안'으로 첫 평가를 시작하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(items, key = { it.id }) { s ->
                ScoreRow(s, byUserName = nameById[s.byUserId])
            }
        }
    }
}

@Composable
private fun ScoreRow(s: SkillScoreDoc, byUserName: String?) {
    val avgPct = (((s.striking + s.grappling + s.stamina + s.technique + s.mental + s.speed) / 6f) * 100).roundToInt()
    val ld = s.evaluatedAt.atZone(seoul).toLocalDate()
    val dateStr = ld.format(dateFmt)
    // 제안자 이름이 풀에 없으면(탈퇴/CREATOR 이름 동기화 전 등) "·" 자체를 생략
    val titleLine = if (byUserName.isNullOrBlank()) "$dateStr · 평균 ${avgPct}점"
        else "$dateStr · 평균 ${avgPct}점 · $byUserName"
    PosseCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(titleLine, style = MaterialTheme.typography.titleMedium)
                Text(
                    skillSummary(s),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(s.status)
        }
    }
}

/**
 * 카드 폭 한 줄에 6개 다 넣으면 "그래플링" 등이 잘려 약자로 들어가야 했었음.
 * 3개씩 2줄로 분리 → SkillSlider 다이얼로그 라벨과 동일한 풀네임 사용 가능.
 */
private fun skillSummary(s: SkillScoreDoc): String {
    fun pct(f: Float) = (f * 100).roundToInt()
    val line1 = "스트라이킹 ${pct(s.striking)} · 그래플링 ${pct(s.grappling)} · 체력 ${pct(s.stamina)}"
    val line2 = "기술 ${pct(s.technique)} · 멘탈 ${pct(s.mental)} · 스피드 ${pct(s.speed)}"
    return "$line1\n$line2"
}

@Composable
private fun StatusChip(status: SkillScoreStatus) {
    val (label, bg) = when (status) {
        SkillScoreStatus.PROPOSED -> "검토 대기" to Color(0xFFFBC02D)
        SkillScoreStatus.APPROVED -> "확정" to Color(0xFF43A047)
        SkillScoreStatus.REJECTED -> "반려" to Color(0xFFC8102E)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ProposeDialog(
    member: MemberDoc,
    onDismiss: () -> Unit,
    onSubmit: (SkillSet) -> Unit,
) {
    // 기준선: 회원의 현재 [MemberDoc.skills] 스냅샷 (있으면). 없으면 0.5 (중앙) 부터 시작 — 시작 위치
    // 가 0 이면 슬라이더 인식이 어려운 UX 문제 회피.
    val initial = member.skills ?: SkillSet(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
    var striking by remember(member.id) { mutableStateOf(initial.striking) }
    var grappling by remember(member.id) { mutableStateOf(initial.grappling) }
    var stamina by remember(member.id) { mutableStateOf(initial.stamina) }
    var technique by remember(member.id) { mutableStateOf(initial.technique) }
    var mental by remember(member.id) { mutableStateOf(initial.mental) }
    var speed by remember(member.id) { mutableStateOf(initial.speed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${member.name} 스킬 점수 제안") },
        text = {
            Column {
                Text(
                    "제출 후 상태: 검토 대기 (관장 확정 시 프로필 차트에 반영)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
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
                "제안",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        onSubmit(SkillSet(striking, grappling, stamina, technique, mental, speed))
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
            "${(value * 100).roundToInt()}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun ChipBtn(
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
