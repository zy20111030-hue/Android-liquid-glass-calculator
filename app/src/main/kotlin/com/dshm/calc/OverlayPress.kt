package com.dshm.calc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class OverlayButtonFeedback { Glass, Menu, Native }

@Composable
internal fun rememberOptionScale(source: InteractionSource): State<Float> {
    val pressed by source.collectIsPressedAsState()
    return animateFloatAsState(if (pressed) 1.08f else 1f,
        spring(dampingRatio = 0.7f, stiffness = 500f, visibilityThreshold = 0.0008f),
        label = "Glass option press")
}

@Composable
private fun BoxScope.MenuRowPressLight(source: InteractionSource, shape: Shape, dark: Boolean) {
    val strength = remember(source) { Animatable(0f) }
    var contact by remember(source) { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(source) {
        val held = mutableListOf<PressInteraction.Press>()
        var animation: Job? = null
        source.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> held.add(event)
                is PressInteraction.Release -> if (!held.remove(event.press)) return@collect
                is PressInteraction.Cancel -> if (!held.remove(event.press)) return@collect
                else -> return@collect
            }
            val active = held.lastOrNull()
            if (active != null) contact = active.pressPosition
            animation?.cancel()
            animation = launch {
                if (active != null) {
                    strength.animateTo(1f, tween(72))
                } else {
                    // In a scrolling grid, clickable emits Press and Release together for a quick tap.
                    if (event is PressInteraction.Release && strength.value < 0.52f) {
                        strength.animateTo(0.52f, tween(26))
                    }
                    strength.animateTo(0f, tween(if (event is PressInteraction.Cancel) 55 else 90))
                }
            }
        }
    }
    val visible by remember(strength) { derivedStateOf { strength.value > 0f } }
    if (!visible) return
    val horizontal = 5.dp
    val vertical = 2.dp
    val inset = with(LocalDensity.current) { Offset(horizontal.roundToPx().toFloat(), vertical.roundToPx().toFloat()) }
    Spacer(Modifier.matchParentSize().padding(horizontal = horizontal, vertical = vertical)
        .menuContactLight(shape, dark,
            contact = { if (contact.isSpecified) contact - inset else Offset.Unspecified },
            strength = { strength.value }))
}

/** Keep the touch target stationary while the glass, rim and label scale together. */
@Composable
internal fun OverlayPressButton(
    material: LayerBackdrop?,
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
    feedback: OverlayButtonFeedback = OverlayButtonFeedback.Glass,
    content: @Composable BoxScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()
    val latestClick by rememberUpdatedState(onClick)
    val stillEnabled by rememberUpdatedState(enabled)
    var pending by remember { mutableStateOf<Job?>(null) }
    // An exiting menu row can stay composed and then be reopened before its click delay ends.
    DisposableEffect(enabled) { onDispose { pending?.cancel() } }
    val nativeFeedback = feedback == OverlayButtonFeedback.Native
    val click = Modifier.clickable(
        enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, interactionSource = source,
        indication = if (nativeFeedback) LocalIndication.current else null
    ) {
        if (nativeFeedback || scope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
            latestClick()
        } else if (pending?.isActive != true) {
            pending = scope.launch {
                delay(110)
                if (stillEnabled) latestClick()
            }
        }
    }
    val scale = if (feedback == OverlayButtonFeedback.Glass) rememberOptionScale(source) else null
    Box(modifier.then(if (nativeFeedback) Modifier.clip(shape) else Modifier).then(click),
        contentAlignment = alignment) {
        val face = Modifier.matchParentSize().padding(facePadding)
        if (scale != null) {
            Spacer(face.drawBackdrop(
                backdrop = requireNotNull(material), shape = { shape }, highlight = null,
                effects = { vibrancy(); blur(2.dp.toPx()); lens(14.dp.toPx(), 22.dp.toPx()) },
                layerBlock = { scaleX = scale.value; scaleY = scale.value },
                onDrawSurface = { drawRect(fill) }
            ).then(if (rim.alpha > 0f) Modifier.border(1.dp, rim, shape) else Modifier))
        } else if (fill.alpha > 0f || rim.alpha > 0f) {
            Spacer(face.background(fill, shape).border(1.dp, rim, shape))
        }
        if (enabled && feedback == OverlayButtonFeedback.Menu) MenuRowPressLight(source, shape, dark)
        if (focused && enabled) Spacer(face.border(2.dp, overlayInk(dark), shape))
        if (scale == null) content()
        else Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            contentAlignment = alignment, content = content)
    }
}
