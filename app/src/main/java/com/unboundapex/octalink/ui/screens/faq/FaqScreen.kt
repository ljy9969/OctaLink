package com.unboundapex.octalink.ui.screens.faq

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen

private data class FaqItem(val q: String, val a: String)

// 자주 묻는 질문 — support/dev 에이전트 초안 → 실제 기능 대조 검수 → CEO 승인 완료 (2026-09-17).
private val FAQ_ITEMS = listOf(
    FaqItem(
        "가입했는데 앱 이용이 안 돼요.",
        "가입 후 관장님(운영진)의 승인이 필요합니다. 승인되면 알림으로 안내드리며, 그때부터 모든 기능을 이용하실 수 있습니다.",
    ),
    FaqItem(
        "출석은 어떻게 체크하나요?",
        "출석 체크인은 출석 화면에서 하실 수 있습니다. 지난 출석 기록은 홈 화면의 '내 주간 출석률' 카드를 누르면 확인하실 수 있습니다.",
    ),
    FaqItem(
        "벨트와 실력은 어떻게 올라가나요?",
        "관장님이 실력을 평가해 스킬을 승급·업데이트하며, 변동 시 알림으로 안내드립니다. 내 실력은 프로필의 6각형 그래프에서 확인하실 수 있습니다.",
    ),
    FaqItem(
        "교류전에는 어떻게 참여하나요?",
        "교류전이 열리면 대진표가 추첨되며, 결과를 알림으로 알려드립니다. 자세한 대진은 교류전 화면에서 확인하실 수 있습니다.",
    ),
    FaqItem(
        "수업 시작 전에 알림을 받고 싶어요.",
        "설정 > 알림 설정 > 수업 리마인더에서 요일과 시간을 선택하시면 수업 30분 전에 알려드립니다.",
    ),
    FaqItem(
        "알림이 오지 않거나 늦게 와요.",
        "수업 리마인더는 \"알람 및 리마인더\" 권한과 배터리 최적화 제외가 필요합니다. 설정 > 알림 설정에서 안내되는 권한을 허용해 주세요. 삼성 기기는 배터리 사용을 \"제한 없음\"으로 변경하시면 도움이 됩니다.",
    ),
    FaqItem(
        "로그아웃하면 기록이 사라지나요?",
        "아니요. 로그아웃해도 데이터는 보존되며, 다음 로그인 시 복원됩니다.",
    ),
    FaqItem(
        "탈퇴하면 기록은 어떻게 되나요?",
        "출석·실력·코멘트 등 과거 기록은 운영 자료로 보존됩니다. 명단에서 완전 삭제를 원하시면 운영자에게 요청해 주세요.",
    ),
)

@Composable
fun FaqScreen(onBack: () -> Unit) {
    PosseScreen(
        title = "자주 묻는 질문",
        trailing = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "뒤로",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) {
        var expandedIndex by remember { mutableStateOf(-1) }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(FAQ_ITEMS) { i, item ->
                val expanded = expandedIndex == i
                PosseCard(
                    modifier = Modifier.clickable {
                        expandedIndex = if (expanded) -1 else i
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.q,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (expanded) "−" else "+",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (expanded) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            item.a,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
