package com.dshm.calc

import androidx.compose.ui.text.TextRange
import org.junit.Assert.*
import org.junit.Test

class ExpressionSessionTest {
    @Test
    fun absoluteValueCanReuseANegativeFractionResult() {
        val session = ExpressionSession()
        session.insertOperator("-")
        session.insertDigit('3')
        session.insertOperator("÷")
        session.insertDigit('4')
        assertEquals("-3/4", session.calculate("Error")?.result)
        session.function(FunctionKind.ABS)
        assertEquals("3/4", session.calculate("Error")?.result)
    }

    @Test
    fun scientificResultContinuesIntoAPowerAndMemorySurvivesClear() {
        val session = ExpressionSession()
        session.insertDigit('2')
        session.power()
        session.insertDigit('3')
        assertEquals("8", session.calculate("Error")?.result)
        session.addToMemory(false)
        session.power(2)
        assertEquals("64", session.calculate("Error")?.result)
        session.clear()
        session.insertDigit('3')
        session.addToMemory(true)
        session.clear()
        session.recallMemory()
        assertEquals("5", session.calculate("Error")?.result)
        session.clearMemory()
        session.recallMemory()
        assertEquals("0", session.calculate("Error")?.result)
    }

    @Test
    fun inverseAndAngleModesReachTheEvaluator() {
        val session = ExpressionSession()
        assertEquals(AngleMode.DEG, session.angleMode)
        session.toggleInverse()
        assertTrue(session.inverse)
        session.function(FunctionKind.ASIN)
        session.insertDigit('1')
        assertEquals(90.0, session.calculate("Error")!!.result.toDouble(), 1e-10)
        session.toggleAngleMode()
        session.function(FunctionKind.ASIN)
        session.insertDigit('1')
        assertEquals(Math.PI / 2, session.calculate("Error")!!.result.toDouble(), 1e-10)
    }

    @Test
    fun completingKeepsTheOriginalFractionAndHistoryFormat() {
        val session = ExpressionSession()
        session.insertFraction()
        session.insertDigit('4')
        session.nextField()
        session.insertDigit('2')
        session.nextField()
        session.insertOperator("+")
        session.insertDigit('1')

        assertEquals(HistoryItem("(2)/(4)+1", "3/2"), session.calculate("Error"))
        val source = session.completedExpression!!
        val numerator = (source.root.first() as FItem.FracItem).num.single()
        session.selectCompleted(numerator)
        val selection = session.selection!!
        assertEquals("2", selection.text.substring(session.selectedRange.min, session.selectedRange.max))
        session.insertOperator("×")
        session.insertDigit('2')
        assertEquals("3", session.calculate("Error")?.result)
        assertEquals("(2)/(4)+1", exprText(source.root))
    }

    @Test
    fun copyingAResultAfterADoubleTapRestoresTheContinuationPoint() {
        val session = ExpressionSession()
        "384".forEach(session::insertDigit)
        session.calculate("Error")
        val phaseOnFirstTap = session.phase
        val result = session.editor.root.single()
        session.moveCaret { it.setCursor(emptyList(), 0, 2) }
        session.select(result, phaseOnFirstTap)
        assertEquals(TextRange(0, 3), session.selectedRange)
        session.dismissSelection()

        session.insertOperator("+")
        "16".forEach(session::insertDigit)
        assertEquals(HistoryItem("384+16", "400"), session.calculate("Error"))
        session.insertDigit('7')
        assertEquals("7", exprText(session.editor.root))
        assertNull(session.completedExpression)
    }
}
