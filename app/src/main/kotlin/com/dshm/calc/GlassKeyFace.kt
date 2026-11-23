package com.dshm.calc

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.abs

private object FullyRoundedKeys : State<Float> { override val value = 1f }
internal val LocalKeyRoundness = staticCompositionLocalOf<State<Float>> { FullyRoundedKeys }

private val CircleShape = AbsoluteRoundedCornerShape(50)
private val RectCorner = 16.dp
private val RectShape = AbsoluteRoundedCornerShape(RectCorner)
private val PressBend = 4.6.dp
private const val PressTravel = 3.4f
private const val PressTravelAxis = 0.9f
private const val PressShift = 1.5f

internal fun Density.keyCornerRadius(shortSide: Float, roundness: Float): Float {
    val circle = shortSide * 0.5f
    val rectangle = minOf(RectCorner.toPx(), circle)
    return rectangle + (circle - rectangle) * roundness.coerceIn(0f, 1f)
}

private fun glassPressDirection(point: Offset, width: Float, height: Float): Offset {
    if (!point.isSpecified || width <= 0f || height <= 0f) return Offset.Zero
    return Offset((2f * point.x / width - 1f).coerceIn(-1f, 1f), (2f * point.y / height - 1f).coerceIn(-1f, 1f))
}

internal fun GraphicsLayerScope.applyKeyPress(pressure: Float, direction: Offset = Offset.Zero) {
    val unit = 1.dp.toPx() * pressure
    val (x, y) = direction
    val travelX = (PressTravel + PressTravelAxis * abs(x)) * unit
    val travelY = (PressTravel + PressTravelAxis * abs(y)) * unit
    scaleX = 1f - travelX / size.width.coerceAtLeast(1f)
    scaleY = 1f - travelY / size.height.coerceAtLeast(1f)
    translationX = -PressShift * x * unit
    translationY = -PressShift * y * unit
}

@Composable
internal fun BoxScope.GlassKeyFace(
    backdrop: Backdrop,
    surfaceColor: Color,
    lightSource: KeyLightSource
) {
    val roundness = LocalKeyRoundness.current
    val density = LocalDensity.current
    val corner by remember(roundness, density, lightSource) {
        derivedStateOf {
            val size = lightSource.size
            with(density) { keyCornerRadius(minOf(size.width, size.height).toFloat(), roundness.value).toDp() }
        }
    }
    val idleShape by remember(roundness, density, lightSource) {
        derivedStateOf {
            when (roundness.value) {
                0f -> RectShape
                1f -> CircleShape
                else -> AbsoluteRoundedCornerShape(corner)
            }
        }
    }
    val pressure = lightSource.pressure
    val direction by remember(lightSource) {
        derivedStateOf {
            val point = lightSource.localContact
            val size = lightSource.size
            glassPressDirection(point, size.width.toFloat(), size.height.toFloat())
        }
    }
    val glassShape by remember(lightSource, density, roundness) {
        derivedStateOf {
            val radius = corner
            val bend = PressBend * pressure.value
            val (x, y) = direction
            if (bend == 0.dp || direction == Offset.Zero) idleShape else AbsoluteRoundedCornerShape(
                topLeft = (radius - bend * (x + y).coerceIn(0f, 1.5f)).coerceAtLeast(0.dp),
                topRight = (radius - bend * (-x + y).coerceIn(0f, 1.5f)).coerceAtLeast(0.dp),
                bottomRight = (radius - bend * (-x - y).coerceIn(0f, 1.5f)).coerceAtLeast(0.dp),
                bottomLeft = (radius - bend * (x - y).coerceIn(0f, 1.5f)).coerceAtLeast(0.dp)
            )
        }
    }

    LaunchedEffect(lightSource) { lightSource.followPressure() }

    Spacer(
        Modifier.matchParentSize()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { glassShape },
                highlight = null,
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    // 折射强度随按压力实时变化：按下越深，背景折射越强
                    val p = pressure.value
                    lens((18f + 20f * p).dp.toPx(), (30f + 28f * p).dp.toPx())
                },
                layerBlock = {
                    applyKeyPress(pressure.value, direction)
                },
                onDrawSurface = { drawRect(surfaceColor) }
            )
            .drawWithCache {
                val dark = surfaceColor.luminance() < 0.5f
                val clearWhite = Color.White.copy(alpha = 0f)
                val coolWhite = Color(0xFFE1EEFF)
                val coolShade = Color(0xFF172338)
                val rimLight = Brush.linearGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.60f else 0.82f),
                    0.30f to Color.White.copy(alpha = 0.33f),
                    0.59f to clearWhite,
                    0.74f to coolWhite.copy(alpha = 0.05f),
                    1f to coolWhite.copy(alpha = if (dark) 0.26f else 0.36f),
                    end = Offset(size.width, size.height)
                )
                val bevelShade = Brush.linearGradient(
                    0f to coolShade.copy(alpha = if (dark) 0.19f else 0.15f),
                    0.45f to coolShade.copy(alpha = 0.02f),
                    1f to coolShade.copy(alpha = if (dark) 0.32f else 0.20f),
                    end = Offset(size.width, size.height)
                )
                // The backdrop clips strokes at the outside contour. Their inner halves form the bevel.
                val bevel = Stroke(3.4.dp.toPx())
                val rim = Stroke(1.7.dp.toPx())
                val idleOutline = idleShape.createOutline(size, layoutDirection, this)
                val contact = lightSource.localContact
                val point = if (contact.isSpecified) {
                    Offset(contact.x.coerceIn(0f, size.width), contact.y.coerceIn(0f, size.height))
                } else {
                    Offset(size.width / 2f, size.height / 2f)
                }
                val radius = (size.minDimension * 0.82f).coerceIn(28.dp.toPx(), 64.dp.toPx())
                val light = Brush.radialGradient(
                    0f to Color.White,
                    0.45f to Color.White.copy(alpha = 0.46f),
                    1f to Color.White.copy(alpha = 0f),
                    center = point,
                    radius = radius
                )
                val glowAlpha = if (dark) 0.26f else 0.34f
                onDrawBehind {
                    val strength = pressure.value
                    val shape = glassShape
                    val outline = if (shape === idleShape) idleOutline else shape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, bevelShade, style = bevel)
                    drawOutline(outline, rimLight, style = rim)
                    if (strength > 0f) {
                        drawRect(light, alpha = glowAlpha * strength)
                        drawOutline(outline, light, alpha = 0.82f * strength, style = rim)
                    }
                }
            }
    )
}
