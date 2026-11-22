package com.dshm.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

class ScientificEvaluatorTest {
    private fun n(text: String): MutableList<FItem> = mutableListOf(FItem.Num(text))
    private fun fn(kind: FunctionKind, value: String) = FItem.Function(kind, n(value))
    private fun value(vararg items: FItem, mode: AngleMode = AngleMode.DEG) =
        evaluate(items.toList(), mode) as RationalResult.Ok

    private fun asDouble(result: RationalResult.Ok) =
        BigDecimal(result.numerator).divide(BigDecimal(result.denominator), MathContext.DECIMAL64).toDouble()

    @Test fun powersAndRootsKeepExactRationalValuesWhenPossible() {
        val fraction = FItem.FracItem(n("16"), n("81"))
        val result = value(FItem.Power(mutableListOf(fraction), n("-0.25")))
        assertEquals("3/2", result.display())
        assertFalse(result.isApproximate)
        assertEquals("-2", value(FItem.Root(n("-8"), n("3"))).display())
        assertEquals("1/2", value(FItem.Root(n("8"), n("-3"))).display())
        val third = FItem.FracItem(n("1"), n("3"))
        assertEquals("-2", value(FItem.Power(n("-8"), mutableListOf(third))).display())
        assertEquals("1/4", value(FItem.Power(n("2"), n("-2"))).display())
        val irrational = value(FItem.Root(n("2")))
        assertTrue(irrational.isApproximate)
        assertEquals(kotlin.math.sqrt(2.0), irrational.display().toDouble(), 1e-14)
    }

    @Test fun trigonometryUsesTheSelectedAngleModeInBothDirections() {
        assertEquals("0.5", value(fn(FunctionKind.SIN, "30")).display())
        assertEquals("0", value(fn(FunctionKind.COS, "90")).display())
        assertEquals("1", value(fn(FunctionKind.TAN, "45")).display())
        assertEquals("0", value(fn(FunctionKind.SIN, "30"), FItem.Op("×"), FItem.Num("2"), FItem.Op("-"), FItem.Num("1")).display())
        assertEquals("30", value(fn(FunctionKind.ASIN, "0.5")).display())
        assertEquals("60", value(fn(FunctionKind.ACOS, "0.5")).display())
        assertEquals(Math.PI / 4, asDouble(value(fn(FunctionKind.ATAN, "1"), mode = AngleMode.RAD)), 1e-14)
        val halfPi = FItem.FracItem(mutableListOf(FItem.Constant(ConstantKind.PI)), n("2"))
        assertEquals("1", value(FItem.Function(FunctionKind.SIN, mutableListOf(halfPi)), mode = AngleMode.RAD).display())
        assertSame(RationalResult.Error, evaluate(listOf(FItem.Function(FunctionKind.TAN, mutableListOf(halfPi))), AngleMode.RAD))
        assertEquals("0", value(fn(FunctionKind.SIN, "360000000000000000000000000000000000")).display())
        assertEquals("1", value(FItem.Function(FunctionKind.LN, mutableListOf(FItem.Constant(ConstantKind.E)))).display())
        assertEquals("3", value(fn(FunctionKind.LOG, "1000")).display())
        assertEquals("-400", value(fn(FunctionKind.LOG, "1E-400")).display())
        val exp = value(FItem.Power(mutableListOf(FItem.Constant(ConstantKind.E)), n("500")))
        assertEquals(1.0, exp.display().toDouble() / Math.exp(500.0), 1e-12)
    }

    @Test fun postfixAndMemoryRetainFractionsAndApproximateFlags() {
        assertEquals("1", value(fn(FunctionKind.FACTORIAL, "0")).display())
        assertEquals("2432902008176640000", value(fn(FunctionKind.FACTORIAL, "20")).display())
        assertEquals("1/8", value(fn(FunctionKind.PERCENT, "12.5")).display())
        val third = value(FItem.FracItem(n("1"), n("3")))
        val quarter = value(FItem.FracItem(n("1"), n("4")))
        assertEquals("1/12", combineMemory(third, quarter, true)?.display())
        assertEquals("-1/4", combineMemory(null, quarter, true)?.display())
        val result = combineMemory(third, value(FItem.Root(n("2"))), false)!!
        assertTrue(result.isApproximate)
        assertEquals(kotlin.math.sqrt(2.0) + 1.0 / 3, result.display().toDouble(), 1e-14)
        assertNull(combineMemory(null, RationalResult.Ok(BigInteger.ONE, BigInteger.ZERO), true))
    }

