package com.dshm.calc

import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop

private val basicRows = listOf(
    listOf("AC", "÷", "×", "⌫"),
    listOf("7", "8", "9", "-"),
    listOf("4", "5", "6", "+")
)
private val bottomRows = listOf(listOf("1", "2", "3"), listOf("%", "0", "."))
private val memoryKeys = listOf("MC", "M+", "M−", "MR")
private val scientificRows = listOf(
    listOf("(", ")", "1/x", "□/□"),
    listOf("x²", "x³", "xʸ", "10ˣ"),
    listOf("x!", "√", "ʸ√x", "∛"),
    listOf("e", "ln", "log", "eˣ"),
    listOf("sin", "cos", "tan", "|x|"),
    listOf("Inv", "Deg", "π", "⇥")
)

@Composable
internal fun CalculatorKeypad(
    session: ExpressionSession, str: Str, backdrop: Backdrop, buttonSurface: Color, fg: Color, equalsInk: Color,
    onEquals: () -> Unit, onToggleTheme: () -> Unit, landscape: Boolean, modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        // Cap the actual glyph height as well as sp, including on short windows with enlarged system fonts.
        val labelLimit = if (landscape) with(density) { ((maxHeight / 6 - 4.dp) * 0.70f).toSp() } else null
        val compactSize = labelLimit?.let { minOf(24f, it.value).coerceAtLeast(1f).sp }
        val key: @Composable (String, Modifier) -> Unit = { symbol, cell ->
            val action: () -> Unit = {
                when (symbol) {
                    "AC" -> session.clear()
                    "." -> session.insertDecimal()
                    "⌫" -> session.backspace()
                    "=" -> onEquals()
                    "÷" -> {
                        if (!landscape && session.selection == null && session.editor.inFraction()) session.nextField()
                        else session.insertOperator("÷")
                    }
                    "+", "-", "×" -> session.insertOperator(symbol)
                    "(" -> session.insertParentheses()
                    ")" -> session.closeParenthesis()
                    "□/□" -> session.scientificFraction()
                    "⇥" -> session.nextField()
                    "1/x" -> session.power(-1)
                    "x²" -> session.power(2)
                    "x³" -> session.power(3)
                    "xʸ" -> session.power()
                    "10ˣ" -> session.exponential(FItem.Num("10"))
                    "eˣ" -> session.exponential(FItem.Constant(ConstantKind.E))
                    "√" -> session.root()
                    "ʸ√x" -> session.root(null)
                    "∛" -> session.root(3)
                    "x!" -> session.function(FunctionKind.FACTORIAL)
                    "%" -> session.function(FunctionKind.PERCENT)
                    "ln" -> session.function(FunctionKind.LN)
                    "log" -> session.function(FunctionKind.LOG)
                    "sin" -> session.function(if (session.inverse) FunctionKind.ASIN else FunctionKind.SIN)
                    "cos" -> session.function(if (session.inverse) FunctionKind.ACOS else FunctionKind.COS)
                    "tan" -> session.function(if (session.inverse) FunctionKind.ATAN else FunctionKind.TAN)
                    "|x|" -> session.function(FunctionKind.ABS)
                    "π" -> session.constant(ConstantKind.PI)
                    "e" -> session.constant(ConstantKind.E)
                    "Inv" -> session.toggleInverse()
                    "Deg" -> session.toggleAngleMode()
                    "MC" -> session.clearMemory()
                    "M+" -> session.addToMemory(false)
                    "M−" -> session.addToMemory(true)
                    "MR" -> session.recallMemory()
                    else -> session.insertDigit(symbol.single())
                }
            }
            val trig = symbol == "sin" || symbol == "cos" || symbol == "tan"
            val displayed = when {
                symbol == "Deg" && session.angleMode == AngleMode.RAD -> "Rad"
                trig && session.inverse -> symbol + "⁻¹"
                else -> symbol
            }
            val hold: (() -> Unit)? = when {
                symbol == "=" -> onToggleTheme
                !landscape && symbol == "AC" -> session::insertParentheses
                !landscape && symbol == "÷" -> session::insertFraction
                else -> null
            }
            val label = when (symbol) {
                "□/□" -> str.insertFraction
                "⇥" -> str.nextField
                "÷" -> str.divide
                "×" -> str.multiply
                "-" -> str.subtract
                "+" -> str.add
                "=" -> str.equalsLabel
                else -> displayed
            }
            val textSize = when {
                compactSize == null -> null
                symbol in memoryKeys || displayed.length >= 3 -> minOf(19f, compactSize.value).sp
                else -> compactSize
            }
            val content: (@Composable () -> Unit)? = when (symbol) {
                "AC" -> {
                    {
                        BasicText("AC", maxLines = 1, softWrap = false, style = TextStyle(color = fg,
                            fontFamily = CalculatorFont, fontSize = compactSize ?: 22.sp, fontWeight = FontWeight.Medium))
                    }
                }
                "⌫" -> {
                    {
                        val width = with(density) { (compactSize ?: 32.sp).toDp() }
                        Image(rememberVectorPainter(BackspaceIcon), str.backspace, Modifier.size(width, width * 0.75f),
                            colorFilter = ColorFilter.tint(fg))
                    }
                }
                "sin", "cos", "tan", "Deg" -> {
                    { ModeKeyLabel(symbol, displayed, TextStyle(color = fg, fontFamily = CalculatorFont, fontSize = textSize ?: 19.sp)) }
                }
                else -> null
            }
            val surface = if (symbol == "Inv" || symbol == "MR") {
                val active = if (symbol == "Inv") session.inverse else session.memory != null
                val mix by animateFloatAsState(if (active) 1f else 0f, tween(145), label = "Scientific key selection")
                lerp(buttonSurface, Color(0xFF439BFF).copy(alpha = 0.35f), mix)
            } else buttonSurface
            val keyModifier = if (symbol == "Inv") cell.semantics { selected = session.inverse } else cell
            GlassButton(displayed, backdrop, keyModifier, action, hold, surface, if (symbol == "=") equalsInk else fg,
                strongHaptic = symbol == "AC" || symbol == "=",
                onClickLabel = when (symbol) {
                    "AC" -> str.clear
                    "(" -> str.insertParen
                    "|x|" -> str.absoluteValue
                    else -> null
                },
                onLongClickLabel = if (hold == null) null else when (symbol) {
                    "AC" -> str.insertParen
                    "÷" -> str.insertFraction
                    else -> null
                },
                symbolLabel = label, compact = landscape, labelSize = textSize, content = content)
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    scientificRows.forEach { symbols ->
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            symbols.forEach { key(it, Modifier.weight(1f).fillMaxHeight()) }
                        }
                    }
                }
                ArithmeticKeys(Modifier.weight(1f).fillMaxHeight(), true, key)
            }
        } else ArithmeticKeys(Modifier.fillMaxWidth(), false, key)
    }
}

