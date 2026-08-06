package com.unboundapex.octalink.ui.screens.routine

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.schema.RoutineDay
import com.unboundapex.octalink.data.schema.RoutineDrill
import com.unboundapex.octalink.data.canUseAiRoutine
import com.unboundapex.octalink.data.isUnset
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.HexagonSkillChart
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.ui.components.SkillStat
import com.unboundapex.octalink.ui.components.TagChip
import com.unboundapex.octalink.ui.components.tagColor
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
        PosseScreen(title = "AI 맞춤 루틴") {
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
    // 운영자가 아직 스킬 점수를 부여하지 않은 신규 회원은 EmptyRoutineCard 에서 자가 점수 입력 가능.
    // 입력값은 이번 회차 generate 호출에만 전달되고 회원 프로필에는 저장되지 않음.
    val needsSelfRating = session.member?.skills.isUnset()
    // 자가입력 상태 — 화면 레벨로 끌어올려 EmptyRoutineCard 의 칩 선택이 헥사곤 차트와 즉시 연동.
    // 키는 SkillSet 필드명. 미선택 축은 맵에 없음 (헥사곤은 0, 서버는 0.5 fallback).
    val selfRatedState: SnapshotStateMap<String, Float>? = remember(needsSelfRating) {
        if (needsSelfRating) mutableStateMapOf() else null
    }
    val skills = when {
        // 자가입력 모드: 선택한 축만 값 반영, 미선택 축은 0 → 헥사곤이 시각적으로 "비어있게" 시작.
        needsSelfRating && selfRatedState != null -> SkillAxes.map {
            SkillStat(it.koLabel, selfRatedState[it.fieldKey] ?: 0f)
        }
        else -> session.member?.skills?.toStats() ?: defaultSkillStats()
    }

    // 비용 정책: 한 주에 1회만 생성. 일단 doc 이 만들어지면 그 주 동안 재요청 불가
    // (Vertex AI + YouTube Data API quota 보호). 다음 주가 되면 weekId 가 바뀌어 새 doc 생성 가능.
    PosseScreen(title = "AI 맞춤 루틴") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                PosseCard {
                    // 1:1 aspect ratio 면 폰 폭만큼 세로가 잡혀 EmptyRoutineCard 가 화면 밖으로 밀림.
                    // 240dp 고정으로 줄여 → 빈 루틴 상태가 한 화면에 다 들어옴. 생성 후 화면에서도 동일.
                    HexagonSkillChart(
                        skills = skills,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    )
                    val focus = routine?.focusSkills.orEmpty()
                    if (focus.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "이번 주 부족한 부분:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            focus.forEach { axis ->
                                TagChip(axisLabelKo(axis))
                            }
                        }
                    }
                    val difficultyKo = routine?.difficulty?.let { difficultyLabelKo(it) }
                    if (difficultyKo != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "내 난이도:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TagChip(difficultyKo)
                        }
                    }
                    val feedback = routine?.weeklyFeedback.orEmpty()
                    if (feedback.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            formatWeeklyFeedback(feedback),
                            // 빼곡함 완화 — 줄 간격(lineHeight) + 글자 간격 살짝 늘려 가독성 확보.
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 24.sp,
                                letterSpacing = 0.2.sp,
                            ),
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
                        needsSelfRating = needsSelfRating,
                        selfRated = selfRatedState,
                        onGenerate = { difficulty, selfRated ->
                            vm.generate(
                                difficulty = difficulty,
                                force = false,
                                selfRatedSkills = selfRated,
                            )
                        },
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

/**
 * 코멘트/피드백을 출처로 언급하는 군더더기 제거 — 드릴 [desc] + [weeklyFeedback] 공통.
 * 회원은 이 화면에서 한 줄 코멘트를 볼 수 없으므로 출처 인용은 의미 없음. "무엇을 어떻게 하라"
 * 는 지시만 남긴다. 서버 재생성 전까지 기존 캐시 doc 의 잔재를 클라이언트에서 즉시 정리.
 */
