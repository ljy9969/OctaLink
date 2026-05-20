package com.unboundapex.octalink.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class SkillStat(val label: String, val value: Float)

@Composable
fun HexagonSkillChart(
    skills: List<SkillStat>,
    modifier: Modifier = Modifier,
    rings: Int = 4,
) {
    require(skills.size == 6) { "HexagonSkillChart expects exactly 6 skills" }
    val gridColor = MaterialTheme.colorScheme.outline
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val strokeColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onBackground
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = labelColor)
    val valueStyle = MaterialTheme.typography.bodyMedium.copy(color = valueColor)

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.58f
        val labelGap = 8.dp.toPx()

        for (r in 1..rings) {
            val ringRadius = radius * (r / rings.toFloat())
            drawHexPath(center, ringRadius, gridColor, stroke = 1f)
        }
        for (i in 0 until 6) {
            val angle = angleAt(i)
            val end = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
            drawLine(gridColor, start = center, end = end, strokeWidth = 1f)
        }

        val polyPath = Path()
        skills.forEachIndexed { i, stat ->
            val v = stat.value.coerceIn(0f, 1f)
            val angle = angleAt(i)
            val px = center.x + radius * v * cos(angle)
            val py = center.y + radius * v * sin(angle)
            if (i == 0) polyPath.moveTo(px, py) else polyPath.lineTo(px, py)
        }
        polyPath.close()
        drawPath(polyPath, color = fillColor)
        drawPath(polyPath, color = strokeColor, style = Stroke(width = 3f))

        skills.forEachIndexed { i, stat ->
            val angle = angleAt(i)
            val anchorX = center.x + (radius + labelGap) * cos(angle)
            val anchorY = center.y + (radius + labelGap) * sin(angle)

            val labelLayout = textMeasurer.measure(AnnotatedString(stat.label), labelStyle)
            val pct = "${(stat.value * 100).roundToInt()}"
            val valueLayout = textMeasurer.measure(AnnotatedString(pct), valueStyle)

            val blockHeight = labelLayout.size.height + valueLayout.size.height + 2.dp.toPx()
            val sinA = sin(angle)
            val verticalAnchor = when {
                sinA < -0.5f -> anchorY - blockHeight
                sinA > 0.5f -> anchorY
                else -> anchorY - blockHeight / 2f
            }

            drawText(
                textMeasurer = textMeasurer,
                text = stat.label,
                topLeft = Offset(anchorX - labelLayout.size.width / 2f, verticalAnchor),
                style = labelStyle
            )
            drawText(
                textMeasurer = textMeasurer,
                text = pct,
                topLeft = Offset(
                    anchorX - valueLayout.size.width / 2f,
                    verticalAnchor + labelLayout.size.height + 2.dp.toPx()
                ),
                style = valueStyle
            )
        }
    }
}

private fun angleAt(i: Int): Float {
    val step = (2 * PI / 6).toFloat()
    return -PI.toFloat() / 2f + step * i
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHexPath(
    center: Offset,
    radius: Float,
    color: Color,
    stroke: Float
) {
    val path = Path()
    for (i in 0 until 6) {
        val a = angleAt(i)
        val x = center.x + radius * cos(a)
        val y = center.y + radius * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color, style = Stroke(width = stroke))
}
