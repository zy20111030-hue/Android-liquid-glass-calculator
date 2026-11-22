package com.dshm.calc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.min

internal val LocalKeypadLights = staticCompositionLocalOf<KeypadLights?> { null }

internal class KeypadLights {
    var plane: LayoutCoordinates? = null
    val keys = mutableStateListOf<KeyLightSource>()
    val emitters = mutableStateListOf<KeyLightSource>()
}

internal class KeyLightSource(private val keypad: KeypadLights, val motion: GlassPressMotion) {
    val localContact get() = motion.contact
    private var pointerDown by mutableStateOf(false)
    private val heldGlow = Animatable(0f)
    val glow get() = maxOf(motion.pressure, heldGlow.value)
    val emitting get() = pointerDown || motion.active || heldGlow.value > 0f
    var size by mutableStateOf(IntSize.Zero)
        private set
    var bounds by mutableStateOf(Rect.Zero)
        private set

    fun place(coordinates: LayoutCoordinates) {
        size = coordinates.size
        val plane = keypad.plane ?: return
        bounds = Rect(plane.localPositionOf(coordinates), size.toSize())
    }

    fun movePointer(position: Offset) {
        val point = Offset(
            position.x.coerceIn(0f, size.width.toFloat()),
            position.y.coerceIn(0f, size.height.toFloat())
        )
        motion.move(point)
    }

    fun beginPointer(position: Offset) {
        movePointer(position)
        pointerDown = true
    }

    fun endPointer() { pointerDown = false }

    suspend fun followContactLight() {
        snapshotFlow { pointerDown }.collectLatest { down ->
            // Physical light survives a drag outside the key. It never manufactures a tap impulse.
            val target = if (down) 0.92f else 0f
            if (target == heldGlow.value) return@collectLatest
            if (currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f) heldGlow.snapTo(target)
            else heldGlow.animateTo(target, tween(if (down) 82 else 105, easing = LinearOutSlowInEasing))
        }
    }

    fun setEmitting(active: Boolean) {
        if (active) {
            if (this !in keypad.emitters) keypad.emitters.add(this)
        } else keypad.emitters.remove(this)
    }

    fun finishPress() {
        endPointer()
        motion.cancel()
        setEmitting(false)
    }
}

// Track light position without consuming the gesture. The native clickable decides release or cancellation.
internal fun Modifier.trackKeyContact(key: KeyLightSource): Modifier =
    onGloballyPositioned(key::place).pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            key.beginPointer(down.position)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    key.movePointer(change.position)
                } while (true)
            } finally {
                key.endPointer()
            }
        }
    }

private data class ReflectedLight(
    val source: KeyLightSource,
    val highlight: Brush,
    val shade: Brush,
    val neighbors: List<KeyLightSource>
)

// A single transparent layer lets neighboring rims respond without redrawing their backdrops.
internal fun Modifier.drawNeighborReflections(keypad: KeypadLights, dark: Boolean, roundness: State<Float>): Modifier = drawWithCache {
    val coreWidth = 1.25.dp.toPx()
    val coreStroke = Stroke(coreWidth)
    val innerStroke = Stroke(1.dp.toPx())
    val innerInset = 2.25.dp.toPx()
    val lights = keypad.emitters.mapNotNull { source ->
        if (source.bounds.isEmpty) return@mapNotNull null
        val local = source.localContact
        val point = if (local.isSpecified) source.bounds.topLeft + local else source.bounds.center
        val radius = (min(source.bounds.width, source.bounds.height) * 0.94f)
            .coerceIn(36.dp.toPx(), 86.dp.toPx())
        val neighbors = keypad.keys.filter { key ->
            val rect = key.bounds
            if (key === source || rect.minDimension <= innerInset * 2) return@filter false
            val dx = point.x - point.x.coerceIn(rect.left, rect.right)
            val dy = point.y - point.y.coerceIn(rect.top, rect.bottom)
            dx * dx + dy * dy < radius * radius
        }
        if (neighbors.isEmpty()) return@mapNotNull null
        fun edgeLight(color: Color) = Brush.radialGradient(
            0f to color,
            0.25f to color.copy(alpha = 0.89f),
            0.66f to color.copy(alpha = 0.24f),
            1f to color.copy(alpha = 0f),
            center = point,
            radius = radius
        )
        ReflectedLight(source, edgeLight(Color.White), edgeLight(Color(0xFF283644)), neighbors)
    }
    onDrawBehind {
        for (light in lights) {
            val strength = light.source.glow
            if (strength <= 0f) continue
            for (neighbor in light.neighbors) {
                // Cross-fade onto a neighboring key as its own bent edge settles back into place.
                val settled = (1f - neighbor.motion.pressure * 2.5f).coerceIn(0f, 1f)
                val intensity = strength * settled
                if (intensity == 0f) continue
                val rect = neighbor.bounds
                val corner = keyCornerRadius(rect.minDimension, roundness.value)
                val edge = rect.deflate(coreWidth / 2f)
                drawRoundRect(
                    light.highlight, edge.topLeft, edge.size, CornerRadius(corner - coreWidth / 2f),
                    alpha = intensity * (if (dark) 0.40f else 0.49f), style = coreStroke
                )
                if (!dark) {
                    val inner = rect.deflate(innerInset)
                    drawRoundRect(
                        light.shade, inner.topLeft, inner.size, CornerRadius(corner - innerInset),
                        alpha = intensity * 0.12f, style = innerStroke
                    )
                }
            }
        }
    }
}
