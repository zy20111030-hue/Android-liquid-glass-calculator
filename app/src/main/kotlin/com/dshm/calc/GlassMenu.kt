package com.dshm.calc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal data class GlassMenuItem(val action: CalculatorMenuAction, val label: String)

private val TriggerShadow = Shadow(radius = 7.dp)

/** Follow the finger before the long-press deadline, without claiming moves from another gesture. */
private suspend fun AwaitPointerEventScope.awaitTriggerRelease(
    id: PointerId, onMove: (Offset) -> Unit
): PointerInputChange? {
    while (true) {
        val change = awaitPointerEvent().changes.singleOrNull() ?: return null
        if (change.id != id || change.isConsumed) return null
        if (change.changedToUp()) return change
        if (change.isOutOfBounds(size, extendedTouchPadding)) return null
        onMove(change.position)
        if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) return null
    }
}

@Composable
internal fun rememberMenuProgress(expanded: Boolean): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        progress.animateTo(if (expanded) 1f else 0f,
            if (expanded) tween(480, easing = CubicBezierEasing(0.22f, 1f, 0.24f, 1f))
            else tween(320, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
    }
    return progress.asState()
}

private enum class DragOrigin { Trigger, List }

private class MenuDrag(private val list: LazyGridState, private val insetX: Float, private val insetY: Float) {
    var anchor by mutableStateOf<LayoutCoordinates?>(null)
    var viewport by mutableStateOf<LayoutCoordinates?>(null)
    var point by mutableStateOf<Offset?>(null)
    var visibleArea by mutableStateOf<Rect?>(null)
    var origin by mutableStateOf<DragOrigin?>(null)
        private set
    val active get() = origin != null
    private var hovered: CalculatorMenuAction? = null

    fun begin(source: DragOrigin, position: Offset): Boolean {
        if (active) return false
        origin = source
        point = position
        return true
    }

    private fun localPoint(): Offset? {
        val position = point ?: return null
        val to = viewport?.takeIf { it.isAttached } ?: return null
        val local = when (origin) {
            DragOrigin.List -> position
            DragOrigin.Trigger -> to.localPositionOf(anchor?.takeIf { it.isAttached } ?: return null, position)
            null -> return null
        }
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
        }?.takeIf { it.key is CalculatorMenuAction }
    }

    fun actionAtPointer() = localPoint()?.let(::itemAt)?.key as? CalculatorMenuAction

    fun lensContact(bounds: Rect): Offset = localPoint()?.minus(bounds.topLeft) ?: Offset.Unspecified

    fun target(): Pair<CalculatorMenuAction, Rect>? {
        val local = localPoint() ?: return null
        val item = itemAt(local) ?: return null
        val x = item.offset.x.toFloat()
        val y = (item.offset.y - list.layoutInfo.viewportStartOffset).toFloat()
        val width = item.size.width.toFloat()
        val height = item.size.height.toFloat()
        // The old lens followed the finger directly, with a 13% attraction to the row centre.
        // Its horizontal extent now belongs to the selected column in landscape.
        val info = list.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull { it.index == 1 }
        val last = info.visibleItemsInfo.lastOrNull { it.index == info.totalItemsCount - 1 }
        val shift = ((local.y - y - height / 2f) * 0.87f).coerceIn(
            if (first?.row == item.row) 0f else -height / 2f,
            if (last?.row == item.row) 0f else height / 2f
        )
        return item.key as CalculatorMenuAction to Rect(x + insetX, y + shift + insetY,
            x + width - insetX, y + height + shift - insetY)
    }

    fun updateHover(haptic: () -> Unit) {
        val next = actionAtPointer()
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
        origin = null
        point = null
        hovered = null
    }
}

private suspend fun AwaitPointerEventScope.consumeUntilReleased(first: List<PointerInputChange>) {
    var changes = first
    while (true) {
        changes.forEach { it.consume() }
        if (changes.none { it.pressed }) return
        changes = awaitPointerEvent(PointerEventPass.Initial).changes
    }
}

/** Leave the down and small moves to clickable. An early swipe belongs to the scrolling grid. */
private suspend fun AwaitPointerEventScope.awaitListHold(down: PointerInputChange): Offset? {
    var position = down.position
    val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val changes = awaitPointerEvent(PointerEventPass.Initial).changes
            val change = changes.singleOrNull()
            if (change == null || change.id != down.id || !change.pressed || change.isConsumed) break
            if (change.isOutOfBounds(size, extendedTouchPadding) ||
                (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            position = change.position
            if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) break
        }
        true
    }
    return if (ended == null) position else null
}

