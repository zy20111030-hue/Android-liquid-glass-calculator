package com.dshm.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel

data class HistoryItem(val expr: String, val result: String)

internal enum class ExpressionPhase { Editing, Result, Error }

/** The same editor and keypad commands survive a change of Activity configuration. */
internal class ExpressionSession : ViewModel() {
    var editor by mutableStateOf(ExpressionEditor())
        private set
    var phase by mutableStateOf(ExpressionPhase.Editing)
        private set
    var completedExpression by mutableStateOf<ExpressionEditor?>(null)
        private set
    var selection by mutableStateOf<SelectableExpression?>(null)
        private set
    var selectedRange by mutableStateOf(TextRange.Zero)
        private set
    var angleMode by mutableStateOf(AngleMode.DEG)
        private set
    var inverse by mutableStateOf(false)
        private set
    var memory by mutableStateOf<RationalResult.Ok?>(null)
        private set
    private var selectingCompletedExpression = false

    // Moving the caret invalidates gestures and restarts blinking without remeasuring the tree.
    var revision by mutableIntStateOf(0)
        private set
    var contentRevision by mutableIntStateOf(0)
        private set

    fun moveCaret(place: (ExpressionEditor) -> Unit) {
        dismissSelection()
        place(editor)
        phase = ExpressionPhase.Editing
        revision++
    }

    fun select(item: FItem? = null, beforeFirstTap: ExpressionPhase = phase) {
        if (beforeFirstTap != ExpressionPhase.Editing) {
            editor.moveToEnd()
            phase = beforeFirstTap
        }
        val document = SelectableExpression(editor)
        selectedRange = if (phase == ExpressionPhase.Editing) document.rangeOf(item) else document.all
        selectingCompletedExpression = false
        selection = document
    }

    fun selectCompleted(item: FItem?) {
        val source = completedExpression ?: return
        val document = SelectableExpression(source)
        selectedRange = document.rangeOf(item)
        selectingCompletedExpression = true
        selection = document
    }

    fun updateSelection(range: TextRange) { selectedRange = range }
    fun dismissSelection() {
        selection = null
        selectingCompletedExpression = false
    }

    fun forgetCompletedExpression() {
        if (selectingCompletedExpression) dismissSelection()
        completedExpression = null
    }

    private fun prepareInput(startNew: Boolean = false): Boolean {
        val document = selection
        if (document != null && !selectingCompletedExpression) {
            document.prepareEdit(editor, selectedRange)
            dismissSelection()
            return !selectedRange.collapsed
        }
        dismissSelection()
        if (phase == ExpressionPhase.Error || (startNew && phase == ExpressionPhase.Result)) editor.clear()
        return false
    }

    private fun edited() {
        phase = ExpressionPhase.Editing
        completedExpression = null
        revision++
        contentRevision++
    }

    fun insertDigit(digit: Char) {
        prepareInput(startNew = true)
        editor.insertChar(digit)
        edited()
    }

    fun insertDecimal() {
        val replaced = prepareInput(startNew = true)
        if (editor.insertDecimal() || replaced) edited()
    }

    fun insertOperator(symbol: String) {
        prepareInput()
        editor.insertOp(symbol)
        edited()
    }

    fun insertFraction() {
        prepareInput(startNew = true)
        editor.newFraction()
        edited()
    }

    fun insertParentheses() {
        prepareInput(startNew = true)
        editor.newParen()
        edited()
    }

    fun closeParenthesis() {
        if (!editor.closeParenthesis()) return
        dismissSelection()
        revision++
    }

    fun scientificFraction() {
        prepareInput()
        editor.newFractionFromOperand()
        edited()
    }

    fun power(exponent: Int? = null) {
        prepareInput()
        editor.newPower(exponent)
        edited()
    }

    fun exponential(base: FItem) {
        prepareInput()
        editor.newExponential(base)
        edited()
    }

    fun root(degree: Int? = 2) {
        prepareInput()
        editor.newRoot(degree)
        edited()
    }

    fun function(kind: FunctionKind) {
        prepareInput(startNew = !kind.postfix && kind != FunctionKind.ABS)
        editor.newFunction(kind)
        edited()
    }

    fun constant(kind: ConstantKind) {
        prepareInput(startNew = true)
        editor.insertConstant(kind)
        edited()
    }

    fun toggleAngleMode() {
        angleMode = if (angleMode == AngleMode.DEG) AngleMode.RAD else AngleMode.DEG
    }

    fun toggleInverse() { inverse = !inverse }
    fun clearMemory() { memory = null }

    fun addToMemory(subtract: Boolean) {
        val value = evaluate(editor.root, angleMode) as? RationalResult.Ok ?: return
        combineMemory(memory, value, subtract)?.let { memory = it }
    }

    fun recallMemory() = useResult(memory?.display() ?: "0")

    fun nextField() {
        if (editor.path.isEmpty()) return
        dismissSelection()
        editor.nextField()
        phase = ExpressionPhase.Editing
        revision++
    }

    fun backspace() {
        if (!prepareInput()) editor.backspace()
        edited()
    }

    fun clear() {
        dismissSelection()
        editor.clear()
        edited()
    }

    fun useResult(value: String) {
        prepareInput(startNew = true)
        editor.insertValue(resultToItem(value))
        edited()
    }

    fun calculate(errorLabel: String): HistoryItem? {
        dismissSelection()
        val input = editor
        val result = evaluate(input.root, angleMode)
        editor = ExpressionEditor()
        contentRevision++
        revision++
        return when (result) {
            is RationalResult.Ok -> {
                val value = result.display()
                completedExpression = input
                phase = ExpressionPhase.Result
                editor.replaceWithValue(resultToItem(value, result.isApproximate))
                HistoryItem(exprText(input.root), value)
            }
            RationalResult.Error -> {
                completedExpression = null
                phase = ExpressionPhase.Error
                editor.replaceWithValue(FItem.Num(errorLabel))
                null
            }
        }
    }
}