    @Test fun undefinedAndExcessiveOperationsReturnAnError() {
        val cases = listOf(
            FItem.Root(n("-2")), FItem.Root(n("8"), n("0")), FItem.Root(n("2"), n("1000000000000000")),
            fn(FunctionKind.LN, "0"), fn(FunctionKind.LOG, "-1"), fn(FunctionKind.ACOS, "1.01"),
            fn(FunctionKind.TAN, "90"), fn(FunctionKind.FACTORIAL, "-1"), fn(FunctionKind.FACTORIAL, "3.5"),
            fn(FunctionKind.FACTORIAL, "99999999"), FItem.Power(n("2"), n("999999999")),
            FItem.Power(n("0"), n("-1")), FItem.Power(n("-2"), n("0.5")), FItem.Power(n("0"), n("0")),
            FItem.Num("1E2147483647"), FItem.Num("NaN"), FItem.Root(), FItem.Function(FunctionKind.SIN)
        )
        for (item in cases) assertSame(exprText(listOf(item)), RationalResult.Error, evaluate(listOf(item)))
        var nested: FItem = FItem.Num("1")
        repeat(2000) { nested = FItem.Power(n("2"), mutableListOf(nested)) }
        assertSame(RationalResult.Error, evaluate(listOf(nested)))
        val editor = ExpressionEditor()
        editor.insertChar('2')
        repeat(2000) { editor.newPower(2) }
        assertSame(RationalResult.Error, evaluate(editor.root))
    }

    @Test fun basicArithmeticStillAcceptsLongPlainIntegersAndDecimalsExactly() {
        val nines = "9".repeat(6000)
        val carry = "1" + "0".repeat(6000)
        assertEquals(carry, value(FItem.Num(nines), FItem.Op("+"), FItem.Num("1")).display())
        val tiny = "0." + "0".repeat(5999) + "1"
        assertEquals("1/$carry", value(FItem.Num(tiny)).display())
        assertEquals("1", value(FItem.Num(tiny), FItem.Op("×"), FItem.Num(carry)).display())
    }

    @Test fun wrappingAndExplicitSlotsPreserveTheRestOfTheExpression() {
        val editor = ExpressionEditor()
        "23".forEach(editor::insertChar)
        editor.setCursor(emptyList(), 0, 1)
        editor.newPower()
        assertEquals(SlotRole.EXPONENT, editor.path.single().role)
        editor.insertChar('4')
        editor.nextField()
        assertEquals("48", (evaluate(editor.root) as RationalResult.Ok).display()) // 2⁴ times the surviving 3.
        editor.clear()
        editor.newRoot(null)
        "27".forEach(editor::insertChar)
        editor.nextField()
        assertEquals(SlotRole.DEGREE, editor.path.single().role)
        editor.insertChar('3')
        editor.nextField()
        editor.newPower(2)
        assertEquals("9", (evaluate(editor.root) as RationalResult.Ok).display())
        assertEquals("root(3,27)^2", exprText(editor.root))
        editor.newFractionFromOperand()
        assertEquals(SlotRole.DENOMINATOR, editor.path.single().role)
        editor.insertChar('6')
        editor.nextField()
        assertTrue(editor.path.isEmpty())
        assertEquals("3/2", (evaluate(editor.root) as RationalResult.Ok).display())
        editor.clear()
        editor.newExponential(FItem.Num("10"))
        editor.insertChar('2')
        editor.nextField()
        editor.newExponential(FItem.Num("2"))
        assertEquals(BigInteger.ONE.shiftLeft(100).toString(), (evaluate(editor.root) as RationalResult.Ok).display())
        editor.clear()
        editor.newPower(2)
        editor.insertChar('4')
        editor.nextField()
        assertTrue(editor.path.isEmpty())
        editor.insertOp("+")
        editor.newRoot(3)
        editor.insertChar('8')
        editor.nextField()
        assertTrue(editor.path.isEmpty())
        assertEquals("18", (evaluate(editor.root) as RationalResult.Ok).display())
    }

