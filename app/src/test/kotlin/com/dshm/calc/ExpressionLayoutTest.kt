package com.dshm.calc

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionLayoutTest {
    // Proportional advances and an asymmetric baseline make misplaced caret/math axes detectable.
    private fun glyph(text: String): FormulaGlyph {
        val stops = text.runningFold(0f) { x, c -> x + if (c == '1') 4f else 10f }
        return FormulaGlyph(MathBox(stops.last(), 7f, 3f), stops) {}
    }

    @Test
    fun asymmetricFractionsInOneRowShareAnAxisWithTheOperator() {
        fun simple() = FItem.FracItem(mutableListOf(FItem.Num("8")), mutableListOf(FItem.Num("8")))
        val left = FItem.FracItem(mutableListOf(simple()), mutableListOf(FItem.Num("8")))
        val right = FItem.FracItem(mutableListOf(FItem.Num("8")), mutableListOf(simple()))
        val plus = FItem.Op("+")
        val measured = mutableListOf<String>()
        val layout = ExpressionLayout(listOf(left, plus, right), 1f) { text ->
            measured += text
            glyph(text)
        }

        assertEquals(46f, layout.height, 0.001f)
        assertSame(left, layout.tokenAt(Offset(8f, 23f)))
        assertSame(plus, layout.tokenAt(Offset(21f, 23f)))
        assertSame(right, layout.tokenAt(Offset(34f, 23f)))
        assertEquals(listOf("0", "8", "+"), measured)
    }

    @Test
    fun hitAndCaretUseTheSameProportionalGlyphAdvancesIncludingTheTrailingEdge() {
        val editor = ExpressionEditor()
        editor.root.add(FItem.Num("12"))
        val layout = ExpressionLayout(editor.root, 1f, shape = ::glyph)
        layout.hitTest(editor, Offset(4.1f, 5f))
        assertEquals(1, editor.charOff)
        assertEquals(4f, layout.cursorRect(editor)!!.left, 0.001f)

        layout.hitTest(editor, Offset(layout.width - 0.1f, 5f))
        assertEquals(1, editor.index)
        assertEquals(layout.width, layout.cursorRect(editor)!!.right, 0.001f)
    }

    @Test
    fun longNumberRunsShareTheirBoundaryCaretsAndKeepOneEditableNumber() {
        val first = "1".repeat(127) + "8"
        val second = "1" + "8".repeat(127)
        val number = FItem.Num(first + second + "18")
        val editor = ExpressionEditor().apply { root += number }
        val shaped = mutableListOf<String>()
        val layout = ExpressionLayout(editor.root, 1f) { text ->
            shaped += text
            glyph(text)
        }
        assertEquals(listOf("0", first, second, "18"), shaped)

        val firstEdge = 127 * 4f + 10f
        val secondEdge = firstEdge + 4f + 127 * 10f
        val end = secondEdge + 4f + 10f
        assertEquals(end + 2f, layout.width, 0.001f)
        fun checkCaret(tapX: Float, offset: Int, caretX: Float) {
            val point = Offset(tapX, 5f)
            assertSame(number, layout.tokenAt(point))
            layout.hitTest(editor, point)
            assertEquals(0, editor.index)
            assertEquals(offset, editor.charOff)
            assertEquals(caretX, layout.cursorRect(editor)!!.left, 0.001f)
        }
        checkCaret(firstEdge - 8f, 127, firstEdge - 10f)
        checkCaret(firstEdge - 1f, 128, firstEdge)
        checkCaret(firstEdge + 1f, 128, firstEdge)
        checkCaret(firstEdge + 3f, 129, firstEdge + 4f)
        checkCaret(secondEdge + 0.5f, 256, secondEdge)
        checkCaret(end - 9f, 257, end - 10f)
        checkCaret(end - 0.5f, 258, end)

        layout.hitTest(editor, Offset(end + 1f, 5f))
        assertEquals(1, editor.index)
        assertEquals(layout.width, layout.cursorRect(editor)!!.right, 0.001f)
        layout.hitTest(editor, Offset(firstEdge, 5f))
        editor.insertChar('9')
        assertEquals(first + "9" + second + "18", number.text)
    }

    @Test
    fun parenthesisAndEmptyFractionSlotsUseTheirFullNestedHitAreas() {
        val editor = ExpressionEditor()
        val fraction = FItem.FracItem()
        val group = FItem.ParenItem(mutableListOf(fraction))
        editor.root.add(group)
        val layout = ExpressionLayout(editor.root, 1f, shape = ::glyph)

        assertSame(group, layout.tokenAt(Offset(8f, 1f)))
        layout.hitTest(editor, Offset(8f, 1f))
        assertSame(group.inner, editor.slot)
        layout.hitTest(editor, Offset(19f, 5f))
        assertSame(fraction.num, editor.slot)
        val numerator = layout.cursorRect(editor)!!
        layout.hitTest(editor, Offset(19f, 17f))
        assertSame(fraction.den, editor.slot)
        val denominator = layout.cursorRect(editor)!!
        assertEquals(numerator.left, denominator.left, 0.001f)
        assertEquals(12f, denominator.top - numerator.top, 0.001f)
    }

    @Test
    fun captionSpacingAppliesOnlyToBinaryOperatorsAndRemainsPartOfTheirHitArea() {
        val plus = FItem.Op("+")
        val items = listOf(FItem.Op("-"), FItem.Num("8"), plus, FItem.Op("-"), FItem.Num("8"))
        val plain = ExpressionLayout(items, 1f, shape = ::glyph)
        val spaced = ExpressionLayout(items, 1f, binaryOperatorPadding = 4f, shape = ::glyph)
        assertEquals(plain.width + 8f, spaced.width, 0.001f)
        assertSame(plus, spaced.tokenAt(Offset(21f, 5f)))
        assertSame(plus, spaced.tokenAt(Offset(37f, 5f)))
    }

    @Test
    fun editorAndCaptionShapeMathMinusWhileKeepingSourceOffsetsAndCopyText() {
        val editor = ExpressionEditor()
        val unary = FItem.Op("-")
        val binary = FItem.Op("-")
        val negative = FItem.Num("-12")
        editor.root.addAll(listOf(unary, FItem.Num("8"), binary, negative))

        for (padding in listOf(0f, 4f)) {
            val measured = mutableListOf<String>()
            val layout = ExpressionLayout(editor.root, 1f, padding) { text ->
                measured += text
                glyph(text)
            }
            assertEquals(listOf("0", "−", "8", "−12"), measured)
            val negativeLeft = 30f + 2f * padding
            val afterSign = Offset(negativeLeft + 10f, 5f)
            assertSame(negative, layout.tokenAt(afterSign))
            layout.hitTest(editor, afterSign)
            assertEquals(1, editor.charOff)
            assertEquals(afterSign.x, layout.cursorRect(editor)!!.left, 0.001f)
        }

        val selection = SelectableExpression(editor)
        assertEquals("-8--12", selection.text)
        for (item in listOf(unary, binary, negative)) {
            val range = selection.rangeOf(item)
            assertEquals(if (item === negative) "-12" else "-", selection.text.substring(range.min, range.max))
        }
    }

    @Test
    fun negativeSuperscriptsAndRootDegreesUseScaledEditableFields() {
        val editor = ExpressionEditor()
        val number = FItem.Num("8")
        val negative = FItem.Num("-12")
        val power = FItem.Power(mutableListOf(number), mutableListOf(negative))
        val degree = FItem.Num("3")
        val radical = FItem.Root(mutableListOf(power), mutableListOf(degree))
        editor.root += radical

        for (operatorPadding in listOf(0f, 3f)) {
            val layout = ExpressionLayout(editor.root, 1f, operatorPadding, ::glyph)
            fun checkField(path: List<SlotStep>, token: FItem, char: Int): Rect {
                assertTrue(editor.setCursor(path, 0, char))
                val caret = layout.cursorRect(editor)!!
                val point = Offset(caret.left + 0.05f, caret.center.y)
                assertSame(token, layout.tokenAt(point))
                editor.moveToEnd()
                layout.hitTest(editor, point)
                assertEquals(path, editor.path)
                assertEquals(char, editor.charOff)
                return caret
            }
            val radicand = listOf(SlotStep(0, SlotRole.RADICAND))
            val base = checkField(radicand + SlotStep(0, SlotRole.BASE), number, 0)
            val exponent = checkField(radicand + SlotStep(0, SlotRole.EXPONENT), negative, 1)
            val index = checkField(listOf(SlotStep(0, SlotRole.DEGREE)), degree, 0)
            assertTrue(exponent.height < base.height && exponent.top < base.top && exponent.left > base.left)
            assertTrue(index.height < base.height && index.top < base.top && index.left < base.left)
            // At the right lip the overbar spans the full compound radicand.
            assertSame(radical, layout.tokenAt(Offset(layout.width - 2.5f, 0.7f)))
        }
    }

    @Test
    fun emptySlotsInNestedScientificTemplatesRemainIndependentlyReachable() {
        val editor = ExpressionEditor()
        val power = FItem.Power()
        val radicand = mutableListOf<FItem>(power)
        val degree = mutableListOf<FItem>()
        val root = FItem.Root(radicand, degree)
        val function = FItem.Function(FunctionKind.SIN, mutableListOf(root))
        editor.root += function
        val layout = ExpressionLayout(editor.root, 1f, shape = ::glyph)
        val inside = listOf(SlotStep(0, SlotRole.ARGUMENT))
        val underRoot = inside + SlotStep(0, SlotRole.RADICAND)
        for ((path, field) in listOf(
            inside + SlotStep(0, SlotRole.DEGREE) to degree,
            underRoot + SlotStep(0, SlotRole.BASE) to power.base,
            underRoot + SlotStep(0, SlotRole.EXPONENT) to power.exponent
        )) {
            assertTrue(editor.setCursor(path, 0, 0))
            val point = layout.cursorRect(editor)!!.center
            editor.moveToEnd()
            layout.hitTest(editor, point)
            assertSame(field, editor.slot)
        }
        assertSame(function, layout.tokenAt(Offset(3f, layout.height / 2f)))
    }

    @Test
    fun deeplyNestedScriptsKeepAReadableSizeAndHitTheInnermostNumber() {
        val inner = FItem.Num("3")
        var value: FItem = inner
        repeat(5) { value = FItem.Power(mutableListOf(FItem.Num("2")), mutableListOf(value)) }
        val editor = ExpressionEditor().apply { root += value }
        val layout = ExpressionLayout(editor.root, 1f, shape = ::glyph)
        val path = List(5) { SlotStep(0, SlotRole.EXPONENT) }
        assertTrue(editor.setCursor(path, 0, 0))
        val caret = layout.cursorRect(editor)!!
        assertEquals(5f, caret.height, 0.001f)
        editor.moveToEnd()
        layout.hitTest(editor, caret.center)
        assertEquals(path, editor.path)
        assertSame(inner, layout.tokenAt(caret.center))
    }

    @Test
    fun absoluteValueBarsSpanTallArgumentsAndRetainSeparateInteriorAndExteriorStops() {
        val numerator = FItem.Num("8")
        val fraction = FItem.FracItem(mutableListOf(numerator), mutableListOf(FItem.Num("2")))
        val absolute = FItem.Function(FunctionKind.ABS, mutableListOf(fraction))
        val editor = ExpressionEditor().apply { root += absolute }
        val doc = SelectableExpression(editor)
        assertEquals("abs(8/2)", doc.text)
        assertEquals(doc.all, doc.rangeOf(absolute))
        val plain = ExpressionLayout(absolute.argument, 1f, shape = ::glyph)
        for (padding in listOf(0f, 2f)) {
            val shaped = mutableListOf<String>()
            val layout = ExpressionLayout(editor.root, 1f, padding) { text -> shaped += text; glyph(text) }
            assertEquals(listOf("0", "8", "2"), shaped)
            assertEquals(plain.height, layout.height, 0.001f)
            val side = (layout.width - plain.width) / 2f
            val right = layout.width - 2f // trailing cursor width
            val leftInside = Offset(side * 0.8f, 0.5f)
            val rightOutside = Offset(right - side * 0.2f, layout.height - 0.5f)
            assertSame(absolute, layout.tokenAt(leftInside))
            assertSame(absolute, layout.tokenAt(rightOutside))
            layout.hitTest(editor, leftInside)
            assertSame(absolute.argument, editor.slot)
            assertEquals(0, editor.index)
            layout.hitTest(editor, rightOutside)
            assertSame(editor.root, editor.slot)
            assertEquals(1, editor.index)
            val inside = Offset(right / 2f, 2f)
            assertSame(numerator, layout.tokenAt(inside))
            layout.hitTest(editor, inside)
            assertSame(fraction.num, editor.slot)
        }
    }
}
