package com.unboundapex.octalink.ui.screens.exchange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import com.unboundapex.octalink.data.geo.GeoDistance
import com.unboundapex.octalink.ui.components.PosseScreen

/**
 * 가입 체육관 → 관원 신청 페이지(교류전 지도·목록에서 진입).
 * 체육관 이름·거리 + 관원(체급·벨트·경력·전적) 목록 + 결투 신청. 신청 이후 승인·일정·결과는 교류전 화면.
 */
@Composable
fun ExchangeGymScreen(
    memberId: String,
    myGymId: String,
    gymId: String,
    onBack: () -> Unit,
    vm: ExchangeGymViewModel = viewModel(),
) {
    LaunchedEffect(memberId, myGymId, gymId) { vm.bind(memberId, myGymId, gymId) }

    val gym by vm.targetGym.collectAsState()
    val myGym by vm.myGym.collectAsState()
    val directory by vm.directory.collectAsState()
    val activeOpponentIds by vm.activeOpponentIds.collectAsState()
    val message by vm.message.collectAsState()

    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    PosseScreen(
        title = "교류 신청",
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                val g = gym
                val m = myGym
                val distance = if (g?.lat != null && g.lng != null && m?.lat != null && m.lng != null) {
                    GeoDistance.label(GeoDistance.meters(m.lat, m.lng, g.lat, g.lng))
                } else null
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(g?.label ?: "체육관", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(
                            g?.address?.split(" ")?.getOrNull(1),
                            distance?.let { "우리 체육관에서 $it" },
                            "관원 ${directory.size}명",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (directory.isEmpty()) {
                item {
                    Text(
                        "이 체육관에 표시할 관원이 없어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(directory, key = { it.id }) { p ->
                ExchangeProfileCard(
                    profile = p,
                    trailing = {
                        if (p.id != memberId) {
                            Button(
                                onClick = { vm.request(p.id) },
                                enabled = p.id !in activeOpponentIds,
                            ) { Text(if (p.id in activeOpponentIds) "진행중" else "신청") }
                        }
                    },
                )
            }
            item {
                Text(
                    "신청하면 상대 본인과 양쪽 운영진이 모두 승인해야 확정돼요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
