package com.dshm.calc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tan

enum class AngleMode { DEG, RAD }

private val displayPrecision = MathContext(15, RoundingMode.HALF_EVEN)
private const val maxPower = 10000
private const val maxResultBits = 16384
private const val maxRootDegree = 10000
private const val maxFactorial = 1000

sealed class RationalResult {
    data class Ok(
        val numerator: BigInteger,
        val denominator: BigInteger,
        val isApproximate: Boolean = false
    ) : RationalResult() {
        val isInteger: Boolean get() = denominator == BigInteger.ONE

        fun display(): String {
            if (!isApproximate) return if (isInteger) numerator.toString() else "$numerator/$denominator"
            val value = BigDecimal(numerator).divide(BigDecimal(denominator), displayPrecision).stripTrailingZeros()
            val exponent = value.precision() - value.scale() - 1
            if (exponent in -8..14) return value.toPlainString()
            return "${value.movePointLeft(exponent).stripTrailingZeros().toPlainString()}E$exponent"
        }
    }

    data object Error : RationalResult()
}

private fun domain(): Nothing = throw ArithmeticException("Outside the supported real domain")

private class Ratio(val n: BigInteger, val d: BigInteger, val approximate: Boolean) {
    companion object {
        fun of(n: BigInteger, d: BigInteger = BigInteger.ONE, approximate: Boolean = false): Ratio {
            if (d.signum() == 0) domain()
            val divisor = if (d.signum() < 0) -n.gcd(d) else n.gcd(d)
            return Ratio(n / divisor, d / divisor, approximate)
        }

        fun integer(n: Int) = of(BigInteger.valueOf(n.toLong()))

        fun decimal(value: BigDecimal, approximate: Boolean = false): Ratio {
            val scale = value.scale()
            return if (scale >= 0) of(value.unscaledValue(), BigInteger.TEN.pow(scale), approximate)
            else of(value.unscaledValue() * BigInteger.TEN.pow(-scale), approximate = approximate)
        }

        fun literal(item: FItem.Num): Ratio {
            val value = BigDecimal(item.text)
            // Plain decimals already supply every digit. A compact exponent could expand to billions.
            if (item.text.contains('e', ignoreCase = true) && value.scale() !in -maxPower..maxPower) domain()
            return decimal(value, item.approximate)
        }

        fun floating(value: Double): Ratio {
            if (!value.isFinite()) domain()
            return decimal(BigDecimal.valueOf(value), true)
        }
    }

    fun plus(o: Ratio) = of(n * o.d + o.n * d, d * o.d, approximate || o.approximate)
    fun minus(o: Ratio) = of(n * o.d - o.n * d, d * o.d, approximate || o.approximate)
    fun times(o: Ratio) = of(n * o.n, d * o.d, approximate || o.approximate)
    fun divide(o: Ratio) = of(n * o.d, d * o.n, approximate || o.approximate)
    fun negative() = Ratio(-n, d, approximate)
    fun reciprocal() = of(d, n, approximate)
    fun decimalValue(): BigDecimal = BigDecimal(n).divide(BigDecimal(d), MathContext.DECIMAL128)
    fun double(): Double {
        val value = decimalValue().toDouble()
        if (!value.isFinite() || (value == 0.0 && n.signum() != 0)) domain()
        return value
    }
    fun result() = RationalResult.Ok(n, d, approximate)
}

