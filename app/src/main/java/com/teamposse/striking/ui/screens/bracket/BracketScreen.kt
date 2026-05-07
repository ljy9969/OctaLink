package com.teamposse.striking.ui.screens.bracket

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teamposse.striking.data.Belt
import com.teamposse.striking.data.Match
import com.teamposse.striking.data.tournament.TournamentUiState
import com.teamposse.striking.data.tournament.TournamentViewModel
import com.teamposse.striking.ui.components.PosseScreen

private val matchHeight: Dp = 64.dp
private val verticalGap: Dp = 28.dp
private val columnGap: Dp = 8.dp
private const val CARD_WIDTH_FRACTION = 0.78f

private enum class BracketMode { EIGHT, FOUR, FINAL_ONLY }

@Composable
fun BracketScreen(
    tournamentVm: TournamentViewModel,
    onBack: () -> Unit,
    onOpenDraw: () -> Unit,
) {
    val state by tournamentVm.state.collectAsState()
    val isInitialized = state.initialized
    val subtitle = remember(state.weightClass, state.beltGroup, isInitialized) {
        if (isInitialized) {
            buildString {
                state.weightClass?.let { append(it.displayName) }
                state.beltGroup?.let {
                    if (isNotEmpty()) append(" · ")
                    append("${it.displayName} 벨트")
                }
                if (isEmpty()) append("토너먼트 진행 중")
            }
        } else "추첨 대기 중"
    }

    PosseScreen(title = "Matches", subtitle = subtitle) {
        if (!isInitialized) {
            EmptyState(onOpenDraw = onOpenDraw, onBack = onBack)
            return@PosseScreen
        }

        val mode = when {
            state.round1.isNotEmpty() -> BracketMode.EIGHT
            state.round2.isNotEmpty() -> BracketMode.FOUR
            else -> BracketMode.FINAL_ONLY
        }
        val labels = when (mode) {
            BracketMode.EIGHT -> listOf("8강", "4강", "결승")
            BracketMode.FOUR -> listOf("4강", "결승")
            BracketMode.FINAL_ONLY -> listOf("결승")
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionChip(
                    text = "새 추첨",
                    bg = MaterialTheme.colorScheme.primary,
                    fg = MaterialTheme.colorScheme.onPrimary,
                    onClick = onOpenDraw,
                )
                Spacer(Modifier.weight(1f))
                ActionChip(
                    text = "초기화",
                    bg = Color(0xFFFBC02D),
                    fg = Color(0xFF1A1A1A),
                    onClick = { tournamentVm.reset() },
                )
            }
            Spacer(Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(columnGap)
            ) {
                labels.forEach { RoundLabel(it, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))

            when (mode) {
                BracketMode.EIGHT -> BracketTree(state = state, vm = tournamentVm)
                BracketMode.FOUR -> BracketTreeFour(state = state, vm = tournamentVm)
                BracketMode.FINAL_ONLY -> BracketTreeFinalOnly(state = state, vm = tournamentVm)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ChampionBanner(final = state.final)
            }
        }
    }
}

@Composable
private fun EmptyState(onOpenDraw: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "🥊",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "아직 진행 중인 토너먼트가 없습니다",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "체급별로 회원을 선택해 추첨을 시작하세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onOpenDraw,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        ) { Text("추첨하기") }
    }
}

