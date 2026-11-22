package com.dshm.calc

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import kotlin.math.abs

private val SidePadding = 14.dp
private val ClockSize = 15.dp
private val ClockGap = 8.dp
private val PanelPadding = 8.dp

private fun morphPhase(progress: Float, start: Float, end: Float): Float {
    val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
internal fun Transition<Boolean>.historyTravel() = animateFloat(
    transitionSpec = { spring(dampingRatio = 0.76f, stiffness = 180f, visibilityThreshold = 0.005f) },
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
        measurer.measure(preview, style, softWrap = false, maxLines = 1, constraints = Constraints(maxWidth = maxTextPixels)).size.width
    }
    val pillWidth by animateDpAsState(
        (with(density) { textWidth.toDp() } + trim).coerceIn(minOf(72.dp, widthLimit), widthLimit),
        tween(160), label = "history preview width"
    )
    val rowHeight = maxOf(ToolbarTouch, with(density) { 18.sp.toDp() } + 18.dp)
    val visibleRows = 1 + maxOf(rowCount, 1) + if (rowCount > 0) 1 else 0
    val panelHeight by animateDpAsState(
        minOf(rowHeight * visibleRows + PanelPadding * 2, maxPanelHeight.coerceAtLeast(1.dp)),
        spring(dampingRatio = 0.85f, stiffness = 230f), label = "history list height"
    )
    val progress = travel.coerceIn(0f, 1f)
    // Hold the circle briefly between the two shape changes, in either direction.
    val circle = morphPhase(progress, 0f, 0.27f)
    val open = morphPhase(progress, 0.38f, 1f)
    val diffusion = morphPhase(progress, 0.10f, 0.42f) * (1f - morphPhase(progress, 0.47f, 0.84f))
    val rebound = minOf(3.dp, 105.dp * abs(travel - progress))
    val diameter = minOf(ToolbarFace, widthLimit)
    val width = lerp(lerp(pillWidth, diameter, circle), expandedWidth.coerceAtLeast(1.dp), open)
    val height = lerp(ToolbarFace, minOf(panelHeight, maxPanelHeight.coerceAtLeast(1.dp)), open)
    val inset = (ToolbarTouch - ToolbarFace) / 2 * (1f - open)
    val corner = lerp(ToolbarFace / 2, 26.dp, open)
    val shape = remember(corner) { RoundedRectangle(corner, RoundedCornerStyle.Continuous) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val press = rememberGlassPressMotion(if (interactive) interaction else null)
    val currentToggle by rememberUpdatedState(onToggle)
    val panelAlpha = morphPhase(progress, 0.50f, 0.92f)
    val panelEnabled = interactive && expanded
    val material = if (panelAlpha > 0f) rememberPressableGlassMaterial() else null

    Box(Modifier.width(width).height(height + inset * 2)
        .then(if (interactive) Modifier else Modifier.clearAndSetSemantics {})) {
        Box(Modifier.matchParentSize().padding(vertical = inset)) {
            // Keep the label, list and pointer coordinates independent of the deforming glass.
            Spacer(Modifier.matchParentSize()
                .graphicsLayer {
                    val force = press.deformation * (1f - 0.65f * open)
                    applyGlassPress(force, glassPressDirection(press.contact, size.width, size.height))
                    scaleX += rebound.toPx() / size.width.coerceAtLeast(1f)
                    scaleY += 0.65f * rebound.toPx() / size.height.coerceAtLeast(1f)
                    transformOrigin = TransformOrigin(0.5f, 0.5f * (1f - open))
                }
                .motionBlur(6.dp * diffusion)
                .overlayGlass(backdrop, shape, dark, (2f + 8f * open).dp,
                    (11f + 6f * open + 3f * press.pressure).dp, (17f + 8f * open + 4f * press.pressure).dp,
                    exportedBackdrop = material?.exportedBackdrop)
                .then(if (interactive && focused && !expanded) Modifier.border(2.dp, contentColor, shape) else Modifier))
            Box(Modifier.matchParentSize().clip(shape), contentAlignment = Alignment.Center) {
                val previewAlpha = 1f - morphPhase(progress, 0.34f, 0.53f)
                if (previewAlpha > 0f) {
                    val labelAlpha = 1f - morphPhase(progress, 0f, 0.23f)
                    val clockInset = ((width - ClockSize) / 2).coerceAtLeast(0.dp)
                    val windowFocused = LocalWindowInfo.current.isWindowFocused
                    val motionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
                    Row(
                        Modifier.fillMaxWidth().graphicsLayer { alpha = previewAlpha }.clearAndSetSemantics {}
                            .padding(horizontal = lerp(minOf(SidePadding, width / 6), clockInset, circle)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ClockIcon(contentColor)
                        Spacer(Modifier.width(ClockGap * (1f - circle)))
                        key(preview) {
                            val marquee = if (interactive && previewActive && windowFocused && !expanded && progress == 0f && motionScale > 0f) {
                                Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                            } else Modifier
                            BasicText(preview, modifier = Modifier.weight(1f).graphicsLayer { alpha = labelAlpha }.clipToBounds().then(marquee),
                                style = style, maxLines = 1, softWrap = false)
                        }
                    }
                }
                if (material != null) {
                    val semantics = if (panelEnabled) Modifier.semantics { paneTitle = str.history }
                        else Modifier.clearAndSetSemantics {}
                    HistoryPanel(
                        entries, str, contentColor, rowHeight, panelEnabled, interaction, focused, material, dark,
                        onToggle, onClear, onUseResult,
                        Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                            .size(expandedWidth, minOf(panelHeight, maxPanelHeight.coerceAtLeast(1.dp)))
                            .graphicsLayer {
                                alpha = panelAlpha
                                translationY = 4.dp.toPx() * (1f - panelAlpha)
                            }.then(semantics)
                            .then(if (panelEnabled) Modifier.pointerInput(Unit) { detectTapGestures { currentToggle() } } else Modifier)
                    )
                }
            }
        }
        // While the list is leaving, only the toolbar row can reverse the transition.
        if (interactive && (!expanded || panelAlpha == 0f)) {
            Box(
                Modifier.fillMaxWidth().height(minOf(ToolbarTouch, height + inset * 2)).clickable(
                    interactionSource = interaction, indication = null, role = Role.Button, onClick = onToggle
                ).semantics {
                    contentDescription = if (latest == null) str.history else "${str.history}, $preview"
                }
            )
        }
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
    material: PressableGlassMaterial,
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
                    OverlayPressButton(material, dark, itemShape, { onUseResult(item.result) },
                        Modifier.weight(1f).height(rowHeight), enabled = enabled,
                        facePadding = PaddingValues(vertical = 2.dp), onClickLabel = str.useResult, alignment = Alignment.CenterStart) {
                        BasicText("${item.expr} = ${item.result}", Modifier.padding(horizontal = 6.dp), style = TextStyle(color = ink, fontSize = 14.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val useAndClose = "${str.useResult} · ${str.close}"
                    OverlayPressButton(material, dark, itemShape, { onUseResult(item.result); onToggle() },
                        Modifier.size(ToolbarTouch).semantics { contentDescription = "$useAndClose, ${item.result}" },
                        enabled = enabled, facePadding = PaddingValues(4.dp), onClickLabel = useAndClose) {
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
            OverlayPressButton(material, dark, itemShape, onClear, Modifier.fillMaxWidth().height(rowHeight),
                enabled = enabled, facePadding = PaddingValues(vertical = 2.dp)) {
                BasicText(str.clearHistory, style = TextStyle(color = ink.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}
