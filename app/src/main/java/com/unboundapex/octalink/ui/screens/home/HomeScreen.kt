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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.R
import com.unboundapex.octalink.data.curriculumForToday
import com.unboundapex.octalink.data.isClosed
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.CageIcon
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.ui.components.TagChip
import com.unboundapex.octalink.ui.screens.attendance.GYM_DAYS_PER_WEEK
import com.unboundapex.octalink.ui.screens.profile.MyCommentsViewModel
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private data class FeedItem(val title: String, val meta: String, val body: String)

/** "받은 코멘트 없음" placeholder. 실제 데이터가 있으면 ViewModel 결과로 대체. */
private val emptyOneLineComment = FeedItem(
    title = "한 줄 코멘트",
    meta = "",
    body = "아직 받은 코멘트가 없습니다.",
)

private val commentDateFmt = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
private fun dayKr(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "월"; DayOfWeek.TUESDAY -> "화"; DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"; DayOfWeek.FRIDAY -> "금"; DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

// 원본 PNG: 흰색 배경 + 검정 로고/텍스트.
// 흰색 픽셀은 알파 0(투명)으로 빼고, 알파는 입력 RGB 평균을 반전(밝을수록 투명, 어두울수록 불투명) →
// 가장자리 안티에일리어싱 보존. 출력 색은 테마별로 분기: 다크는 흰색, 라이트는 Slate(#1A1A1F).

/** 다크 테마용 — 출력 색 흰색. 거의-검정 배경 위에서 로고가 흰색으로 떠 보임. */
private val logoKeyWhiteFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0f,         0f,         0f,         0f, 255f,
            0f,         0f,         0f,         0f, 255f,
            0f,         0f,         0f,         0f, 255f,
            -1f / 3f,   -1f / 3f,   -1f / 3f,   0f, 255f
        )
    )
)

/** 라이트 테마용 — 출력 색 Slate(#1A1A1F). 흰/회색 배경에 다크 로고. */
private val logoKeyDarkFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0f,         0f,         0f,         0f, 0x1A.toFloat(),
            0f,         0f,         0f,         0f, 0x1A.toFloat(),
            0f,         0f,         0f,         0f, 0x1F.toFloat(),
            -1f / 3f,   -1f / 3f,   -1f / 3f,   0f, 255f
        )
    )
)