internal fun stripCommentReferences(text: String): String =
    text.trim()
        // "관장님 피드백처럼" / "코멘트처럼" / "피드백처럼" 등 출처 인용 어구 (뒤 공백 포함) 제거.
        .replace(Regex("관장님?의?\\s*(?:코멘트|피드백)처럼\\s*"), "")
        // "([1], [2])" / "[1]" 등 숫자 대괄호 인용. 주변 공백은 건드리지 않아 단어 병합 방지.
        .replace(Regex("\\(?\\[\\d+\\](?:\\s*,\\s*\\[\\d+\\])*\\)?"), "")
        // "관장님/관장의" 작성자 수식 — 코멘트는 운영진만 작성하므로 자명 ("코멘트를 반영" 이면 충분).
        .replace(Regex("관장님?의?\\s*(?=코멘트|피드백)"), "")
        .replace(Regex("\\s+([,.!?])"), "$1") // 제거 후 생긴 " ," / " ." 등 공백 정리
        .replace(Regex("[ \\t]{2,}"), " ")    // 이중 공백 1칸으로
        .trim()

/**
 * weeklyFeedback 표시용 — 출처 인용 제거([stripCommentReferences]) 후 2문장씩 묶어 문단 분리.
 * 매 문장마다 끊으면 코치 톤 흐름이 흩어지므로 2문장 단위 문단화로 가독성 + 흐름을 모두 확보.
 */
internal fun formatWeeklyFeedback(raw: String): String {
    val cleaned = stripCommentReferences(raw)
    val sentences = Regex("(?<=[.!?])\\s+").split(cleaned).filter { it.isNotBlank() }
    return sentences.chunked(2).joinToString("\n\n") { it.joinToString(" ") }
}

/** 난이도 enum → 한국어 표시명 (UI 칩 + tagColor 키). */
internal fun difficultyLabelKo(value: String): String = when (value) {
    DIFFICULTY_BEGINNER -> "초급"
    DIFFICULTY_INTERMEDIATE -> "중급"
    DIFFICULTY_ADVANCED -> "고급"
    else -> value
}

