package com.unboundapex.octalink.ui.screens.bracket

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.Member
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.avatarById
import com.unboundapex.octalink.data.avatarFor
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import com.unboundapex.octalink.data.session.SessionState
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.data.tournament.TournamentViewModel
import com.unboundapex.octalink.ui.components.AvatarTile
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.ui.components.WeightClassInfoDialog

@Composable
fun BracketDrawScreen(
    sessionVm: SessionViewModel,
    tournamentVm: TournamentViewModel,
    onBack: () -> Unit,
    onDrawComplete: () -> Unit,
) {
    val session by sessionVm.state.collectAsState()
    var weightFilter by remember { mutableStateOf<WeightClass?>(WeightClass.FEATHER) }
    var beltFilter by remember { mutableStateOf<Belt?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    // 추첨 풀 = APPROVED 회원 전원 (시드 40명 + 가입 승인된 신규 회원). 가입 플로우에서 새로 승인된
    // 회원이 즉시 합류됨. MemberDoc → Member 어댑터: BracketDrawScreen 의 기존 시그니처 유지.
    val approvedFlow = remember {
        RepositoryProvider.members.observeByStatus(MembershipStatus.APPROVED)
    }
    val approvedDocs by approvedFlow.collectAsState(initial = emptyList())

    // 관장(MASTER)은 추첨 대상에서 제외 — 토너먼트를 운영하는 쪽이지 본인이 출전하지 않음.
    // CREATOR/COACH 는 회원 자격으로 훈련 중인 경우가 많아 추첨 풀 유지.
    val drawPool: List<Member> = remember(approvedDocs) {
        approvedDocs
            .filter { it.role != Role.MASTER }
            .map { doc ->
                Member(
                    id = doc.id,
                    name = doc.name,
                    belt = doc.belt,
                    weightClass = doc.weightClass,
                    // 캐릭터는 성별 + 체급에서 자동 파생 (저장된 avatarId 무시).
                    avatarId = avatarFor(doc.gender, doc.weightClass).id,
                )
            }
    }

    val filtered = remember(weightFilter, beltFilter, drawPool) {
        drawPool
            .filter { m ->
                (weightFilter == null || m.weightClass == weightFilter) &&
                    (beltFilter == null || m.belt == beltFilter)
            }
            .let { list ->
                if (beltFilter == null) {
                    // 전체: 벨트 낮은 순 (화이트 → 블랙), 동순위면 이름순
                    list.sortedWith(compareBy({ it.belt.ordinal }, { it.name }))
                } else {
                    // 특정 벨트: 이름순
                    list.sortedBy { it.name }
                }
            }
    }

    var weightInfoOpen by remember { mutableStateOf(false) }

    PosseScreen(title = "Draw", subtitle = "토너먼트 추첨") {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DrawActionChip(
                    text = "ⓘ 체급 안내",
                    bg = Color(0xFFFBC02D),
                    fg = Color(0xFF1A1A1A),
                    onClick = { weightInfoOpen = true },
                )
                Spacer(Modifier.weight(1f))
                DrawActionChip(
                    text = "대진표로 돌아가기",
                    bg = Color(0xFF1E88E5),
                    fg = Color.White,
                    onClick = onBack,
                )
            }
            Spacer(Modifier.height(8.dp))

            // 체급 필터 (필수)
            Text(
                "체급 (필수)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WeightClass.values().forEachIndexed { idx, wc ->
                    WeightChip(
                        label = wc.shortLabel,
                        selected = weightFilter == wc,
                        gradient = weightGradient(idx),
                        onClick = { weightFilter = wc; selectedIds = emptySet() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 벨트 필터 (선택)
            Text(
                "벨트 (선택, 미선택 시 전체)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 정식 5단계 벨트만 필터 칩으로 노출 (UNKNOWN 은 운영자가 갱신 전 임시 상태)
                Belt.gradedValues.forEach { b ->
                    BeltChip(
                        belt = b,
                        selected = beltFilter == b,
                        onClick = {
                            beltFilter = if (beltFilter == b) null else b
                            selectedIds = emptySet()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "참가 후보 ${filtered.size}명",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                val allSelected = filtered.isNotEmpty() &&
                    filtered.all { it.id in selectedIds } &&
                    filtered.size <= 8
                val toggleLabel = if (allSelected || (filtered.size > 8 && selectedIds.size >= 8)) "선택 해제"
                else "전체 선택"
                DrawActionChip(
                    text = toggleLabel,
                    bg = MaterialTheme.colorScheme.primary,
                    fg = MaterialTheme.colorScheme.onPrimary,
                    onClick = {
                        selectedIds = if (allSelected) {
                            emptySet()
                        } else {
                            filtered.take(8).map { it.id }.toSet()
                        }
                    },
                )
            }
            Spacer(Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(filtered) { m ->
                    val checked = m.id in selectedIds
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(0.8f)) {
                            MemberRow(
                                member = m,
                                session = session,
                                checked = checked,
                                onToggle = {
                                    selectedIds = if (checked) selectedIds - m.id else selectedIds + m.id
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val members = filtered.filter { it.id in selectedIds }
                    tournamentVm.draw(members, weightFilter, beltFilter)
                    onDrawComplete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = selectedIds.size in 2..8,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    when {
                        selectedIds.size < 2 -> "최소 2명 선택"
                        selectedIds.size > 8 -> "최대 8명까지"
                        else -> "${selectedIds.size}명 추첨하기"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (weightInfoOpen) {
        WeightClassInfoDialog(onDismiss = { weightInfoOpen = false })
    }
}

@Composable
private fun DrawActionChip(
    text: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun MemberRow(member: Member, session: SessionState, checked: Boolean, onToggle: () -> Unit) {
    val effectiveAvatarId =
        if (member.name == session.name) session.avatarId else member.avatarId
    PosseCard(
        modifier = Modifier.clickable { onToggle() },
        padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        leftStripeColor = member.belt.ringColor,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarTile(
                avatar = avatarById(effectiveAvatarId),
                size = 40.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    member.belt.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            CheckIndicator(checked)
        }
    }
}

@Composable
private fun CheckIndicator(checked: Boolean) {
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (checked) MaterialTheme.colorScheme.primary
                else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text("✓", color = Color.White, fontWeight = FontWeight.Black)
        }
    }
}

// ----- 칩 컴포저블 -----

private val chipShape = RoundedCornerShape(50)
private const val UNSELECTED_ALPHA = 0.45f

/** 체급 칩의 배경 그라데이션. 인덱스에 따라 점점 진한 색조로. */
private fun weightGradient(index: Int): Brush {
    // 페더(밝은 오렌지/레드) → 헤비(딥 레드/마룬)로 진행
    val palette = listOf(
        Color(0xFFFF6B35) to Color(0xFFE53935),  // 페더
        Color(0xFFE53935) to Color(0xFFC8102E),  // 라이트
        Color(0xFFC8102E) to Color(0xFFAD1A1A),  // 웰터
        Color(0xFFAD1A1A) to Color(0xFF7B1010),  // 미들
        Color(0xFF7B1010) to Color(0xFF3E0606),  // 헤비
    )
    val (start, end) = palette[index.coerceIn(palette.indices)]
    return Brush.horizontalGradient(listOf(start, end))
}

@Composable
private fun WeightChip(
    label: String,
    selected: Boolean,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .height(32.dp)
            .alpha(if (selected) 1f else UNSELECTED_ALPHA)
            .clip(chipShape)
            .background(gradient)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor), chipShape)
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun beltTextColor(belt: com.unboundapex.octalink.data.Belt): Color = when (belt) {
    com.unboundapex.octalink.data.Belt.WHITE -> Color(0xFF111111)
    else -> Color.White
}

@Composable
private fun BeltChip(
    belt: com.unboundapex.octalink.data.Belt,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .height(32.dp)
            .alpha(if (selected) 1f else UNSELECTED_ALPHA)
            .clip(chipShape)
            .background(belt.ringColor)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor), chipShape)
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            belt.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = beltTextColor(belt),
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