@Composable
fun HomeScreen(
    sessionVm: SessionViewModel,
    onOpenBracket: () -> Unit,
    onOpenInfo: () -> Unit = {},
    onOpenMyAttendance: () -> Unit = {},
    myCommentsVm: MyCommentsViewModel = viewModel(),
    weeklyAttendanceVm: HomeWeeklyAttendanceViewModel = viewModel(),
    weeklyMissionVm: WeeklyMissionViewModel = viewModel(),
    gymActivityVm: HomeGymActivityViewModel = viewModel(),
    sparringMatchVm: HomeSparringMatchViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val memberId = session.member?.id
    // member id 가 set/변경 될 때 구독 대상 갱신
    LaunchedEffect(memberId) {
        myCommentsVm.observeFor(memberId)
        weeklyAttendanceVm.observeFor(memberId)
    }
    val myComments by myCommentsVm.myComments.collectAsState()
    val weeklyAttendCount by weeklyAttendanceVm.weeklyCount.collectAsState()
    // REVIEW 화면 WeeklyRateBadge 와 동일 공식: count 는 6 으로 cap, % = capped*100/6
    val weeklyCapped = weeklyAttendCount.coerceAtMost(GYM_DAYS_PER_WEEK)
    val weeklyPct = weeklyCapped * 100 / GYM_DAYS_PER_WEEK
    val weeklyMission by weeklyMissionVm.mission.collectAsState()
    val weeklyMissionText = weeklyMission?.text ?: "이번 주 미션이 아직 설정되지 않았습니다."
    val gymActivity by gymActivityVm.state.collectAsState()
    val sparringMatch by sparringMatchVm.match.collectAsState()
    val sparringMatchItem = sparringMatch?.let { m ->
        val date = m.drawnAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
        FeedItem(
            title = "스파링 매치",
            meta = "${date.format(commentDateFmt)} ${dayKr(date.dayOfWeek)} · ${m.title}",
            body = "대진표가 업데이트 되었습니다. 확인하고 컨디션 체크해주세요.",
        )
    } ?: FeedItem(
        title = "스파링 매치",
        meta = "",
        body = "이번 주 대진표 추첨이 아직 없습니다.",
    )
    // 가장 최신 (classDate, 동일하면 createdAt) 한 건만 홈 카드에 노출
    val latestComment = remember(myComments) {
        myComments.maxWithOrNull(
            compareBy({ it.classDate }, { it.createdAt })
        )
    }
    val oneLineComment = latestComment?.let { c ->
        FeedItem(
            title = "한 줄 코멘트",
            meta = "${c.classDate.format(commentDateFmt)} ${dayKr(c.classDate.dayOfWeek)} · ${c.byMasterName}",
            body = c.text,
        )
    } ?: emptyOneLineComment

    // 오늘의 커리큘럼 — 평일 그룹 수업 매핑. 주말(토/일) 은 [curriculumForToday] 가 null,
    // 평일 공휴일은 weeklyCurriculum 에는 매칭되지만 [isClosed] 가 true → 카드 자체를 숨김.
    val today = remember { LocalDate.now(ZoneId.of("Asia/Seoul")) }
    val todayCurriculum = remember(today) { curriculumForToday(today) }
    val gymClosedToday = remember(today) { isClosed(today) }
    // 오늘의 커리큘럼 카드는 태그를 색깔 칩으로 노출하므로 일반 FeedItem 대신 dedicated 렌더.

    PosseScreen(
        subtitle = "개인의 성장, 함께하는 진화",
        subtitleEmphasis = listOf("성장", "진화"),
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
                        if (gymActivity.isClosed) {
                            // 휴무일(일요일/공휴일) — 활성도 수치 대신 "오늘은\n휴무" 노출.
                            Text(
                                "오늘은\n휴무",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                "${gymActivity.percent}%",
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "출석 ${gymActivity.attendCount} / 등록 ${gymActivity.totalMembers}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    PosseCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenMyAttendance() }
                    ) {
                        Text(
                            "내 주간 출석률",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "$weeklyPct%",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "$weeklyCapped / $GYM_DAYS_PER_WEEK 일",
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
                            weeklyMissionText,
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
            if (todayCurriculum != null && !gymClosedToday) {
                item {
                    TodayCurriculumCard(
                        coach = todayCurriculum.coach,
                        tag = todayCurriculum.tag,
                        theme = todayCurriculum.theme,
                    )
                }
            }
            item { TitleMetaCard(sparringMatchItem) }
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
                    "주소 · 전화 · 운영시간 · 소셜",
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

/**
 * 오늘의 커리큘럼 — meta 우측에 [TagChip] 색깔 pill 로 태그 노출.
 * 코치명은 그 왼쪽에 회색 라벨 텍스트로 분리해 [CurriculumScreen] 와 시각적 어휘 통일.
 */
@Composable
private fun TodayCurriculumCard(coach: String, tag: String, theme: String) {
    PosseCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "오늘의 커리큘럼",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$coach · ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TagChip(tag)
        }
        Text(
            theme,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeHeader() {
    val gloveScale = remember { Animatable(0.2f) }
    val gloveAlpha = remember { Animatable(0f) }
    val gloveOffsetX = remember { Animatable(80f) }
    // 라이트 테마면 로고를 다크 필터로 출력. 글러브 이모지는 다크 테마에서만 흰색,
    // 라이트 테마는 native 이모지 색(글러브 검정 그림자) 유지.
    val currentTheme by com.unboundapex.octalink.ui.theme.AppThemeStore.theme.collectAsState()
    val isLight = currentTheme == com.unboundapex.octalink.ui.theme.AppTheme.LIGHT
    val logoFilter = if (isLight) logoKeyDarkFilter else logoKeyWhiteFilter

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
            .padding(top = 4.dp)
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
            colorFilter = logoFilter,
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
