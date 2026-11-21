package com.dshm.calc

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectableExpressionTest {
    @Test
    fun replacingTheWholeNumeratorAtOffsetZeroKeepsTheDenominator() {
        val editor = FracEditor().apply {
            newFraction()
            "78".forEach(::insertChar)
            nextField()
            "56".forEach(::insertChar)
            nextField()
        }
        val expression = SelectableExpression(editor)
        val fraction = editor.root.single() as FItem.FracItem
        val numerator = expression.rangeOf(fraction.num.single())
        assertEquals(TextRange(0, 2), numerator)

        expression.prepareEdit(editor, numerator)
        editor.insertChar('9')

        assertEquals("9/78", SelectableExpression(editor).text)
        assertEquals("9", (fraction.num.single() as FItem.Num).text)
        assertEquals("78", (fraction.den.single() as FItem.Num).text)
    }

    @Test
    fun replacingABackwardSelectionAcrossAnOperatorKeepsBothNumberEnds() {
        val editor = FracEditor().apply {
            "123".forEach(::insertChar)
            insertOp("+")
            "456".forEach(::insertChar)
        }

        SelectableExpression(editor).prepareEdit(editor, TextRange(5, 2))
        editor.insertChar('8')

        assertEquals("12856", (editor.root.single() as FItem.Num).text)
    }

    @Test
    fun deletingOnlyAnOperatorJoinsTheNumbersAndLeavesTheCaretAtTheJoin() {
        val editor = FracEditor().apply {
            "12".forEach(::insertChar)
            insertOp("-")
            "34".forEach(::insertChar)
        }

        SelectableExpression(editor).prepareEdit(editor, TextRange(2, 3))
        assertEquals("1234", (editor.root.single() as FItem.Num).text)

        editor.insertOp("×")
        assertEquals("12×34", SelectableExpression(editor).text)
    }

    @Test
    fun replacingTheFractionSlashPreservesBothOperandsAndTheirOrder() {
        val editor = FracEditor().apply {
            newFraction()
            insertChar('4')
            nextField()
            "12".forEach(::insertChar)
            nextField()
        }

        SelectableExpression(editor).prepareEdit(editor, TextRange(2, 3))
        editor.insertOp("+")

        assertEquals("12+4", SelectableExpression(editor).text)
    }
}
