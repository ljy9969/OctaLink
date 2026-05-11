package com.unboundapex.octalink.ui.screens.admin

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.isCreator
import com.unboundapex.octalink.data.schema.isMaster
import com.unboundapex.octalink.data.schema.isStaff
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen

/**
 * 운영진/관장/창조자 전용 페이지 — 하단 nav 탭 "운영" 진입점.
 *
 * 권한별 카드 가시성:
 * - 운영진 (COACH+): 회원 코멘트 / 출결 검토 / 토너먼트 / 공지 / 스킬 입력 (미구현 placeholder)
 * - 관장 (MASTER+): 회원 가입 승인 큐 (활성) + 스킬 검토 (placeholder)
 * - 창조자 (CREATOR): 회원 역할 부여 (별도 [com.unboundapex.octalink.ui.screens.creator.CreatorScreen])
 *
 * 회원(MEMBER)에게는 탭 자체가 안 보임 (PosseApp 의 동적 tabs 처리).
 */
@Composable
fun AdminScreen(
    sessionVm: SessionViewModel,
    onOpenCreator: () -> Unit,
    approvalVm: MemberApprovalViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val pendingMembers by approvalVm.pending.collectAsState()
    val role = session.role

    val subtitle = when {
        role.isCreator -> "운영 전체 + 권한 부여 (창조자)"
        role.isMaster -> "운영 전체 (관장)"
        role.isStaff -> "일상 운영 (코치)"
        else -> ""
    }

    PosseScreen(title = "Admin", subtitle = subtitle) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // 운영진 공통 (COACH + MASTER + CREATOR) — 미구현 placeholder들
            if (role.isStaff) {
                item {
                    PosseCard(leftStripeColor = MaterialTheme.colorScheme.primary) {
                        Text("운영진 공통", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        StaffAction("회원 한 줄 코멘트 작성") {}
                        StaffAction("출결 검토 — 회원별 일자별 출석 이력") {}
                        StaffAction("토너먼트 추첨/대진 관리") {}
                        StaffAction("공지 작성 (커뮤니티)") {}
                        StaffAction("스킬 점수 입력 (제안 → 관장 검토)") {}
                        Spacer(Modifier.height(4.dp))
                        FootnoteText("준비 중 — 후속 Repository 작업에서 활성화")
                    }
                }
            }

            // 관장 — 회원 가입 승인 큐 (활성)
            if (role.isMaster) {
                item {
                    PosseCard(leftStripeColor = MaterialTheme.colorScheme.primary) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "회원 가입 승인",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            PendingBadge(count = pendingMembers.size)
                        }
                        Spacer(Modifier.height(4.dp))
                        if (pendingMembers.isEmpty()) {
                            Text(
                                "현재 대기 중인 가입 신청이 없습니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            pendingMembers.forEach { m ->
                                PendingMemberRow(
                                    member = m,
                                    onApprove = { approvalVm.approve(m.id) },
                                    onReject = { approvalVm.reject(m.id) },
                                )
                            }
                        }
                    }
                }
                item {
                    PosseCard(leftStripeColor = MaterialTheme.colorScheme.primary) {
                        Text("관장 전용", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        StaffAction("스킬 점수 검토/확정 (PROPOSED → APPROVED)") {}
                        Spacer(Modifier.height(4.dp))
                        FootnoteText("준비 중 — 후속 Repository 작업에서 활성화")
                    }
                }
            }

            // 창조자 단독 (CREATOR) — 회원 역할 부여 페이지 진입
            if (role.isCreator) {
                item {
                    PosseCard(leftStripeColor = MaterialTheme.colorScheme.primary) {
                        Text("창조자 전용", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        StaffAction("권한 부여 페이지 (회원 → 코치/관장 승격)") {
                            onOpenCreator()
                        }
                        Spacer(Modifier.height(4.dp))
                        FootnoteText("관장 계정 탈취 시 권한 상승 공격 차단 위해 분리됨")
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingMemberRow(
    member: MemberDoc,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(member.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${member.weightClass.displayName} · ${member.belt.displayName} 벨트",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionChip(
            label = "승인",
            bg = MaterialTheme.colorScheme.primary,
            fg = MaterialTheme.colorScheme.onPrimary,
            onClick = onApprove,
        )
        Spacer(Modifier.width(6.dp))
        ActionChip(
            label = "거부",
            bg = MaterialTheme.colorScheme.surfaceVariant,
            fg = MaterialTheme.colorScheme.onSurface,
            onClick = onReject,
        )
    }
}

@Composable
private fun PendingBadge(count: Int) {
    if (count <= 0) return
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun ActionChip(
    label: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
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

@Composable
private fun StaffAction(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "▸ $label",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FootnoteText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
