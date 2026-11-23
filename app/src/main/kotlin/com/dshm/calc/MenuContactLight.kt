package com.dshm.calc

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** A local glint on the surface and two short reflections where the light meets the bevel. */
internal fun Modifier.menuContactLight(
    shape: Shape, dark: Boolean, contact: () -> Offset, strength: () -> Float
): Modifier = drawWithCache {
    if (size.minDimension <= 0f) return@drawWithCache onDrawBehind {}
    val outline = shape.createOutline(size, layoutDirection, this)
    val point = contact().let {
        if (it.isSpecified) Offset(it.x.coerceIn(0f, size.width), it.y.coerceIn(0f, size.height))
        else Offset(size.width / 2f, size.height / 2f)
    }
    val radius = minOf(size.minDimension * 0.73f, 27.dp.toPx())
    val cool = if (dark) Color(0xFFCFDFFF) else Color(0xFFECF6FF)
    val body = Brush.radialGradient(
        0f to Color.White.copy(alpha = if (dark) 0.12f else 0.18f),
        0.42f to Color.White.copy(alpha = if (dark) 0.056f else 0.08f),
        1f to Color.White.copy(alpha = 0f), center = point, radius = radius
    )
    val lip = Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)),
        center = Offset(point.x, 0f), radius = radius * 1.18f)
    val lower = Brush.radialGradient(listOf(cool, cool.copy(alpha = 0f)),
        center = Offset(point.x, size.height), radius = radius * 0.83f)
    val rim = Stroke(1.35.dp.toPx())
    val lowerRim = Stroke(1.dp.toPx())
    onDrawBehind {
        val level = strength().coerceIn(0f, 1f)
        if (level > 0f) {
            drawOutline(outline, body, alpha = level)
            drawOutline(outline, lip, alpha = level * (if (dark) 0.48f else 0.66f), style = rim)
            drawOutline(outline, lower, alpha = level * (if (dark) 0.32f else 0.48f), style = lowerRim)
        }
    }
}
