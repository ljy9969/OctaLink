package com.unboundapex.octalink.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unboundapex.octalink.data.schema.isStaff
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen

private data class Post(val author: String, val time: String, val tag: String, val body: String, val likes: Int)

private val posts = listOf(
    Post("관장 김파시", "5/4 월 11:30", "공지", "이번 주 금요일 스파링 데이입니다. 마우스피스 꼭 챙겨오세요.", 24),
    Post("이지연", "5/4 월 09:15", "기록", "오늘 첫 스파링 했습니다. 잽 거리감 잡는 게 제일 어려웠어요.", 18),
    Post("박정호", "5/3 일 18:42", "팁", "샌드백 칠 때 발 위치 영상으로 찍어보니 자세 다 무너져 있더라구요. 추천합니다.", 31),
)

@Composable
fun CommunityScreen(sessionVm: SessionViewModel) {
    val session by sessionVm.state.collectAsState()
    PosseScreen(title = "Community", subtitle = "팀원들의 기록과 응원") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 글 작성 진입점 — 모든 회원 + 운영진은 공지 작성 추가
            item {
                PosseCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ComposeAction(
                            label = "+ 글 쓰기",
                            modifier = Modifier.weight(1f),
                            onClick = {},
                        )
                        if (session.role.isStaff) {
                            ComposeAction(
                                label = "+ 공지 작성",
                                modifier = Modifier.weight(1f),
                                onClick = {},
                                tagBg = MaterialTheme.colorScheme.primary,
                                tagFg = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "준비 중 — Firestore + Auth 도입 후 활성화",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(posts) { p ->
                PosseCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TagBadge(p.tag)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            p.author,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            p.time,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(p.body, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("♥ ${p.likes}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ComposeAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    tagBg: Color = MaterialTheme.colorScheme.surfaceVariant,
    tagFg: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = tagFg,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tagBg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun TagBadge(tag: String) {
    val (bg, fg) = when (tag) {
        "공지" -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        "기록" -> Color(0xFF1E88E5) to Color.White
        "팁"   -> Color(0xFFFBC02D) to Color(0xFF1A1A1A)
        "질문" -> Color(0xFF7B1FA2) to Color.White
        else   -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = tag,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
