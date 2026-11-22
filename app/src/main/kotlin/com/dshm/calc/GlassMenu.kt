package com.dshm.calc

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal data class GlassMenuItem(val action: CalculatorOverlay, val label: String)

/** Pointer positions stay in the stationary trigger until projected into the current list viewport. */
private class MenuDrag(private val list: LazyGridState, private val insetX: Float, private val insetY: Float) {
    var anchor by mutableStateOf<LayoutCoordinates?>(null)
    var viewport by mutableStateOf<LayoutCoordinates?>(null)
    var point by mutableStateOf<Offset?>(null)
    var visibleArea by mutableStateOf<Rect?>(null)
    var active by mutableStateOf(false)
    private var hovered: CalculatorOverlay? = null

    private fun localPoint(): Offset? {
        val from = anchor?.takeIf { it.isAttached } ?: return null
        val to = viewport?.takeIf { it.isAttached } ?: return null
        val local = to.localPositionOf(from, point ?: return null)
        return local.takeIf { it.x >= 0 && it.x < to.size.width && it.y >= 0 && it.y < to.size.height &&
            visibleArea?.contains(it) != false }
    }

    private fun itemAt(local: Offset): LazyGridItemInfo? {
        val layout = list.layoutInfo
        val y = local.y + layout.viewportStartOffset
        // Vertical grid items expose physical horizontal offsets, including RTL placement.
        return layout.visibleItemsInfo.firstOrNull { item ->
            local.x >= item.offset.x && local.x < item.offset.x + item.size.width &&
                y >= item.offset.y && y < item.offset.y + item.size.height
        }?.takeIf { it.key is CalculatorOverlay }
    }

    fun actionAtPointer() = localPoint()?.let(::itemAt)?.key as? CalculatorOverlay

    fun target(): Pair<CalculatorOverlay, Rect>? {
        val local = localPoint() ?: return null
        val item = itemAt(local) ?: return null
        val x = item.offset.x.toFloat()
        val y = (item.offset.y - list.layoutInfo.viewportStartOffset).toFloat()
        val width = item.size.width.toFloat()
        val height = item.size.height.toFloat()
        val dx = ((local.x - x - width / 2f) * 0.035f).coerceIn(-insetX, insetX)
        val dy = ((local.y - y - height / 2f) * 0.12f).coerceIn(-insetY, insetY)
        return item.key as CalculatorOverlay to Rect(x + insetX + dx, y + insetY + dy,
            x + width - insetX + dx, y + height - insetY + dy)
    }

    fun updateHover(next: CalculatorOverlay?, haptic: () -> Unit) {
        if (next != hovered) {
            hovered = next
            if (next != null) haptic()
        }
    }

    fun edgeScrollFraction(edgeSize: Float): Float {
        val y = localPoint()?.y ?: return 0f
        val height = viewport?.size?.height?.toFloat() ?: return 0f
        val band = minOf(edgeSize, height / 4f)
        return when {
            band <= 0f -> 0f
            y < band && list.canScrollBackward -> (y - band) / band
            y > height - band && list.canScrollForward -> (y - height + band) / band
            else -> 0f
        }
    }

    fun finish() {
        active = false
        point = null
        hovered = null
    }
}

@Composable
private fun MenuSelectionLens(source: Backdrop, bounds: () -> Rect, shape: Shape, dark: Boolean) {
    val rim = remember(dark) { Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (dark) 0.53f else 0.90f),
        Color.White.copy(alpha = 0.12f), Color(0xFF657D99).copy(alpha = 0.48f)
    )) }
    Box(Modifier.layout { measurable, constraints ->
        val rect = bounds()
        if (rect.isEmpty) {
            layout(constraints.maxWidth, constraints.maxHeight) {}
        } else {
            val width = rect.width.roundToInt().coerceAtLeast(1)
            val height = rect.height.roundToInt().coerceAtLeast(1)
            val glass = measurable.measure(Constraints.fixed(width, height))
            layout(constraints.maxWidth, constraints.maxHeight) {
                glass.place(rect.left.roundToInt(), rect.top.roundToInt())
            }
        }
    }.drawBackdrop(backdrop = source, shape = { shape }, highlight = null,
        effects = { lens(10.dp.toPx(), 7.dp.toPx()) },
        onDrawSurface = { drawRect(if (dark) Color.White.copy(alpha = 0.09f) else Color(0xFF405977).copy(alpha = 0.08f)) })
        .border(1.dp, rim, shape))
}

