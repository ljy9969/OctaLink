package com.unboundapex.octalink.ui.screens.creator

import androidx.compose.foundation.background
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.Role
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen

/**
 * 창조자 전용 — 회원 역할 부여 화면. [Role.CREATOR] 만 진입 (PosseApp 라우팅).
 *
 * 데이터 출처: [RoleGrantViewModel] → [com.unboundapex.octalink.data.repo.MemberRepository].
 * Repository 가 in-memory 인지 Firestore 인지 무관 — VM 만 변경하면 됨.
 *
 * 정책: APPROVED 회원 중 CREATOR 본인 제외. 체급/벨트 내림차순 정렬.
 */
@Composable
fun CreatorScreen(
    onBack: () -> Unit,
    vm: RoleGrantViewModel = viewModel(),
) {
    val members by vm.grantable.collectAsState()

    PosseScreen(title = "Creator", subtitle = "앱 제작자 전용 — 회원 역할 부여") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                PosseCard(leftStripeColor = MaterialTheme.colorScheme.primary) {
                    Text(
                        "권한 부여",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "회원 역할 변경은 창조자 단독 권한입니다. 관장 계정 탈취 시 권한 상승 공격을 차단하기 위해 분리되었습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(members, key = { it.id }) { m ->
                MemberRoleRow(
                    member = m,
                    onChangeRole = { target -> vm.grantRole(m.id, target) },
                )
            }
        }
    }
}

@Composable
private fun MemberRoleRow(
    member: MemberDoc,
    onChangeRole: (Role) -> Unit,
) {
    PosseCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    RoleBadge(member.role)
                }
                Text(
                    "${member.weightClass.displayName} · ${member.belt.displayName} 벨트",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val targets = when (member.role) {
                Role.MEMBER -> listOf(Role.COACH, Role.MASTER)
                Role.COACH -> listOf(Role.MEMBER, Role.MASTER)
                Role.MASTER -> listOf(Role.MEMBER, Role.COACH)
                Role.CREATOR -> emptyList()
            }
            if (targets.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    targets.forEach { target ->
                        RoleChangeButton(
                            label = roleLabel(target),
                            onClick = { onChangeRole(target) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(role: Role) {
    val (bg, fg, label) = when (role) {
        Role.CREATOR -> Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, "창조자")
        Role.MASTER -> Triple(Color(0xFFC8102E), Color.White, "관장")
        Role.COACH -> Triple(Color(0xFF1E88E5), Color.White, "코치")
        Role.MEMBER -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, "회원")
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun RoleChangeButton(label: String, onClick: () -> Unit) {
    Text(
        text = "→ $label",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun roleLabel(r: Role): String = when (r) {
    Role.CREATOR -> "창조자"
    Role.MASTER -> "관장"
    Role.COACH -> "코치"
    Role.MEMBER -> "회원"
}
