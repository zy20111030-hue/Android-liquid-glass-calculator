package com.dshm.calc

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.scrollableArea
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.ceil

private class DisplayTap(val time: Long, val position: Offset, val revision: Int, val token: FItem?, val phase: ExpressionPhase)

private fun Offset.inFormula(scroll: MutableFloatState) = copy(x = x + scroll.floatValue)

@Composable
internal fun ExpressionDisplay(
    session: ExpressionSession, labels: Str, ink: Color, lightText: Boolean,
    font: FontFamily, modifier: Modifier = Modifier, active: Boolean = true
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val editor = session.editor
    val revision = session.revision
    val contentRevision = session.contentRevision
    val document = session.selection
    val length = remember(editor, contentRevision) { editor.textLen() }
    val targetSize = when { length > 10 -> 40f; length > 7 -> 48f; else -> 62f }
    val editingSize by animateFloatAsState(targetSize, tween(125, easing = LinearOutSlowInEasing), label = "Formula type size")
    val size = if (document == null && length <= FORMULA_RUN_LENGTH) editingSize.sp else targetSize.sp
    val shadow = with(density) {
        Shadow(if (lightText) Color(0x99070D19) else Color(0x90FFFFFF), Offset(0f, 0.8.dp.toPx()), 1.8.dp.toPx())
    }
    val style = TextStyle(color = ink, fontSize = size, fontFamily = font, shadow = shadow)
    val gestureConfig = LocalViewConfiguration.current
    var previousTap by remember(session) { mutableStateOf<DisplayTap?>(null) }
    val scroll = rememberSaveable { mutableFloatStateOf(0f) }
    val phase = session.phase
    val source = session.completedExpression.takeIf { phase == ExpressionPhase.Result && document == null }
    val arrival = remember { Animatable(if (phase == ExpressionPhase.Result) 1f else 0f) }
    LaunchedEffect(phase, active) {
        when {
            phase != ExpressionPhase.Result -> arrival.snapTo(0f)
            !active -> arrival.snapTo(1f)
            else -> arrival.animateTo(1f, tween(175, easing = LinearOutSlowInEasing))
        }
    }
    val showCaret = active && LocalWindowInfo.current.isWindowFocused
    val blinkEnabled = showCaret && rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor != 0f

    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomEnd) {
        val viewport = constraints.maxWidth
        val height = constraints.maxHeight
        val gap = with(density) { 3.dp.toPx() }
        val captionStyle = style.copy(color = ink.copy(alpha = 0.90f), fontSize = 17.sp)
        val caption = remember(source, measurer, captionStyle) {
            source?.let { measureExpression(it.root, measurer, captionStyle, spaceOperators = true) }
        }
        val natural = remember(editor, contentRevision, measurer, style) {
            if (editor.root.isEmpty()) null else measureExpression(editor.root, measurer, style)
        }
        val availableForMain = height - (caption?.let { it.height + gap } ?: 0f)
        val layout = remember(natural, availableForMain) {
            // Keep the completed formula and result visible together when both fit at a readable size.
            if (natural == null || natural.height <= availableForMain || height <= 0) natural
            else {
                val fitted = with(density) { (size.toPx() * availableForMain / natural.height * 0.96f).toSp().value.coerceIn(18f, size.value).sp }
                measureExpression(editor.root, measurer, style.copy(fontSize = fitted))
            }
        }
        val reserveHandles = maxHeight >= 160.dp
        val caret = if (document == null && phase == ExpressionPhase.Editing) layout?.cursorRect(editor) else null
        val vertical = rememberScrollState()
        val totalHeight = (layout?.height ?: 0f) + (caption?.let { it.height + gap } ?: 0f)
        val needsVerticalScroll = if (document == null) totalHeight > height
            else with(density) { 48.dp.toPx() + size.toPx() * 1.6f > height }
        LaunchedEffect(revision, caret, needsVerticalScroll, vertical.maxValue, vertical.viewportSize) {
            if (!needsVerticalScroll || vertical.maxValue == Int.MAX_VALUE || vertical.viewportSize == 0) return@LaunchedEffect
            when {
                phase != ExpressionPhase.Editing && document == null -> vertical.scrollTo(vertical.maxValue)
                caret != null && caret.top < vertical.value -> vertical.scrollTo(caret.top.toInt())
                caret != null && caret.bottom > vertical.value + vertical.viewportSize ->
                    vertical.scrollTo(caret.bottom.toInt() - vertical.viewportSize)
            }
        }
        var blinkOn by remember(revision, caret, blinkEnabled) { mutableStateOf(true) }
        LaunchedEffect(revision, caret, blinkEnabled) {
            while (caret != null && blinkEnabled) {
                delay(530)
                blinkOn = !blinkOn
            }
        }
        if (document != null) {
            Box(Modifier.matchParentSize().pointerInput(session) { detectTapGestures { session.dismissSelection() } })
        }
        val columnModifier = if (needsVerticalScroll) Modifier.verticalScroll(vertical) else Modifier
        Column(columnModifier, horizontalAlignment = Alignment.End) {
            if (caption != null && source != null) {
                val captionScroll = rememberSaveable { mutableFloatStateOf(0f) }
                val plain = remember(source) { SelectableExpression(source).text }
                FormulaLine(caption, viewport, captionScroll, ink.copy(alpha = 0.90f), Modifier.graphicsLayer {
                    alpha = arrival.value
                    translationY = (1f - arrival.value) * 4.dp.toPx()
                }.semantics {
                    contentDescription = plain
                    onLongClick(labels.selectValue) { session.selectCompleted(null); true }
                }.pointerInput(source, caption) {
                    detectTapGestures(
                        onDoubleTap = { session.selectCompleted(caption.tokenAt(it.inFormula(captionScroll))) },
                        onLongPress = { session.selectCompleted(caption.tokenAt(it.inFormula(captionScroll))) }
                    )
                })
                Spacer(Modifier.height(3.dp))
            }
            if (document != null) {
                DisplaySelection(
                    document.text, session.selectedRange, session::updateSelection, style, ink, lightText,
                    labels.copy, labels.selectAll, labels.done, session::dismissSelection, interactive = active
                )
                if (reserveHandles) Spacer(Modifier.height(18.dp))
            } else if (layout == null) {
                BasicText("0", style = style, modifier = Modifier.semantics {
                    onLongClick(labels.selectValue) { session.select(); true }
                }.pointerInput(session) {
                    detectTapGestures(onLongPress = { session.select() }, onDoubleTap = { session.select() })
                })
            } else {
                val expression = remember(editor, contentRevision) { exprText(editor.root) }
                val liveLayout by rememberUpdatedState(layout)
                val resultMotion = if (phase == ExpressionPhase.Result) Modifier.graphicsLayer {
                    translationY = (1f - arrival.value) * 4.dp.toPx()
                    alpha = 0.76f + 0.24f * arrival.value
                } else Modifier
                FormulaLine(layout, viewport, scroll, ink, resultMotion.semantics {
                    contentDescription = expression
                    onLongClick(labels.selectValue) { session.select(); true }
                }.pointerInput(session, revision, gestureConfig) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            previousTap = null
                            session.select(liveLayout.tokenAt(offset.inFormula(scroll)))
                        },
                        onTap = { offset ->
                            val now = SystemClock.uptimeMillis()
                            val point = offset.inFormula(scroll)
                            val token = liveLayout.tokenAt(point)
                            val first = previousTap
                            if (first != null && first.revision == revision && first.token === token &&
                                now - first.time <= gestureConfig.doubleTapTimeoutMillis &&
                                (point - first.position).getDistance() <= gestureConfig.touchSlop * 2) {
                                previousTap = null
                                session.select(token, first.phase)
                            } else {
                                session.moveCaret { liveLayout.hitTest(it, point) }
                                previousTap = DisplayTap(now, point, session.revision, token, phase)
                            }
                        }
                    )
                }, revision, caret, caretVisible = { showCaret && blinkOn })
            }
        }
    }
}