// Zero velocity and acceleration at each seam keep the outward and return paths reversible.
private fun menuPhase(progress: Float, start: Float, end: Float): Float {
    val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * t * (10f + t * (6f * t - 15f))
}

/** The glass is measured at its live size. Both the real backdrop and the content clip use this frame. */
internal fun menuFrame(
    width: Float, height: Float, face: Float, inset: Float, travel: Float,
    progress: Float, direction: LayoutDirection
): Rect {
    // Clear the history pill's 40 dp toolbar band before borrowing horizontal space.
    val drop = menuPhase(progress, 0f, 0.38f)
    val wide = menuPhase(progress, 0.28f, 1f)
    val tall = 0.035f * drop + 0.965f * menuPhase(progress, 0.21f, 1f)
    val w = face + (width - face) * wide
    val h = face + (height - face) * tall
    val endInset = inset * (1f - wide)
    val x = if (direction == LayoutDirection.Ltr) width - w - endInset else endInset
    val y = (inset - travel) * (1f - drop)
    return Rect(x, y, x + w, y + h)
}

private fun Modifier.menuLabelReveal(progress: Float, start: Float): Modifier {
    val reveal = ((progress - start) / (1f - start).coerceAtMost(0.33f)).coerceIn(0f, 1f)
    if (reveal == 1f) return this
    if (reveal == 0f) return graphicsLayer { alpha = 0f }
    return graphicsLayer {
        alpha = reveal
        translationY = -4.dp.toPx() * (1f - reveal)
    }
}

