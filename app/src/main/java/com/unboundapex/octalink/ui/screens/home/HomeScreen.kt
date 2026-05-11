package com.unboundapex.octalink.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unboundapex.octalink.R
import com.unboundapex.octalink.ui.components.CageIcon
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class FeedItem(val title: String, val meta: String, val body: String)

private val todayClass = FeedItem(
    title = "오늘의 커리큘럼",
    meta = "관장 김파시",
    body = "잽-스트레이트 거리감 + 인사이드 로우킥",
)

private val sparringMatch = FeedItem(
    title = "스파링 매치",
    meta = "5/8 금 19:30 · 라이트급",
    body = "대진표가 업데이트 되었습니다. 확인하고 컨디션 체크해주세요.",
)

private val oneLineComment = FeedItem(
    title = "한 줄 코멘트",
    meta = "5/1 금 · 관장 김파시",
    body = "리드 잽 후 체중 이동을 반박자 늦춰보세요. 카운터 맞을 위험 줄어듭니다.",
)

private val invertColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

@Composable
fun HomeScreen(onOpenBracket: () -> Unit, onOpenInfo: () -> Unit = {}) {
    PosseScreen(
        subtitle = "개인의 성장, 함께하는 진화",
        header = { HomeHeader() }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PosseCard(modifier = Modifier.weight(1f)) {
                        Text(
                            "오늘 체육관 활성도",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "72%",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "출석 18 / 등록 25",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PosseCard(modifier = Modifier.weight(1f)) {
                        Text(
                            "내 주간 출석률",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "3 / 3",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "이번 주 미션 100%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PosseCard(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            "주간 미션",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "• 체육관 3회 출석\n• 스파링 1라운드 이상\n• 개인 영상 1개 업로드",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    PosseCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onOpenBracket() }
                    ) {
                        Text(
                            "이번 주 대진표",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CageIcon(modifier = Modifier.size(32.dp))
                            }
                            Text(
                                "→",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            item { TitleMetaCard(todayClass) }
            item { TitleMetaCard(sparringMatch) }
            item { TitleMetaCard(oneLineComment) }
            item { GymInfoCard(onClick = onOpenInfo) }
        }
    }
}

@Composable
private fun GymInfoCard(onClick: () -> Unit) {
    PosseCard(modifier = Modifier.clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text("체육관 정보", style = MaterialTheme.typography.titleMedium)
                Text(
                    "주소 · 전화 · 운영시간 · 소셜 · 정책",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun TitleMetaCard(item: FeedItem) {
    PosseCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                item.meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            item.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeHeader() {
    val gloveScale = remember { Animatable(0.2f) }
    val gloveAlpha = remember { Animatable(0f) }
    val gloveOffsetX = remember { Animatable(80f) }

    LaunchedEffect(Unit) {
        // 단발 정권 임팩트 (~630ms)
        gloveScale.snapTo(0.2f)
        gloveAlpha.snapTo(0f)
        gloveOffsetX.snapTo(80f)

        // attack
        launch { gloveAlpha.animateTo(1f, tween(90)) }
        launch { gloveOffsetX.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
        gloveScale.animateTo(1.6f, tween(320, easing = FastOutSlowInEasing))

        // hold
        kotlinx.coroutines.delay(40)

        // recoil
        launch { gloveScale.animateTo(0.4f, tween(270, easing = LinearOutSlowInEasing)) }
        gloveAlpha.animateTo(0f, tween(270))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.logo_octalink),
            contentDescription = "OctaLink",
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1800f / 403f),
            contentScale = ContentScale.Fit,
            colorFilter = invertColorFilter
        )
        Text(
            text = "👊",
            color = Color.White,
            fontSize = 96.sp,
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = gloveOffsetX.value.dp.roundToPx(),
                        y = 0
                    )
                }
                .scale(gloveScale.value)
                .alpha(gloveAlpha.value)
        )
    }
}