    @Test fun functionAndParenthesisKeysSupportJuxtapositionAndNearestClose() {
        val editor = ExpressionEditor()
        editor.insertChar('2')
        editor.newFunction(FunctionKind.SIN)
        "30".forEach(editor::insertChar)
        assertTrue(editor.closeParenthesis())
        editor.insertOp("+")
        editor.insertChar('1')
        assertEquals("2sin(30)+1", exprText(editor.root))
        assertEquals("2", (evaluate(editor.root) as RationalResult.Ok).display())
        editor.clear()
        editor.newParen()
        editor.newFunction(FunctionKind.LN)
        editor.newFunction(FunctionKind.FACTORIAL)
        editor.insertChar('1')
        assertTrue(editor.closeParenthesis())
        assertEquals(SlotRole.CONTENT, editor.path.single().role)
        assertTrue(editor.closeParenthesis())
        assertFalse(editor.closeParenthesis())
        editor.clear()
        editor.insertChar('2')
        editor.insertConstant(ConstantKind.PI)
        editor.newParen()
        editor.insertChar('3')
        editor.insertOp("+")
        editor.insertChar('4')
        assertEquals(14 * Math.PI, asDouble(evaluate(editor.root) as RationalResult.Ok), 1e-13)
    }

    @Test fun absoluteValueAcceptsAnEmptyArgumentOrWrapsTheSignedOperandAtTheCaret() {
        val editor = ExpressionEditor()
        editor.newFunction(FunctionKind.ABS)
        assertEquals(SlotRole.ARGUMENT, editor.path.single().role)
        editor.insertOp("-")
        editor.insertChar('3')
        assertEquals("abs(-3)", exprText(editor.root))
        assertEquals("3", (evaluate(editor.root) as RationalResult.Ok).display())

        for ((prefix, expected) in listOf(
            "-" to "abs(-3)", "5-" to "5-abs(3)", "5+-" to "5+abs(-3)", "5--" to "5-abs(-3)"
        )) {
            editor.clear()
            for (key in prefix) {
                if (key.isDigit()) editor.insertChar(key) else editor.insertOp(key.toString())
            }
            editor.insertChar('3')
            editor.newFunction(FunctionKind.ABS)
            assertEquals(expected, exprText(editor.root))
            assertTrue(editor.path.isEmpty())
        }

        editor.replaceWithValue(FItem.Num("-35"))
        editor.setCursor(emptyList(), 0, 2)
        editor.newFunction(FunctionKind.ABS)
        assertEquals("abs(-3)×5", exprText(editor.root))
        assertEquals("15", (evaluate(editor.root) as RationalResult.Ok).display())
    }

    @Test fun absoluteValuePreservesRationalAndApproximateResults() {
        val editor = ExpressionEditor()
        editor.replaceWithValue(resultToItem("-13/17"))
        editor.newFunction(FunctionKind.ABS)
        val fraction = evaluate(editor.root) as RationalResult.Ok
        assertEquals("13/17", fraction.display())
        assertFalse(fraction.isApproximate)

        editor.replaceWithValue(FItem.Num("-1.23456789123456E-45", approximate = true))
        editor.newFunction(FunctionKind.ABS)
        val tiny = evaluate(editor.root) as RationalResult.Ok
        assertEquals("1.23456789123456E-45", tiny.display())
        assertTrue(tiny.isApproximate)
    }

    @Test fun approximateResultsReturnFromHistoryWithoutTurningIntoLargeFractions() {
        val small = value(fn(FunctionKind.SIN, "1E-12"), mode = AngleMode.RAD)
        val copy = value(resultToItem(small.display()))
        assertTrue(copy.isApproximate)
        assertEquals(small.display(), copy.display())
        val original = value(FItem.Root(n("2")))
        assertEquals(original.display(), value(resultToItem(original.display())).display())
        assertEquals("1/8", value(FItem.Num("0.125")).display())
    }
}
