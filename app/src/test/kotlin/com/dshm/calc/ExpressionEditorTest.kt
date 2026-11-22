package com.dshm.calc

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionEditorTest {
    @Test
    fun mixedTemplatesFollowTheirOwnFieldsAndReturnToTheEnclosingField() {
        val editor = ExpressionEditor()
        editor.newParen()
        editor.newFraction()
        editor.newParen()
        val outer = SlotStep(0, SlotRole.CONTENT)
        val denominator = SlotStep(0, SlotRole.DENOMINATOR)
        assertEquals(listOf(outer, denominator, SlotStep(0, SlotRole.CONTENT)), editor.path)
        editor.newFraction() // The existing three-level nesting limit also applies to mixed templates.
        assertTrue(editor.slot.isEmpty())
        editor.insertChar('2')
        editor.nextField()
        assertEquals(listOf(outer, denominator), editor.path)
        editor.nextField()
        assertEquals(listOf(outer, SlotStep(0, SlotRole.NUMERATOR)), editor.path)
        editor.insertChar('3')
        editor.nextField()
        assertEquals(listOf(outer), editor.path)
        editor.nextField()
        assertSame(editor.root, editor.slot)
        assertEquals(1, editor.index)
        assertEquals("3/2", (evaluate(editor.root) as RationalResult.Ok).display())
    }

    @Test
    fun aGeometryAddressReentersTheExactCharacterAndRejectsStaleLocations() {
        val editor = ExpressionEditor()
        val address = listOf(SlotStep(0, SlotRole.NUMERATOR), SlotStep(0, SlotRole.CONTENT))
        val content = mutableListOf<FItem>(FItem.Num("23"))
        val fraction = FItem.FracItem(mutableListOf(FItem.ParenItem(content)), mutableListOf(FItem.Num("5")))
        editor.replaceWithValue(fraction)
        assertTrue(editor.setCursor(address, 0, 1))
        assertFalse(editor.setCursor(listOf(SlotStep(0, SlotRole.CONTENT)), 0, 0))
        assertFalse(editor.setCursor(address, 0, 5))
        assertSame(content, editor.slot)
        editor.insertChar('4')
        assertEquals("243", (content.single() as FItem.Num).text)
        val text = SelectableExpression(editor)
        val range = text.rangeOf(content.single())
        editor.moveToEnd()
        text.prepareEdit(editor, TextRange(range.start + 1, range.start + 2))
        editor.insertChar('6')
        assertEquals(address, editor.path)
        assertEquals("263/5", (evaluate(editor.root) as RationalResult.Ok).display())
    }

    @Test
    fun deletingAnEmptyInnerTemplateJoinsOnlyTheSurroundingDenominatorDigits() {
        val editor = ExpressionEditor()
        editor.newFraction()
        "12".forEach(editor::insertChar)
        val denominator = listOf(SlotStep(0, SlotRole.DENOMINATOR))
        editor.setCursor(denominator, 0, 1)
        editor.newParen()
        editor.backspace()
        assertEquals(denominator, editor.path)
        assertEquals(1, editor.charOff)
        editor.insertChar('0')
        editor.nextField()
        editor.insertChar('5')
        assertEquals("(5)/(102)", exprText(editor.root))
        val historicalResult = resultToItem("5/102")
        editor.replaceWithValue(historicalResult)
        assertEquals("5/102", SelectableExpression(editor).text)
    }

    @Test
    fun historicalExactResultsAndLocalizedErrorLabelsKeepTheirDisplayText() {
        val editor = ExpressionEditor()
        for (saved in listOf("10000000000000000000003", "-7/4")) {
            editor.replaceWithValue(resultToItem(saved))
            assertEquals(saved, (evaluate(editor.root) as RationalResult.Ok).display())
            assertEquals(saved, SelectableExpression(editor).text)
        }
        for (error in listOf("错误", "錯誤", "Error")) {
            editor.replaceWithValue(FItem.Num(error))
            assertSame(RationalResult.Error, evaluate(editor.root))
            assertEquals(error, SelectableExpression(editor).text)
        }
    }

    @Test
    fun recalledDigitsSpliceAtTheCaretAndReplaceOnlyTheSelectedDigits() {
        val editor = ExpressionEditor()
        editor.replaceWithValue(FItem.Num("1234"))
        editor.setCursor(emptyList(), 0, 2)
        editor.insertValue(resultToItem("89"))
        assertEquals("128934", (editor.root.single() as FItem.Num).text)
        assertEquals(4, editor.charOff)
        SelectableExpression(editor).prepareEdit(editor, TextRange(4, 2))
        editor.insertValue(resultToItem("5"))
        assertEquals("12534", (evaluate(editor.root) as RationalResult.Ok).display())
        assertEquals(3, editor.charOff)

        editor.replaceWithValue(FItem.Num("-12"))
        editor.setCursor(emptyList(), 0, 1)
        editor.insertValue(resultToItem("5"))
        assertEquals("-512", (editor.root.single() as FItem.Num).text)
    }

    @Test
    fun recalledFractionAndSignedValueKeepBothNeighborsAfterSelection() {
        for ((value, expected) in listOf("3/4" to "6", "-3" to "-24")) {
            val editor = ExpressionEditor()
            editor.replaceWithValue(FItem.Num("234"))
            SelectableExpression(editor).prepareEdit(editor, TextRange(1, 2))
            editor.insertValue(resultToItem(value))
            assertEquals("2", (editor.root[0] as FItem.Num).text)
            assertEquals("×", (editor.root[1] as FItem.Op).sym)
            assertEquals("×", (editor.root[3] as FItem.Op).sym)
            assertEquals("4", (editor.root[4] as FItem.Num).text)
            assertEquals(3, editor.index)
            assertEquals(expected, (evaluate(editor.root) as RationalResult.Ok).display())
        }
    }

    @Test
    fun recallAtOperandAndOperatorBoundariesUsesOnlyTheNecessaryMultiplication() {
        val editor = ExpressionEditor()
        editor.replaceWithValue(resultToItem("1/2"))
        editor.insertValue(resultToItem("6"))
        assertEquals("3", (evaluate(editor.root) as RationalResult.Ok).display())
        editor.insertOp("+")
        editor.insertValue(resultToItem("-2"))
        assertEquals("1", (evaluate(editor.root) as RationalResult.Ok).display())

        editor.replaceWithValue(FItem.Num("-12"))
        editor.setCursor(emptyList(), 0, 0)
        editor.insertValue(resultToItem("5"))
        assertEquals("-60", (evaluate(editor.root) as RationalResult.Ok).display())
    }
}
