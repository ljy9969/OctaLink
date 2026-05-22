package com.unboundapex.octalink.ui.screens.attendance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.CheckInWindow
import com.unboundapex.octalink.data.avatarById
import com.unboundapex.octalink.data.checkInWindow
import com.unboundapex.octalink.data.schema.Role
import com.unboundapex.octalink.data.schema.isStaff
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.data.currentOrNextClassLabel
import com.unboundapex.octalink.data.isClosed
import com.unboundapex.octalink.data.isHoliday
import java.time.LocalDate
import java.time.ZoneId
import com.unboundapex.octalink.ui.components.AvatarTile
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private fun shortDateLabel(date: LocalDate): String {
    val day = when (date.dayOfWeek) {
        java.time.DayOfWeek.MONDAY -> "월"
        java.time.DayOfWeek.TUESDAY -> "화"
        java.time.DayOfWeek.WEDNESDAY -> "수"
        java.time.DayOfWeek.THURSDAY -> "목"
        java.time.DayOfWeek.FRIDAY -> "금"
        java.time.DayOfWeek.SATURDAY -> "토"
        java.time.DayOfWeek.SUNDAY -> "일"
    }
    return "${date.monthValue}/${date.dayOfMonth} $day"
}

@Composable
fun AttendanceScreen(
    sessionVm: SessionViewModel,
    onOpenReview: () -> Unit = {},
    attendanceVm: AttendanceViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val todayPeers by attendanceVm.todayPeers.collectAsState()

    val classLabel = remember { currentOrNextClassLabel() }
    val today = remember { LocalDate.now(ZoneId.of("Asia/Seoul")) }
    val dateLabel = remember(today) { shortDateLabel(today) }
    val closedToday = remember { isClosed(today) }
    val closedReason = remember { if (isHoliday(today)) "공휴일" else "일요일 정기 휴무" }
    val window = remember { checkInWindow() }
    val classesEnded = window == CheckInWindow.AFTER_LAST_CLASS || window == CheckInWindow.NO_CLASS_TODAY
    val tooEarly = window == CheckInWindow.BEFORE_WINDOW
    // 관장(MASTER) 은 영업일 상시 운영 — 출석 체크인 자체 비활성. 코치/창조자는 일반 회원처럼 체크인 가능.
    val isMasterRole = session.role == Role.MASTER
    val cantCheckIn = window != CheckInWindow.OPEN || isMasterRole

    // 내 체크인 여부 = todayPeers 에 session.member.id 가 포함되어 있는지
    val myMemberId = session.member?.id
    val mySelfCheckIn = todayPeers.firstOrNull { it.member.id == myMemberId }
    val checkedIn = mySelfCheckIn != null
    val myCheckInTimeLabel = mySelfCheckIn?.attendance?.checkInAt?.let {
        it.atZone(ZoneId.of("Asia/Seoul")).toLocalTime()
            .format(DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
    } ?: LocalTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))

    PosseScreen(title = "Attendance", subtitle = "$dateLabel\n$classLabel") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 운영진 전용 (관장+코치+창조자) — 출결 검토 진입점
            if (session.role.isStaff) {
                item {
                    PosseCard(
                        modifier = Modifier.clickable { onOpenReview() },
                        leftStripeColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            "운영진 모드",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "▸ 회원 출결 검토",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "탭하면 회원별 출석 기록 확인 및 삭제",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                PosseCard {
                    Text(
                        when {
                            isMasterRole -> "관장 — 체크인 불필요"
                            cantCheckIn -> "체크인 불가"
                            checkedIn -> "체크인 완료"
                            else -> "출석 체크인"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when {
                            isMasterRole -> "체육관을 상시 운영 중이라 별도 체크인이 없습니다. 🥋"
                            closedToday -> "오늘은 $closedReason 입니다. 🧘"
                            classesEnded -> "다음 수업에서 봐요! 👋"
                            tooEarly -> "수업 시작 30분 전부터 체크인 가능합니다. 🕰️"
                            checkedIn -> "오늘도 불태워봅시다! 🔥"
                            else -> "지금 체크인 가능합니다\n(수업 시작 10분 후까지). ✅"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val memberId = myMemberId ?: return@Button
                            if (checkedIn) attendanceVm.cancelCheckIn(memberId)
                            else attendanceVm.checkIn(memberId, classDefId = "default")
                        },
                        enabled = !cantCheckIn && myMemberId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (checkedIn) Color(0xFFC8102E)
                            else Color(0xFF1E88E5),
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            when {
                                isMasterRole -> "관장"
                                closedToday -> "휴무일"
                                classesEnded -> "수업 종료"
                                tooEarly -> "대기"
                                checkedIn -> "체크인 취소"
                                else -> "체크인"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    if (checkedIn && !cantCheckIn) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "체크인 시각 $myCheckInTimeLabel 🔥",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            // 출석 동료 리스트 — todayPeers 는 이미 본인 포함 (collectionGroup 쿼리 결과)
            val displayList = if (cantCheckIn) emptyList() else todayPeers

            if (!cantCheckIn) {
                item {
                    Text(
                        "오늘 함께하는 동료 ${displayList.size}명",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }
            items(displayList.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { peer ->
                        val isSelf = peer.member.id == myMemberId
                        val timeLabel = peer.attendance.checkInAt
                            .atZone(ZoneId.of("Asia/Seoul"))
                            .toLocalTime()
                            .format(DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
                        PosseCard(
                            modifier = Modifier.weight(1f),
                            padding = PaddingValues(12.dp),
                            leftStripeColor = peer.member.belt.ringColor,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarTile(
                                    avatar = avatarById(peer.member.avatarId),
                                    size = 40.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        if (isSelf) "${peer.member.name} (나)" else peer.member.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelf) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        timeLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