@Composable
private fun FormulaLine(
    layout: ExpressionLayout, viewport: Int, offset: MutableFloatState, ink: Color, modifier: Modifier,
    revision: Int = 0, caret: Rect? = null, caretVisible: () -> Boolean = { true }
) {
    val density = LocalDensity.current
    val width = minOf(viewport, ceil(layout.width).toInt())
    val limit = (layout.width - width).coerceAtLeast(0f)
    val currentLimit by rememberUpdatedState(limit)
    val scrolling = rememberScrollableState { delta ->
        val old = offset.floatValue
        val target = (old + delta).coerceIn(0f, currentLimit)
        offset.floatValue = target
        target - old
    }
    val bounded = remember(scrolling, offset) {
        object : ScrollableState by scrolling {
            override val canScrollForward get() = offset.floatValue < currentLimit
            override val canScrollBackward get() = offset.floatValue > 0f
        }
    }
    LaunchedEffect(layout, viewport, caret, revision) {
        if (viewport <= 0) return@LaunchedEffect
        val margin = with(density) { 6.dp.toPx() }
        val target = when {
            caret == null -> limit
            caret.right + margin > offset.floatValue + viewport -> caret.right + margin - viewport
            caret.left - margin < offset.floatValue -> caret.left - margin
            else -> offset.floatValue
        }.coerceIn(0f, limit)
        bounded.scroll { scrollBy(target - offset.floatValue) }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        // Logical coordinates can span hundreds of thousands of pixels. Only the viewport is a layer.
        Canvas(Modifier.size(with(density) { width.toDp() }, with(density) { layout.height.toDp() })
            .scrollableArea(bounded, Orientation.Horizontal, enabled = limit > 0f,
                reverseScrolling = LocalLayoutDirection.current == LayoutDirection.Rtl)
            .semantics { horizontalScrollAxisRange = ScrollAxisRange({ offset.floatValue }, { limit }) }
            .then(modifier)) {
            val left = offset.floatValue.coerceIn(0f, limit)
            val right = left + size.width
            translate(left = -left) {
                with(layout) { drawContent(ink, left, right) }
                if (caret != null && caretVisible()) {
                    val inset = caret.height * 0.13f
                    drawRoundRect(ink, Offset(caret.left, caret.top + inset), Size(caret.width, caret.height - inset * 2), CornerRadius(caret.width / 2f))
                }
            }
        }
    }
}
