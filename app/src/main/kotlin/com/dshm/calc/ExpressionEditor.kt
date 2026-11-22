package com.dshm.calc

internal enum class SlotRole { NUMERATOR, DENOMINATOR, CONTENT, BASE, EXPONENT, RADICAND, DEGREE, ARGUMENT }

enum class FunctionKind(val label: String, val postfix: Boolean = false) {
    SIN("sin"), COS("cos"), TAN("tan"), ASIN("asin"), ACOS("acos"), ATAN("atan"),
    LN("ln"), LOG("log"), ABS("abs"), FACTORIAL("!", true), PERCENT("%", true)
}

enum class ConstantKind(val label: String) { PI("π"), E("e") }

/** One step from an expression list to a child list of the item at [index]. */
internal data class SlotStep(val index: Int, val role: SlotRole)

internal class ExpressionField(val role: SlotRole, val items: MutableList<FItem>)

sealed class FItem {
    class Num(var text: String, val approximate: Boolean = false) : FItem()
    class Op(val sym: String) : FItem()
    class Constant(val kind: ConstantKind) : FItem()

    sealed class Template : FItem() {
        /** Input order. The renderer may place these slots in a different visual order. */
        internal abstract val fields: List<ExpressionField>
        internal open fun nextField(role: SlotRole) = fields.getOrNull(fields.indexOfFirst { it.role == role } + 1)
    }

    class FracItem(
        val num: MutableList<FItem> = mutableListOf(),
        val den: MutableList<FItem> = mutableListOf(),
        numeratorFirst: Boolean = false
    ) : Template() {
        override val fields = listOf(
            ExpressionField(SlotRole.DENOMINATOR, den),
            ExpressionField(SlotRole.NUMERATOR, num)
        ).let { if (numeratorFirst) it.reversed() else it }
    }

    class ParenItem(val inner: MutableList<FItem> = mutableListOf()) : Template() {
        override val fields = listOf(ExpressionField(SlotRole.CONTENT, inner))
    }

    class Power(
        val base: MutableList<FItem> = mutableListOf(),
        val exponent: MutableList<FItem> = mutableListOf(),
        private val fixedExponent: Boolean = false
    ) : Template() {
        override val fields = listOf(ExpressionField(SlotRole.BASE, base), ExpressionField(SlotRole.EXPONENT, exponent))
        override fun nextField(role: SlotRole) = if (fixedExponent) null else super.nextField(role)
    }

    class Root(
        val radicand: MutableList<FItem> = mutableListOf(),
        val degree: MutableList<FItem>? = null,
        private val fixedDegree: Boolean = false
    ) : Template() {
        override val fields = listOfNotNull(
            ExpressionField(SlotRole.RADICAND, radicand), degree?.let { ExpressionField(SlotRole.DEGREE, it) }
        )
        override fun nextField(role: SlotRole) = if (fixedDegree) null else super.nextField(role)
    }

    class Function(val kind: FunctionKind, val argument: MutableList<FItem> = mutableListOf()) : Template() {
        override val fields = listOf(ExpressionField(SlotRole.ARGUMENT, argument))
    }
}

internal class ExpressionEditor {
    val root = mutableListOf<FItem>()
    var slot: MutableList<FItem> = root
        private set
    var path: List<SlotStep> = emptyList()
        private set
    var index = 0
    var charOff = 0

    fun inFraction() = path.any { it.role == SlotRole.NUMERATOR || it.role == SlotRole.DENOMINATOR }

    private fun resolveSlot(steps: List<SlotStep>): MutableList<FItem>? {
        var field = root
        for (step in steps) {
            val owner = field.getOrNull(step.index) as? FItem.Template ?: return null
            field = owner.fields.firstOrNull { it.role == step.role }?.items ?: return null
        }
        return field
    }

    /** A stale or invalid address leaves the current insertion point intact. */
    fun setCursor(path: List<SlotStep>, index: Int, charOff: Int): Boolean {
        val target = resolveSlot(path) ?: return false
        val length = (target.getOrNull(index) as? FItem.Num)?.text?.length ?: 0
        if (index !in 0..target.size || charOff !in 0..length) return false
        this.path = path.toList()
        this.slot = target
        this.index = index
        this.charOff = charOff
        return true
    }

    fun moveToEnd() {
        setCursor(emptyList(), root.size, 0)
    }

    fun clear() {
        root.clear()
        moveToEnd()
    }

    fun replaceWithValue(item: FItem) {
        root.clear()
        root.add(item)
        moveToEnd()
    }

