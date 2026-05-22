package com.unboundapex.octalink.ui.screens.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.RoutineDay
import com.unboundapex.octalink.data.schema.RoutineDrill
import com.unboundapex.octalink.data.schema.isCreator
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.HexagonSkillChart
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.ui.components.SkillStat
import com.unboundapex.octalink.ui.screens.home.axisLabelKo

/**
 * AI 주간 보강 루틴 상세 화면.
 *
 *  - 상단: 헥사곤 차트 + 약축 강조 칩 + 한 줄 코치 피드백
 *  - 중단: 요일별 드릴 카드 (운동 GIF + 한국어 이름 + 셋트 + 시간 + 한국어 설명)
 *  - 하단: "AI 가 참고한 정보" — 최근 한 줄 코멘트 N건 (referencedCommentIds)
 *  - 우상단 액션: "새로 받기" — Cloud Function 재호출 (force=true).
 *
 * 루틴 doc 미존재 시 안내 + "이번 주 루틴 받기" CTA.
 */
@Composable
fun WeeklyRoutineScreen(
    sessionVm: SessionViewModel,
    vm: WeeklyRoutineViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val memberId = session.member?.id

    // Phase 1 베타 비용 통제 — 창조자 외엔 진입 차단 (서버 onCall + Firestore rules 도 동일 게이트).
    if (!session.role.isCreator) {
        PosseScreen(subtitle = "AI 보강 루틴") {
            PosseCard {
                Text(
                    "이 기능은 베타 검증 중이라 아직 일반 공개 전이에요.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Vertex AI 사용량 / 추천 품질을 안정화하면 모든 회원에게 열어드릴게요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LaunchedEffect(memberId) { vm.bind(memberId) }
    val routine by vm.routine.collectAsState()
    val genState by vm.genState.collectAsState()
    val skills = session.member?.skills?.toStats() ?: defaultSkillStats()

    val refComments by rememberReferencedComments(memberId, routine?.referencedCommentIds)

    PosseScreen(
        subtitle = "AI 보강 루틴",
        subtitleEmphasis = listOf("AI"),
        trailing = {
            if (memberId != null) {
                OutlinedButton(onClick = { vm.generate(force = true) }) {
                    Text(if (genState is GenerateState.Loading) "생성 중…" else "새로 받기")
                }
            }
        },
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                PosseCard {
                    HexagonSkillChart(
                        skills = skills,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                    val focus = routine?.focusSkills.orEmpty()
                    if (focus.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "이번 주 부족한 부분:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            focus.forEach { axis ->
                                FocusChip(axisLabelKo(axis))
                            }
                        }
                    }
                    val feedback = routine?.weeklyFeedback.orEmpty()
                    if (feedback.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            feedback,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            val doc = routine
            if (doc == null) {
                item { EmptyRoutineCard(genState = genState, onGenerate = { vm.generate(false) }) }
            } else {
                doc.days.forEachIndexed { dayIdx, day ->
                    item {
                        DayCard(
                            day = day,
                            onDrillToggle = { drillIdx, done, skipped ->
                                vm.setDrillState(doc.weekId, dayIdx, drillIdx, done, skipped)
                            },
                        )
                    }
                }

                item {
                    PosseCard {
                        Text("AI 가 참고한 정보", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "최근 ${doc.referencedCommentIds.size}건의 관장 한 줄 코멘트 + 6축 스킬 점수 + 벨트 / 체급 / 입관 기간을 함께 보고 작성했어요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (refComments.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            refComments.forEach { c ->
                                Text(
                                    "· $c",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            (genState as? GenerateState.Error)?.let { err ->
                item {
                    Text(
                        err.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DayCard(
    day: RoutineDay,
    onDrillToggle: (drillIdx: Int, done: Boolean, skipped: Boolean) -> Unit,
) {
    val totalMin = day.drills.sumOf { it.durationMin }
    PosseCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${day.day} · ${day.title}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "총 ${totalMin}분",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        day.drills.forEachIndexed { i, drill ->
            DrillRow(
                drill = drill,
                onMark = { done, skipped -> onDrillToggle(i, done, skipped) },
            )
            if (i != day.drills.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DrillRow(
    drill: RoutineDrill,
    onMark: (done: Boolean, skipped: Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            DrillVisual(drill)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    drill.koName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${drill.sets} · ${drill.durationMin}분 · ${axisLabelKo(drill.targetAxis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (drill.desc.isNotBlank()) {
                    Text(
                        drill.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrillStatePill(
                label = if (drill.done) "✓ 했음" else "했음",
                selected = drill.done,
                tint = Color(0xFF27AE60),
                onClick = { onMark(!drill.done, false) },
            )
            DrillStatePill(
                label = if (drill.skipped) "✕ 건너뜀" else "건너뜀",
                selected = drill.skipped,
                tint = Color(0xFFE57373),
                onClick = { onMark(false, !drill.skipped) },
            )
        }
    }
}

@Composable
private fun DrillVisual(drill: RoutineDrill) {
    val context = LocalContext.current
    val gifUrl = drill.gifUrl
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!gifUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(gifUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = drill.koName,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // ExerciseDB 매핑 실패 — Phase 3 에서 YouTube 폴백으로 대체 예정.
            Text(
                "동작\n준비중",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrillStatePill(label: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    val bg = if (selected) tint.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun EmptyRoutineCard(genState: GenerateState, onGenerate: () -> Unit) {
    PosseCard {
        Text(
            "이번 주 AI 보강 루틴이 아직 없어요.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "부족한 스킬 + 한 줄 코멘트를 보고 일별 30분 이내의 짧은 드릴을 만들어드립니다!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuroraGradientButton(
                label = if (genState is GenerateState.Loading) "받는 중…" else "이번 주 루틴 받기",
                enabled = genState !is GenerateState.Loading,
                onClick = onGenerate,
            )
            if (genState is GenerateState.Loading) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * 오로라 그라데이션 CTA — AI / 우주 무드를 위한 4색 linear gradient.
 *
 * Indigo → Violet → Magenta → Cyan 흐름. Material `Button` 은 단색 container 만 받으므로
 * Box 기반 커스텀. 비활성 시 alpha 0.4 + clickable 차단.
 */
@Composable
private fun AuroraGradientButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val auroraBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF4F46E5), // 딥 인디고 — 우주
            Color(0xFF9333EA), // 바이올렛 — AI
            Color(0xFFEC4899), // 마젠타 — 오로라 메인
            Color(0xFF06B6D4), // 시안 — 오로라 끝자락
        ),
    )
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(24.dp))
            .background(auroraBrush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** SkillSet null 시 fallback — 모든 축 50% 로 평평하게. */
private fun defaultSkillStats(): List<SkillStat> = listOf(
    SkillStat("스트라이킹", 0.5f),
    SkillStat("그래플링", 0.5f),
    SkillStat("체력", 0.5f),
    SkillStat("기술", 0.5f),
    SkillStat("멘탈", 0.5f),
    SkillStat("스피드", 0.5f),
)

/**
 * 참고 코멘트 텍스트 묶음 — `members/{uid}/comments/{commentId}` 를 observeByMember 로 받아
 * referencedCommentIds 만 필터링. 화면이 사라지면 자동 cancel.
 */
@Composable
private fun rememberReferencedComments(
    memberId: String?,
    ids: List<String>?,
): State<List<String>> {
    val state = remember(memberId, ids) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(memberId, ids) {
        if (memberId.isNullOrBlank() || ids.isNullOrEmpty()) {
            state.value = emptyList()
            return@LaunchedEffect
        }
        runCatching {
            RepositoryProvider.comments.observeByMember(memberId).collect { list ->
                state.value = list
                    .filter { it.id in ids }
                    .map { c -> "${c.classDate} · ${c.byMasterName}: ${c.text}" }
            }
        }
    }
    return state
}