/** After takeover the original row must never receive an unconsumed up, even before recomposition. */
private suspend fun AwaitPointerEventScope.trackMenuSelection(
    drag: MenuDrag, id: PointerId, haptic: () -> Unit, onMove: (Offset) -> Unit = {}
): CalculatorMenuAction? {
    val owner = drag.origin
    while (true) {
        val changes = awaitPointerEvent(PointerEventPass.Initial).changes
        val change = changes.singleOrNull()
        if (drag.origin != owner || change == null || change.id != id || change.isConsumed) {
            if (drag.origin == owner) drag.point = null
            consumeUntilReleased(changes)
            return null
        }
        val released = change.changedToUp()
        drag.point = change.position
        change.consume()
        if (released) return drag.actionAtPointer()
        onMove(change.position)
        drag.updateHover(haptic)
    }
}

@Composable
private fun MenuSelectionLens(source: Backdrop, bounds: () -> Rect, shape: Shape, dark: Boolean, contact: () -> Offset) {
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
    }.drawBackdrop(backdrop = source, shape = { shape }, highlight = null, shadow = null,
        effects = { vibrancy(); blur(1.dp.toPx()); lens(7.dp.toPx(), 11.dp.toPx()) },
        onDrawSurface = { drawRect(Color.White.copy(alpha = if (dark) 0.035f else 0.055f)) })
        .clip(shape)
        .menuContactLight(shape, dark, contact) { 1f }
        .border(0.65.dp, Color.White.copy(alpha = if (dark) 0.12f else 0.18f), shape))
}

// Zero velocity and acceleration at each seam keep the outward and return paths reversible.
private fun menuPhase(progress: Float, start: Float, end: Float): Float {
    val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * t * (10f + t * (6f * t - 15f))
}

/** The glass is measured at its live size. Both the real backdrop and the content clip use this frame. */
internal fun menuFrame(
    width: Float, height: Float, face: Float, inset: Float, travel: Float,
    progress: Float, direction: LayoutDirection, clearOfToolbar: Boolean = false
): Rect {
    // Follow the original straight path wherever the toolbar is outside the sweep.
    // In a narrow layout first clear the history pill's 40 dp band, then resume that morph.
    val drop = if (clearOfToolbar) progress else menuPhase(progress, 0f, 0.38f)
    val growth = if (clearOfToolbar) progress else progress * menuPhase(progress, 0.30f, 0.50f)
    val w = face + (width - face) * growth
    val h = face + (height - face) * growth
    val endInset = inset * (1f - growth)
    val x = if (direction == LayoutDirection.Ltr) width - w - endInset else endInset
    val y = (inset - travel) * (1f - drop)
    return Rect(x, y, x + w, y + h)
}

@Composable
private fun MenuDots(color: Color, horizontal: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gap = 5.2.dp.toPx()
        val step = if (horizontal) Offset(gap, 0f) else Offset(0f, gap)
        for (i in -1..1) drawCircle(color, 1.7.dp.toPx(), center + step * i.toFloat())
    }
}

private fun Modifier.menuGlaze(shape: Shape, dark: Boolean, progress: Float): Modifier =
    drawWithCache {
        val tint = Color.White.copy(alpha = (if (dark) 0.07f else 0.16f) * (1f - 0.85f * progress))
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind { drawOutline(outline, tint) }
    }

