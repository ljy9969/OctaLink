package com.unboundapex.octalink.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * MMA 옥타곤 케이지 — 위에서 본 경기장 일러스트.
 * - 채워진 매트 (어두운 캔버스)
 * - 굵은 외곽 프레임 (빨강) = 케이지 펜스
 * - 8개 꼭짓점에 펜스 기둥
 * - 매트 가장자리 라인 + 중앙 원 (레프리 위치)
 */
@Composable
fun CageIcon(
    modifier: Modifier = Modifier,
    frameColor: Color = MaterialTheme.colorScheme.primary,
    matColor: Color = MaterialTheme.colorScheme.background,
    matEdgeColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val outerR = min(w, h) / 2f * 0.96f
        val matEdgeR = outerR * 0.78f
        val centerR = outerR * 0.18f

        // 옥타곤은 평평한 변이 상하로 오도록 -22.5도 회전
        val rotationOffset = -PI.toFloat() / 8f
        val step = (PI * 2 / 8).toFloat()

        val outer = (0 until 8).map { i ->
            val a = rotationOffset + step * i
            Offset(cx + outerR * cos(a), cy + outerR * sin(a))
        }
        val matEdge = (0 until 8).map { i ->
            val a = rotationOffset + step * i
            Offset(cx + matEdgeR * cos(a), cy + matEdgeR * sin(a))
        }

        val frameStroke = (size.minDimension * 0.07f).coerceAtLeast(2.5f)
        val matEdgeStroke = (size.minDimension * 0.018f).coerceAtLeast(1f)
        val postRadius = (size.minDimension * 0.05f).coerceAtLeast(2f)

        // 1) 채워진 매트 (배경 옥타곤)
        val matPath = Path().apply {
            moveTo(outer[0].x, outer[0].y)
            for (i in 1 until 8) lineTo(outer[i].x, outer[i].y)
            close()
        }
        drawPath(matPath, color = matColor)

        // 2) 매트 가장자리 라인 (페인트 보더 같은 안쪽 옥타곤)
        val matEdgePath = Path().apply {
            moveTo(matEdge[0].x, matEdge[0].y)
            for (i in 1 until 8) lineTo(matEdge[i].x, matEdge[i].y)
            close()
        }
        drawPath(matEdgePath, color = matEdgeColor, style = Stroke(width = matEdgeStroke))

        // 3) 외곽 프레임 (빨강 굵은 펜스)
        drawPath(
            matPath,
            color = frameColor,
            style = Stroke(width = frameStroke, cap = StrokeCap.Round),
        )

        // 4) 펜스 기둥 (꼭짓점에 동그란 포인트)
        outer.forEach { p ->
            drawCircle(frameColor, radius = postRadius, center = p)
        }

        // 5) 중앙 원 (레프리 위치 / 시작 마커)
        drawCircle(
            frameColor,
            radius = centerR,
            center = Offset(cx, cy),
            style = Stroke(width = matEdgeStroke * 1.4f),
        )
    }
}
