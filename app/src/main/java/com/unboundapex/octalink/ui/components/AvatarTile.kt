package com.unboundapex.octalink.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

@Composable
fun AvatarTile(
    avatar: Avatar,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    ringColor: Color? = null,
    ringWidth: Dp = 3.dp,
) {
    val context = LocalContext.current
    val resId = remember(avatar.resourceName) {
        context.resources.getIdentifier(avatar.resourceName, "drawable", context.packageName)
    }

    val hasImage = resId != 0
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (hasImage) Color.White else avatar.accent)
            .let { if (ringColor != null) it.border(ringWidth, ringColor, CircleShape) else it },
        contentAlignment = Alignment.Center
    ) {
        if (hasImage) {
            Image(
                painter = painterResource(resId),
                contentDescription = avatar.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = avatar.initial,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value / 2.6f).sp,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