    /** Insert unsigned digits at the caret. Other historical values remain complete operands. */
    fun insertValue(item: FItem) {
        val digits = (item as? FItem.Num)?.text?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        var at = historyInsertionIndex(digits != null)
        val left = slot.getOrNull(at - 1)
        val right = slot.getOrNull(at)
        val inserted = if (digits != null && left is FItem.Num) {
            left.text += digits
            left
        } else {
            if (left != null && left !is FItem.Op) {
                slot.add(at, FItem.Op("×"))
                at++
            }
            slot.add(at, item)
            at++
            item
        }
        if (digits != null && inserted is FItem.Num) {
            index = at - 1
            charOff = inserted.text.length
            if (right is FItem.Num && !right.text.startsWith('-') &&
                !(inserted.text.contains('.') && right.text.contains('.'))) {
                inserted.text += right.text
                slot.removeAt(at)
                return
            }
        } else {
            index = at
            charOff = 0
        }
        if (right != null && right !is FItem.Op) slot.add(at, FItem.Op("×"))
    }

    private fun historyInsertionIndex(insertingDigits: Boolean): Int {
        val number = slot.getOrNull(index) as? FItem.Num
        if (!insertingDigits && number != null && charOff in 1 until number.text.length) {
            // A sign or decimal point alone cannot be multiplied. Keep it with its digits.
            if (number.text.take(charOff).none(Char::isDigit)) return index
            if (number.text.drop(charOff).none(Char::isDigit)) return index + 1
        }
        return splitAtCursor()
    }

    fun insertChar(c: Char) {
        val cur = slot.getOrNull(index)
        if (cur is FItem.Num) {
            val off = charOff.coerceIn(0, cur.text.length)
            cur.text = cur.text.substring(0, off) + c + cur.text.substring(off)
            charOff = off + 1
        } else {
            val previous = slot.getOrNull(index - 1) as? FItem.Num
            if (previous == null) {
                val left = slot.getOrNull(index - 1)
                if (left != null && left !is FItem.Op) {
                    slot.add(index, FItem.Op("×"))
                    index++
                }
                slot.add(index, FItem.Num(c.toString()))
                if (cur is FItem.Power) slot.add(index + 1, FItem.Op("×"))
                charOff = 1
            } else {
                index--
                previous.text += c
                charOff = previous.text.length
            }
        }
    }

    fun insertDecimal(): Boolean {
        val number = slot.getOrNull(index) as? FItem.Num ?: slot.getOrNull(index - 1) as? FItem.Num
        if (number?.text?.contains('.') == true) return false
        if (number == null) insertChar('0')
        insertChar('.')
        return true
    }

    private fun splitAtCursor(): Int {
        val number = slot.getOrNull(index) as? FItem.Num ?: return index
        val offset = charOff.coerceIn(0, number.text.length)
        if (offset == 0) return index
        if (offset < number.text.length) {
            slot.add(index + 1, FItem.Num(number.text.substring(offset)))
            number.text = number.text.substring(0, offset)
        }
        return index + 1
    }

    fun insertOp(sym: String) {
        val at = splitAtCursor()
        slot.add(at, FItem.Op(sym))
        index = at + 1
        charOff = 0
    }

    fun backspace() {
        val cur = slot.getOrNull(index)
        if (cur is FItem.Num && charOff > 0) {
            cur.text = cur.text.removeRange(charOff - 1, charOff)
            charOff--
            if (cur.text.isEmpty()) {
                slot.removeAt(index)
                mergeNumbersAtCursor()
            }
            return
        }
        if (index == 0) {
            val step = path.lastOrNull() ?: return
            setCursor(path.dropLast(1), step.index, 0)
            slot.removeAt(index)
        } else {
            index--
            val prev = slot[index]
            if (prev is FItem.Num && prev.text.length > 1) {
                prev.text = prev.text.dropLast(1)
                charOff = prev.text.length
                return
            }
            slot.removeAt(index)
        }
        charOff = 0
        mergeNumbersAtCursor()
    }

    fun mergeNumbersAtCursor() {
        val left = slot.getOrNull(index - 1) as? FItem.Num ?: return
        val right = slot.getOrNull(index) as? FItem.Num ?: return
        charOff = left.text.length + charOff.coerceIn(0, right.text.length)
        left.text += right.text
        slot.removeAt(index)
        index--
    }

    fun newFraction() = insertTemplate(FItem.FracItem())
    fun newParen() = insertTemplate(FItem.ParenItem(), 32)

    fun closeParenthesis(): Boolean {
        for (level in path.indices.reversed()) {
            val step = path[level]
            val parent = path.take(level)
            val owner = resolveSlot(parent)?.getOrNull(step.index)
            if (owner is FItem.ParenItem || (owner is FItem.Function && !owner.kind.postfix)) {
                return setCursor(parent, step.index + 1, 0)
            }
        }
        return false
    }

