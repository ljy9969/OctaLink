package com.unboundapex.octalink.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unboundapex.octalink.data.Avatar

/**
 * 원형 캐릭터 타일. 캐릭터 본체 PNG 만 렌더 — 벨트 색 링 / 벨트 마스크 tint 모두 제거됨.
 * 벨트 색 표시는 카드 좌측 스트라이프, 벨트 칩, "{벨트명} 벨트" 텍스트로 분리되어 있어
 * 아바타에 색을 덧입히면 시각 정보가 중복되고, 스프라이트 슬라이싱 오류(쇼츠 영역 포함)가
 * 다른 벨트색일 때 의도하지 않은 영역까지 tint 되는 부작용이 있었음.
 */
@Composable
fun AvatarTile(
    avatar: Avatar,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val context = LocalContext.current
    val bodyResId = remember(avatar.bodyResourceName) {
        context.resources.getIdentifier(avatar.bodyResourceName, "drawable", context.packageName)
    }
    val hasImage = bodyResId != 0

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (hasImage) Color.Transparent else avatar.accent),
        contentAlignment = Alignment.Center,
    ) {
        if (hasImage) {
            Image(
                painter = painterResource(bodyResId),
                contentDescription = avatar.displayName,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = avatar.initial,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value / 2.6f).sp,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
