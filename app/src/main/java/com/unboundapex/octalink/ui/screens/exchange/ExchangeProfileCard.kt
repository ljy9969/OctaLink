package com.unboundapex.octalink.ui.screens.exchange

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unboundapex.octalink.data.avatarById
import com.unboundapex.octalink.data.schema.PublicProfileDoc
import com.unboundapex.octalink.ui.components.AvatarTile
import com.unboundapex.octalink.ui.components.PosseCard
import java.time.LocalDate

/** 교류전 명단/상대 표시용 제한 프로필 카드 — 성별·체급·벨트·경력·전적만. */
@Composable
fun ExchangeProfileCard(
    profile: PublicProfileDoc,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    PosseCard(modifier = modifier, leftStripeColor = profile.belt.ringColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarTile(avatar = avatarById(profile.avatarId), size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${genderLabel(profile.gender)} · ${profile.weightClass.displayName} · ${profile.belt.displayName} 벨트",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${careerLabel(profile.careerStartYm)}  ·  ${recordLabel(profile)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

internal fun genderLabel(gender: String?): String = when (gender) {
    "MALE" -> "남"
    "FEMALE" -> "여"
    else -> "-"
}

/** "YYYY-MM" → "경력 N년차". 미입력이면 "경력 미입력". */
internal fun careerLabel(careerStartYm: String?): String {
    val year = careerStartYm?.take(4)?.toIntOrNull() ?: return "경력 미입력"
    val yrs = LocalDate.now().year - year + 1
    return "경력 ${yrs.coerceAtLeast(1)}년차"
}

/** "N전 N승 N패 N무". */
internal fun recordLabel(p: PublicProfileDoc): String {
    val total = p.exchangeWins + p.exchangeLosses + p.exchangeDraws
    return "${total}전 ${p.exchangeWins}승 ${p.exchangeLosses}패 ${p.exchangeDraws}무"
}
