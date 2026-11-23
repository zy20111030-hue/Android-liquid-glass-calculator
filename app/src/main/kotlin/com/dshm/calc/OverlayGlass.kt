package com.dshm.calc

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur as blurLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

internal fun overlaySurface(dark: Boolean) =
    if (dark) Color(0xFF171D28).copy(alpha = 0.62f) else Color(0xFFF8FAFF).copy(alpha = 0.75f)

internal fun overlayInk(dark: Boolean) = if (dark) Color(0xFFF5F6F9) else Color(0xFF171C25)

// Blur the moving silhouette separately from the glass sampling the scene underneath it.
internal fun Modifier.motionBlur(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= 31 && radius > 0.1.dp) blurLayer(radius, BlurredEdgeTreatment.Unbounded) else this

@Composable
internal fun BoxScope.OverlayScrim(alpha: Float, onDismiss: () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = alpha))
        .pointerInput(Unit) { detectTapGestures { dismiss() } })
}

internal fun Modifier.overlayGlass(
    backdrop: Backdrop, shape: Shape, dark: Boolean,
    blurRadius: Dp = 5.dp, refractionHeight: Dp = 12.dp, refractionAmount: Dp = 17.dp,
    exportedBackdrop: LayerBackdrop? = null, shadow: Shadow = Shadow.Default
): Modifier = drawBackdrop(
    backdrop = backdrop, shape = { shape }, highlight = null, shadow = { shadow }, exportedBackdrop = exportedBackdrop,
    effects = { vibrancy(); blur(blurRadius.toPx()); lens(refractionHeight.toPx(), refractionAmount.toPx()) },
    onDrawSurface = { drawRect(overlaySurface(dark)) }
).clip(shape).drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) 0.46f else 0.82f),
        Color.White.copy(alpha = 0.08f), Color(0xFF8796AC).copy(alpha = 0.38f)),
        end = Offset(size.width, size.height))
    onDrawWithContent {
        drawContent()
        drawOutline(outline, rim, style = Stroke(1.5.dp.toPx()))
    }
}
