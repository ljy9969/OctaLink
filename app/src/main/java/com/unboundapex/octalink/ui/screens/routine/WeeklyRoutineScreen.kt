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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.schema.RoutineDay
import com.unboundapex.octalink.data.schema.RoutineDrill
import com.unboundapex.octalink.data.canUseAiRoutine
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

    // Phase 1 베타 비용 통제 — 화이트리스트 (CREATOR + AI_ROUTINE_BETA_UIDS) 외엔 진입 차단.
    // 서버 onCall + Firestore rules 도 동일 게이트.
    if (!session.canUseAiRoutine()) {
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

    // 비용 정책: 한 주에 1회만 생성. 일단 doc 이 만들어지면 그 주 동안 재요청 불가
    // (Vertex AI + YouTube Data API quota 보호). 다음 주가 되면 weekId 가 바뀌어 새 doc 생성 가능.
    PosseScreen(
        subtitle = "AI 보강 루틴",
        subtitleEmphasis = listOf("AI"),
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
                item {
                    EmptyRoutineCard(
                        genState = genState,
                        onGenerate = { vm.generate(difficulty = it, force = false) },
                    )
                }
            } else {
                doc.days.forEach { day ->
                    item { DayCard(day = day) }
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
private fun DayCard(day: RoutineDay) {
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
            DrillRow(drill = drill)
            if (i != day.drills.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DrillRow(drill: RoutineDrill) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
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
}

/**
 * 드릴 시각 자리 — Phase 1.
 *
 *  - `videoId` 있으면: `img.youtube.com/vi/{id}/hqdefault.jpg` 썸네일 + ▶ 오버레이.
 *    탭 시 `youtube.com/watch?v={id}` 로 바로 진입 (해당 영상 재생).
 *  - `videoId` 없으면 (API quota 초과 / 검색 실패): 빨강 ▶ 박스 + 탭 시 검색 결과 페이지 폴백.
 *
 * Phase 4 에서 자체 도장 큐레이션 라이브러리 도입 시 이 컴포저블 교체 예정.
 */
@Composable
private fun DrillVisual(drill: RoutineDrill) {
    val context = LocalContext.current
    val videoId = drill.videoId
    val query = drill.youtubeQuery
    val hasContent = !videoId.isNullOrBlank() || query.isNotBlank()

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (videoId.isNullOrBlank() && query.isNotBlank()) Color(0xFFCC0000)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(enabled = hasContent) {
                runCatching {
                    val url = if (!videoId.isNullOrBlank()) {
                        "https://www.youtube.com/watch?v=$videoId"
                    } else {
                        "https://www.youtube.com/results?search_query=" +
                            java.net.URLEncoder.encode(query, "UTF-8")
                    }
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url),
                        ),
                    )
                }.onFailure {
                    android.util.Log.e("OctaLink.WeeklyRoutine", "YouTube launch failed", it)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            !videoId.isNullOrBlank() -> {
                // 썸네일 + 어두운 그라데이션 + 가운데 ▶.
                coil.compose.AsyncImage(
                    model = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                    contentDescription = drill.koName,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xCCCC0000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "▶",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            query.isNotBlank() -> {
                Text(
                    "▶",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            else -> {
                Text(
                    "준비중",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyRoutineCard(
    genState: GenerateState,
    onGenerate: (difficulty: String) -> Unit,
) {
    // 난이도 선택은 이 카드 안에서만 살아있음 (생성 후엔 사라짐 → 재생성 불가 → 비용 보호).
    var difficulty by remember { mutableStateOf(DIFFICULTY_INTERMEDIATE) }
    PosseCard {
        Text(
            "이번 주 AI 보강 루틴이 아직 없어요.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            // 폰 화면 폭에 맞춰 자연스럽게 wrap 되도록 강제 줄바꿈(\n) 제거.
            "난이도를 고른 뒤 버튼을 눌러주세요.\n\n부족한 스킬과 한 줄 코멘트를 보고 30분 이내의\n짧은 드릴을 만들어드립니다.\n\n(일주일에 1회만 생성 가능)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "난이도",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        DifficultySelector(
            current = difficulty,
            onSelect = { difficulty = it },
            enabled = genState !is GenerateState.Loading,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuroraGradientButton(
                label = if (genState is GenerateState.Loading) "요청 중…" else "이번 주 루틴 받기",
                enabled = genState !is GenerateState.Loading,
                onClick = { onGenerate(difficulty) },
            )
            if (genState is GenerateState.Loading) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** 난이도 칩 3개 — 사용자가 가벼움 / 보통 / 빡셈 선택. Cloud Function 으로 그대로 전달. */
@Composable
private fun DifficultySelector(current: String, onSelect: (String) -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        DifficultyChip("초급", DIFFICULTY_BEGINNER, BeginnerBrush, current, enabled, onSelect)
        DifficultyChip("중급", DIFFICULTY_INTERMEDIATE, IntermediateBrush, current, enabled, onSelect)
        DifficultyChip("고급", DIFFICULTY_ADVANCED, AdvancedBrush, current, enabled, onSelect)
    }
}

/**
 * 난이도별 그라데이션 — 강도 직관 (시원함 → 따뜻함 → 격렬함).
 *
 *  - 초급: emerald → cyan — 가볍고 시원한 톤
 *  - 중급: amber → red — 본격 워밍업 톤
 *  - 고급: deep red → violet — 격렬 + 한계 도전 톤 (오로라 CTA 와도 톤 연속)
 */
private val BeginnerBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF10B981), Color(0xFF06B6D4)),
)
private val IntermediateBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFF59E0B), Color(0xFFEF4444)),
)
private val AdvancedBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFDC2626), Color(0xFF7C3AED)),
)

@Composable
private fun DifficultyChip(
    label: String,
    value: String,
    selectedBrush: Brush,
    current: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val selected = current == value
    val labelColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) Modifier.background(selectedBrush)
                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            )
            .clickable(enabled = enabled) { onSelect(value) }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// 난이도 상수 — Cloud Function 의 difficulty 인자와 1:1 매핑.
internal const val DIFFICULTY_BEGINNER = "BEGINNER"
internal const val DIFFICULTY_INTERMEDIATE = "INTERMEDIATE"
internal const val DIFFICULTY_ADVANCED = "ADVANCED"

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

