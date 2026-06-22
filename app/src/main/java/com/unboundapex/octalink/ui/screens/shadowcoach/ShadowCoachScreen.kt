package com.unboundapex.octalink.ui.screens.shadowcoach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unboundapex.octalink.data.shadowcoach.PostureCheck
import com.unboundapex.octalink.data.shadowcoach.Technique
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen

/**
 * AI 쉐도우 코치 — 카메라 기반 실시간 자세 분석 (격투기 쉐도우 코칭).
 *
 * **현재: Phase 1 스캐폴드** — 진입점 + 기능 안내 + MVP 범위 표시까지. 카메라(CameraX) +
 * 온디바이스 포즈 추정(MediaPipe PoseLandmarker) + 잽 감지/실시간 코칭은 다음 단계에서 구현.
 *
 * 처리 방식: **온디바이스** — 카메라 영상은 단말 밖으로 전송되지 않음. (관절 좌표만 로컬 분석)
 */
@Composable
fun ShadowCoachScreen(onBack: () -> Unit = {}) {
    PosseScreen(title = "AI 쉐도우 코치", subtitle = "카메라로 쉐도우 복싱 자세 실시간 분석") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                PosseCard {
                    Text("이렇게 동작해요", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• 폰을 세워두고 전신이 화면에 들어오게 서요\n" +
                            "• 카메라가 관절을 인식해 쉐도우 자세를 실시간 분석해요\n" +
                            "• 잽 횟수 카운트 + 가드·턱 자세 코칭 + 세션 요약 점수\n" +
                            "• 영상은 저장·전송되지 않고 단말에서만 분석돼요 (온디바이스)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                PosseCard {
                    Text("MVP 범위", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("감지 기술", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        Technique.entries.joinToString("  ") {
                            (if (it.enabledInMvp) "✅ " else "🕒 ") + it.displayName
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("자세 코칭", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        PostureCheck.entries.joinToString("  ") {
                            (if (it.enabledInMvp) "✅ " else "🕒 ") + it.displayName
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                PosseCard {
                    Text(
                        "🚧 준비 중",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "카메라 실시간 분석 기능을 다음 업데이트에서 제공할 예정이에요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
