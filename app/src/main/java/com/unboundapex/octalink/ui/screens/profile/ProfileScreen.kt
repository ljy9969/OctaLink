package com.unboundapex.octalink.ui.screens.profile

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
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.avatarById
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.AvatarTile
import com.unboundapex.octalink.ui.components.HexagonSkillChart
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import java.time.LocalDate
import java.time.Period

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
    var leaveConfirmOpen by remember { mutableStateOf(false) }
    val avatar = avatarById(session.avatarId)
    val belt = session.belt
    val skills = (session.member?.skills ?: SkillSet.EMPTY).toStats()
    // 실제 도장 입관일 — Firestore `members/{uid}.joinDate` 에서 (가입 폼에서 사용자가 입력).
    // 아직 회원 doc 이 없는 LOADING 단계 폴백은 오늘 (이번 달 입관 표시).
    val joinDate = session.member?.joinDate ?: LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
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
                            modifier = Modifier.padding(start = 10.dp),
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
                        }
                    }
                }
            }
            item {
                PosseCard(padding = PaddingValues(4.dp)) {
                    // 차트 + 우측 하단 평균 점수 오버레이. skills 6축 평균(0..1) → 0..100 정수.
                    val avgScore = (skills.sumOf { it.value.toDouble() } / skills.size * 100).toInt()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    ) {
                        HexagonSkillChart(
                            skills = skills,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                "평균",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "$avgScore",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
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

            // 운영진/관장/창조자 전용 작업은 하단 nav "운영" 탭으로 이전 (AdminScreen 참조)
            item {
                PosseCard(modifier = Modifier.clickable { sessionVm.signOut() }) {
                    Text(
                        "로그아웃",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "세션 종료. 데이터는 보존되며 다음 로그인 시 복원",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                PosseCard(modifier = Modifier.clickable { leaveConfirmOpen = true }) {
                    Text(
                        "회원 탈퇴",
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color(0xFFC8102E),
                    )
                    Text(
                        "앱 이용 중단. 도장 명단 완전 삭제는 관장님께 별도 요청",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (leaveConfirmOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { leaveConfirmOpen = false },
            title = {
                Text(
                    "정말 탈퇴하시겠습니까?",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    "• 즉시 앱 이용이 중단됩니다\n" +
                        "• 출석/스킬/한 줄 코멘트 등 과거 기록은 운영 자료로 보존됩니다\n" +
                        "• 도장 명단에서 완전 삭제는 관장님께 별도 요청해주세요\n" +
                        "• 같은 카카오 계정으로 재가입 시 새 회원으로 처리됩니다",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Text(
                    "탈퇴",
                    color = androidx.compose.ui.graphics.Color(0xFFC8102E),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable {
                            leaveConfirmOpen = false
                            sessionVm.leaveMembership()
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
                        .clickable { leaveConfirmOpen = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }

}

