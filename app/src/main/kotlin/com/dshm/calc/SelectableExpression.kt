package com.dshm.calc

import androidx.compose.ui.text.TextRange
import java.util.IdentityHashMap

/** A left operand of a power or postfix operator needs an explicit boundary in these cases. */
internal fun requiresVisibleGrouping(items: List<FItem>): Boolean = when (val only = items.singleOrNull()) {
    is FItem.Num -> only.text.startsWith('-') || only.text.startsWith('+')
    is FItem.FracItem, is FItem.Power -> true
    null -> items.size > 1
    else -> false
}

/** A selectable text view of the editor tree. Offsets are valid until the next edit. */
internal class SelectableExpression(editor: ExpressionEditor) {
    private val nodes = IdentityHashMap<FItem, TextRange>()
    private val slots = IdentityHashMap<MutableList<FItem>, TextRange>()
    private val delimiters = mutableMapOf<Int, FItem>()
    val text = buildString {
        if (editor.root.isEmpty()) append('0') else appendSlot(editor.root)
    }
    val all get() = TextRange(0, text.length)
    fun rangeOf(item: FItem?): TextRange = nodes[item] ?: all

    private fun StringBuilder.appendSlot(slot: MutableList<FItem>) {
        val slotStart = length
        for (item in slot) {
            val start = length
            when (item) {
                is FItem.Num -> append(item.text)
                is FItem.Op -> append(item.sym)
                is FItem.Constant -> append(item.kind.label)
                is FItem.ParenItem -> {
                    delimiters[length] = item
                    append('(')
                    appendSlot(item.inner)
                    delimiters[length] = item
                    append(')')
                }
                is FItem.FracItem -> {
                    // A fraction is one operand, including to the right of another division.
                    if (slot.size > 1) append('(')
                    appendFractionPart(item.num)
                    delimiters[length] = item
                    append('/')
                    appendFractionPart(item.den)
                    if (slot.size > 1) append(')')
                }
                is FItem.Power -> {
                    appendOperand(item.base)
                    appendSyntax("^", item)
                    appendOperand(item.exponent)
                }
                is FItem.Root -> {
                    val degree = item.degree
                    appendSyntax(if (degree == null) "sqrt(" else "root(", item)
                    if (degree != null) {
                        appendSlot(degree)
                        appendSyntax(",", item)
                    }
                    appendSlot(item.radicand)
                    appendSyntax(")", item)
                }
                is FItem.Function -> {
                    if (item.kind.postfix) {
                        appendOperand(item.argument)
                        appendSyntax(item.kind.label, item)
                    } else {
                        appendSyntax(item.kind.label + "(", item)
                        appendSlot(item.argument)
                        appendSyntax(")", item)
                    }
                }
            }
            nodes[item] = TextRange(start, length)
        }
        slots[slot] = TextRange(slotStart, length)
    }

    private fun StringBuilder.appendSyntax(value: String, owner: FItem.Template) {
        for (ch in value) {
            delimiters[length] = owner
            append(ch)
        }
    }

    private fun StringBuilder.appendOperand(part: MutableList<FItem>) {
        val group = part.isEmpty() || requiresVisibleGrouping(part)
        if (group) append('(')
        appendSlot(part)
        if (group) append(')')
    }

    private fun StringBuilder.appendFractionPart(part: MutableList<FItem>) {
        val only = part.singleOrNull()
        // Fractions flatten to '/'. A division in either child therefore needs grouping.
        val group = only == null || only is FItem.FracItem || only is FItem.Op
        if (group) append('(')
        appendSlot(part)
        if (group) append(')')
    }