    fun newFractionFromOperand() {
        val fraction = FItem.FracItem(numeratorFirst = true)
        wrapOperand(fraction, SlotRole.NUMERATOR, SlotRole.DENOMINATOR)
    }

    fun newPower(exponent: Int? = null) {
        val power = FItem.Power(fixedExponent = exponent != null)
        if (exponent != null) power.exponent.add(FItem.Num(exponent.toString()))
        wrapOperand(power, SlotRole.BASE, if (exponent == null) SlotRole.EXPONENT else null)
    }

    fun newRoot(degree: Int? = 2) {
        val index = when (degree) {
            2 -> null
            null -> mutableListOf()
            else -> mutableListOf<FItem>(FItem.Num(degree.toString()))
        }
        val radical = FItem.Root(degree = index, fixedDegree = degree != null)
        wrapOperand(radical, SlotRole.RADICAND, if (degree == null) SlotRole.DEGREE else null)
    }

    fun newExponential(base: FItem) {
        wrapOperand(FItem.Power(base = mutableListOf(base)), SlotRole.EXPONENT, null)
    }

    fun newFunction(kind: FunctionKind) {
        val function = FItem.Function(kind)
        if (kind.postfix || kind == FunctionKind.ABS) {
            wrapOperand(function, SlotRole.ARGUMENT, null, includeUnarySigns = kind == FunctionKind.ABS)
        } else insertTemplate(function, 32)
    }

    fun insertConstant(kind: ConstantKind) = insertValue(FItem.Constant(kind))

    private fun isUnarySignAt(position: Int): Boolean {
        val symbol = (slot.getOrNull(position) as? FItem.Op)?.sym ?: return false
        return (symbol == "+" || symbol == "-") && (position == 0 || slot[position - 1] is FItem.Op)
    }

    private fun wrapOperand(template: FItem.Template, argument: SlotRole, next: SlotRole?, includeUnarySigns: Boolean = false) {
        if (path.size >= 32) return
        val candidate = if (charOff > 0 && slot.getOrNull(index) is FItem.Num) slot[index] else slot.getOrNull(index - 1)
        fun depth(item: FItem): Int = if (item is FItem.Template) {
            1 + (item.fields.maxOfOrNull { field -> field.items.maxOfOrNull(::depth) ?: 0 } ?: 0)
        } else 0
        if (candidate != null && path.size + depth(candidate) >= 32) return
        var at = historyInsertionIndex(false)
        val captured = slot.getOrNull(at - 1)?.takeUnless { it is FItem.Op }
        if (captured != null) {
            var first = at - 1
            while (includeUnarySigns && isUnarySignAt(first - 1)) first--
            val operand = slot.subList(first, at)
            template.fields.first { it.role == argument }.items.addAll(operand)
            operand.clear()
            at = first
        }
        slot.add(at, template)
        if (slot.getOrNull(at + 1)?.let { it !is FItem.Op } == true) {
            slot.add(at + 1, FItem.Op("×"))
        }
        val focus = if (captured == null) argument else next
        if (focus == null) setCursor(path, at + 1, 0)
        else setCursor(path + SlotStep(at, focus), 0, 0)
    }

    private fun insertTemplate(template: FItem.Template, depthLimit: Int = 3) {
        if (path.size >= depthLimit) return
        val at = splitAtCursor()
        slot.add(at, template)
        setCursor(path + SlotStep(at, template.fields.first().role), 0, 0)
    }

    fun nextField() {
        val step = path.lastOrNull() ?: return
        val parentPath = path.dropLast(1)
        val parent = checkNotNull(resolveSlot(parentPath))
        val owner = parent[step.index] as FItem.Template
        val next = owner.nextField(step.role)
        if (next == null) {
            setCursor(parentPath, step.index + 1, 0)
        } else {
            setCursor(parentPath + SlotStep(step.index, next.role), 0, 0)
        }
    }

    fun textLen(): Int {
        fun length(items: List<FItem>): Int = items.sumOf { item ->
            when (item) {
                is FItem.Num -> item.text.length
                is FItem.Op -> 1
                is FItem.Constant -> item.kind.label.length
                is FItem.Template -> 2 + item.fields.sumOf { length(it.items) }
            }
        }
        return length(root)
    }
}

/** Exact noninteger history entries use a/b, scientific approximations use decimal notation. */
internal fun resultToItem(s: String, approximate: Boolean = s.contains('.') || s.contains('E')): FItem {
    val slash = s.indexOf('/')
    if (slash > 0) {
        return FItem.FracItem(
            num = mutableListOf(FItem.Num(s.substring(0, slash))),
            den = mutableListOf(FItem.Num(s.substring(slash + 1)))
        )
    }
    return FItem.Num(s, approximate)
}