@Composable
private fun ModeKeyLabel(base: String, current: String, style: TextStyle) {
    val progress by animateFloatAsState(if (current == base) 0f else 1f, tween(150), label = "Scientific key label")
    // The spoken text switches with the action, even when the visual is between two states.
    val semantics = Modifier.clearAndSetSemantics { text = AnnotatedString(current) }
    if (base == "Deg") {
        val showingRadians = progress >= 0.5f
        val alpha = if (showingRadians) 2f * progress - 1f else 1f - 2f * progress
        Box(semantics, contentAlignment = Alignment.Center) {
            BasicText(if (showingRadians) "Rad" else "Deg", Modifier.graphicsLayer {
                this.alpha = alpha
                translationY = (if (showingRadians) 1f else -1f) * (1f - alpha) * 1.5.dp.toPx()
            }, maxLines = 1, softWrap = false, style = style)
        }
        return
    }
    // Pin the function name at the center whenever space permits. The suffix uses the same
    // baseline and reserves its advance in both modes, including on narrow landscape keys.
    Layout(modifier = semantics, content = {
        BasicText(base, maxLines = 1, softWrap = false, style = style)
        BasicText("⁻¹", Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 1.5.dp.toPx()
        }, maxLines = 1, softWrap = false, style = style)
    }) { children, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val name = children[0].measure(loose)
        val suffix = children[1].measure(loose.copy(maxWidth = (loose.maxWidth - name.width).coerceAtLeast(0)))
        val width = (name.width + suffix.width * 2).coerceIn(constraints.minWidth, constraints.maxWidth)
        val x = minOf((width - name.width) / 2, width - name.width - suffix.width)
        val nameBaseline = name[FirstBaseline].takeUnless { it == AlignmentLine.Unspecified } ?: name.height
        val suffixBaseline = suffix[FirstBaseline].takeUnless { it == AlignmentLine.Unspecified } ?: suffix.height
        val baseline = maxOf(nameBaseline, suffixBaseline)
        val nameY = baseline - nameBaseline
        val suffixY = baseline - suffixBaseline
        val height = maxOf(nameY + name.height, suffixY + suffix.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            name.place(x, nameY)
            suffix.place(x + name.width, suffixY)
        }
    }
}

/** Both orientations use the same arithmetic rows, including the tall equals key. */
@Composable
private fun ArithmeticKeys(modifier: Modifier, landscape: Boolean, key: @Composable (String, Modifier) -> Unit) {
    Column(modifier) {
        if (landscape) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                memoryKeys.forEach { key(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
        basicRows.forEach { symbols ->
            Row(if (landscape) Modifier.weight(1f) else Modifier) {
                symbols.forEach { symbol ->
                    val cell = Modifier.weight(1f)
                    key(symbol, if (landscape) cell.fillMaxHeight() else cell.aspectRatio(1f))
                }
            }
        }
        Row(if (landscape) Modifier.weight(2f) else Modifier) {
            Column(Modifier.weight(3f)) {
                bottomRows.forEach { symbols ->
                    Row(if (landscape) Modifier.weight(1f) else Modifier) {
                        symbols.forEach { symbol ->
                            val cell = Modifier.weight(1f)
                            key(symbol, if (landscape) cell.fillMaxHeight() else cell.aspectRatio(1f))
                        }
                    }
                }
            }
            val equals = Modifier.weight(1f)
            key("=", if (landscape) equals.fillMaxHeight() else equals.aspectRatio(0.5f))
        }
    }
}

private val BackspaceIcon = ImageVector.Builder("Backspace", 32.dp, 24.dp, 32f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineJoin = StrokeJoin.Round,
        strokeLineCap = StrokeCap.Round) {
        moveTo(11f, 2.3f)
        lineTo(27f, 2.3f)
        quadTo(30f, 2.3f, 30f, 5.3f)
        lineTo(30f, 18.7f)
        quadTo(30f, 21.7f, 27f, 21.7f)
        lineTo(11f, 21.7f)
        lineTo(2f, 12f)
        close()
        moveTo(15f, 8f)
        lineTo(23f, 16f)
        moveTo(23f, 8f)
        lineTo(15f, 16f)
    }
}.build()
