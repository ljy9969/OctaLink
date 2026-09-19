package com.unboundapex.octalink.ui.screens.inquiry

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.schema.InquiryDoc
import com.unboundapex.octalink.data.schema.InquiryStatus
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen

/** 운영진(관장) 전용 — 회원 1:1 문의 확인 + 답변 수정/게시. 게시 권한은 Firestore rules(isMaster) 도 강제. */
@Composable
fun AdminInquiryScreen(
    sessionVm: SessionViewModel,
    onBack: () -> Unit,
    vm: InquiryViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val answeredBy = session.member?.id ?: "operator"
    val inquiries by remember { vm.allForGym() }.collectAsState(initial = emptyList())

    PosseScreen(
        title = "1:1 문의 관리",
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
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (inquiries.isEmpty()) {
                item {
                    Text(
                        "아직 들어온 문의가 없습니다.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(inquiries, key = { it.id }) { inq ->
                    AdminInquiryCard(
                        inq = inq,
                        onPost = { answer, onDone, onError ->
                            vm.postAnswer(inq.id, answer, answeredBy, onDone, onError)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminInquiryCard(
    inq: InquiryDoc,
    onPost: (answer: String, onDone: () -> Unit, onError: (String) -> Unit) -> Unit,
) {
    // 사전 입력: DRAFTED(막 올라온 CEO 검토 완료 초안 — 최초 답변 또는 '작업 완료 안내')면 그 초안을
    // 우선 채운다. 그 외(ANSWERED 등)엔 게시된 답변을 보여준다. status 가 바뀌면 다시 채움.
    var answer by remember(inq.id, inq.status) {
        mutableStateOf(
            if (inq.status == InquiryStatus.DRAFTED) (inq.draftAnswer ?: inq.answer ?: "")
            else (inq.answer ?: inq.draftAnswer ?: ""),
        )
    }
    var sending by remember(inq.id) { mutableStateOf(false) }
    var error by remember(inq.id) { mutableStateOf<String?>(null) }

    PosseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                INQUIRY_TS_FMT.format(inq.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            val catColor = inquiryCategoryColor(inq.category)
            Text(
                inq.category.label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(catColor)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                inq.authorName.ifBlank { "회원" },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                statusLabel(inq.status),
                style = MaterialTheme.typography.labelSmall,
                color = if (inq.status == InquiryStatus.ANSWERED) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(inq.text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        if (!inq.draftAnswer.isNullOrBlank() && inq.status == InquiryStatus.DRAFTED) {
            // 이미 답변이 게시된 뒤 다시 DRAFTED 면 = dev 작업 완료 후 support 가 올린 '완료 안내' 초안.
            val isCompletion = !inq.answer.isNullOrBlank()
            Text(
                if (isCompletion) "작업 완료 안내 초안이 준비됐어요 (CEO 검토 완료) — 수정 후 게시하세요."
                else "CEO 검토 완료 초안이 입력되어 있어요 — 수정 후 게시하세요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
        }
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("답변") },
        )
        error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                sending = true
                error = null
                onPost(answer, { sending = false }, { e -> sending = false; error = e })
            },
            enabled = !sending && answer.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    sending -> "게시 중…"
                    inq.status == InquiryStatus.ANSWERED -> "답변 수정 게시"
                    else -> "게시"
                },
            )
        }
    }
}

private fun statusLabel(s: InquiryStatus): String = when (s) {
    InquiryStatus.PENDING -> "확인 중"
    InquiryStatus.DRAFTED -> "초안 검토"
    InquiryStatus.ANSWERED -> "답변 완료"
}
