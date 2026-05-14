package com.unboundapex.octalink.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unboundapex.octalink.data.WeightClass

/**
 * 체급 안내 모달 — 페더/라이트/웰터/미들/헤비 별 체중 기준(kg) 표시.
 *
 * BracketDrawScreen + SignupScreen 양쪽에서 공유 사용. 운영자 / 신규 가입자 모두 본인 체급
 * 모를 때 즉시 참조 가능하도록 분리.
 */
@Composable
fun WeightClassInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "체급 안내",
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column {
                Text(
                    "체중 기준 (kg)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                WeightClass.values().forEachIndexed { idx, wc ->
                    val prevMax = if (idx == 0) null else WeightClass.values()[idx - 1].maxKg
                    val rangeText = when {
                        prevMax == null -> "~ ${wc.maxKg}kg"
                        wc.maxKg == null -> "${prevMax}kg ~"
                        else -> "${prevMax} ~ ${wc.maxKg}kg"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            wc.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .width(72.dp)
                        )
                        Text(
                            rangeText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Text(
                "닫기",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    )
}
