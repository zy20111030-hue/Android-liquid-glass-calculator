package com.dshm.calc

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import java.util.IdentityHashMap
import kotlin.math.abs

/** A box extends above and below the common mathematical axis of its row. */
internal data class MathBox(val width: Float, val above: Float, val below: Float) {
    val height: Float get() = above + below
}

/** The caret advances and the drawing come from a single shaped text run. */
internal class FormulaGlyph(
    val box: MathBox,
    val caretStops: List<Float>,
    val draw: DrawScope.() -> Unit
)

// A single Android text layout has a maximum line width, even with soft wrapping disabled.
internal const val FORMULA_RUN_LENGTH = 128

internal fun measureExpression(
    items: List<FItem>, measurer: TextMeasurer, style: TextStyle, spaceOperators: Boolean = false
): ExpressionLayout {
    fun shape(s: String) = measurer.measure(AnnotatedString(s), style, softWrap = false, maxLines = 1)
    val zero = shape("0")
    val density = zero.layoutInput.density
    val em = if (style.fontSize.isSp) with(density) { style.fontSize.toPx() } else zero.size.height.toFloat()
    val axisAboveBaseline = if (style.fontSize.isSp) em * 0.28f
        else zero.firstBaseline - zero.size.height / 2f
    return ExpressionLayout(items, density.density, if (spaceOperators) em * 0.2f else 0f) { text ->
        val run = if (text == "0") zero else shape(text)
        val above = (run.firstBaseline - axisAboveBaseline).coerceIn(0f, run.size.height.toFloat())
        FormulaGlyph(
            MathBox(run.size.width.toFloat(), above, run.size.height - above),
            (0..text.length).map { run.getHorizontalPosition(it, usePrimaryDirection = true) }
        ) { drawText(run, topLeft = Offset.Zero) }
    }
}