/** Integer floor root by Newton iteration. Only an exact final candidate is accepted. */
private fun exactRoot(value: BigInteger, degree: Int): BigInteger? {
    if (value.signum() < 0) return if (degree % 2 == 0) null else exactRoot(-value, degree)?.negate()
    if (value <= BigInteger.ONE || degree == 1) return value
    if (degree >= value.bitLength()) return null
    // High orders can overshoot by a large factor in Newton's first step. Binary search
    // needs only a few probes when the answer itself has very few bits.
    val rootBits = (value.bitLength() + degree - 1) / degree
    var high = BigInteger.ONE.shiftLeft(rootBits)
    if (rootBits <= 32) {
        var low = BigInteger.ONE
        while (low <= high) {
            val guess = (low + high).shiftRight(1)
            when (guess.pow(degree).compareTo(value)) {
                0 -> return guess
                -1 -> low = guess + BigInteger.ONE
                else -> high = guess - BigInteger.ONE
            }
        }
        return null
    }
    val divisor = BigInteger.valueOf(degree.toLong())
    while (true) {
        val next = ((divisor - BigInteger.ONE) * high + value / high.pow(degree - 1)) / divisor
        if (next >= high) return high.takeIf { it.pow(degree) == value }
        high = next
    }
}

private fun integerPower(base: Ratio, exponent: Int, approximate: Boolean): Ratio {
    if (base.n.signum() == 0 && exponent <= 0) domain()
    if (base.approximate) {
        val rounded = base.decimalValue().pow(exponent, MathContext.DECIMAL128).stripTrailingZeros()
        if (rounded.scale() !in -4900..4900) domain()
        return Ratio.decimal(rounded, true)
    }
    val count = abs(exponent)
    val factor = if (exponent < 0) base.reciprocal() else base
    fun checkedPow(n: BigInteger): BigInteger {
        if (count == 0) return BigInteger.ONE
        if (count.toLong() * (n.abs().bitLength() - 1) > maxResultBits) domain()
        return n.pow(count).also { if (it.bitLength() > maxResultBits) domain() }
    }
    return Ratio.of(checkedPow(factor.n), checkedPow(factor.d), approximate)
}

private fun power(base: Ratio, exponent: Ratio): Ratio {
    val approximate = base.approximate || exponent.approximate
    if (maxOf(base.n.bitLength(), base.d.bitLength(), exponent.n.bitLength(), exponent.d.bitLength()) > maxResultBits) domain()
    if (exponent.d == BigInteger.ONE) {
        if (exponent.n.abs() > BigInteger.valueOf(maxPower.toLong())) domain()
        return integerPower(base, exponent.n.toInt(), approximate)
    }
    if (base.n.signum() == 0) {
        if (exponent.n.signum() <= 0) domain()
        return Ratio.of(BigInteger.ZERO, approximate = approximate)
    }
    if (base.n.signum() < 0 && !exponent.d.testBit(0)) domain()
    if (exponent.d <= BigInteger.valueOf(maxRootDegree.toLong()) && exponent.n.abs() <= BigInteger.valueOf(maxPower.toLong())) {
        val order = exponent.d.toInt()
        val top = exactRoot(base.n, order)
        val bottom = exactRoot(base.d, order)
        if (top != null && bottom != null) return integerPower(Ratio.of(top, bottom), exponent.n.toInt(), approximate)
    }
    val result = abs(base.double()).pow(exponent.double())
    if (result == 0.0) domain()
    return Ratio.floating(if (base.n.signum() < 0 && exponent.n.testBit(0)) -result else result)
}

private fun radical(base: Ratio, degree: Ratio): Ratio {
    if (degree.n.signum() == 0 || degree.n.abs() > degree.d * BigInteger.valueOf(maxRootDegree.toLong())) domain()
    return power(base, degree.reciprocal())
}

private fun factorial(value: Ratio): Ratio {
    if (value.d != BigInteger.ONE || value.n.signum() < 0 || value.n > BigInteger.valueOf(maxFactorial.toLong())) domain()
    var product = BigInteger.ONE
    for (factor in 2..value.n.toInt()) product *= BigInteger.valueOf(factor.toLong())
    return Ratio.of(product, approximate = value.approximate)
}

