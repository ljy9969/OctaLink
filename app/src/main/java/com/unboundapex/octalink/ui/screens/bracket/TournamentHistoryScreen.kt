package com.unboundapex.octalink.ui.screens.bracket

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.schema.isMaster
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.ui.components.WeightClassChip
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 토너먼트 히스토리 — 도장의 모든 토너먼트(진행 중 + 완료) 카드 리스트.
 * 카드 탭 시 BracketScreen 으로 진입해 해당 토너먼트의 대진표를 read-only 로 표시.
 *
 * 권한: 모든 APPROVED 회원 (대진표와 동일).
 */
@Composable
fun TournamentHistoryScreen(
    onBack: () -> Unit,
    onOpenTournament: (tournamentId: String) -> Unit,
    sessionVm: SessionViewModel,
    vm: TournamentHistoryViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val myMemberId = session.member?.id
    val canDelete = session.role.isMaster
    LaunchedEffect(myMemberId) { vm.observeForMember(myMemberId) }

    val items by vm.items.collectAsState()
    // "내 참가만" 토글 — 본인 참가 토너먼트만 노출. 본인 참가 0건이면 빈 상태 안내.
    var filterMine by remember { mutableStateOf(false) }
    val myParticipationCount = remember(items) { items.count { it.isMyParticipation } }
    val visibleItems = remember(items, filterMine) {
        if (filterMine) items.filter { it.isMyParticipation } else items
    }
    val totalLabel = when {
        items.isEmpty() -> "기록 없음"
        filterMine -> "내 참가 ${visibleItems.size} / 총 ${items.size}개"
        else -> "총 ${items.size}개"
    }

    // 우발 클릭 방지 — 삭제는 confirm 다이얼로그 거침. null = 닫힘.
    var deleteTarget by remember { mutableStateOf<TournamentHistoryItem?>(null) }

    PosseScreen(title = "History", subtitle = "토너먼트 히스토리 · $totalLabel") {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChipBtn(
                    text = "← 뒤로",
                    bg = MaterialTheme.colorScheme.surfaceVariant,
                    fg = MaterialTheme.colorScheme.onSurface,
                    onClick = onBack,
                )
                Spacer(Modifier.weight(1f))
                // 본인 참가 토너먼트가 한 건이라도 있을 때만 필터 노출 — 0건이면 토글 의미 없음.
                if (myParticipationCount > 0) {
                    FilterChipBtn(
                        text = if (filterMine) "✓ 내 참가만 (${myParticipationCount})" else "내 참가만 (${myParticipationCount})",
                        active = filterMine,
                        onClick = { filterMine = !filterMine },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (items.isEmpty()) {
                PosseCard {
                    Text(
                        "아직 진행된 토너먼트가 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (visibleItems.isEmpty()) {
                // 필터 ON 인데 결과 0 — 이론상 myParticipationCount > 0 일 때만 토글 노출하므로
                // 도달 안 함. 안전망으로 안내 카드.
                PosseCard {
                    Text(
                        "필터 조건에 맞는 토너먼트가 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(visibleItems, key = { it.tournament.id }) { item ->
                        TournamentHistoryCard(
                            item = item,
                            onClick = { onOpenTournament(item.tournament.id) },
                            canDelete = canDelete,
                            onDelete = { deleteTarget = item },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        val t = target.tournament
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("토너먼트 삭제") },
            text = {
                Text(
                    buildString {
                        append("'${t.title}' 토너먼트를 영구 삭제하시겠습니까?")
                        if (target.championName != null) {
                            append("\n\n🏆 우승: ${target.championName}")
                        }
                        append("\n참가자: ${target.participantCount}명")
                        append("\n\n매치 기록 전체가 함께 삭제되며 되돌릴 수 없습니다.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Text(
                    "삭제",
                    color = Color(0xFFC8102E),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            vm.delete(t.id)
                            deleteTarget = null
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
                        .clickable { deleteTarget = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }
}

@Composable
private fun TournamentHistoryCard(
    item: TournamentHistoryItem,
    onClick: () -> Unit,
    canDelete: Boolean = false,
    onDelete: () -> Unit = {},
) {
    val t = item.tournament
    val finished = t.finishedAt != null
    PosseCard(modifier = Modifier.clickable { onClick() }) {
        // 1행: 체급 칩 (또는 폴백 텍스트) + 상태 칩 + 본인 참가 배지 + 관장 삭제 칩
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val wc = t.weightClass
            if (wc != null) {
                WeightClassChip(weightClass = wc)
            } else {
                // 구버전 doc 등 weightClass 누락 — title 텍스트로 폴백.
                Text(
                    t.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            if (item.isMyParticipation) {
                StatusChip(text = "내 참가", bg = Color(0xFF1E88E5))
                Spacer(Modifier.width(6.dp))
            }
            StatusChip(
                text = if (finished) "완료" else "진행 중",
                bg = if (finished) MaterialTheme.colorScheme.primary
                else Color(0xFFFBC02D),
                fg = if (finished) MaterialTheme.colorScheme.onPrimary
                else Color(0xFF1A1A1A),
            )
            if (canDelete) {
                Spacer(Modifier.width(6.dp))
                // 칩에 자체 clickable — 카드 onClick 으로 전파되지 않고 confirm 다이얼로그 진입.
                Text(
                    "삭제",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF555560))
                        .clickable { onDelete() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        // 2행: 추첨/완료일 + 참가자 수
        Text(
            buildString {
                append("추첨 ${formatHistoryDate(t.drawAt)}")
                t.finishedAt?.let {
                    append(" · 완료 ${formatHistoryDate(it)}")
                }
                append(" · 총 ${item.participantCount}명")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 3행: 우승자 (완료 토너먼트만, 이름 + 벨트색 underline)
        if (finished && item.championName != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🏆 우승  ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val belt = item.championBelt
                // 라이트 테마의 WHITE 벨트처럼 ringColor 가 배경과 거의 동색일 때 stroke 윤곽이
                // 살아나도록 outline 한 줄 깔고 위에 컬러 stroke. drawBehind 라 레이아웃은 동일.
                val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                Text(
                    item.championName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = if (belt != null) Modifier.drawBehind {
                        val colorStrokePx = 3.dp.toPx()
                        val outlineStrokePx = colorStrokePx + 1.5.dp.toPx()
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = outlineStrokePx,
                        )
                        drawLine(
                            color = belt.ringColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = colorStrokePx,
                        )
                    } else Modifier,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    bg: Color,
    fg: Color = Color.White,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
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

/**
 * 토글 필터 칩 — active = "내 참가" 칩과 동일 블루(#1E88E5) + 흰 글씨,
 * inactive = surfaceVariant + 본문 색. 카드 안의 "내 참가" 배지와 시각 어휘 통일.
 */
@Composable
private fun FilterChipBtn(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (active) Color(0xFF1E88E5) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (active) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(
                1.dp,
                if (active) Color(0xFF1E88E5) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(6.dp),
            )
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

private val seoul = ZoneId.of("Asia/Seoul")
// 앱 전체 통일 날짜 포맷 — "5/20(수)".
private val historyDateFmt = DateTimeFormatter.ofPattern("M/d(EEE)", java.util.Locale.KOREAN)

private fun formatHistoryDate(instant: java.time.Instant): String =
    instant.atZone(seoul).toLocalDate().format(historyDateFmt)