    /** Put the keypad insertion point at the selected text, preserving surviving groups. */
    fun prepareEdit(editor: ExpressionEditor, selection: TextRange) {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        if (start == 0 && end == text.length) {
            editor.clear()
            return
        }
        val delimiter = delimiters[start]?.takeIf { owner ->
            end > start && (start until end).all { delimiters[it] === owner }
        }
        // An interior offset disambiguates a group from an adjacent number ending at its start.
        val anchor = if (delimiter != null) nodes.getValue(delimiter).start + 1 else start
        placeCursor(editor, anchor, end, editor.root, emptyList(), delimiter)
        if (delimiter != null) {
            unwrap(editor, delimiter, text[start] == ')')
            return
        }
        if (end > start) {
            removeRange(editor.root, start, end)
            val current = editor.slot.getOrNull(editor.index)
            if (current is FItem.Num && editor.charOff == current.text.length && editor.slot.getOrNull(editor.index + 1) is FItem.Num) {
                editor.index++
                editor.charOff = 0
            }
            editor.mergeNumbersAtCursor()
        }
    }

    private fun placeCursor(
        editor: ExpressionEditor, offset: Int, end: Int, slot: MutableList<FItem>,
        path: List<SlotStep>, delimiter: FItem?
    ) {
        for ((index, item) in slot.withIndex()) {
            val range = nodes.getValue(item)
            // A template may start at the same offset as a child (e.g. the numerator in 3/4).
            val startsInChild = item is FItem.Template && offset == range.start && end < range.end &&
                item.fields.any { slots.getValue(it.items).start == offset }
            if (item === delimiter || (offset <= range.start && !startsInChild)) {
                editor.setCursor(path, index, 0)
                return
            }
            if (item is FItem.Num && offset <= range.end) {
                editor.setCursor(path, index, offset - range.start)
                return
            }
            if (offset < range.end) {
                if (item is FItem.Template) {
                    val visualOrder = item.fields.sortedBy { slots.getValue(it.items).start }
                    val field = visualOrder.firstOrNull { offset <= slots.getValue(it.items).end } ?: visualOrder.last()
                    placeCursor(editor, offset, end, field.items, path + SlotStep(index, field.role), delimiter)
                } else {
                    editor.setCursor(path, index, 0)
                }
                return
            }
        }
        editor.setCursor(path, slot.size, 0)
    }

    private fun unwrap(editor: ExpressionEditor, group: FItem, closing: Boolean) {
        val slot = editor.slot
        val start = editor.index
        fun operand(field: MutableList<FItem>): List<FItem> =
            if (field.size > 1) listOf(FItem.ParenItem(field)) else field
        val items: List<FItem>
        when (group) {
            is FItem.ParenItem -> {
                items = group.inner
                if (closing) editor.index += items.size
            }
            is FItem.FracItem -> {
                val numerator = operand(group.num)
                items = numerator + operand(group.den)
                editor.index += numerator.size
            }
            is FItem.Power -> {
                val base = operand(group.base)
                items = base + operand(group.exponent)
                editor.index += base.size
            }
            is FItem.Function -> {
                items = group.argument
                if (group.kind.postfix || closing) editor.index += items.size
            }
            is FItem.Root -> {
                val degree = group.degree?.let(::operand).orEmpty()
                items = degree + if (degree.isEmpty()) group.radicand else operand(group.radicand)
                editor.index += if (closing) items.size else degree.size
            }
            else -> return
        }
        slot.removeAt(start)
        slot.addAll(start, items)
        // Unwrapping can put digits together at either edge as well as at the fraction slash.
        for (i in minOf(start + items.size, slot.lastIndex) downTo maxOf(start, 1)) {
            val left = slot[i - 1] as? FItem.Num ?: continue
            val right = slot[i] as? FItem.Num ?: continue
            if (editor.index == i) editor.charOff += left.text.length
            if (editor.index >= i) editor.index--
            left.text += right.text
            slot.removeAt(i)
        }
    }

    private fun removeRange(slot: MutableList<FItem>, start: Int, end: Int) {
        for (index in slot.indices.reversed()) {
            val item = slot[index]
            val range = nodes.getValue(item)
            if (range.end <= start || range.start >= end) continue
            if (start <= range.start && range.end <= end) {
                slot.removeAt(index)
            } else when (item) {
                is FItem.Num -> item.text = item.text.removeRange(
                    (start - range.start).coerceAtLeast(0), (end - range.start).coerceAtMost(item.text.length)
                )
                is FItem.Template -> item.fields.forEach { removeRange(it.items, start, end) }
                is FItem.Op, is FItem.Constant -> Unit
            }
        }
    }
}