private fun trig(kind: FunctionKind, value: Ratio, mode: AngleMode): Ratio {
    // Reduce degree input exactly, including arbitrarily large integral angles.
    val angle = if (mode == AngleMode.DEG) Ratio.of(value.n % (BigInteger.valueOf(360) * value.d), value.d) else value
    if (mode == AngleMode.DEG && angle.d == BigInteger.ONE) {
        val degrees = (angle.n.toInt() + 360) % 360
        // Common rational values stay exact through a subsequent multiply or subtraction.
        val halfUnits = when (kind) {
            FunctionKind.SIN -> when (degrees) {
                30, 150 -> 1
                210, 330 -> -1
                else -> null
            }
            FunctionKind.COS -> when (degrees) {
                60, 300 -> 1
                120, 240 -> -1
                else -> null
            }
            else -> when (degrees) {
                45, 225 -> 2
                135, 315 -> -2
                else -> null
            }
        }
        if (halfUnits != null) return Ratio.of(BigInteger.valueOf(halfUnits.toLong()), BigInteger.valueOf(2), true)
    }
    val radians = if (mode == AngleMode.DEG) Math.toRadians(angle.double()) else angle.double()
    val quadrant = radians / (Math.PI / 2)
    val nearest = round(quadrant)
    val onAxis = if (mode == AngleMode.DEG) angle.n % (BigInteger.valueOf(90) * angle.d) == BigInteger.ZERO
        else quadrant == nearest && abs(nearest) < 1e15
    if (onAxis) {
        val q = ((nearest.toLong() % 4 + 4) % 4).toInt()
        val result = when (kind) {
            FunctionKind.SIN -> intArrayOf(0, 1, 0, -1)[q]
            FunctionKind.COS -> intArrayOf(1, 0, -1, 0)[q]
            else -> if (q % 2 == 0) 0 else domain()
        }
        return Ratio.of(BigInteger.valueOf(result.toLong()), approximate = value.approximate)
    }
    val numeric = when (kind) {
        FunctionKind.SIN -> sin(radians)
        FunctionKind.COS -> cos(radians)
        else -> tan(radians)
    }
    return Ratio.floating(numeric)
}

private fun applyFunction(kind: FunctionKind, value: Ratio, mode: AngleMode): Ratio {
    return when (kind) {
        FunctionKind.ABS -> if (value.n.signum() < 0) value.negative() else value
        FunctionKind.FACTORIAL -> factorial(value)
        FunctionKind.PERCENT -> value.divide(Ratio.integer(100))
        FunctionKind.SIN, FunctionKind.COS, FunctionKind.TAN -> trig(kind, value, mode)
        FunctionKind.ASIN, FunctionKind.ACOS, FunctionKind.ATAN -> {
            if (mode == AngleMode.DEG) {
                val principal = when (kind) {
                    FunctionKind.ASIN, FunctionKind.ACOS -> when {
                        value.n.abs() == value.d -> 90
                        value.n.abs().shiftLeft(1) == value.d -> 30
                        else -> null
                    }
                    else -> if (value.n.abs() == value.d) 45 else null
                }
                if (principal != null) {
                    val signed = principal * value.n.signum()
                    return Ratio.integer(if (kind == FunctionKind.ACOS) 90 - signed else signed)
                }
            }
            val radians = when (kind) {
                FunctionKind.ASIN -> asin(value.double())
                FunctionKind.ACOS -> acos(value.double())
                else -> atan(value.double())
            }
            Ratio.floating(if (mode == AngleMode.DEG) Math.toDegrees(radians) else radians)
        }
        FunctionKind.LN, FunctionKind.LOG -> {
            if (value.n.signum() <= 0) domain()
            val number = value.decimalValue()
            val exponent = number.precision() - number.scale() - 1
            val mantissa = number.movePointLeft(exponent).toDouble()
            val result = if (kind == FunctionKind.LOG) log10(mantissa) + exponent else ln(mantissa) + exponent * ln(10.0)
            Ratio.floating(result)
        }
    }
}