@Composable
internal fun GlassMenu(
    expanded: Boolean, progress: Float, backdrop: Backdrop, dark: Boolean, landscape: Boolean, title: String, closeLabel: String,
    items: List<GlassMenuItem>, triggerAlpha: Float, triggerEnabled: Boolean, onExpandedChange: (Boolean) -> Unit, onSelect: (CalculatorMenuAction) -> Unit,
    onDragHaptic: () -> Unit, modifier: Modifier = Modifier
) {
    val list = rememberLazyGridState()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val drag = remember(list, density) { with(density) { MenuDrag(list, 6.dp.toPx(), 2.dp.toPx()) } }
    val hovered = remember(drag) { derivedStateOf { if (drag.active) drag.target() else null } }
    val lensBounds = remember(hovered) { { hovered.value?.second ?: Rect.Zero } }
    val lensShape = remember { RoundedCornerShape(50) }
    val currentExpanded by rememberUpdatedState(expanded)
    val changeExpanded by rememberUpdatedState(onExpandedChange)
    val haptic by rememberUpdatedState(onDragHaptic)
    val select by rememberUpdatedState(onSelect)
    val foreground = overlayInk(dark)
    var keyPressed by remember { mutableStateOf(false) }
    var pointerPressed by remember { mutableStateOf(false) }
    var contact by remember { mutableStateOf(Offset.Unspecified) }
    val faceInset = with(density) { ((ToolbarTouch - ToolbarFace) / 2).toPx() }
    var focused by remember { mutableStateOf(false) }
    val pressure by animateFloatAsState(if (keyPressed || pointerPressed) 1f else 0f,
        tween(90), label = "Menu press")
    LaunchedEffect(expanded) {
        if (expanded) list.scrollToItem(0) else drag.finish()
    }
    LaunchedEffect(triggerEnabled) {
        if (!triggerEnabled) {
            keyPressed = false
            pointerPressed = false
            focused = false
            drag.finish()
        }
    }
    LaunchedEffect(drag.active, expanded, density) {
        if (!drag.active || !expanded) {
            return@LaunchedEffect
        }
        val edge = with(density) { 38.dp.toPx() }
        val speed = with(density) { 360.dp.toPx() }
        launch {
            // Also detect a new row moving under a still finger during edge scrolling or opening.
            snapshotFlow { drag.actionAtPointer() }.collect { drag.updateHover(haptic) }
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
        // Even the longest landscape pill is capped at 120 dp on the start side of the toolbar.
        val clearOfToolbar = landscape && availableWidth - panelWidth >= 126.dp
        if (progress > 0f) {
            val p = progress.coerceIn(0f, 1f)
            val pulse = if (p > 0f && p < 1f) sin(Math.PI * p).toFloat() else 0f
            val travel = panelTop - topMargin
            val face = with(density) { ToolbarFace.toPx() }
            val inset = with(density) { (triggerSize - ToolbarFace).toPx() / 2f }
            val travelPx = with(density) { travel.toPx() }
            val radius = (ToolbarFace / 2) * (1f - p) + 22.dp * p
            val iconAlpha = (1f - p / 0.5f).coerceIn(0f, 1f)
            val contentAlpha = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
            val surfaceShape = RoundedRectangle(radius, RoundedCornerStyle.Continuous)
            val clipShape = if (p == 1f) surfaceShape else GenericShape { size, direction ->
                val bounds = menuFrame(size.width, size.height, face, inset, travelPx, p, direction, clearOfToolbar)
                addOutline(surfaceShape.createOutline(bounds.size, direction, density))
                translate(bounds.topLeft)
            }
            val menuSource = if (drag.active && contentAlpha > 0f) rememberLayerBackdrop() else null
            Box(Modifier.padding(top = topMargin, end = endMargin).motionBlur((9f * pulse).dp)
                .graphicsLayer {
                    val frame = menuFrame(size.width, size.height - travelPx, face, inset, travelPx, p, direction, clearOfToolbar)
                    transformOrigin = TransformOrigin(frame.center.x / size.width.coerceAtLeast(1f),
                        (travelPx + frame.center.y) / size.height.coerceAtLeast(1f))
                    // Only the small face leaving the toolbar belongs to the pressed control.
                    scaleX = 1f - 0.06f * pressure * iconAlpha
                    scaleY = scaleX
                }) {
                // Record the complete grid first. Replace its normal pixels under the lens so a
                // refracted letter cannot show a second, unbent copy of itself underneath.
                val recordedPanel = if (menuSource == null) Modifier else Modifier.drawWithCache {
                    val lens = lensBounds()
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
                        val frame = menuFrame(w.toFloat(), h - travelPx, face, inset, travelPx, p, layoutDirection, clearOfToolbar)
                        drag.visibleArea = frame
                        val glass = measurable.measure(Constraints.fixed(frame.width.roundToInt().coerceAtLeast(1),
                            frame.height.roundToInt().coerceAtLeast(1)))
                        layout(w, h) { glass.place(frame.left.roundToInt(), (travelPx + frame.top).roundToInt()) }
                    }.overlayGlass(backdrop, surfaceShape, dark, 2.dp, (16f + 12f * p).dp,
                        (24f + 14f * p).dp, shadow = Shadow(radius = lerp(TriggerShadow.radius, Shadow.Default.radius, p)))
                        .menuGlaze(surfaceShape, dark, p)) {
                        if (iconAlpha > 0f) MenuDots(foreground, horizontal = false,
                            Modifier.matchParentSize().graphicsLayer { alpha = iconAlpha })
                    }
                    Box(Modifier.padding(top = travel)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.width(panelWidth).heightIn(max = panelMaxHeight)
                                .clip(clipShape)
                                .graphicsLayer { alpha = contentAlpha }
                                .onGloballyPositioned { drag.viewport = it }
                                .pointerInput(drag, expanded) {
                                    if (!expanded) return@pointerInput
                                    awaitEachGesture {
                                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                                        if (drag.active || list.isScrollInProgress ||
                                            drag.visibleArea?.contains(down.position) != true) return@awaitEachGesture
                                        val position = awaitListHold(down) ?: return@awaitEachGesture
                                        if (!drag.begin(DragOrigin.List, position)) return@awaitEachGesture
                                        try {
                                            drag.updateHover(haptic)
                                            val action = trackMenuSelection(drag, down.id, haptic)
                                            if (currentExpanded && action != null) select(action)
                                        } finally {
                                            if (drag.origin == DragOrigin.List) drag.finish()
                                        }
                                    }
                                }
                                .then(if (expanded) Modifier.semantics {
                                    paneTitle = title
                                    dismiss(closeLabel) { changeExpanded(false); true }
                                } else Modifier.clearAndSetSemantics {}),
                            state = list,
                            userScrollEnabled = expanded && !drag.active,
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            item(key = "menu-title", span = { GridItemSpan(maxLineSpan) }) {
                                BasicText(title, Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                    .semantics { heading() }, style = titleStyle)
                            }
                            items(items, key = { it.action }) { item ->
                                OverlayPressButton(material = null, dark = dark,
                                    shape = RoundedRectangle(15.dp, RoundedCornerStyle.Continuous),
                                    onClick = { select(item.action) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    enabled = expanded && !drag.active, feedback = OverlayButtonFeedback.Menu, alignment = Alignment.CenterStart) {
                                    BasicText(item.label, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), style = labelStyle)
                                }
                            }
                        }
                    }
                }
                // The long-press lens samples the panel and its text. It is outside the recorded layer.
                if (menuSource != null) {
                    Box(Modifier.matchParentSize().padding(top = travel).clip(clipShape)) {
                        MenuSelectionLens(menuSource, lensBounds, lensShape, dark) { drag.lensContact(lensBounds()) }
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
                        contact = Offset.Unspecified
                        keyPressed = true
                    } else if (it.type == KeyEventType.KeyUp && keyPressed) {
                        keyPressed = false
                        changeExpanded(!currentExpanded)
                    }
                    true
                }
            }.onFocusChanged {
                focused = it.isFocused
                if (!focused && keyPressed) {
                    keyPressed = false
                }
            }.focusable()
            .pointerInput(drag, faceInset) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (drag.active) return@awaitEachGesture
                    var position = down.position
                    contact = down.position - Offset(faceInset, faceInset)
                    pointerPressed = true
                    try {
                        // A cancelled press and a long-press timeout are distinct outcomes.
                        val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            val up = awaitTriggerRelease(down.id) { point ->
                                position = point
                                contact = point - Offset(faceInset, faceInset)
                            }
                            if (up != null) {
                                up.consume()
                                changeExpanded(!currentExpanded)
                            }
                            true
                        }
                        if (finished != null) return@awaitEachGesture
                        if (!drag.begin(DragOrigin.Trigger, position)) return@awaitEachGesture
                        changeExpanded(true)
                        val action = trackMenuSelection(drag, down.id, haptic) { point ->
                            pointerPressed = point.x in 0f..size.width.toFloat() && point.y in 0f..size.height.toFloat()
                            if (pointerPressed) contact = point - Offset(faceInset, faceInset)
                        }
                        if (currentExpanded && action != null) select(action)
                    } finally {
                        pointerPressed = false
                        if (drag.origin == DragOrigin.Trigger) drag.finish()
                    }
                }
            }
        Box(Modifier.padding(top = topMargin, end = endMargin).size(triggerSize)
            .graphicsLayer { alpha = triggerAlpha }.onGloballyPositioned { drag.anchor = it }.then(triggerInput),
            contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(50)
            // The original dots travel inside the expanding face. The new toolbar also has
            // a stationary close control, revealed after those moving dots have faded.
            val collapsed = progress <= 0f
            val closeAlpha = if (collapsed) 1f else ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
            if (closeAlpha > 0f) {
                Box(Modifier.size(ToolbarFace).graphicsLayer {
                    alpha = closeAlpha
                    scaleX = 1f - 0.06f * pressure
                    scaleY = scaleX
                }.overlayGlass(backdrop, shape, dark, 2.dp, 16.dp, 24.dp, shadow = TriggerShadow)
                    .menuGlaze(shape, dark, 0f)
                    .menuContactLight(shape, dark, { contact }, { pressure })
                    .then(if (focused) Modifier.border(2.dp, foreground, shape) else Modifier)) {
                    MenuDots(foreground, horizontal = !collapsed, Modifier.matchParentSize())
                }
            }
        }
    }
}
