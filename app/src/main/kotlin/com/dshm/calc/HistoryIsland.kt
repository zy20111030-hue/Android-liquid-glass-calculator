package com.dshm.calc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur as blurModifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch

private val IslandHeight = 40.dp
private val IslandPadH = 14.dp
private val IslandIcon = 15.dp
private val IslandGap = 8.dp
private val EdgeFade = 12.dp
private val PanelRowH = 44.dp
private val PanelHeaderH = 36.dp
private val PanelPadV = 10.dp

private fun lerpDp(a: Dp, b: Dp, t: Float): Dp = a + (b - a) * t.coerceIn(0f, 1f)
private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

/**
 * 灵动岛：液态玻璃材质，收缩态常驻最近一条历史；点击后经过
 * 按压 → 收成圆形 → 放大为面板 的非线性形变，展开历史列表。
 */
@Composable
internal fun HistoryIsland(
    history: List<HistoryItem>,
    backdrop: Backdrop,
    surfaceColor: Color,
    contentColor: Color,
    collapsedMaxWidth: Dp,
    expanded: Boolean,
    onToggle: () -> Unit,
    expandedWidth: Dp,
    maxPanelHeight: Dp,
    onClear: () -> Unit,
    onUseResult: (String) -> Unit
) {

    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        pressScale.animateTo(if (pressed) 0.94f else 1f, tween(90))
    }

    val p = progress.value
    val latest = history.firstOrNull()
    val text = latest?.let { "${it.expr} = ${it.result}" } ?: ""

    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 13.sp, color = contentColor, fontWeight = FontWeight.Medium)
    val textW = remember(text, style) { measurer.measure(AnnotatedString(text), style).size.width }
    val density = LocalDensity.current
    val textDp = with(density) { textW.toDp() }
    val tail = IslandIcon + IslandGap + IslandPadH * 2
    val pillW = (textDp + tail).coerceIn(72.dp, collapsedMaxWidth)
    val avail = (pillW - tail).coerceAtLeast(0.dp)
    val overflow = (textDp - avail).coerceAtLeast(0.dp)

    val marquee = rememberInfiniteTransition(label = "island-marquee")
    val shift by marquee.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = if (overflow > 0.dp) 5200 else 4000
                0f at 0
                0f at 900
                1f at 3900
                1f at 4700
                0f at 5200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "shift"
    )

    // 展开高度：按真实行数计算（0 条空态也算一行高度），上限 maxPanelHeight
    val rows = history.size
    val contentH = PanelHeaderH + PanelRowH * rows.coerceAtLeast(1).toFloat() + PanelRowH + PanelPadV * 2f
    val targetH = contentH.coerceAtMost(maxPanelHeight)

    // 胶囊 → 圆 → 面板
    val circleP = 0.30f
    val w: Dp
    val h: Dp
    val radius: Dp
    if (p <= circleP) {
        val t = p / circleP
        w = lerpDp(pillW, IslandHeight, t)
        h = IslandHeight
        radius = IslandHeight / 2
    } else {
        val t = (p - circleP) / (1f - circleP)
        w = lerpDp(IslandHeight, expandedWidth, t)
        h = lerpDp(IslandHeight, targetH, t)
        radius = lerpDp(IslandHeight / 2, 30.dp, t)
    }
    val shape: androidx.compose.ui.graphics.Shape = com.kyant.shapes.RoundedRectangle(
        radius, com.kyant.shapes.RoundedCornerStyle.Continuous
    )
    // 形变过程中岛本身高斯模糊，归位时平滑消失
    val morphBlur = (kotlin.math.sin(p * Math.PI).toFloat() * 9f).dp

    // 展开状态由外部控制，弹簧动画天然支持中途打断
    LaunchedEffect(expanded) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = 0.86f,
                stiffness = 150f,
                visibilityThreshold = 0.001f
            )
        )
    }

    val collapsedAlpha = (1f - p / 0.35f).coerceIn(0f, 1f)
    val expandedAlpha = ((p - 0.55f) / 0.45f).coerceIn(0f, 1f)

    Box(
        Modifier
            .width(w)
            .height(h)
            .blurModifier(morphBlur, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                highlight = null,
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens((11f + 6f * p).dp.toPx(), (17f + 8f * p).dp.toPx())
                },
                layerBlock = {},
                onDrawSurface = { drawRect(surfaceColor) }
            )
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val rimLight = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.58f),
                    0.34f to Color.White.copy(alpha = 0.22f),
                    0.66f to Color.Transparent,
                    1f to Color.White.copy(alpha = 0.32f),
                    end = Offset(size.width, size.height)
                )
                val bevelShade = Brush.linearGradient(
                    0f to Color(0xFF172338).copy(alpha = 0.16f),
                    0.5f to Color.Transparent,
                    1f to Color(0xFF172338).copy(alpha = 0.22f),
                    end = Offset(size.width, size.height)
                )
                onDrawBehind {
                    drawOutline(outline, bevelShade, style = Stroke(3.4.dp.toPx()))
                    drawOutline(outline, rimLight, style = Stroke(1.7.dp.toPx()))
                }
            }
            .clickable(interactionSource = interaction, indication = null) { onToggle() }
    ) {
        // 收缩内容
        if (collapsedAlpha > 0.01f) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = collapsedAlpha }
                    .padding(horizontal = IslandPadH),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(IslandIcon)) {
                    val stroke = 1.35.dp.toPx()
                    drawCircle(contentColor, radius = size.minDimension * 0.42f, style = Stroke(stroke))
                    drawLine(
                        contentColor, center,
                        androidx.compose.ui.geometry.Offset(center.x, size.height * 0.26f),
                        stroke, StrokeCap.Round
                    )
                    drawLine(
                        contentColor, center,
                        androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.58f),
                        stroke, StrokeCap.Round
                    )
                }
                Spacer(Modifier.width(IslandGap))
                Box(
                    Modifier
                        .weight(1f)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val f = EdgeFade.toPx() / size.width.coerceAtLeast(1f)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Black,
                                    f.coerceIn(0f, 0.4f) to Color.Transparent,
                                    (1f - f).coerceIn(0.6f, 1f) to Color.Transparent,
                                    1f to Color.Black
                                ),
                                blendMode = BlendMode.DstOut
                            )
                        }
                ) {
                    if (overflow > 0.dp) {
                        Box(Modifier.offset(x = -overflow * shift)) {
                            androidx.compose.foundation.text.BasicText(text, style = style, maxLines = 1, softWrap = false)
                        }
                    } else {
                        androidx.compose.foundation.text.BasicText(text, style = style, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        // 展开内容
        if (expandedAlpha > 0.01f) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = expandedAlpha
                        val s = 0.94f + 0.06f * expandedAlpha
                        scaleX = s
                        scaleY = s
                    }
                    .padding(horizontal = 14.dp, vertical = PanelPadV)
            ) {
                Row(
                    Modifier.fillMaxWidth().height(PanelHeaderH),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.text.BasicText(
                        "历史",
                        style = TextStyle(color = contentColor.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
                if (history.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(PanelRowH), contentAlignment = Alignment.CenterStart) {
                        androidx.compose.foundation.text.BasicText(
                            "暂无历史",
                            style = TextStyle(color = contentColor.copy(alpha = 0.45f), fontSize = 13.sp)
                        )
                    }
                } else {
                    history.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(PanelRowH)
                                .clickable { onUseResult(item.result) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                "${item.expr} = ${item.result}",
                                style = TextStyle(color = contentColor, fontSize = 13.sp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.size(32.dp).clickable { onUseResult(item.result); onToggle() },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(Modifier.size(15.dp)) {
                                    val s = 1.4.dp.toPx()
                                    val r = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx())
                                    drawRoundRect(
                                        contentColor.copy(alpha = 0.75f),
                                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.10f),
                                        size = androidx.compose.ui.geometry.Size(size.width * 0.66f, size.height * 0.66f),
                                        cornerRadius = r, style = Stroke(s)
                                    )
                                    drawRoundRect(
                                        contentColor.copy(alpha = 0.75f),
                                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.10f, size.height * 0.26f),
                                        size = androidx.compose.ui.geometry.Size(size.width * 0.66f, size.height * 0.66f),
                                        cornerRadius = r, style = Stroke(s)
                                    )
                                }
                            }
                        }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(PanelRowH).clickable { onClear() },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicText(
                        "清空历史",
                        style = TextStyle(color = contentColor.copy(alpha = 0.55f), fontSize = 13.sp)
                    )
                }
            }
        }
    }
}

