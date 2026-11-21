package com.dshm.calc
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.withTimeoutOrNull

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur as blurModifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle

private fun menuLerp(a: Dp, b: Dp, t: Float): Dp = a + (b - a) * t.coerceIn(0f, 1f)

/**
 * 三点菜单：收缩态是 44dp 液态玻璃圆点，点击后弹簧放大成玻璃面板（右上角锚定）。
 */
@Composable
internal fun GlassMenu(
    visible: Boolean,
    backdrop: Backdrop,
    surfaceColor: Color,
    targetWidth: Dp,
    targetHeight: Dp,
    onToggle: () -> Unit,
    itemCount: Int = 0,
    itemHeight: Dp = 54.dp,
    headerHeight: Dp = 54.dp,
    onSelect: (Int) -> Unit = {},
    onHoverChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val menuBackdrop = rememberLayerBackdrop()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (visible) {
                androidx.compose.animation.core.tween(
                    durationMillis = 480,
                    easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.24f, 1f)
                )
            } else {
                androidx.compose.animation.core.tween(
                    durationMillis = 320,
                    easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
                )
            }
        )
    }
    val p = progress.value

    val w = menuLerp(44.dp, targetWidth, p)
    val h = menuLerp(44.dp, targetHeight, p)
    val radius = menuLerp(22.dp, 26.dp, p)
    val shape: Shape = RoundedRectangle(radius, RoundedCornerStyle.Continuous)
    val contentAlpha = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
    val iconAlpha = (1f - p / 0.5f).coerceIn(0f, 1f)
    val morphBlur = (kotlin.math.sin(p * Math.PI).toFloat() * 9f).dp
    val visibleState = androidx.compose.runtime.rememberUpdatedState(visible)
    var hoverY by remember { androidx.compose.runtime.mutableStateOf(-1f) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressAnim = remember { Animatable(0f) }
    LaunchedEffect(pressed) { pressAnim.animateTo(if (pressed) 1f else 0f, androidx.compose.animation.core.tween(90)) }

    Box(
        modifier
            .width(w)
            .height(h)
            .offset(y = 56.dp * p)
            .blurModifier(morphBlur, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
            .graphicsLayer {
                val s = 1f - 0.06f * pressAnim.value
                scaleX = s
                scaleY = s
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                highlight = null,
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens((16f + 12f * p).dp.toPx(), (24f + 14f * p).dp.toPx())
                },
                layerBlock = {},
                onDrawSurface = { drawRect(surfaceColor.copy(alpha = surfaceColor.alpha * (1f - 0.85f * p))) }
            )
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val rim = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.55f),
                    0.35f to Color.White.copy(alpha = 0.22f),
                    0.7f to Color.Transparent,
                    1f to Color.White.copy(alpha = 0.30f),
                    end = Offset(size.width, size.height)
                )
                val bevel = Brush.linearGradient(
                    0f to Color(0xFF172338).copy(alpha = 0.16f),
                    0.5f to Color.Transparent,
                    1f to Color(0xFF172338).copy(alpha = 0.22f),
                    end = Offset(size.width, size.height)
                )
                onDrawBehind {
                    drawOutline(outline, bevel, style = Stroke(3.4.dp.toPx()))
                    drawOutline(outline, rim, style = Stroke(1.7.dp.toPx()))
                    val glowStrength = pressAnim.value
                    if (glowStrength > 0f) {
                        val glow = Brush.radialGradient(
                            0f to Color.White,
                            0.45f to Color.White.copy(alpha = 0.46f),
                            1f to Color.White.copy(alpha = 0f),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension * 0.82f
                        )
                        drawRect(glow, alpha = 0.35f * glowStrength)
                        drawOutline(outline, glow, alpha = 0.8f * glowStrength, style = Stroke(1.7.dp.toPx()))
                    }
                }
            }
            .pointerInput(itemCount) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val headerPx = headerHeight.toPx()
                    val rowPx = itemHeight.toPx()
                    fun idxAt(y: Float): Int =
                        ((y - headerPx) / rowPx).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                    val upEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation()
                    }
                    if (upEarly != null) {
                        // 未到长按就抬起 → 点按
                        onToggle()
                        return@awaitEachGesture
                    }
                    // 长按 → 展开，并保持手指不离开即可继续拖选
                    if (!visibleState.value) onToggle()
                    var selecting = itemCount > 0
                    while (selecting) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            val i = if (hoverY >= 0f) idxAt(hoverY) else -1
                            hoverY = -1f
                            if (i >= 0) onSelect(i)
                            break
                        }
                        val prev = hoverY
                        hoverY = if (ch.position.y > headerPx) ch.position.y else -1f
                        val ni = if (hoverY >= 0f) idxAt(hoverY) else -1
                        val pi = if (prev >= 0f) idxAt(prev) else -1
                        if (ni != pi && ni >= 0) onHoverChange(ni)
                    }
                }
            }
    ) {
        // 收缩态：⋮ 图标
        if (iconAlpha > 0.01f) {
            Canvas(Modifier.matchParentSize().graphicsLayer { alpha = iconAlpha }) {
                val r = 1.7.dp.toPx()
                val gap = 5.2.dp.toPx()
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawCircle(Color.White, r, Offset(cx, cy - gap))
                drawCircle(Color.White, r, Offset(cx, cy))
                drawCircle(Color.White, r, Offset(cx, cy + gap))
            }
        }
        // 展开态：菜单项
        if (contentAlpha > 0.01f) {
            Box(Modifier.fillMaxSize().layerBackdrop(menuBackdrop)) {
                Column(Modifier.graphicsLayer { alpha = contentAlpha }) {
                    content()
                }
            }
        if (hoverY >= 0f && contentAlpha > 0.01f && itemCount > 0) {
            val d = androidx.compose.ui.platform.LocalDensity.current
            val headerPx = with(d) { headerHeight.toPx() }
            val rowPx = with(d) { itemHeight.toPx() }
            val idx = ((hoverY - headerPx) / rowPx).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
            val center = headerPx + (idx + 0.5f) * rowPx
            val magnetY = hoverY + (center - hoverY) * 0.13f
            val topPx = (magnetY - rowPx / 2f).coerceIn(headerPx, headerPx + (itemCount - 1) * rowPx)
            val hoverShape: Shape = RoundedRectangle((itemHeight - 4.dp) / 2, RoundedCornerStyle.Continuous)
            Box(
                Modifier
                    .offset(y = with(d) { topPx.toDp() })
                    .padding(horizontal = 6.dp)
                    .fillMaxWidth()
                    .height(itemHeight - 4.dp)
                    .drawBackdrop(
                        backdrop = menuBackdrop,
                        shape = { hoverShape },
                        highlight = null,
                        effects = {
                            vibrancy()
                            blur(1f.dp.toPx())
                            lens(7f.dp.toPx(), 11f.dp.toPx())
                        },
                        layerBlock = {},
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) }
                    )
                    .drawWithCache {
                        val o = hoverShape.createOutline(size, layoutDirection, this)
                        onDrawBehind {
                            drawOutline(o, Color.White.copy(alpha = 0.40f), style = Stroke(1.1f.dp.toPx()))
                        }
                    }
            )
        }
        }
}
}

/** 菜单遮罩：点空白关闭 */
@Composable
internal fun GlassMenuScrim(onDismiss: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = interaction, indication = null) { onDismiss() }
    )
}

/** 菜单容器：右上角锚定 */
@Composable
internal fun GlassMenuAnchor(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.TopEnd) { content() }
}
