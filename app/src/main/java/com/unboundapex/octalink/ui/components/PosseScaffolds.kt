package com.unboundapex.octalink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun PosseScreen(
    title: String? = null,
    subtitle: String? = null,
    subtitleEmphasis: List<String> = emptyList(),
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (header != null) {
            header()
            if (subtitle != null) {
                Text(
                    text = buildEmphasizedSubtitle(
                        text = subtitle,
                        emphasis = subtitleEmphasis,
                        emphasisColor = MaterialTheme.colorScheme.primary,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-6).dp)
                        .padding(horizontal = 20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        } else if (title != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (subtitle != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        } else if (subtitle != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(20.dp))
        } else {
            Spacer(Modifier.height(20.dp))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            content()
        }
    }
}

private fun buildEmphasizedSubtitle(
    text: String,
    emphasis: List<String>,
    emphasisColor: Color,
): AnnotatedString {
    if (emphasis.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            var nextStart = -1
            var nextWord = ""
            for (word in emphasis) {
                if (word.isEmpty()) continue
                val idx = text.indexOf(word, cursor)
                if (idx >= 0 && (nextStart < 0 || idx < nextStart)) {
                    nextStart = idx
                    nextWord = word
                }
            }
            if (nextStart < 0) {
                append(text.substring(cursor))
                break
            }
            if (nextStart > cursor) append(text.substring(cursor, nextStart))
            withStyle(SpanStyle(color = emphasisColor, fontWeight = FontWeight.ExtraBold)) {
                append(nextWord)
            }
            cursor = nextStart + nextWord.length
        }
    }
}

@Composable
fun PosseCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    leftStripeColor: Color? = null,
    /** 그라데이션 스트라이프 — [leftStripeColor] 보다 우선. 둘 다 null 이면 스트라이프 없음. */
    leftStripeBrush: androidx.compose.ui.graphics.Brush? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        // 좌측 stripe 는 Column 의 측정 높이를 따라가야 함. 과거 구조는 `Row(IntrinsicSize.Min)` +
        // `Box.fillMaxHeight` 였는데, 자식이 `Modifier.aspectRatio(...)` 같이 intrinsic 쿼리에
        // 0을 반환하는 modifier 를 쓰면 측정 높이가 실제 렌더 높이보다 작게 잡혀서 하단 콘텐츠
        // (좋아요/수정/삭제, 더보기/접기 등) 가 Surface 의 둥근 clip 밖으로 잘리는 버그가 있었음.
        // drawBehind 는 measure 후 draw 단계에서 그리므로 stripe 가 실제 column 높이를 그대로
        // 따라가며, intrinsic 의존이 없어 어떤 자식 modifier 와도 안전하게 호환됨.
        val hasStripe = leftStripeBrush != null || leftStripeColor != null
        val stripeWidth = 8.dp
        val columnMod = if (hasStripe) {
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val w = stripeWidth.toPx()
                    when {
                        leftStripeBrush != null ->
                            drawRect(brush = leftStripeBrush, topLeft = Offset.Zero, size = Size(w, size.height))
                        leftStripeColor != null ->
                            drawRect(color = leftStripeColor, topLeft = Offset.Zero, size = Size(w, size.height))
                    }
                }
                .padding(start = stripeWidth)
                .padding(padding)
        } else {
            Modifier.padding(padding)
        }
        Column(
            modifier = columnMod,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { content() }
    }
}
