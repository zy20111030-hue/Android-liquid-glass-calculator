package com.dshm.calc

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectableExpressionTest {
    @Test
    fun replacingTheWholeNumeratorAtOffsetZeroKeepsTheDenominator() {
        val editor = ExpressionEditor().apply {
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
        val editor = ExpressionEditor().apply {
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
        val editor = ExpressionEditor().apply {
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
        val editor = ExpressionEditor().apply {
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

    @Test
    fun scientificCopyPreservesPowersRootArgumentOrderConstantsAndPostfixScope() {
        val exponent = FItem.Num("-2")
        val pi = FItem.Constant(ConstantKind.PI)
        val power = FItem.Power(mutableListOf(pi), mutableListOf(exponent))
        val root = FItem.Root(mutableListOf(power), mutableListOf(FItem.Num("3")))
        val sine = FItem.Function(FunctionKind.SIN, mutableListOf(root))
        val sum = mutableListOf<FItem>(FItem.Num("3"), FItem.Op("+"), FItem.Num("2"))
        val fraction = FItem.FracItem(sum, mutableListOf(FItem.Num("4")))
        val postfix = FItem.Function(FunctionKind.FACTORIAL, mutableListOf(fraction))
        val editor = ExpressionEditor().apply { this.root.addAll(listOf(sine, FItem.Op("+"), postfix)) }
        val document = SelectableExpression(editor)
        assertEquals("sin(root(3,π^(-2)))+((3+2)/4)!", document.text)
        for ((token, expected) in listOf(pi to "π", exponent to "-2", power to "π^(-2)", root to "root(3,π^(-2))",
            postfix to "((3+2)/4)!")) {
            val range = document.rangeOf(token)
            assertEquals(expected, document.text.substring(range.min, range.max))
        }
    }

    @Test
    fun replacingOnlyTheExponentOfANegativeBaseKeepsTheBaseAndItsScope() {
        val exponent = FItem.Num("-3")
        val power = FItem.Power(mutableListOf(FItem.Num("-2")), mutableListOf(exponent))
        val editor = ExpressionEditor().apply { root += power }
        val document = SelectableExpression(editor)
        assertEquals("(-2)^(-3)", document.text)
        document.prepareEdit(editor, document.rangeOf(exponent))
        editor.insertChar('7')
        assertEquals("(-2)^7", SelectableExpression(editor).text)
    }

    @Test
    fun replacingAScientificSeparatorPreservesBothFields() {
        val root = FItem.Root(mutableListOf(FItem.Num("27")), mutableListOf(FItem.Num("3")))
        val editor = ExpressionEditor().apply { this.root += FItem.Function(FunctionKind.LN, mutableListOf(root)) }
        val document = SelectableExpression(editor)
        val comma = document.text.indexOf(',')
        document.prepareEdit(editor, TextRange(comma, comma + 1))
        editor.insertOp("+")
        assertEquals("ln(3+27)", SelectableExpression(editor).text)
    }

    @Test
    fun factorialPercentAndSquareRootAreCopiedAndEditableAsDistinctOperations() {
        val value = FItem.Num("25")
        val percent = FItem.Function(FunctionKind.PERCENT, mutableListOf(value))
        val sqrt = FItem.Root(mutableListOf(percent))
        val editor = ExpressionEditor().apply { root += sqrt }
        val document = SelectableExpression(editor)
        assertEquals("sqrt(25%)", document.text)
        val percentMark = document.text.indexOf('%')
        document.prepareEdit(editor, TextRange(percentMark, percentMark + 1))
        editor.insertChar('6')
        assertEquals("sqrt(256)", SelectableExpression(editor).text)
    }
}