/** The list is one editable slot. Adjacent structured operands use ordinary implicit multiplication. */
private fun evalSlot(items: List<FItem>, mode: AngleMode, depth: Int = 0): Ratio {
    if (depth > 64) domain()
    fun child(slot: List<FItem>): Ratio = evalSlot(slot, mode, depth + 1)
    var position = 0
    fun factor(): Ratio {
        var negative = false
        while (true) {
            val sign = (items.getOrNull(position) as? FItem.Op)?.sym
            if (sign != "+" && sign != "-") break
            if (sign == "-") negative = !negative
            position++
        }
        val value = when (val item = items.getOrNull(position++) ?: domain()) {
            is FItem.Num -> Ratio.literal(item)
            is FItem.Constant -> Ratio.floating(if (item.kind == ConstantKind.PI) Math.PI else Math.E)
            is FItem.Op -> domain()
            is FItem.ParenItem -> child(item.inner)
            is FItem.FracItem -> child(item.num).divide(child(item.den))
            is FItem.Power -> power(child(item.base), child(item.exponent))
            is FItem.Root -> radical(child(item.radicand), item.degree?.let(::child) ?: Ratio.integer(2))
            is FItem.Function -> applyFunction(item.kind, child(item.argument), mode)
        }
        return if (negative) value.negative() else value
    }

    fun term(): Ratio {
        var value = factor()
        while (position < items.size) {
            val next = items[position]
            if (next is FItem.Op) {
                if (next.sym != "×" && next.sym != "÷") break
                position++
                val other = factor()
                value = if (next.sym == "×") value.times(other) else value.divide(other)
            } else {
                if (next is FItem.Num && items[position - 1] is FItem.Num) domain()
                value = value.times(factor())
            }
        }
        return value
    }

    var result = term()
    while (position < items.size) {
        val operator = (items[position++] as? FItem.Op)?.sym
        val other = term()
        result = when (operator) {
            "+" -> result.plus(other)
            "-" -> result.minus(other)
            else -> domain()
        }
    }
    return result
}

fun evaluate(tokens: List<FItem>, angleMode: AngleMode = AngleMode.DEG): RationalResult = try {
    evalSlot(tokens, angleMode).result()
} catch (_: ArithmeticException) {
    RationalResult.Error
} catch (_: NumberFormatException) {
    RationalResult.Error
}

fun combineMemory(stored: RationalResult.Ok?, value: RationalResult.Ok, subtract: Boolean): RationalResult.Ok? = try {
    val prior = stored?.let { Ratio.of(it.numerator, it.denominator, it.isApproximate) } ?: Ratio.integer(0)
    val added = Ratio.of(value.numerator, value.denominator, value.isApproximate)
    (if (subtract) prior.minus(added) else prior.plus(added)).result()
} catch (_: ArithmeticException) {
    null
}

/** Compatible with existing history records; scientific operators have explicit scope. */
fun exprText(items: List<FItem>): String = items.joinToString("") { item ->
    when (item) {
        is FItem.Num -> item.text
        is FItem.Op -> item.sym
        is FItem.Constant -> item.kind.label
        is FItem.FracItem -> "(${exprText(item.num)})/(${exprText(item.den)})"
        is FItem.ParenItem -> "(${exprText(item.inner)})"
        is FItem.Power -> "${grouped(item.base)}^${grouped(item.exponent)}"
        is FItem.Root -> item.degree?.let { "root(${exprText(it)},${exprText(item.radicand)})" } ?: "sqrt(${exprText(item.radicand)})"
        is FItem.Function -> if (item.kind.postfix) grouped(item.argument) + item.kind.label
            else "${item.kind.label}(${exprText(item.argument)})"
    }
}

private fun grouped(items: List<FItem>): String {
    val item = items.singleOrNull()
    val text = exprText(items)
    return when {
        item == null || item is FItem.FracItem || text.startsWith('-') || text.startsWith('+') || item is FItem.Power -> "($text)"
        else -> text
    }
}
