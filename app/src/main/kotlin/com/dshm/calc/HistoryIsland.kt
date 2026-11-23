package com.dshm.calc

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val SidePadding = 14.dp
private val ClockSize = 15.dp
private val ClockGap = 8.dp
private val PanelPadding = 8.dp

internal const val HistoryCircleProgress = 0.30f

@Composable
internal fun Transition<Boolean>.historyTravel() = animateFloat(
    transitionSpec = { spring(dampingRatio = 0.86f, stiffness = 150f, visibilityThreshold = 0.001f) },
    label = "History island"
) { if (it) 1f else 0f }

/** The outer pill occupies a 48 dp toolbar slot. Its glass face is 40 dp high. */
@Composable
internal fun HistoryIsland(
    history: List<HistoryItem>,
    backdrop: Backdrop,
    dark: Boolean,
    str: Str,
    collapsedMaxWidth: Dp,
    expanded: Boolean,
    travel: Float,
    settledOpen: Boolean,
    previewActive: Boolean,
    interactive: Boolean,
    onToggle: () -> Unit,
    expandedWidth: Dp,
    maxPanelHeight: Dp,
    onClear: () -> Unit,
    onUseResult: (String) -> Unit
) {
    val contentColor = overlayInk(dark)
    val entries = history.take(10)
    val latest = entries.firstOrNull()
    val preview = latest?.let { "${it.expr} = ${it.result}" } ?: str.history
    val rowCount = entries.size
    val density = LocalDensity.current
    val style = TextStyle(fontSize = 13.sp, color = contentColor, fontWeight = FontWeight.Medium)
    val measurer = rememberTextMeasurer()
    val widthLimit = collapsedMaxWidth.coerceAtLeast(1.dp)
    val trim = SidePadding * 2 + ClockSize + ClockGap
    val maxTextPixels = with(density) { (widthLimit - trim).coerceAtLeast(0.dp).roundToPx() }
    val textWidth = remember(preview, style, measurer, maxTextPixels) {
        measurer.measure(preview, style, softWrap = false, maxLines = 1,
            constraints = Constraints(maxWidth = maxTextPixels)).multiParagraph.maxIntrinsicWidth
    }
    val pillWidth = (with(density) { textWidth.toDp() } + trim).coerceIn(minOf(72.dp, widthLimit), widthLimit)
    val previewWidth = with(density) {
        (pillWidth - minOf(SidePadding, pillWidth / 6) * 2 - ClockGap - ClockSize).coerceAtLeast(0.dp).roundToPx()
    }
    val rowHeight = maxOf(ToolbarTouch, with(density) { 18.sp.toDp() } + 18.dp)
    val visibleRows = 1 + maxOf(rowCount, 1) + if (rowCount > 0) 1 else 0
    val panelHeight by animateDpAsState(
        minOf(rowHeight * visibleRows + PanelPadding * 2, maxPanelHeight.coerceAtLeast(1.dp)),
        spring(dampingRatio = 0.82f, stiffness = 150f, visibilityThreshold = 0.5.dp), label = "history list height"
    )
    val progress = travel.coerceIn(0f, 1f)
    val circle = (progress / HistoryCircleProgress).coerceAtMost(1f)
    val open = ((progress - HistoryCircleProgress) / (1f - HistoryCircleProgress)).coerceAtLeast(0f)
    val diameter = minOf(ToolbarFace, widthLimit)
    val width = lerp(lerp(pillWidth, diameter, circle), expandedWidth.coerceAtLeast(1.dp), open)
    val height = lerp(ToolbarFace, minOf(panelHeight, maxPanelHeight.coerceAtLeast(1.dp)), open)
    val inset = (ToolbarTouch - ToolbarFace) / 2 * (1f - open)
    val corner = lerp(ToolbarFace / 2, 26.dp, open)
    val shape = remember(corner) { RoundedRectangle(corner, RoundedCornerStyle.Continuous) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val triggerInteraction = remember { MutableInteractionSource() }
    val triggerPressed by triggerInteraction.collectIsPressedAsState()
    val triggerFocused by triggerInteraction.collectIsFocusedAsState()
    val pressScale = animateFloatAsState(if (interactive && (pressed || triggerPressed)) 0.94f else 1f, tween(90), label = "History press")
    val currentToggle by rememberUpdatedState(onToggle)
    val previewAlpha = (1f - progress / 0.35f).coerceIn(0f, 1f)
    val panelAlpha = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f)
    val panelEnabled = interactive && settledOpen && !triggerPressed
    val showPanel = panelAlpha > 0.01f

    Box(Modifier.width(width).height(height + inset * 2)
        .then(if (interactive) Modifier else Modifier.clearAndSetSemantics {})) {
        Box(Modifier.matchParentSize().padding(vertical = inset)
            .motionBlur((9f * sin(progress * PI).toFloat()).dp)
            .drawWithContent {
                // Draw the face and its contents together while keeping the touch targets stationary.
                scale(pressScale.value) { this@drawWithContent.drawContent() }
            }) {
            Spacer(Modifier.matchParentSize()
                .overlayGlass(backdrop, shape, dark, (2f + 8f * open).dp,
                    (11f + 6f * progress).dp, (17f + 8f * progress).dp)
                .then(if (interactive && triggerFocused && !expanded) Modifier.border(2.dp, contentColor, shape) else Modifier))
            Box(Modifier.matchParentSize().clip(shape), contentAlignment = Alignment.Center) {
                if (previewAlpha > 0.01f) {
                    Row(
                        Modifier.fillMaxWidth().graphicsLayer { alpha = previewAlpha }.clearAndSetSemantics {}
                            .padding(horizontal = minOf(SidePadding, width / 6)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ClockIcon(contentColor)
                        Spacer(Modifier.width(ClockGap))
                        key(preview) {
                            HistoryPreview(preview, style, (textWidth - previewWidth).coerceAtLeast(0f),
                                active = previewActive && interactive, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (showPanel) {
                    val semantics = if (panelEnabled) Modifier.semantics { paneTitle = str.history }
                        else Modifier.clearAndSetSemantics {}
                    HistoryPanel(
                        entries, str, contentColor, rowHeight, panelEnabled, interaction, focused, dark,
                        onToggle, onClear, onUseResult,
                        Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                            .size(expandedWidth, minOf(panelHeight, maxPanelHeight.coerceAtLeast(1.dp)))
                            .graphicsLayer { alpha = panelAlpha }.drawWithContent {
                                scale(0.94f + 0.06f * panelAlpha) { this@drawWithContent.drawContent() }
                            }.then(semantics)
                            .then(if (panelEnabled) Modifier.pointerInput(interaction) {
                                detectTapGestures(onPress = { point ->
                                    val press = PressInteraction.Press(point)
                                    interaction.tryEmit(press)
                                    var released = false
                                    try {
                                        released = tryAwaitRelease()
                                    } finally {
                                        interaction.tryEmit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                                    }
                                }) { currentToggle() }
                            } else Modifier)
                    )
                }
            }
        }
        // A second tap during the morph must hit the same control, even as the list fades in.
        if (interactive && !panelEnabled) {
            Box(
                Modifier.fillMaxWidth().height(minOf(ToolbarTouch, height + inset * 2)).clickable(
                    interactionSource = triggerInteraction, indication = null, role = Role.Button, onClick = onToggle
                ).semantics {
                    contentDescription = if (latest == null) str.history else "${str.history}, $preview"
                }
            )
        }
    }
}

@Composable
private fun HistoryPreview(text: String, style: TextStyle, overflowPx: Float, active: Boolean, modifier: Modifier) {
    if (overflowPx <= 0f) {
        BasicText(text, modifier, style, maxLines = 1, softWrap = false)
        return
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val shift = if (active && windowFocused && durationScale > 0f) {
        rememberInfiniteTransition(label = "History marquee").animateFloat(0f, 1f, infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5200
                0f at 0
                0f at 900
                1f at 3900
                1f at 4700
                0f at 5200
            },
            repeatMode = RepeatMode.Restart
        ), label = "History text position")
    } else null
    Box(modifier.clipToBounds()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val edge = (12.dp.toPx() / size.width.coerceAtLeast(1f)).coerceAtMost(0.4f)
            val mask = Brush.horizontalGradient(
                0f to Color.Black, edge to Color.Transparent, (1f - edge) to Color.Transparent, 1f to Color.Black
            )
            onDrawWithContent {
                drawContent()
                drawRect(mask, blendMode = BlendMode.DstOut)
            }
        }) {
        BasicText(text, Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
            .offset { IntOffset((-overflowPx * (shift?.value ?: 0f)).roundToInt(), 0) },
            style, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ClockIcon(color: Color) {
    Canvas(Modifier.size(ClockSize)) {
        val stroke = 1.4.dp.toPx()
        drawCircle(color, radius = size.minDimension * 0.42f, style = Stroke(stroke))
        drawLine(color, center, Offset(center.x, size.height * 0.25f), stroke, StrokeCap.Round)
        drawLine(color, center, Offset(size.width * 0.69f, size.height * 0.59f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun HistoryPanel(
    history: List<HistoryItem>,
    str: Str,
    ink: Color,
    rowHeight: Dp,
    enabled: Boolean,
    headerInteraction: MutableInteractionSource,
    headerFocused: Boolean,
    dark: Boolean,
    onToggle: () -> Unit,
    onClear: () -> Unit,
    onUseResult: (String) -> Unit,
    modifier: Modifier
) {
    // The header and footer also scroll, so even a short multi-window panel can reach every action.
    Column(modifier.verticalScroll(rememberScrollState(), enabled = enabled).padding(horizontal = 14.dp, vertical = PanelPadding)) {
        val itemShape = remember { RoundedRectangle(13.dp, RoundedCornerStyle.Continuous) }
        Row(
            Modifier.fillMaxWidth().height(rowHeight)
                .then(if (enabled && headerFocused) Modifier.border(2.dp, ink, itemShape) else Modifier)
                .clickable(enabled = enabled, interactionSource = headerInteraction, indication = null,
                    role = Role.Button, onClickLabel = str.close, onClick = onToggle)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(str.history, modifier = Modifier.weight(1f).semantics { heading() },
                style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            Canvas(Modifier.size(16.dp)) {
                val tip = Offset(center.x, size.height * 0.34f)
                drawLine(ink, Offset(size.width * 0.19f, size.height * 0.65f), tip, 1.6.dp.toPx(), StrokeCap.Round)
                drawLine(ink, tip, Offset(size.width * 0.81f, size.height * 0.65f), 1.6.dp.toPx(), StrokeCap.Round)
            }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(rowHeight).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                BasicText(str.emptyHistory, style = TextStyle(color = ink.copy(alpha = 0.82f), fontSize = 13.sp))
            }
        } else {
            for (item in history) {
                Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                    OverlayPressButton(null, dark, itemShape, { onUseResult(item.result) },
                        Modifier.weight(1f).height(rowHeight), enabled = enabled, feedback = OverlayButtonFeedback.Native,
                        facePadding = PaddingValues(vertical = 2.dp), onClickLabel = str.useResult, alignment = Alignment.CenterStart) {
                        BasicText("${item.expr} = ${item.result}", Modifier.padding(horizontal = 6.dp), style = TextStyle(color = ink, fontSize = 14.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val useAndClose = "${str.useResult} · ${str.close}"
                    OverlayPressButton(null, dark, itemShape, { onUseResult(item.result); onToggle() },
                        Modifier.size(ToolbarTouch).semantics { contentDescription = "$useAndClose, ${item.result}" },
                        enabled = enabled, feedback = OverlayButtonFeedback.Native, facePadding = PaddingValues(4.dp), onClickLabel = useAndClose) {
                        Canvas(Modifier.size(18.dp)) {
                            val stroke = 1.6.dp.toPx()
                            val corner = Offset(size.width * 0.77f, size.height * 0.64f)
                            val tip = Offset(size.width * 0.22f, corner.y)
                            drawLine(ink, Offset(corner.x, size.height * 0.23f), corner, stroke, StrokeCap.Round)
                            drawLine(ink, corner, tip, stroke, StrokeCap.Round)
                            drawLine(ink, tip, Offset(size.width * 0.44f, size.height * 0.42f), stroke, StrokeCap.Round)
                            drawLine(ink, tip, Offset(size.width * 0.44f, size.height * 0.86f), stroke, StrokeCap.Round)
                        }
                    }
                }
            }
            OverlayPressButton(null, dark, itemShape, onClear, Modifier.fillMaxWidth().height(rowHeight),
                enabled = enabled, feedback = OverlayButtonFeedback.Native, facePadding = PaddingValues(vertical = 2.dp)) {
                BasicText(str.clearHistory, style = TextStyle(color = ink.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}
