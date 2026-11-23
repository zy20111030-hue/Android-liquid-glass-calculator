package com.dshm.calc

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop

internal sealed interface CalculatorMenuAction : java.io.Serializable {
    data object ScientificCalculator : CalculatorMenuAction
}

internal enum class CalculatorOverlay : CalculatorMenuAction {
    MENU, HISTORY, THEME, WALLPAPER, WALLPAPER_PERMISSION, VIBRATION, KEY_SHAPE, LANGUAGE, INTRO, ABOUT
}

internal val LocalPanelMaterial = staticCompositionLocalOf<LayerBackdrop> { error("Missing OverlayPanel material") }

internal fun menuItems(str: Str, shapeTitle: String, introTitle: String, manualScientific: Boolean) = listOf(
    GlassMenuItem(CalculatorMenuAction.ScientificCalculator, if (manualScientific) str.followOrientation else str.scientificCalculator),
    GlassMenuItem(CalculatorOverlay.THEME, str.darkMode),
    GlassMenuItem(CalculatorOverlay.WALLPAPER, str.wallpaper),
    GlassMenuItem(CalculatorOverlay.VIBRATION, str.vibration),
    GlassMenuItem(CalculatorOverlay.KEY_SHAPE, shapeTitle),
    GlassMenuItem(CalculatorOverlay.LANGUAGE, str.language),
    GlassMenuItem(CalculatorOverlay.INTRO, introTitle),
    GlassMenuItem(CalculatorOverlay.ABOUT, str.about)
)

/** The panel and its source share a window, so backdrop coordinates also match after rotation. */
@Composable
internal fun OverlayPanel(
    title: String, backdrop: Backdrop, dark: Boolean, onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val presentation = LocalPanelPresentation.current
    val material = rememberLayerBackdrop()
    val shape = RoundedCornerShape(24.dp)
    val dimAmount = if (dark) 0.22f else 0.14f
    val dim = rememberCanvasBackdrop { drawRect(Color.Black.copy(alpha = presentation.progress.value * dimAmount)) }
    val scene = rememberCombinedBackdrop(backdrop, dim)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        OverlayScrim(presentation.progress.value * dimAmount, onDismiss)
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center) {
            Box(
                Modifier.widthIn(max = 420.dp).fillMaxWidth()
                    .offset { IntOffset(0, presentation.offsetY.value.dp.roundToPx()) }
                    .graphicsLayer { alpha = presentation.progress.value }
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = title; dismiss { onDismiss(); true } }
            ) {
                Spacer(Modifier.matchParentSize().overlayGlass(scene, shape, dark, exportedBackdrop = material))
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp)) {
                    CompositionLocalProvider(LocalPanelMaterial provides material) { content() }
                }
            }
        }
    }
}

@Composable
internal fun PanelHeading(text: String, dark: Boolean) {
    BasicText(text, Modifier.padding(start = 5.dp, end = 5.dp, top = 3.dp, bottom = 10.dp),
        style = TextStyle(color = overlayInk(dark), fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
}

@Composable
internal fun PanelButton(text: String, dark: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    chosen: Boolean? = null, secondary: Boolean = false,
    feedback: OverlayButtonFeedback = OverlayButtonFeedback.Glass) {
    val shape = RoundedCornerShape(24.dp)
    val material = LocalPanelMaterial.current
    val accent = if (dark) Color(0xFFB8D9FF) else Color(0xFF104981)
    val fill = when (chosen) {
        true -> if (dark) Color(0xFF255D91).copy(alpha = 0.82f) else Color(0xFFC2DEFF).copy(alpha = 0.85f)
        else -> if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.32f)
    }
    OverlayPressButton(
        material = material, dark = dark, shape = shape, onClick = onClick,
        enabled = LocalPanelPresentation.current.interactive,
        modifier = modifier.heightIn(min = 48.dp).then(if (chosen != null) Modifier.semantics { selected = chosen } else Modifier),
        facePadding = PaddingValues(vertical = 2.dp), fill = fill, feedback = feedback,
        rim = overlayInk(dark).copy(alpha = if (chosen == true) 0.25f else 0.10f)
    ) {
        BasicText(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = TextStyle(color = if (secondary || chosen == true) accent else overlayInk(dark),
            fontSize = 16.sp, fontWeight = if (chosen == true) FontWeight.SemiBold else FontWeight.Normal))
    }
}

@Composable
internal fun SettingsPanel(
    page: CalculatorOverlay, title: String, str: Str, intro: FeatureIntro, shapeLabels: Pair<String, String>,
    current: String?, dark: Boolean, backdrop: Backdrop, onChoose: (String) -> Unit,
    onDismiss: () -> Unit
) = key(page) {
    val choices = when (page) {
        CalculatorOverlay.THEME -> listOf("system" to str.followSystem, "wallpaper" to str.wallpaperColor, "manual" to str.none)
        CalculatorOverlay.VIBRATION -> listOf("off" to str.off, "standard" to str.standard, "enhanced" to str.enhanced)
        CalculatorOverlay.KEY_SHAPE -> listOf("circle" to shapeLabels.first, "rect" to shapeLabels.second)
        CalculatorOverlay.LANGUAGE -> listOf("zh-CN" to "简体中文", "zh-TW" to "繁體中文", "en" to "English")
        else -> emptyList()
    }
    OverlayPanel(title, backdrop, dark, onDismiss) {
        // Scroll the entire card so every action is reachable even with a large accessibility font.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            PanelHeading(title, dark)
            choices.forEach { (value, label) ->
                PanelButton(label, dark, { onChoose(value) }, chosen = value == current, modifier = Modifier.fillMaxWidth())
            }
            val lines = when (page) {
                CalculatorOverlay.INTRO -> intro.lines
                CalculatorOverlay.ABOUT -> listOf(str.version)
                else -> emptyList()
            }
            lines.forEach { line ->
                BasicText(line, Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                    style = TextStyle(color = overlayInk(dark), fontSize = 15.sp, lineHeight = 21.sp))
            }
            val finish = page == CalculatorOverlay.ABOUT || page == CalculatorOverlay.INTRO
            PanelButton(if (finish) str.ok else str.cancel, dark, onDismiss, secondary = true,
                modifier = Modifier.align(Alignment.End))
        }
    }
}
