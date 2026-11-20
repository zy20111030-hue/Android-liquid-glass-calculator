package com.dshm.calc

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlin.math.abs

private val KeyCorner = 16.dp
private val KeyShape = AbsoluteRoundedCornerShape(KeyCorner)
private val PressIn = tween<Float>(72, easing = LinearOutSlowInEasing)
private val QuickTap = tween<Float>(32, easing = LinearOutSlowInEasing)
private val Release = tween<Float>(150)
private val Cancel = tween<Float>(80)

@Composable
internal fun BoxScope.GlassKeyFace(
    backdrop: Backdrop,
    surfaceColor: Color,
    interactions: InteractionSource,
    lightSource: KeyLightSource
) {
    val pressure = lightSource.pressure
    val direction by remember(lightSource) {
        derivedStateOf {
            val point = lightSource.localContact
            val size = lightSource.size
            if (point.isSpecified && size.width > 0 && size.height > 0) {
                Offset(
                    (2f * point.x / size.width - 1f).coerceIn(-1f, 1f),
                    (2f * point.y / size.height - 1f).coerceIn(-1f, 1f)
                )
            } else Offset.Zero
        }
    }
    val glassShape by remember(lightSource) {
        derivedStateOf {
            val bend = 2.8.dp * pressure.value
            val (x, y) = direction
            if (bend == 0.dp || direction == Offset.Zero) KeyShape else AbsoluteRoundedCornerShape(
                topLeft = KeyCorner + bend * (-x - y).coerceIn(0f, 1.5f),
                topRight = KeyCorner + bend * (x - y).coerceIn(0f, 1.5f),
                bottomRight = KeyCorner + bend * (x + y).coerceIn(0f, 1.5f),
                bottomLeft = KeyCorner + bend * (-x + y).coerceIn(0f, 1.5f)
            )
        }
    }

    LaunchedEffect(interactions, lightSource) {
        val held = mutableListOf<PressInteraction.Press>()
        interactions.interactions.mapNotNull { event ->
            when (event) {
                is PressInteraction.Press -> {
                    held.add(event)
                    event
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val ended = if (event is PressInteraction.Release) event.press
                                else (event as PressInteraction.Cancel).press
                    val wasLatest = ended === held.lastOrNull()
                    held.remove(ended)
                    if (wasLatest) held.lastOrNull() ?: event else null
                }
                else -> null
            }
        }.collectLatest { event ->
            when (event) {
                is PressInteraction.Press -> {
                    lightSource.beginPress(event.pressPosition)
                    pressure.animateTo(1f, PressIn)
                }
                is PressInteraction.Release -> {
                    // A down/up pair can arrive in one frame. Let the light register on a quick tap.
                    if (pressure.value < 0.52f) pressure.animateTo(0.52f, QuickTap)
                    pressure.animateTo(0f, Release)
                    lightSource.finishPress()
                }
                is PressInteraction.Cancel -> {
                    pressure.animateTo(0f, Cancel)
                    lightSource.finishPress()
                }
            }
        }
    }

    Spacer(
        Modifier.matchParentSize()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { glassShape },
                highlight = null,
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(18.dp.toPx(), 30.dp.toPx())
                },
                layerBlock = {
                    val unit = 1.dp.toPx() * pressure.value
                    val (x, y) = direction
                    val travelX = (1.8f + 0.44f * abs(x)) * unit
                    val travelY = (1.8f + 0.44f * abs(y)) * unit
                    scaleX = 1f - travelX / size.width.coerceAtLeast(1f)
                    scaleY = 1f - travelY / size.height.coerceAtLeast(1f)
                    // Shift the compressed face away from the fingertip so the far edge stays seated.
                    translationX = -0.85f * x * unit
                    translationY = -0.85f * y * unit
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
                val idleOutline = KeyShape.createOutline(size, layoutDirection, this)
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
                val glowAlpha = if (dark) 0.19f else 0.27f
                onDrawBehind {
                    val strength = pressure.value
                    val shape = glassShape
                    val outline = if (shape === KeyShape) idleOutline else shape.createOutline(size, layoutDirection, this)
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
