package com.dshm.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens

internal class PressableGlassMaterial(val layer: LayerBackdrop) {
    private var activeButtons by mutableIntStateOf(0)
    val exportedBackdrop: LayerBackdrop? get() = layer.takeIf { activeButtons > 0 }

    fun feedback(active: Boolean) {
        activeButtons += if (active) 1 else -1
    }
}

@Composable
internal fun rememberPressableGlassMaterial(): PressableGlassMaterial {
    val layer = rememberLayerBackdrop()
    return remember(layer) { PressableGlassMaterial(layer) }
}

/** The glass moves under the finger. Focus, hit testing, labels and click delivery stay on the outer button. */
@Composable
internal fun OverlayPressButton(
    material: PressableGlassMaterial,
    dark: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    facePadding: PaddingValues = PaddingValues(0.dp),
    fill: Color = Color.Transparent,
    rim: Color = Color.Transparent,
    alignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val motion = rememberGlassPressMotion(source)
    val pressure = motion.pressure
    val active = motion.active
    val refracting = pressure > 0f
    val contact = motion.contact
    DisposableEffect(material, refracting) {
        if (refracting) material.feedback(true)
        onDispose { if (refracting) material.feedback(false) }
    }
    Box(
        modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            // Show contact immediately inside scrollable lists. Only clickable decides whether to activate.
            awaitEachGesture {
                val downEvent = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                motion.press(downEvent.position)
                var up = false
                try {
                    up = waitForUpOrCancellation(PointerEventPass.Initial) != null
                } finally {
                    // A scroll may cancel before clickable emits a Press. A valid up is handled by its native Release.
                    if (!up) motion.cancel()
                }
            }
        }.clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button,
            interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = alignment
    ) {
        val face = Modifier.matchParentSize().padding(facePadding)
        if (active) {
            val direction = LocalLayoutDirection.current
            val localContact = with(LocalDensity.current) {
                if (contact.isSpecified) contact - Offset(
                    facePadding.calculateLeftPadding(direction).toPx(), facePadding.calculateTopPadding().toPx()
                ) else Offset.Unspecified
            }
            Box(face.graphicsLayer {
                applyGlassPress(motion.deformation, glassPressDirection(localContact, size.width, size.height))
            }.clip(shape).background(fill)) {
                if (refracting) Spacer(Modifier.matchParentSize().graphicsLayer {
                    alpha = pressure
                    compositingStrategy = CompositingStrategy.Offscreen
                }.drawWithCache {
                    val point = if (localContact.isSpecified) Offset(
                        localContact.x.coerceIn(0f, size.width), localContact.y.coerceIn(0f, size.height)
                    ) else Offset(size.width / 2f, size.height / 2f)
                    val radius = (size.minDimension * 1.48f).coerceIn(30.dp.toPx(), 88.dp.toPx())
                    val mask = Brush.radialGradient(listOf(Color.White, Color.Transparent), point, radius)
                    val glint = Brush.radialGradient(
                        0f to Color.White.copy(alpha = if (dark) 0.30f else 0.55f),
                        0.40f to Color.White.copy(alpha = if (dark) 0.13f else 0.20f),
                        1f to Color.Transparent,
                        center = point, radius = radius * 0.72f
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(mask, blendMode = BlendMode.DstIn)
                        drawRect(glint)
                    }
                }.drawBackdrop(
                    backdrop = material.layer, shape = { shape }, highlight = null,
                    effects = { lens((5f + 5f * pressure).dp.toPx(), (6f + 8f * pressure).dp.toPx()) },
                    onDrawSurface = { drawRect(fill) }
                ))
                val edge = lerp(rim, Color.White.copy(alpha = 0.65f), pressure)
                Spacer(Modifier.matchParentSize().border(1.dp, edge, shape))
            }
        } else if (fill.alpha > 0f || rim.alpha > 0f) {
            Spacer(face.background(fill, shape).border(1.dp, rim, shape))
        }
        if (focused && enabled) {
            Spacer(face.border(2.dp, overlayInk(dark), shape))
        }
        content()
    }
}
