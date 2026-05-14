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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unboundapex.octalink.data.Avatar
import com.unboundapex.octalink.data.Belt

/**
 * Circular avatar tile with optional belt-color ring and belt mask overlay.
 *
 * Rendering layers (when image assets exist):
 *   1. bodyResourceName PNG  — full character, TopCenter crop
 *   2. beltMaskResourceName PNG  — white belt silhouette, tinted with belt.ringColor
 *      (also drawn for Belt.UNKNOWN — gray tint hides the natural belt color
 *      to make "ungraded" status visually clear)
 *
 * [ringColor] overrides the automatic belt-color ring (useful for selection highlights).
 * Ring border is skipped for Belt.UNKNOWN to further distinguish ungraded members.
 */
@Composable
fun AvatarTile(
    avatar: Avatar,
    belt: Belt = Belt.UNKNOWN,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    ringColor: Color? = null,
    ringWidth: Dp = 3.dp,
    showBeltRing: Boolean = true,
) {
    val context = LocalContext.current
    val bodyResId = remember(avatar.bodyResourceName) {
        context.resources.getIdentifier(avatar.bodyResourceName, "drawable", context.packageName)
    }
    val maskResId = remember(avatar.beltMaskResourceName) {
        context.resources.getIdentifier(avatar.beltMaskResourceName, "drawable", context.packageName)
    }

    val hasImage = bodyResId != 0
    val autoRing = if (showBeltRing && belt != Belt.UNKNOWN) belt.ringColor else null
    val effectiveRing = ringColor ?: autoRing

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (hasImage) Color.Transparent else avatar.accent)
            .let { m -> if (effectiveRing != null) m.border(ringWidth, effectiveRing, CircleShape) else m },
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
            if (maskResId != 0) {
                Image(
                    painter = painterResource(maskResId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(belt.ringColor),
                )
            }
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
