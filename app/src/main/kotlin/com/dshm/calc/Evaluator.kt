package com.dshm.calc

import java.math.BigInteger

/*
 * 表达式模型 + 求值链路（纯 Kotlin）。
 * 只依赖 Kotlin 标准库与 java.math.BigInteger，不引用任何 android.* / androidx.* 类。
 *
 * 链路：表达式树（数字/运算符/分数/括号） → 递归下降（括号与优先级）
 *      → BigInteger 有理数精确运算 → 约分（结果假分数化）。
 */

/* ===================== 表达式模型 ===================== */

sealed class FItem {
    class Num(var text: String) : FItem()
    class Op(val sym: String) : FItem()
    class FracItem(
        val num: MutableList<FItem> = mutableListOf(),
        val den: MutableList<FItem> = mutableListOf()
    ) : FItem()
    class ParenItem(
        val inner: MutableList<FItem> = mutableListOf()
    ) : FItem()
}

/* ===================== 求值结果 ===================== */

sealed class RationalResult {
    data class Ok(val numerator: BigInteger, val denominator: BigInteger) : RationalResult() {
        val isInteger: Boolean get() = denominator == BigInteger.ONE

        /** 显示文本：整数原样输出，否则为假分数 a/b。 */
        fun display(): String = if (isInteger) numerator.toString() else "$numerator/$denominator"
    }

    data object Error : RationalResult()
}

/* ===================== 有理数（BigInteger 精确运算） ===================== */

private class Ratio(val n: BigInteger, val d: BigInteger) {
    companion object {
        fun of(n: BigInteger, d: BigInteger): Ratio {
            if (d.signum() == 0) throw ArithmeticException("div0")
            var nn = n
            var dd = d
            if (dd.signum() < 0) { nn = nn.negate(); dd = dd.negate() }
            val g = nn.gcd(dd)
            if (g.signum() != 0 && g != BigInteger.ONE) { nn = nn.divide(g); dd = dd.divide(g) }
            return Ratio(nn, dd)
        }
    }

    fun plus(o: Ratio) = of(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d))
    fun minus(o: Ratio) = of(n.multiply(o.d).subtract(o.n.multiply(d)), d.multiply(o.d))
    fun times(o: Ratio) = of(n.multiply(o.n), d.multiply(o.d))
    fun divide(o: Ratio) = of(n.multiply(o.d), d.multiply(o.n))
    fun isInteger() = d == BigInteger.ONE
    fun isZero() = n.signum() == 0
}

/** 把数字文本解析成有理数（纯 BigInteger，不依赖 BigDecimal）。 */
private fun textToRatio(s: String): Ratio? {
    if (s.isEmpty() || s == "." || s == "-") return null
    var t = s
    var neg = false
    if (t.startsWith("-")) { neg = true; t = t.substring(1) }
    if (t.isEmpty()) return null
    if (t.count { it == '.' } > 1) return null
    if (!t.all { it.isDigit() || it == '.' }) return null
    val dot = t.indexOf('.')
    val intPart = if (dot >= 0) t.substring(0, dot) else t
    val fracPart = if (dot >= 0) t.substring(dot + 1) else ""
    if (intPart.isEmpty() && fracPart.isEmpty()) return null
    val digits = intPart + fracPart
    if (digits.isEmpty()) return null
    val n = BigInteger(digits)
    val den = BigInteger.TEN.pow(fracPart.length)
    return try {
        Ratio.of(if (neg) n.negate() else n, den)
    } catch (e: Exception) {
        null
    }
}

/** 递归下降求值：因子（数字/分数/括号/正负号） → 乘除 → 加减。 */
private fun evalSlot(items: List<FItem>): Ratio? {
    var pos = 0
    fun factor(): Ratio? {
        if (pos >= items.size) return null
        val it = items[pos]
        if (it is FItem.Op && (it.sym == "-" || it.sym == "+")) {
            pos++
            val v = factor() ?: return null
            return if (it.sym == "-") Ratio.of(v.n.negate(), v.d) else v
        }
        return when (it) {
            is FItem.Num -> { pos++; textToRatio(it.text) }
            is FItem.FracItem -> {
                pos++
                val num = evalSlot(it.num) ?: return null
                val den = evalSlot(it.den) ?: return null
                if (den.isZero()) null else try { num.divide(den) } catch (e: ArithmeticException) { null }
            }
            is FItem.ParenItem -> { pos++; evalSlot(it.inner) }
            else -> null
        }
    }

    fun term(): Ratio? {
        var v = factor() ?: return null
        while (pos < items.size) {
            val it = items[pos]
            if (it is FItem.Op && (it.sym == "×" || it.sym == "÷")) {
                pos++
                val r = factor() ?: return null
                v = if (it.sym == "×") v.times(r) else { if (r.isZero()) return null; v.divide(r) }
            } else break
        }
        return v
    }

    fun expr(): Ratio? {
        var v = term() ?: return null
        while (pos < items.size) {
            val it = items[pos]
            if (it is FItem.Op && (it.sym == "+" || it.sym == "-")) {
                pos++
                val r = term() ?: return null
                v = if (it.sym == "+") v.plus(r) else v.minus(r)
            } else break
        }
        return v
    }

    val r = expr()
    return if (pos == items.size) r else null
}

/* ===================== 对外入口 ===================== */

/**
 * 求值入口。
 *
 * @param tokens 表达式按键序列（数字 / 运算符 / 堆叠分数 / 括号组成的树）
 * @return 已约分的有理数结果（分子、分母），或 [RationalResult.Error]
 */
fun evaluate(tokens: List<FItem>): RationalResult {
    val r = evalSlot(tokens) ?: return RationalResult.Error
    return RationalResult.Ok(r.n, r.d)
}

/** 把表达式树还原成可读文本（历史记录用）。 */
fun exprText(items: List<FItem>): String = items.joinToString("") {
    when (it) {
        is FItem.Num -> it.text
        is FItem.Op -> it.sym
        is FItem.FracItem -> "(${exprText(it.num)})/(${exprText(it.den)})"
        is FItem.ParenItem -> "(${exprText(it.inner)})"
    }
}
