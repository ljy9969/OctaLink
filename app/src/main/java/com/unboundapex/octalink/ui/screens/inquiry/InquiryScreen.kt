package com.unboundapex.octalink.ui.screens.inquiry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
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
import com.unboundapex.octalink.data.schema.InquiryCategory
import com.unboundapex.octalink.data.schema.InquiryDoc
import com.unboundapex.octalink.data.schema.InquiryStatus
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import kotlinx.coroutines.flow.flowOf

@Composable
fun InquiryScreen(
    sessionVm: SessionViewModel,
    onBack: () -> Unit,
    vm: InquiryViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    val myId = session.member?.id
    val myName = session.name
    val writeState by vm.writeState.collectAsState()
    val myInquiries by remember(myId) {
        if (myId != null) vm.mine(myId) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    var category by remember { mutableStateOf(InquiryCategory.QUESTION) }
    var text by remember { mutableStateOf("") }

    // 전송 완료되면 입력 비우고 상태 초기화.
    LaunchedEffect(writeState) {
        if (writeState is InquiryWriteState.Done) {
            text = ""
            vm.resetWriteState()
        }
    }

    PosseScreen(
        title = "1:1 문의",
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
            item {
                PosseCard {
                    Text("문의 남기기", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "칭찬·개선 제안·문의·버그를 남겨주시면 확인 후 답변드립니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InquiryCategory.values().forEach { c ->
                            CategoryChip(
                                label = c.label,
                                color = inquiryCategoryColor(c),
                                selected = category == c,
                                onClick = { category = c },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("내용을 입력해 주세요") },
                    )
                    val ws = writeState
                    if (ws is InquiryWriteState.Error) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            ws.message,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { if (myId != null) vm.submit(myId, myName, category, text) },
                        enabled = myId != null && text.isNotBlank() && writeState !is InquiryWriteState.Sending,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (writeState is InquiryWriteState.Sending) "전송 중…" else "보내기")
                    }
                }
            }

            item {
                Text(
                    "내 문의",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (myInquiries.isEmpty()) {
                item {
                    Text(
                        "아직 남긴 문의가 없어요.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(myInquiries, key = { it.id }) { inq ->
                    InquiryCard(inq)
                }
            }
        }
    }
}

/** 카테고리별 고유 색 — 칩/배지에 공통 사용(다크·라이트 모두 가독). */
internal fun inquiryCategoryColor(c: InquiryCategory): Color = when (c) {
    InquiryCategory.PRAISE -> Color(0xFF2E7D32)      // 초록 — 칭찬
    InquiryCategory.IMPROVEMENT -> Color(0xFF1565C0) // 파랑 — 개선 제안
    InquiryCategory.QUESTION -> Color(0xFF6A4CAF)    // 보라 — 문의
    InquiryCategory.BUG -> Color(0xFFC8102E)         // 빨강 — 버그 신고
}

@Composable
private fun CategoryChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) Color.White else color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color else color.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun InquiryCard(inq: InquiryDoc) {
    PosseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.weight(1f))
            Text(
                if (inq.status == InquiryStatus.ANSWERED) "답변 완료" else "확인 중",
                style = MaterialTheme.typography.labelSmall,
                color = if (inq.status == InquiryStatus.ANSWERED) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(inq.text, style = MaterialTheme.typography.bodyMedium)
        val answer = inq.answer
        if (!answer.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
            ) {
                Text(
                    "답변",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    answer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