@Composable
private fun ActionChip(
    text: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
) {
    Text(
        text = text,
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

/**
 * 승자 → 다음 라운드 연결선용 "불타는" 펄스 애니메이션.
 * 색상: 빨강 ↔ 주황/노랑 사이 진동
 * 스트로크: 두께 펄스
 */
@Composable
private fun rememberFirePulse(): Pair<Color, Float> {
    val transition = rememberInfiniteTransition(label = "firePulse")
    val color by transition.animateColor(
        initialValue = Color(0xFFFF3300),
        targetValue = Color(0xFFFFD000),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fireColor",
    )
    val stroke by transition.animateFloat(
        initialValue = 3.5f,
        targetValue = 5.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fireStroke",
    )
    return color to stroke
}

@Composable
private fun RoundLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
private fun BracketTree(state: TournamentUiState, vm: TournamentViewModel) {
    val totalHeight = matchHeight * 4 + verticalGap * 3
    val lineColor = MaterialTheme.colorScheme.outline
    val (fireColor, fireStroke) = rememberFirePulse()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val colW = (size.width - 2 * columnGap.toPx()) / 3f
            val cardW = colW * CARD_WIDTH_FRACTION
            val cardMargin = (colW - cardW) / 2f
            val mh = matchHeight.toPx()
            val gap = verticalGap.toPx()
            val gx = columnGap.toPx() / 2f

            val r1Centers = (0..3).map { i -> i * (mh + gap) + mh / 2f }
            val r1Tops = (0..3).map { i -> i * (mh + gap) }
            val r2Centers = listOf(
                (r1Centers[0] + r1Centers[1]) / 2f,
                (r1Centers[2] + r1Centers[3]) / 2f
            )
            val r2Tops = r2Centers.map { it - mh / 2f }
            val r3Center = (r2Centers[0] + r2Centers[1]) / 2f

            // 컬럼 경계가 아니라 카드 가장자리 기준
            val col1Right = colW - cardMargin
            val col2Left = colW + columnGap.toPx() + cardMargin
            val col2Right = 2 * colW + columnGap.toPx() - cardMargin
            val col3Left = 2 * colW + 2 * columnGap.toPx() + cardMargin

            // 행 Y 계산: 카드의 위쪽 행(red)은 카드 상단+mh/4, 아래쪽 행(blue)은 카드 상단+3mh/4
            fun rowY(cardTop: Float, isBlueRow: Boolean): Float =
                cardTop + if (isBlueRow) mh * 3f / 4f else mh / 4f

            // 8강 → 4강 베이스 라인 (winner 결정된 카드 쪽 라인은 숨김, 반대쪽은 유지)
            for (pair in 0..1) {
                val mTop = state.round1.getOrNull(pair * 2)
                val mBot = state.round1.getOrNull(pair * 2 + 1)
                val anyWinner = mTop?.winner != null || mBot?.winner != null
                val topY = r1Centers[pair * 2]
                val botY = r1Centers[pair * 2 + 1]
                val midY = r2Centers[pair]
                val joinX = col1Right + gx

                // 카드 → joinX stub
                if (mTop?.winner == null) {
                    drawLine(lineColor, Offset(col1Right, topY), Offset(joinX, topY), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                if (mBot?.winner == null) {
                    drawLine(lineColor, Offset(col1Right, botY), Offset(joinX, botY), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                // 수직 절반: 윗쪽은 mTop이 패자/미정일 때, 아래쪽은 mBot이 패자/미정일 때
                if (mTop?.winner == null) {
                    drawLine(lineColor, Offset(joinX, topY), Offset(joinX, midY), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                if (mBot?.winner == null) {
                    drawLine(lineColor, Offset(joinX, midY), Offset(joinX, botY), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                // 다음 라운드 horizontal: 양쪽 다 미정일 때만 (fire 가 cover)
                if (!anyWinner) {
                    drawLine(lineColor, Offset(joinX, midY), Offset(col2Left, midY), strokeWidth = 2f, cap = StrokeCap.Round)
                }
            }

            // 8강 승자 → 4강 불타는 경로 (승자가 있는 행에서 출발, 항상 그림)
            state.round1.forEachIndexed { idx, m ->
                if (m.winner == null) return@forEachIndexed
                val winnerIsBlue = m.winner == m.blue
                val winnerY = rowY(r1Tops[idx], winnerIsBlue)
                val midY = r2Centers[idx / 2]
                val joinX = col1Right + gx
                drawLine(fireColor, Offset(col1Right, winnerY), Offset(joinX, winnerY), strokeWidth = fireStroke, cap = StrokeCap.Round)
                drawLine(fireColor, Offset(joinX, winnerY), Offset(joinX, midY), strokeWidth = fireStroke, cap = StrokeCap.Round)
                drawLine(fireColor, Offset(joinX, midY), Offset(col2Left, midY), strokeWidth = fireStroke, cap = StrokeCap.Round)
            }

            // 4강 → 결승 베이스 라인 (winner 결정된 카드 쪽은 숨김, 반대쪽 절반은 유지)
            run {
                val mTop = state.round2.getOrNull(0)
                val mBot = state.round2.getOrNull(1)
                val anyWinner = mTop?.winner != null || mBot?.winner != null
                val topY = r2Centers[0]
                val botY = r2Centers[1]
                val midY = r3Center
                val joinX = col2Right + gx

                if (mTop?.winner == null) {
                    drawLine(lineColor, Offset(col2Right, topY), Offset(joinX, topY), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                if (mBot?.winner == null) {
                    drawLine(lineColor, Offset(col2Right, botY), Offset(joinX, botY), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                if (mTop?.winner == null) {
                    drawLine(lineColor, Offset(joinX, topY), Offset(joinX, midY), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                if (mBot?.winner == null) {
                    drawLine(lineColor, Offset(joinX, midY), Offset(joinX, botY), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                if (!anyWinner) {
                    drawLine(lineColor, Offset(joinX, midY), Offset(col3Left, midY), strokeWidth = 2f, cap = StrokeCap.Round)
                }
            }

            // 4강 승자 → 결승 불타는 경로 (항상 그림)
            state.round2.forEachIndexed { idx, m ->
                if (m.winner == null) return@forEachIndexed
                val winnerIsBlue = m.winner == m.blue
                val winnerY = rowY(r2Tops[idx], winnerIsBlue)
                val midY = r3Center
                val joinX = col2Right + gx
                drawLine(fireColor, Offset(col2Right, winnerY), Offset(joinX, winnerY), strokeWidth = fireStroke, cap = StrokeCap.Round)
                drawLine(fireColor, Offset(joinX, winnerY), Offset(joinX, midY), strokeWidth = fireStroke, cap = StrokeCap.Round)
                drawLine(fireColor, Offset(joinX, midY), Offset(col3Left, midY), strokeWidth = fireStroke, cap = StrokeCap.Round)
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(columnGap)
        ) {
            val cardMod = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                state.round1.forEachIndexed { idx, m ->
                    MatchCard(m, vm = vm, modifier = cardMod) { winner ->
                        vm.setRound1Winner(idx, winner)
                    }
                    if (idx < state.round1.lastIndex) Spacer(Modifier.height(verticalGap))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height((matchHeight + verticalGap) / 2))
                if (state.round2.isNotEmpty()) {
                    MatchCard(state.round2[0], vm = vm, modifier = cardMod) { winner ->
                        vm.setRound2Winner(0, winner)
                    }
                    Spacer(Modifier.height(matchHeight + verticalGap * 2))
                    MatchCard(state.round2[1], vm = vm, modifier = cardMod) { winner ->
                        vm.setRound2Winner(1, winner)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height((matchHeight + verticalGap) * 3 / 2))
                MatchCard(
                    state.final,
                    vm = vm,
                    modifier = cardMod,
                    emphasis = true,
                ) { winner ->
                    vm.setFinalWinner(winner)
                }
            }
        }
    }
}

@Composable
private fun BracketTreeFour(state: TournamentUiState, vm: TournamentViewModel) {
    val totalHeight = matchHeight * 2 + verticalGap
    val lineColor = MaterialTheme.colorScheme.outline
    val (fireColor, fireStroke) = rememberFirePulse()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val colW = (size.width - columnGap.toPx()) / 2f
            val cardMargin = colW * (1f - CARD_WIDTH_FRACTION) / 2f
            val mh = matchHeight.toPx()
            val gap = verticalGap.toPx()
            val gx = columnGap.toPx() / 2f

            val r0Tops = listOf(0f, mh + gap)
            val r0Centers = listOf(mh / 2f, mh * 1.5f + gap)
            val r1Center = (r0Centers[0] + r0Centers[1]) / 2f

            val col1Right = colW - cardMargin
            val col2Left = colW + columnGap.toPx() + cardMargin
            val joinX = col1Right + gx

            val mTop = state.round2.getOrNull(0)
            val mBot = state.round2.getOrNull(1)
            val anyWinner = mTop?.winner != null || mBot?.winner != null

            if (mTop?.winner == null) {
                drawLine(lineColor, Offset(col1Right, r0Centers[0]), Offset(joinX, r0Centers[0]), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            if (mBot?.winner == null) {
                drawLine(lineColor, Offset(col1Right, r0Centers[1]), Offset(joinX, r0Centers[1]), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            if (mTop?.winner == null) {
                drawLine(lineColor, Offset(joinX, r0Centers[0]), Offset(joinX, r1Center), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            if (mBot?.winner == null) {
                drawLine(lineColor, Offset(joinX, r1Center), Offset(joinX, r0Centers[1]), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            if (!anyWinner) {
                drawLine(lineColor, Offset(joinX, r1Center), Offset(col2Left, r1Center), strokeWidth = 2f, cap = StrokeCap.Round)
            }

            // 4강 승자 → 결승 불타는 경로 (항상 그림)
            state.round2.forEachIndexed { idx, m ->
                if (m.winner == null) return@forEachIndexed
                val winnerIsBlue = m.winner == m.blue
                val winnerY = r0Tops[idx] + if (winnerIsBlue) mh * 3f / 4f else mh / 4f
                drawLine(fireColor, Offset(col1Right, winnerY), Offset(joinX, winnerY), strokeWidth = fireStroke, cap = StrokeCap.Round)
                drawLine(fireColor, Offset(joinX, winnerY), Offset(joinX, r1Center), strokeWidth = fireStroke, cap = StrokeCap.Round)
                drawLine(fireColor, Offset(joinX, r1Center), Offset(col2Left, r1Center), strokeWidth = fireStroke, cap = StrokeCap.Round)
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(columnGap)
        ) {
            val cardMod = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                state.round2.forEachIndexed { idx, m ->
                    MatchCard(m, vm = vm, modifier = cardMod) { winner ->
                        vm.setRound2Winner(idx, winner)
                    }
                    if (idx < state.round2.lastIndex) Spacer(Modifier.height(verticalGap))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height((matchHeight + verticalGap) / 2))
                MatchCard(
                    state.final,
                    vm = vm,
                    modifier = cardMod,
                    emphasis = true,
                ) { winner ->
                    vm.setFinalWinner(winner)
                }
            }
        }
    }
}

@Composable
private fun BracketTreeFinalOnly(state: TournamentUiState, vm: TournamentViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(matchHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.fillMaxWidth(0.6f)) {
            MatchCard(
                state.final,
                vm = vm,
                emphasis = true,
            ) { winner ->
                vm.setFinalWinner(winner)
            }
        }
    }
}

@Composable
private fun MatchCard(
    m: Match,
    vm: TournamentViewModel,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    onPickWinner: (String) -> Unit = {},
) {
    val redStripe = vm.beltOf(m.red)?.ringColor
        ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val blueStripe = vm.beltOf(m.blue)?.ringColor
        ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val bothReal = !m.isPending  // 양쪽 모두 실명일 때만 결과 입력 가능
    Column(
        modifier = modifier
            .height(matchHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FighterRow(
            name = m.red,
            isWinner = m.winner != null && m.winner == m.red,
            isLoser = m.winner != null && m.winner != m.red,
            stripeColor = redStripe,
            // 자기가 이미 승자가 아닌 경우에만 클릭 가능 (다른 사람 클릭으로 전환)
            isPickable = bothReal && m.red != "?" && m.winner != m.red,
            onClick = { onPickWinner(m.red) },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        )
        FighterRow(
            name = m.blue,
            isWinner = m.winner != null && m.winner == m.blue,
            isLoser = m.winner != null && m.winner != m.blue,
            stripeColor = blueStripe,
            isPickable = bothReal && m.blue != "?" && m.winner != m.blue,
            onClick = { onPickWinner(m.blue) },
        )
    }
}

@Composable
private fun ColumnScope.FighterRow(
    name: String,
    isWinner: Boolean,
    isLoser: Boolean,
    stripeColor: Color,
    isPickable: Boolean = false,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = true)
            .clickable(enabled = isPickable) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .fillMaxSize()
                .background(stripeColor)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                isWinner -> Color(0xFFFFFFFF)
                isLoser -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (isWinner) FontWeight.ExtraBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun ChampionBanner(final: Match) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "🏆 CHAMPION",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        val champ = when {
            final.winner != null -> final.winner!!
            final.red != "?" && final.blue != "?" -> "${final.red} vs ${final.blue}"
            final.red != "?" -> "${final.red} 결승 진출\n상대 미정"
            final.blue != "?" -> "${final.blue} 결승 진출\n상대 미정"
            else -> "결승 진출자 미정"
        }
        Text(
            champ,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

