package com.dshm.calc

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.createChildTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

internal val ToolbarTop = 6.dp
internal val ToolbarEdge = 12.dp
internal val ToolbarTouch = 48.dp
internal val ToolbarFace = 40.dp

private fun CalculatorOverlay?.settingsPage() =
    this?.takeUnless { it == CalculatorOverlay.MENU || it == CalculatorOverlay.HISTORY }

internal class PanelPresentation(val progress: State<Float>, val interactive: Boolean)
internal val LocalPanelPresentation = compositionLocalOf<PanelPresentation> { error("Missing overlay host") }

internal val Transition<CalculatorOverlay?>.onStage: Boolean
    get() = currentState != null || targetState != null || isRunning

/** Keep drawing the scene for the glass, but give touches and accessibility to the foreground. */
internal fun Modifier.coveredByOverlay(covered: Boolean): Modifier = if (!covered) this else
    clearAndSetSemantics { }.focusProperties { canFocus = false }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

@Composable
@OptIn(ExperimentalTransitionApi::class)
internal fun CalculatorOverlays(
    transition: Transition<CalculatorOverlay?>, navigate: (CalculatorOverlay?) -> Unit, scene: Backdrop,
    landscape: Boolean, dark: Boolean, titleColor: Color, str: Str, items: List<GlassMenuItem>, history: List<HistoryItem>,
    clearHistory: () -> Unit, useResult: (String) -> Unit, dragHaptic: () -> Unit,
    pageContent: @Composable (CalculatorOverlay, Backdrop) -> Unit
) {
    val island = transition.createChildTransition(label = "History visibility") { it == CalculatorOverlay.HISTORY }
    val islandTravel by island.historyTravel()
    val menu = transition.createChildTransition(label = "Menu visibility") { it == CalculatorOverlay.MENU }
    val page = transition.targetState.settingsPage()
    val pageVisibility = transition.createChildTransition(label = "Settings visibility") { it.settingsPage() != null }
    val reveal = pageVisibility.animateFloat(transitionSpec = {
        if (targetState) tween(245, easing = LinearOutSlowInEasing) else tween(165, easing = FastOutLinearInEasing)
    }, label = "Settings glass") { if (it) 1f else 0f }
    var lastPage by remember { mutableStateOf(page) }
    SideEffect { if (page != null) lastPage = page }
    val pageOnStage = page != null || pageVisibility.currentState || reveal.value > 0f
    val islandOnStage = island.currentState || island.targetState || island.isRunning
    val menuOnStage = menu.currentState || menu.targetState || menu.isRunning
    val shade = transition.animateFloat(transitionSpec = { tween(180) }, label = "Toolbar shade") {
        if (it == CalculatorOverlay.MENU || it == CalculatorOverlay.HISTORY) 1f else 0f
    }
    val toolbar = rememberLayerBackdrop()
    val heading = rememberLayerBackdrop()
    val dimAmount = if (dark) 0.16f else 0.09f
    val dim = rememberCanvasBackdrop { drawRect(Color.Black.copy(alpha = shade.value * dimAmount)) }
    val menuSource = rememberCombinedBackdrop(scene, heading, dim)
    val historySource = rememberCombinedBackdrop(scene, dim)
    val pageSource = rememberCombinedBackdrop(scene, toolbar)
    BackHandler(transition.onStage) { navigate(null) }

    Box(Modifier.fillMaxSize().coveredByOverlay(pageOnStage)
        .then(if (pageOnStage) Modifier.layerBackdrop(toolbar) else Modifier)) {
        // The title and the menu fit again when the island reaches the circular phase of its return.
        val peerAlpha = 1f - (islandTravel / 0.32f).coerceIn(0f, 1f)
        if (islandOnStage && !menuOnStage) OverlayScrim(shade.value * dimAmount) { navigate(null) }
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()
                .padding(horizontal = ToolbarEdge, vertical = ToolbarTop),
                contentAlignment = if (landscape) Alignment.TopStart else Alignment.TopCenter) {
            val titleStyle = TextStyle(color = titleColor, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            val titleWidth = if (landscape) 0.dp else {
                val measurer = rememberTextMeasurer()
                val width = remember(str.title, titleStyle, measurer) {
                    measurer.measure(str.title, titleStyle, maxLines = 1, softWrap = false).size.width
                }
                minOf(with(LocalDensity.current) { width.toDp() }, (maxWidth - 72.dp).coerceAtLeast(0.dp) / 2)
            }
            val panelWidth = maxWidth
            val panelHeight = maxHeight
            val pillWidth = if (landscape) minOf(panelWidth * 0.18f, 120.dp)
                else (panelWidth - (maxOf(titleWidth, ToolbarTouch) + 6.dp) * 2).coerceAtLeast(1.dp)
            // Only the occupied toolbar band is recorded. The menu can refract it without sampling itself.
            Box(Modifier.fillMaxWidth().then(if (menuOnStage) Modifier.layerBackdrop(heading) else Modifier),
                contentAlignment = if (landscape) Alignment.TopStart else Alignment.TopCenter) {
                if (!landscape && peerAlpha > 0f) {
                    Box(Modifier.align(Alignment.TopStart).height(ToolbarTouch).widthIn(max = titleWidth)
                        .graphicsLayer { alpha = peerAlpha },
                        contentAlignment = Alignment.CenterStart) {
                        BasicText(str.title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HistoryIsland(history, if (islandOnStage && !menuOnStage) historySource else scene, dark, str,
                    collapsedMaxWidth = pillWidth.coerceAtMost(panelWidth), expanded = island.targetState,
                    travel = islandTravel, previewActive = !pageOnStage && !menuOnStage, interactive = !pageOnStage && !menuOnStage,
                    onToggle = { navigate(if (island.targetState) null else CalculatorOverlay.HISTORY) },
                    expandedWidth = panelWidth, maxPanelHeight = panelHeight,
                    onClear = clearHistory, onUseResult = useResult)
            }
        }
        if (menuOnStage) OverlayScrim(shade.value * dimAmount) { navigate(null) }
        if (peerAlpha > 0f || menuOnStage) {
            GlassMenu(visibility = menu, backdrop = if (menuOnStage) menuSource else scene, dark = dark, title = str.settings,
                closeLabel = str.close, items = items, triggerAlpha = peerAlpha, triggerEnabled = !island.targetState && !pageOnStage,
                onExpandedChange = { navigate(if (it) CalculatorOverlay.MENU else null) },
                onSelect = navigate, onDragHaptic = dragHaptic,
                modifier = Modifier.fillMaxSize().safeDrawingPadding())
        }
    }
    val shown = page ?: lastPage
    if (pageOnStage && shown != null) {
        val presentation = remember(reveal, page != null) { PanelPresentation(reveal, page != null) }
        CompositionLocalProvider(LocalPanelPresentation provides presentation) {
            Box(Modifier.fillMaxSize().coveredByOverlay(page == null)) { pageContent(shown, pageSource) }
        }
    }
}