internal class ExpressionLayout(
    items: List<FItem>,
    density: Float,
    private val binaryOperatorPadding: Float = 0f,
    private val shape: (String) -> FormulaGlyph
) {
    // Coordinates in a measured node are relative to its left edge and mathematical axis.
    // A child scale applies to its entire subtree, including hit targets and insertion stops.
    private class MeasuredRow(val items: List<FItem>, val nodes: List<Node>, val box: MathBox)
    private class Field(val role: SlotRole, val row: MeasuredRow, val x: Float, val axis: Float, val scale: Float = 1f)
    private class Run(val glyph: FormulaGlyph, val x: Float, val top: Float, val scaleX: Float = 1f, val scaleY: Float = scaleX)
    private class Rule(val from: Offset, val to: Offset, val thickness: Float)
    private class Edge(val role: SlotRole? = null, val end: Boolean = false)
    private class Surface(val bounds: Rect, val left: Edge, val right: Edge)
    private class Node(
        val box: MathBox, val marks: List<Run> = emptyList(), val fields: List<Field> = emptyList(),
        val rules: List<Rule> = emptyList(), val surfaces: List<Surface> = emptyList()
    )
    private class Row(val path: List<SlotStep>, val top: Float, val scale: Float) {
        val boundaries = mutableListOf<Float>()
        val digits = mutableMapOf<Int, List<Float>>()
    }
    private class Position(val row: Row, val index: Int)
    private class Hit(
        val bounds: Rect, val token: FItem?, val before: Position, val after: Position = before,
        val digitStops: List<Float> = emptyList()
    )

    private val glyphs = mutableMapOf<String, FormulaGlyph>()
    private val rows = IdentityHashMap<List<FItem>, Row>()
    private val runs = mutableListOf<Run>()
    private val rules = mutableListOf<Rule>()
    private val placeholders = mutableListOf<Rect>()
    private val hits = mutableListOf<Hit>()
    private val before = Edge()
    private val after = Edge(end = true)
    private val caretWidth = 2f * density
    private val fractionPad = 1.5f * density
    private val halfGap = density
    private val ruleThickness = 1.5f * density
    private val line = glyph("0").box
    private val emptyWidth = maxOf(16f * density, line.width * 0.75f)
    val width: Float
    val height: Float

    init {
        val measured = measureRow(items, 1f)
        width = measured.box.width + caretWidth
        height = measured.box.height
        val row = placeRow(measured, 0f, measured.box.above, 1f, emptyList())
        hits += Hit(Rect(measured.box.width, 0f, width, height), items.lastOrNull(), Position(row, items.size))
    }

    // Both minus characters occupy one UTF-16 unit. Caret offsets still address the source text.
    private fun glyph(text: String) = glyphs.getOrPut(text) { shape(text.replace('-', '−')) }

    private fun measureRow(items: List<FItem>, scale: Float): MeasuredRow {
        val nodes = items.mapIndexed { index, item ->
            when (item) {
                is FItem.Num -> numberNode(item.text)
                is FItem.Constant -> textNode(item.kind.label)
                is FItem.Op -> textNode(item.sym, if (index > 0 && items[index - 1] !is FItem.Op) binaryOperatorPadding else 0f)
                is FItem.FracItem -> fraction(item, scale)
                is FItem.ParenItem -> argument(measureRow(item.inner, scale), SlotRole.CONTENT, parentheses = true)
                is FItem.Power -> power(item, scale)
                is FItem.Root -> radical(item, scale)
                is FItem.Function -> {
                    val row = measureRow(item.argument, scale)
                    when {
                        item.kind == FunctionKind.ABS -> absoluteValue(row)
                        item.kind.postfix -> argument(row, SlotRole.ARGUMENT, parentheses = requiresVisibleGrouping(item.argument), suffix = item.kind.label)
                        else -> argument(row, SlotRole.ARGUMENT, parentheses = true, prefix = item.kind.label)
                    }
                }
            }
        }
        val box = MathBox(if (nodes.isEmpty()) emptyWidth else nodes.fold(0f) { w, n -> w + n.box.width },
            maxOf(line.above, nodes.maxOfOrNull { it.box.above } ?: 0f),
            maxOf(line.below, nodes.maxOfOrNull { it.box.below } ?: 0f))
        return MeasuredRow(items, nodes, box)
    }

    private fun textNode(text: String, padding: Float = 0f): Node {
        val mark = glyph(text)
        return Node(mark.box.copy(width = mark.box.width + padding * 2f), listOf(Run(mark, padding, -mark.box.above)))
    }

    private fun numberNode(text: String): Node {
        if (text.length <= FORMULA_RUN_LENGTH) return textNode(text)
        var x = 0f
        val marks = text.chunked(FORMULA_RUN_LENGTH) { chunk ->
            val mark = glyph(chunk.toString())
            val run = Run(mark, x, -mark.box.above)
            x += mark.box.width
            run
        }
        return Node(MathBox(x, marks.maxOf { it.glyph.box.above }, marks.maxOf { it.glyph.box.below }), marks)
    }

    private fun fraction(item: FItem.FracItem, scale: Float): Node {
        val num = measureRow(item.num, scale)
        val den = measureRow(item.den, scale)
        val box = MathBox(maxOf(num.box.width, den.box.width) + fractionPad * 2f,
            num.box.height + halfGap, den.box.height + halfGap)
        val fields = listOf(Field(SlotRole.NUMERATOR, num, (box.width - num.box.width) / 2f, -halfGap - num.box.below),
            Field(SlotRole.DENOMINATOR, den, (box.width - den.box.width) / 2f, halfGap + den.box.above))
        val surfaces = listOf(Surface(Rect(0f, -halfGap, box.width, halfGap), before, after),
            Surface(Rect(0f, -box.above, fractionPad, box.below), before, before),
            Surface(Rect(box.width - fractionPad, -box.above, box.width, box.below), after, after))
        return Node(box, fields = fields, rules = listOf(Rule(Offset.Zero, Offset(box.width, 0f), ruleThickness)), surfaces = surfaces)
    }

    private fun absoluteValue(value: MeasuredRow): Node {
        val stroke = maxOf(ruleThickness, line.height * 0.038f)
        val side = maxOf(stroke * 2.5f, line.height * 0.22f)
        val box = value.box.copy(width = value.box.width + side * 2f)
        val inset = maxOf(stroke / 2f, line.height * 0.09f)
        fun bar(x: Float) = Rule(Offset(x, -box.above + inset), Offset(x, box.below - inset), stroke)
        val field = Field(SlotRole.ARGUMENT, value, side, 0f)
        val edges = listOf(Surface(Rect(0f, -box.above, side, box.below), before, Edge(SlotRole.ARGUMENT)),
            Surface(Rect(box.width - side, -box.above, box.width, box.below), Edge(SlotRole.ARGUMENT, end = true), after))
        return Node(box, fields = listOf(field), rules = listOf(bar(side / 2f), bar(box.width - side / 2f)), surfaces = edges)
    }

    // Parentheses stretch only vertically. A function's name and postfix signs stay at text size.
    private fun argument(
        value: MeasuredRow, role: SlotRole, parentheses: Boolean, prefix: String = "", suffix: String = ""
    ): Node {
        val marks = mutableListOf<Run>()
        val surfaces = mutableListOf<Surface>()
        val innerStart = Edge(role)
        val innerEnd = Edge(role, end = true)
        val opening = if (parentheses) glyph("(") else null
        val closing = if (parentheses) glyph(")") else null
        val above = maxOf(value.box.above, opening?.box?.above ?: 0f, closing?.box?.above ?: 0f)
        val below = maxOf(value.box.below, opening?.box?.below ?: 0f, closing?.box?.below ?: 0f)
        var x = 0f
        fun mark(glyph: FormulaGlyph, left: Edge, right: Edge, stretch: Boolean = false) {
            val top = if (stretch) -above else -glyph.box.above
            val bottom = if (stretch) below else glyph.box.below
            marks += Run(glyph, x, top, scaleY = (bottom - top) / glyph.box.height)
            surfaces += Surface(Rect(x, top, x + glyph.box.width, bottom), left, right)
            x += glyph.box.width
        }
        if (prefix.isNotEmpty()) mark(glyph(prefix), before, innerStart)
        if (opening != null) mark(opening, before, innerStart, stretch = true)
        val field = Field(role, value, x, 0f)
        x += value.box.width
        if (closing != null) mark(closing, innerEnd, after, stretch = true)
        if (suffix.isNotEmpty()) mark(glyph(suffix), innerEnd, after)
        return Node(MathBox(x, above, below), marks, listOf(field), surfaces = surfaces)
    }

    // Use a minimum script size like math typesetting's scriptscript style for deep nesting.
    private fun relativeScriptScale(parent: Float, ratio: Float) = maxOf(parent * ratio, 0.5f) / parent

    private fun power(item: FItem.Power, scale: Float): Node {
        val scriptScale = relativeScriptScale(scale, 0.70f)
        val base = argument(measureRow(item.base, scale), SlotRole.BASE, requiresVisibleGrouping(item.base))
        val exponent = measureRow(item.exponent, scale * scriptScale)
        val gap = line.height * 0.05f
        val rise = maxOf(base.box.above * 0.8f, line.above * 0.50f + exponent.box.below * scriptScale)
        val field = Field(SlotRole.EXPONENT, exponent, base.box.width + gap, -rise, scriptScale)
        val box = MathBox(field.x + exponent.box.width * scriptScale,
            maxOf(base.box.above, rise + exponent.box.above * scriptScale),
            maxOf(base.box.below, exponent.box.below * scriptScale - rise))
        val scriptBottom = field.axis + exponent.box.below * scriptScale
        return Node(box, base.marks, base.fields + field, surfaces = base.surfaces + listOf(
            Surface(Rect(base.box.width, -box.above, field.x, box.below), Edge(SlotRole.BASE, end = true), Edge(SlotRole.EXPONENT)),
            Surface(Rect(field.x, scriptBottom, box.width, box.below), after, after)))
    }

    private fun radical(item: FItem.Root, scale: Float): Node {
        val indexScale = relativeScriptScale(scale, 0.57f)
        val value = measureRow(item.radicand, scale)
        val degree = item.degree?.let { measureRow(it, scale * indexScale) }
        val em = line.height
        val indexWidth = (degree?.box?.width ?: 0f) * indexScale
        val signX = maxOf(0f, indexWidth - em * 0.15f)
        val barX = signX + em * 0.58f
        val valueX = barX + em * 0.09f
        val barY = -value.box.above - em * 0.10f
        val degreeAxis = -value.box.above * 0.55f - (degree?.box?.below ?: 0f) * indexScale
        val box = MathBox(valueX + value.box.width + em * 0.08f,
            maxOf(-barY + ruleThickness / 2f, -degreeAxis + (degree?.box?.above ?: 0f) * indexScale), value.box.below)
        val bottom = value.box.below - ruleThickness / 2f
        val lineStart = Offset(signX + ruleThickness / 2f, -em * 0.06f)
        val shoulder = Offset(signX + em * 0.15f, -em * 0.15f)
        val hook = Offset(signX + em * 0.32f, bottom)
        val top = Offset(barX, barY)
        val strokes = listOf(Rule(lineStart, shoulder, ruleThickness), Rule(shoulder, hook, ruleThickness),
            Rule(hook, top, ruleThickness), Rule(top, Offset(box.width, barY), ruleThickness))
        val fields = mutableListOf(Field(SlotRole.RADICAND, value, valueX, 0f))
        if (degree != null) fields += Field(SlotRole.DEGREE, degree, 0f, degreeAxis, indexScale)
        return Node(box, fields = fields, rules = strokes, surfaces = listOf(
            Surface(Rect(signX, -box.above, valueX, box.below), before, Edge(SlotRole.RADICAND)),
            Surface(Rect(barX, -box.above, box.width, -value.box.above), Edge(SlotRole.RADICAND), after)))
    }

    private fun placeRow(measured: MeasuredRow, left: Float, axis: Float, scale: Float, path: List<SlotStep>): Row {
        val row = Row(path, axis - line.above * scale, scale)
        rows[measured.items] = row
        if (measured.items.isEmpty()) {
            row.boundaries += left + (emptyWidth - caretWidth) * scale / 2f
            val bounds = Rect(left, row.top, left + emptyWidth * scale, axis + line.below * scale)
            hits += Hit(bounds, null, Position(row, 0))
            when (path.lastOrNull()?.role) {
                SlotRole.BASE, SlotRole.EXPONENT, SlotRole.DEGREE, SlotRole.RADICAND, SlotRole.ARGUMENT ->
                    placeholders += bounds.deflate(minOf(bounds.width, bounds.height) * 0.18f)
                else -> Unit
            }
            return row
        }
        var x = left
        for ((index, node) in measured.nodes.withIndex()) {
            val item = measured.items[index]
            val origin = Offset(x, axis)
            fun project(rect: Rect) = Rect(origin + rect.topLeft * scale, origin + rect.bottomRight * scale)
            val bounds = project(Rect(0f, -node.box.above, node.box.width, node.box.below))
            val start = Position(row, index)
            val end = Position(row, index + 1)
            row.boundaries += x
            for (mark in node.marks) {
                runs += Run(mark.glyph, x + mark.x * scale, axis + mark.top * scale, mark.scaleX * scale, mark.scaleY * scale)
            }
            for (rule in node.rules) rules += Rule(origin + rule.from * scale, origin + rule.to * scale, rule.thickness * scale)
            if (item is FItem.Num) {
                val stops = buildList(item.text.length + 1) {
                    for ((part, mark) in node.marks.withIndex()) {
                        val advances = mark.glyph.caretStops
                        // The next run owns the shared insertion point at its leading edge.
                        val count = if (part == node.marks.lastIndex) advances.size else advances.lastIndex
                        for (i in 0 until count) add(x + (mark.x + advances[i]) * scale)
                    }
                }
                row.digits[index] = stops
                hits += Hit(bounds, item, start, digitStops = stops)
            } else if (node.fields.isEmpty()) {
                hits += Hit(bounds, item, start, end)
            } else {
                val children = node.fields.associate { field ->
                    field.role to placeRow(field.row, x + field.x * scale, axis + field.axis * scale, scale * field.scale,
                        path + SlotStep(index, field.role))
                }
                fun position(edge: Edge): Position {
                    val child = children[edge.role] ?: return if (edge.end) end else start
                    return Position(child, if (edge.end) child.boundaries.lastIndex else 0)
                }
                for (surface in node.surfaces) hits += Hit(project(surface.bounds), item, position(surface.left), position(surface.right))
            }
            x = bounds.right
        }
        row.boundaries += x
        return row
    }

    private fun targetAt(point: Offset): Hit? =
        hits.firstOrNull { it.bounds.contains(point) } ?: hits.minByOrNull {
            val dx = point.x - point.x.coerceIn(it.bounds.left, it.bounds.right)
            val dy = point.y - point.y.coerceIn(it.bounds.top, it.bounds.bottom)
            dx * dx + dy * dy
        }

    fun tokenAt(point: Offset): FItem? = targetAt(point)?.token

    fun hitTest(editor: ExpressionEditor, point: Offset) {
        val hit = targetAt(point) ?: return
        val stop = hit.digitStops.indices.minByOrNull { abs(hit.digitStops[it] - point.x) } ?: 0
        val pos = if (point.x < hit.bounds.center.x) hit.before else hit.after
        editor.setCursor(pos.row.path, pos.index, stop)
    }

    fun cursorRect(editor: ExpressionEditor): Rect? {
        val row = rows[editor.slot] ?: return null
        val stops = row.digits[editor.index]
        val x = stops?.get(editor.charOff.coerceIn(0, stops.lastIndex)) ?: row.boundaries.getOrNull(editor.index) ?: return null
        return Rect(x, row.top, x + caretWidth * row.scale, row.top + line.height * row.scale)
    }

    fun DrawScope.drawContent(color: Color, visibleLeft: Float, visibleRight: Float) {
        val bleed = line.height * 0.15f
        for (run in runs) {
            if (run.x > visibleRight + bleed || run.x + run.glyph.box.width * run.scaleX < visibleLeft - bleed) continue
            withTransform({
                translate(run.x, run.top)
                if (run.scaleX != 1f || run.scaleY != 1f) scale(run.scaleX, run.scaleY, Offset.Zero)
            }) { run.glyph.draw(this) }
        }
        for (rule in rules) drawLine(color, rule.from, rule.to, rule.thickness, StrokeCap.Round)
        for (box in placeholders) drawRoundRect(color.copy(alpha = color.alpha * 0.13f), box.topLeft,
            Size(box.width, box.height), CornerRadius(box.height * 0.12f))
    }
}