@Composable
private fun DayCard(day: RoutineDay) {
    val totalMin = day.drills.sumOf { it.durationMin }
    // 그날 드릴들의 target axis 중복 제거 → 카테고리 칩 (예: 그래플링 / 기술).
    val uniqueAxes = day.drills.map { it.targetAxis }.distinct()
    PosseCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                day.day,
                style = MaterialTheme.typography.titleMedium,
            )
            uniqueAxes.forEach { axis ->
                TagChip(axisLabelKo(axis))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "총 ${totalMin}분",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (day.title.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                day.title,
                style = MaterialTheme.typography.bodyMedium,
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
            val desc = stripCommentReferences(drill.desc)
            if (desc.isNotBlank()) {
                Text(
                    desc,
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
    needsSelfRating: Boolean,
    /** 화면 레벨에서 호이스팅된 자가입력 상태 — null 이면 자가입력 모드 아님. */
    selfRated: SnapshotStateMap<String, Float>?,
    onGenerate: (difficulty: String, selfRatedSkills: Map<String, Float>?) -> Unit,
) {
    // 난이도 선택은 이 카드 안에서만 살아있음 (생성 후엔 사라짐 → 재생성 불가 → 비용 보호).
    var difficulty by remember { mutableStateOf(DIFFICULTY_INTERMEDIATE) }
    PosseCard {
        Text(
            "이번 주 AI 코치의 맞춤 루틴이 아직 없어요.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            // 폰 화면 폭에 맞춰 자연스럽게 wrap 되도록 강제 줄바꿈(\n) 제거.
            "난이도를 고른 뒤 버튼을 눌러주세요.\n\n부족한 스킬과 한 줄 코멘트를 보고 20분 이내의\n짧은 드릴을 만들어드립니다.\n\n(일주일에 1회만 생성 가능)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (needsSelfRating && selfRated != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "내가 생각하는 스킬",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "아직 스킬 평가 전이라 AI가 참고할 점수가 없어요.\n" +
                    "본인이 생각하는 수준을 고르면 이번 루틴에만 사용해요.\n" +
                    "(프로필 점수에는 저장되지 않아요)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SkillAxes.forEach { axis ->
                SelfSkillRow(
                    label = axis.koLabel,
                    current = selfRated[axis.fieldKey],
                    onSelect = { selfRated[axis.fieldKey] = it },
                    enabled = genState !is GenerateState.Loading,
                )
                Spacer(Modifier.height(4.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "난이도",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
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
                onClick = {
                    // 사용자가 실제로 고른 축만 payload 에 포함. 미선택 축은 서버에서 0.5 fallback.
                    val payload = if (needsSelfRating && selfRated != null) {
                        selfRated.toMap().takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                    onGenerate(difficulty, payload)
                },
            )
            if (genState is GenerateState.Loading) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** 6축 자가평가 한 행 — 좌측 축 색 라벨 칩 + 우측 낮음/보통/높음 3단 등급 칩.
 *  [current] null 이면 아직 등급 미선택 — 칩 3개 모두 비선택 톤. */
@Composable
private fun SelfSkillRow(
    label: String,
    current: Float?,
    onSelect: (Float) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 라벨 영역 폭 고정 — 6행 모두 우측 등급 칩 시작점이 정렬되도록.
        // "스트라이킹"(5자) 기준 + 칩 좌우 padding 수용.
        Box(
            modifier = Modifier.width(84.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            SkillAxisChip(label = label)
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            SelfRatingChip("낮음", SELF_RATE_LOW, current, SELF_RATE_LOW_COLOR, enabled, onSelect)
            SelfRatingChip("보통", SELF_RATE_NORMAL, current, SELF_RATE_NORMAL_COLOR, enabled, onSelect)
            SelfRatingChip("높음", SELF_RATE_HIGH, current, SELF_RATE_HIGH_COLOR, enabled, onSelect)
        }
    }
}

/**
 * 6축 라벨 칩 — 축별 [tagColor] 텍스트 + 같은 색 alpha 14% 배경 (tonal).
 * 본문 톤을 흩지 않으면서도 축 구분이 즉시 인지되도록 차분한 채도로 처리.
 */
@Composable
private fun SkillAxisChip(label: String) {
    val accent = tagColor(label)
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = accent,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * 등급 칩 — 비선택은 [accent] alpha 14% tonal, 선택은 solid [accent] + 흰 글씨.
 * 등급마다 accent 가 다르므로 비선택 상태에서도 낮음/보통/높음 색이 즉시 구분된다.
 */
@Composable
private fun SelfRatingChip(
    label: String,
    value: Float,
    current: Float?,
    accent: Color,
    enabled: Boolean,
    onSelect: (Float) -> Unit,
) {
    val selected = current == value
    val bg = if (selected) Modifier.background(accent)
             else Modifier.background(accent.copy(alpha = 0.14f))
    val labelColor = if (selected) Color.White else accent
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .then(bg)
            .clickable(enabled = enabled) { onSelect(value) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/**
 * 6축 메타데이터 — 자가평가 UI 라벨 + Cloud Function payload key.
 * fieldKey 는 SkillSet 필드명과 동일해 서버 AXIS_FIELDS 와 1:1 매핑된다.
 */
private data class SkillAxisMeta(val koLabel: String, val fieldKey: String)
private val SkillAxes = listOf(
    SkillAxisMeta("스트라이킹", "striking"),
    SkillAxisMeta("그래플링", "grappling"),
    SkillAxisMeta("체력", "stamina"),
    SkillAxisMeta("기술", "technique"),
    SkillAxisMeta("멘탈", "mental"),
    SkillAxisMeta("스피드", "speed"),
)

/** 자가평가 3단 — 서버 fallback (0.5) 와 보통이 정렬되도록 0.25/0.5/0.75 사용. */
private const val SELF_RATE_LOW = 0.25f
private const val SELF_RATE_NORMAL = 0.5f
private const val SELF_RATE_HIGH = 0.75f

// 등급별 색 — 난이도(초급/중급/고급) 팔레트와 통일된 강도 스케일.
// tagColor("초급"/"중급"/"고급") 와 동일 값이지만, 라벨이 "낮음/보통/높음" 이라
// tagColor 매핑이 안 닿아서 상수로 직접 분리.
private val SELF_RATE_LOW_COLOR = Color(0xFF10B981)    // 에메랄드 (낮음)
private val SELF_RATE_NORMAL_COLOR = Color(0xFFF59E0B) // 앰버 (보통)
private val SELF_RATE_HIGH_COLOR = Color(0xFFDC2626)   // 레드 (높음)

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

