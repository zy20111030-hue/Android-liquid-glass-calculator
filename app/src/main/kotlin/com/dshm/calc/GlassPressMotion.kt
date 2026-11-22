package com.dshm.calc

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private val load = tween<Float>(80, easing = LinearOutSlowInEasing)
private val shortTap = tween<Float>(36, easing = LinearOutSlowInEasing)
private val unload = tween<Float>(118, easing = CubicBezierEasing(0.19f, 0.52f, 0.45f, 1f))
private val settle = tween<Float>(76, easing = FastOutSlowInEasing)
private const val maxRebound = 0.028f

@Stable
internal class GlassPressMotion(private val scope: CoroutineScope) {
    var deformation by mutableFloatStateOf(0f)
        private set
    val pressure get() = deformation.coerceIn(0f, 1f)
    var active by mutableStateOf(false)
        private set
    var contact by mutableStateOf(Offset.Unspecified)
        private set
    private var held = false
    private var isolatedPress = true
    private var animation: Job? = null
    private val instant get() = scope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f

    fun move(position: Offset) { contact = position }

    fun press(position: Offset = Offset.Unspecified) {
        if (held) return
        isolatedPress = !active
        held = true
        active = true
        contact = position
        animation?.cancel()
        if (instant) {
            deformation = 1f
            return
        }
        animation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            travelTo(1f, load)
        }
    }

    fun release() = finish(completed = true)
    fun cancel() = finish(completed = false)

    private fun finish(completed: Boolean) {
        if (!held) return
        held = false
        animation?.cancel()
        if (instant || (!completed && deformation == 0f)) {
            deformation = 0f
            active = false
            return
        }
        animation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // A real down/up can fit between frames. Only a completed tap earns this short impulse.
            if (completed && pressure < 0.48f) {
                travelTo(0.48f, shortTap)
            }
            // Unload immediately. Only one uninterrupted, fully loaded tap can travel past neutral.
            val rest = if (completed && isolatedPress && pressure > 0.60f) -maxRebound * pressure else 0f
            travelTo(rest, unload)
            if (rest < 0f) travelTo(0f, settle)
            active = false
        }
    }

    private suspend fun travelTo(target: Float, spec: AnimationSpec<Float>) {
        animate(deformation, target, animationSpec = spec) { value, _ -> deformation = value }
    }
}

@Composable
internal fun rememberGlassPressMotion(interactionSource: InteractionSource? = null): GlassPressMotion {
    val scope = rememberCoroutineScope()
    val motion = remember(scope) { GlassPressMotion(scope) }
    if (interactionSource != null) LaunchedEffect(motion, interactionSource) {
        val presses = mutableSetOf<PressInteraction.Press>()
        try {
            interactionSource.interactions.collect { event ->
                when (event) {
                    is PressInteraction.Press -> if (presses.add(event)) motion.press(event.pressPosition)
                    is PressInteraction.Release -> if (presses.remove(event.press) && presses.isEmpty()) motion.release()
                    is PressInteraction.Cancel -> if (presses.remove(event.press) && presses.isEmpty()) motion.cancel()
                    else -> Unit
                }
            }
        } finally {
            motion.cancel()
        }
    }
    return motion
}

internal fun glassPressDirection(point: Offset, width: Float, height: Float): Offset {
    if (!point.isSpecified || width <= 0f || height <= 0f) return Offset.Zero
    return Offset((2f * point.x / width - 1f).coerceIn(-1f, 1f), (2f * point.y / height - 1f).coerceIn(-1f, 1f))
}

/** Apply to the glass face alone. The short side and dp caps bound even very wide or tall controls. */
internal fun GraphicsLayerScope.applyGlassPress(deformation: Float, direction: Offset = Offset.Zero) {
    val strength = deformation.coerceIn(-maxRebound, 1f)
    val width = size.width.coerceAtLeast(1f)
    val height = size.height.coerceAtLeast(1f)
    val side = minOf(width, height)
    val x = direction.x.coerceIn(-1f, 1f)
    val y = direction.y.coerceIn(-1f, 1f)
    val depth = minOf(height * (0.071f + 0.018f * abs(y)), side * 0.12f, 6.4.dp.toPx())
    val spread = minOf(width * 0.028f, side * 0.045f, 2.8.dp.toPx())
    val shift = minOf(side * 0.022f, 1.5.dp.toPx()) * strength
    scaleY = 1f - strength * depth / height
    scaleX = 1f + strength * spread * (1.12f * abs(y) - 0.55f - 0.85f * abs(x)) / width
    translationX = -x * shift
    translationY = (0.38f - 0.72f * y) * shift
}
