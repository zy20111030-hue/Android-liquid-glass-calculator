package com.dshm.calc

import androidx.compose.ui.text.TextRange
import java.util.IdentityHashMap

/** A selectable text view of the editor tree. Offsets are valid until the next edit. */
internal class SelectableExpression(editor: FracEditor) {
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
            }
            nodes[item] = TextRange(start, length)
        }
        slots[slot] = TextRange(slotStart, length)
    }

    private fun StringBuilder.appendFractionPart(part: MutableList<FItem>) {
        val only = part.singleOrNull()
        val group = only !is FItem.Num && only !is FItem.ParenItem
        if (group) append('(')
        appendSlot(part)
        if (group) append(')')
    }

    /** Put the keypad insertion point at the selected text, preserving surviving groups. */
    fun prepareEdit(editor: FracEditor, selection: TextRange) {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        if (start == 0 && end == text.length) {
            editor.clear()
            return
        }
        val delimiter = if (end == start + 1) delimiters[start] else null
        // An interior offset disambiguates a group from an adjacent number ending at its start.
        val anchor = if (delimiter != null) nodes.getValue(delimiter).start + 1 else start
        placeCursor(editor, anchor, end, editor.root, emptyList(), emptyList(), delimiter)
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
        editor: FracEditor, offset: Int, end: Int, slot: MutableList<FItem>,
        fractions: List<FracFrame>, parentheses: List<ParenFrame>, delimiter: FItem?
    ) {
        for ((index, item) in slot.withIndex()) {
            val range = nodes.getValue(item)
            // A simple fraction starts at the same text offset as its numerator.
            // Replacing just that numerator must keep the caret inside the fraction.
            val startsInNumerator = item is FItem.FracItem && offset == range.start &&
                slots.getValue(item.num).start == offset && end < range.end
            if (item === delimiter || (offset <= range.start && !startsInNumerator)) {
                editor.setCursor(slot, index, 0, fractions, parentheses)
                return
            }
            if (item is FItem.Num && offset <= range.end) {
                editor.setCursor(slot, index, offset - range.start, fractions, parentheses)
                return
            }
            if (offset < range.end) {
                when (item) {
                    is FItem.ParenItem -> placeCursor(editor, offset, end, item.inner, fractions, parentheses + ParenFrame(slot, index, item), delimiter)
                    is FItem.FracItem -> {
                        val inNumerator = offset <= slots.getValue(item.num).end
                        val part = if (inNumerator) item.num else item.den
                        placeCursor(editor, offset, end, part, fractions + FracFrame(slot, index, item, inNumerator), parentheses, delimiter)
                    }
                    else -> editor.setCursor(slot, index, 0, fractions, parentheses)
                }
                return
            }
        }
        editor.setCursor(slot, slot.size, 0, fractions, parentheses)
    }

    private fun unwrap(editor: FracEditor, group: FItem, closing: Boolean) {
        val slot = editor.slot
        val start = editor.index
        val items: List<FItem>
        when (group) {
            is FItem.ParenItem -> {
                items = group.inner
                if (closing) editor.index += items.size
            }
            is FItem.FracItem -> {
                fun operand(part: MutableList<FItem>): List<FItem> =
                    if (part.size > 1) listOf(FItem.ParenItem(part)) else part
                val numerator = operand(group.num)
                items = numerator + operand(group.den)
                editor.index += numerator.size
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
                is FItem.ParenItem -> removeRange(item.inner, start, end)
                is FItem.FracItem -> {
                    removeRange(item.num, start, end)
                    removeRange(item.den, start, end)
                }
                is FItem.Op -> Unit
            }
        }
    }
}