@Composable
internal fun GlassMenu(
    visibility: Transition<Boolean>, backdrop: Backdrop, dark: Boolean, title: String, closeLabel: String,
    items: List<GlassMenuItem>, triggerAlpha: Float, triggerEnabled: Boolean, onExpandedChange: (Boolean) -> Unit, onSelect: (CalculatorOverlay) -> Unit,
    onDragHaptic: () -> Unit, modifier: Modifier = Modifier
) {
    val expanded = visibility.targetState
    val list = rememberLazyGridState()
    val density = LocalDensity.current
    val drag = remember(list, density) { with(density) { MenuDrag(list, 5.dp.toPx(), 2.dp.toPx()) } }
    val lensBounds = remember(drag) { Animatable(Rect.Zero, Rect.VectorConverter) }
    val lensShape = remember { RoundedRectangle(18.dp, RoundedCornerStyle.Continuous) }
    val currentExpanded by rememberUpdatedState(expanded)
    val changeExpanded by rememberUpdatedState(onExpandedChange)
    val haptic by rememberUpdatedState(onDragHaptic)
    val select by rememberUpdatedState(onSelect)
    val foreground = overlayInk(dark)
    var keyPressed by remember { mutableStateOf(false) }
    var contact by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var focused by remember { mutableStateOf(false) }
    val press = rememberGlassPressMotion()
    val pressure = press.pressure
    val direction = Offset(contact.x * 2f - 1f, contact.y * 2f - 1f)
    val progress by visibility.animateFloat(transitionSpec = {
        if (targetState) tween(390, easing = CubicBezierEasing(0.23f, 0.72f, 0.21f, 1f))
        else tween(280, easing = CubicBezierEasing(0.34f, 0.08f, 0.36f, 1f))
    }, label = "Menu glass morph") { if (it) 1f else 0f }
    LaunchedEffect(expanded) {
        if (expanded) list.scrollToItem(0)
    }
    LaunchedEffect(triggerEnabled) {
        if (!triggerEnabled) {
            keyPressed = false
            focused = false
            press.cancel()
            drag.finish()
        }
    }
    LaunchedEffect(drag.active, expanded, density) {
        if (!drag.active || !expanded) {
            lensBounds.snapTo(Rect.Zero)
            return@LaunchedEffect
        }
        val edge = with(density) { 38.dp.toPx() }
        val speed = with(density) { 360.dp.toPx() }
        launch {
            snapshotFlow { drag.target() }.collectLatest { target ->
                drag.updateHover(target?.first, haptic)
                val bounds = target?.second ?: Rect.Zero
                if (bounds.isEmpty || lensBounds.value.isEmpty) lensBounds.snapTo(bounds)
                else lensBounds.animateTo(bounds, spring(dampingRatio = 1f, stiffness = 760f,
                    visibilityThreshold = Rect(0.5f, 0.5f, 0.5f, 0.5f)))
            }
        }
        // A stationary finger in the middle of the grid does not request display frames.
        snapshotFlow { drag.edgeScrollFraction(edge) != 0f }.collectLatest { scrolling ->
            if (scrolling) {
                var previous = withFrameNanos { it }
                while (drag.active) {
                    val now = withFrameNanos { it }
                    val fraction = drag.edgeScrollFraction(edge)
                    if (fraction == 0f) break
                    list.scrollBy(fraction * speed * ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f))
                    previous = now
                }
            }
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.TopEnd) {
        val endMargin = ToolbarEdge
        val topMargin = ToolbarTop
        val triggerSize = ToolbarTouch
        val panelTop = topMargin + triggerSize + 4.dp
        val availableWidth = (maxWidth - endMargin * 2).coerceAtLeast(1.dp)
        val panelMaxHeight = (maxHeight - panelTop - 8.dp).coerceAtLeast(1.dp)
        val labelStyle = TextStyle(color = foreground, fontSize = 16.sp)
        val titleStyle = TextStyle(color = foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        val columns = if (maxWidth <= maxHeight || availableWidth < 336.dp) 1 else {
            val measurer = rememberTextMeasurer()
            // Check the single-column layout with the current language and accessibility font size.
            val naturalHeight = remember(measurer, items, title, labelStyle, titleStyle, density) {
                with(density) {
                    val textWidth = Constraints(maxWidth = (252.dp - 36.dp).roundToPx())
                    val header = measurer.measure(title, titleStyle, constraints = textWidth).size.height + 20.dp.roundToPx()
                    val rows = items.sumOf { item ->
                        val textHeight = measurer.measure(item.label, labelStyle, constraints = textWidth).size.height
                        maxOf(48.dp.roundToPx(), textHeight + 16.dp.roundToPx())
                    }
                    (12.dp.roundToPx() + header + rows).toDp()
                }
            }
            if (panelMaxHeight < naturalHeight) 2 else 1
        }
        val panelWidth = minOf(availableWidth, if (columns == 2) 440.dp else 252.dp)
        if (visibility.currentState || visibility.targetState || progress > 0f) {
            val p = progress.coerceIn(0f, 1f)
            val pulse = if (p > 0f && p < 1f) sin(Math.PI * p).toFloat() else 0f
            val travel = panelTop - topMargin
            val face = with(density) { ToolbarFace.toPx() }
            val inset = with(density) { (triggerSize - ToolbarFace).toPx() / 2f }
            val travelPx = with(density) { travel.toPx() }
            val radius = (ToolbarFace / 2) * (1f - p) + 22.dp * p + 5.dp * pulse
            val surfaceShape = RoundedRectangle(radius, RoundedCornerStyle.Continuous)
            val clipShape = if (p == 1f) surfaceShape else GenericShape { size, direction ->
                val bounds = menuFrame(size.width, size.height, face, inset, travelPx, p, direction)
                addRoundRect(RoundRect(bounds, CornerRadius(with(density) { radius.toPx() })))
            }
            val material = rememberPressableGlassMaterial()
            val menuSource = if (drag.active) rememberLayerBackdrop() else null
            Box(Modifier.padding(top = topMargin, end = endMargin)) {
                // Record the complete grid first. Replace its normal pixels under the lens so a
                // refracted letter cannot show a second, unbent copy of itself underneath.
                val recordedPanel = if (menuSource == null) Modifier else Modifier.drawWithCache {
                    val lens = lensBounds.value
                    val mask = if (lens.isEmpty) null else {
                        val ellipse = Path().apply {
                            addOutline(lensShape.createOutline(lens.size, layoutDirection, this@drawWithCache))
                            translate(Offset(lens.left, lens.top + travelPx))
                        }
                        val viewport = Path().apply {
                            val gridSize = Size(size.width, (size.height - travelPx).coerceAtLeast(0f))
                            addOutline(clipShape.createOutline(gridSize, layoutDirection, this@drawWithCache))
                            translate(Offset(0f, travelPx))
                        }
                        Path.combine(PathOperation.Intersect, viewport, ellipse)
                    }
                    onDrawWithContent {
                        if (mask == null) drawContent() else clipPath(mask, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
                    }
                }.layerBackdrop(menuSource)
                Box(recordedPanel) {
                    // The grid determines the final dimensions once. Only the glass changes size;
                    // its refraction samples the scene at the current outline, without scaling a snapshot.
                    Box(Modifier.matchParentSize().layout { measurable, constraints ->
                        val w = constraints.maxWidth
                        val h = constraints.maxHeight
                        val frame = menuFrame(w.toFloat(), h - travelPx, face, inset, travelPx, p, layoutDirection)
                        drag.visibleArea = frame
                        val glass = measurable.measure(Constraints.fixed(frame.width.roundToInt().coerceAtLeast(1),
                            frame.height.roundToInt().coerceAtLeast(1)))
                        layout(w, h) { glass.place(frame.left.roundToInt(), (travelPx + frame.top).roundToInt()) }
                    }.graphicsLayer {
                        alpha = menuPhase(p, 0f, 0.14f)
                        applyGlassPress(press.deformation * (1f - p), direction)
                    }
                        .motionBlur((4f * pulse * menuPhase(p, 0.26f, 0.50f)).dp)
                        .overlayGlass(backdrop, surfaceShape, dark, (2f + 6f * p).dp, (9f + 5f * p).dp,
                            (13f + 6f * p + 5f * pulse).dp, exportedBackdrop = material.exportedBackdrop))
                    Box(Modifier.padding(top = travel)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.width(panelWidth).heightIn(max = panelMaxHeight)
                                .clip(clipShape)
                                .onGloballyPositioned { drag.viewport = it }
                                .then(if (expanded) Modifier.semantics {
                                    paneTitle = title
                                    dismiss(closeLabel) { changeExpanded(false); true }
                                } else Modifier.clearAndSetSemantics {}),
                            state = list,
                            userScrollEnabled = expanded,
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            item(key = "menu-title", span = { GridItemSpan(maxLineSpan) }) {
                                BasicText(title, Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                    .semantics { heading() }.menuLabelReveal(p, 0.25f), style = titleStyle)
                            }
                            itemsIndexed(items, key = { _, item -> item.action }) { index, item ->
                                val row = index / columns
                                OverlayPressButton(material = material, dark = dark,
                                    shape = RoundedRectangle(15.dp, RoundedCornerStyle.Continuous),
                                    onClick = { select(item.action) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    enabled = expanded, facePadding = PaddingValues(horizontal = 5.dp, vertical = 3.dp),
                                    alignment = Alignment.CenterStart) {
                                    BasicText(item.label, Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                                        .menuLabelReveal(p, 0.38f + 0.035f * row.coerceAtMost(6)), style = labelStyle)
                                }
                            }
                        }
                    }
                }
                // The long-press lens samples the panel and its text. It is outside the recorded layer.
                if (menuSource != null) {
                    Box(Modifier.matchParentSize().padding(top = travel).clip(clipShape)) {
                        MenuSelectionLens(menuSource, { lensBounds.value }, lensShape, dark)
                    }
                }
            }
        }

        val actionLabel = if (expanded) closeLabel else title
        val triggerInput = if (!triggerEnabled) Modifier.clearAndSetSemantics {} else Modifier
            .semantics {
                contentDescription = actionLabel
                role = Role.Button
                onClick { changeExpanded(!currentExpanded); true }
                onLongClick { changeExpanded(true); true }
            }
            .onKeyEvent {
                if (it.key != Key.Enter && it.key != Key.NumPadEnter && it.key != Key.DirectionCenter && it.key != Key.Spacebar) false
                else {
                    if (it.type == KeyEventType.KeyDown) {
                        if (!keyPressed) press.press()
                        keyPressed = true
                        contact = Offset(0.5f, 0.5f)
                    } else if (it.type == KeyEventType.KeyUp && keyPressed) {
                        keyPressed = false
                        press.release()
                        changeExpanded(!currentExpanded)
                    }
                    true
                }
            }.onFocusChanged {
                focused = it.isFocused
                if (!focused && keyPressed) {
                    keyPressed = false
                    press.cancel()
                }
            }.focusable()
            .pointerInput(drag) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    contact = Offset((down.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                        (down.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f))
                    press.press(down.position)
                    try {
                        // A cancelled press and a long-press timeout are distinct outcomes.
                        val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                up.consume()
                                press.release()
                                changeExpanded(!currentExpanded)
                            }
                            true
                        }
                        if (finished != null) return@awaitEachGesture
                        drag.point = down.position
                        drag.active = true
                        changeExpanded(true)
                        haptic()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.singleOrNull { it.id == down.id } ?: break
                            if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                            drag.point = change.position
                            if (!change.pressed) {
                                change.consume()
                                press.release()
                                if (currentExpanded) drag.actionAtPointer()?.let(select)
                                break
                            }
                            change.consume()
                        }
                    } finally {
                        press.cancel()
                        drag.finish()
                    }
                }
            }
        Box(Modifier.padding(top = topMargin, end = endMargin).size(triggerSize)
            .graphicsLayer { alpha = triggerAlpha }.onGloballyPositioned { drag.anchor = it }.then(triggerInput),
            contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(50)
            // The initial circle hands its material to the expanding panel. Bring the separate
            // close button's glass back as the panel clears the stationary toolbar target.
            val circleAlpha = 1f - menuPhase(progress, 0f, 0.14f) + menuPhase(progress, 0.21f, 0.45f)
            Box(Modifier.size(ToolbarFace).graphicsLayer {
                alpha = circleAlpha
                applyGlassPress(press.deformation, direction)
            }.overlayGlass(backdrop, shape, dark, (2f - 0.7f * pressure).dp, (9f + 5f * pressure).dp,
                (13f + 9f * pressure).dp)
                .then(if (focused) Modifier.border(2.dp, foreground, shape) else Modifier)) {
                if (pressure > 0f) Canvas(Modifier.matchParentSize()) {
                    val point = Offset(size.width * contact.x, size.height * contact.y)
                    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.32f * pressure), Color.Transparent),
                        center = point, radius = size.minDimension * 0.76f))
                }
            }
            Canvas(Modifier.size(ToolbarFace)) {
                val angle = Math.PI * 0.5 * progress
                val gap = 5.15.dp.toPx()
                val step = Offset(kotlin.math.sin(angle).toFloat() * gap, kotlin.math.cos(angle).toFloat() * gap)
                for (i in -1..1) drawCircle(foreground, 1.65.dp.toPx(), center + step * i.toFloat())
            }
        }
    }
}
