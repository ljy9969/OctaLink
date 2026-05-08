package com.unboundapex.octalink.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.avatarById
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.AvatarPickerSheet
import com.unboundapex.octalink.ui.components.AvatarTile
import com.unboundapex.octalink.ui.components.HexagonSkillChart
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.ui.components.SkillStat
import java.time.LocalDate
import java.time.Period

private val skills = listOf(
    SkillStat("스트라이킹", 0.72f),
    SkillStat("그래플링", 0.45f),
    SkillStat("체력", 0.80f),
    SkillStat("기술", 0.58f),
    SkillStat("멘탈", 0.66f),
    SkillStat("스피드", 0.70f),
)

private data class Comment(val date: String, val coach: String, val text: String)

private fun membershipLabel(joinDate: LocalDate, today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))): String {
    val period = Period.between(joinDate, today)
    return when {
        period.years > 0 && period.months > 0 -> "${period.years}년 ${period.months}개월 차"
        period.years > 0 -> "${period.years}년 차"
        period.months > 0 -> "${period.months}개월 차"
        else -> "이번 달 입관"
    }
}

private val coachComments = listOf(
    Comment("5/1 금", "관장 김파시", "리드 잽 후 체중 이동을 반박자 늦춰보세요. 카운터 위험이 줄어듭니다."),
    Comment("4/29 수", "코치 박", "샌드백 라운드 후반에 가드가 내려갑니다. 마지막 30초 의식적으로 올리기."),
    Comment("4/26 일", "관장 김파시", "테이크다운 디펜스 시 골반 각도 좋아졌습니다. 그대로 유지."),
)

@Composable
fun ProfileScreen(sessionVm: SessionViewModel) {
    val session by sessionVm.state.collectAsState()
    var pickerOpen by remember { mutableStateOf(false) }
    val avatar = avatarById(session.avatarId)
    val belt = session.belt
    val joinDate = remember { LocalDate.of(2026, 1, 1) }
    val membership = remember(joinDate) { membershipLabel(joinDate) }

    PosseScreen(title = "Profile", subtitle = "${session.name} · ${belt.displayName} 벨트 · $membership") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                PosseCard(leftStripeColor = belt.ringColor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarTile(
                            avatar = avatar,
                            size = 88.dp,
                            ringColor = null,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .clickable { pickerOpen = true }
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 20.dp)
                        ) {
                            Text(session.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${avatar.displayName} · ${belt.displayName} 벨트",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "탭해서 캐릭터 변경",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { pickerOpen = true }
                            )
                        }
                    }
                }
            }
            item {
                PosseCard(padding = PaddingValues(4.dp)) {
                    HexagonSkillChart(
                        skills = skills,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }
            }
            item {
                PosseCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                "승률",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "64%",
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "스파링 25전 16승 9패",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "최근 10경기 7승 3패",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            item {
                PosseCard {
                    Text("관장님 한 줄 코멘트", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    coachComments.forEach { c ->
                        Text("${c.date} · ${c.coach}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(c.text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        AvatarPickerSheet(
            selectedId = session.avatarId,
            onDismiss = { pickerOpen = false },
            onSelect = {
                sessionVm.updateAvatar(it.id)
                pickerOpen = false
            }
        )
    }
}
