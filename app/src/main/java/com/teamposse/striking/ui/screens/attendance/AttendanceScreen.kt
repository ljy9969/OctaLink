package com.teamposse.striking.ui.screens.attendance

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.teamposse.striking.data.Belt
import com.teamposse.striking.data.CheckInWindow
import com.teamposse.striking.data.avatarById
import com.teamposse.striking.data.checkInWindow
import com.teamposse.striking.data.session.SessionViewModel
import com.teamposse.striking.data.currentOrNextClassLabel
import com.teamposse.striking.data.isClosed
import com.teamposse.striking.data.isHoliday
import java.time.LocalDate
import java.time.ZoneId
import com.teamposse.striking.ui.components.AvatarTile
import com.teamposse.striking.ui.components.PosseCard
import com.teamposse.striking.ui.components.PosseScreen
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private data class CheckedIn(
    val name: String,
    val belt: Belt,
    val avatarId: String,
    val time: String,
)

private val alreadyCheckedIn = listOf(
    CheckedIn("박정호", Belt.BLUE, "ken", "18:42"),
    CheckedIn("김상혁", Belt.PURPLE, "akuma", "18:48"),
    CheckedIn("정유진", Belt.WHITE, "chun_li", "18:51"),
    CheckedIn("신예린", Belt.WHITE, "cammy", "18:55"),
    CheckedIn("한도윤", Belt.BLUE, "ryu", "18:58"),
    CheckedIn("최민서", Belt.BLUE, "guile", "19:01"),
)

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
fun AttendanceScreen(sessionVm: SessionViewModel) {
    val session by sessionVm.state.collectAsState()
    var checkedIn by remember { mutableStateOf(false) }
    val nowLabel = remember { LocalTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("HH:mm")) }
    val classLabel = remember { currentOrNextClassLabel() }
    val today = remember { LocalDate.now(ZoneId.of("Asia/Seoul")) }
    val dateLabel = remember(today) { shortDateLabel(today) }
    val closedToday = remember { isClosed(today) }
    val closedReason = remember { if (isHoliday(today)) "공휴일" else "일요일 정기 휴무" }
    val window = remember { checkInWindow() }
    val classesEnded = window == CheckInWindow.AFTER_LAST_CLASS || window == CheckInWindow.NO_CLASS_TODAY
    val tooEarly = window == CheckInWindow.BEFORE_WINDOW
    val cantCheckIn = window != CheckInWindow.OPEN

    // 체크인 불가 시 상태 강제 해제
    if (cantCheckIn && checkedIn) checkedIn = false

    PosseScreen(title = "Attendance", subtitle = "$dateLabel\n$classLabel") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                PosseCard {
                    Text(
                        when {
                            cantCheckIn -> "체크인 불가"
                            checkedIn -> "체크인 완료"
                            else -> "출석 체크인"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when {
                            closedToday -> "오늘은 $closedReason 입니다. 🧘"
                            classesEnded -> "다음 수업에서 봐요! 👋"
                            tooEarly -> "수업 시작 30분 전부터 가능. 🕰️"
                            checkedIn -> "오늘도 불태워봅시다! 🔥"
                            else -> "지금 체크인 가능합니다. ✅"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { checkedIn = !checkedIn },
                        enabled = !cantCheckIn,
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
                            "체크인 시각 $nowLabel · 연속 출석 8일째 💪",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            val displayList = when {
                cantCheckIn -> emptyList()
                checkedIn -> alreadyCheckedIn + CheckedIn(
                    name = session.name,
                    belt = session.belt,
                    avatarId = session.avatarId,
                    time = nowLabel,
                )
                else -> alreadyCheckedIn
            }

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
                        val isSelf = peer.name == session.name
                        PosseCard(
                            modifier = Modifier.weight(1f),
                            padding = PaddingValues(12.dp),
                            leftStripeColor = peer.belt.ringColor,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarTile(
                                    avatar = avatarById(peer.avatarId),
                                    size = 40.dp,
                                    ringColor = null,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        if (isSelf) "${peer.name} (나)" else peer.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelf) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        peer.time,
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
