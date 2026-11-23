package com.dshm.calc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.copyText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.coerceIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The inline actions own the touch toolbar. The text field still owns its handles and semantics.
private object InlineSelectionToolbar : TextContextMenuProvider {
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) = Unit
}

@Composable
internal fun DisplaySelection(
    text: String,
    selection: TextRange,
    onSelectionChange: (TextRange) -> Unit,
    style: TextStyle,
    foreground: Color,
    darkSurface: Boolean,
    copyLabel: String,
    selectAllLabel: String,
    doneLabel: String,
    onDismiss: () -> Unit,
    interactive: Boolean
) {
    val range = selection.coerceIn(0, text.length)
    val field = remember(text) { TextFieldState(text, range) }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val externalRange by rememberUpdatedState(range)
    val reportRange by rememberUpdatedState(onSelectionChange)
    val context = LocalContext.current

    LaunchedEffect(field, interactive) { if (interactive) focus.requestFocus() }
    LaunchedEffect(field, range) {
        if (field.selection != range) field.edit { this.selection = range }
    }
    LaunchedEffect(field) {
        // BasicTextField collapses its internal selection on blur. Keep the caller's range
        // while a toolbar action or a calculator key has focus.
        snapshotFlow { if (focused) field.selection else null }.collect { current ->
            if (current != null && current != externalRange) reportRange(current)
        }
    }
    val active = if (focused) field.selection else range
    fun copySelection(): Boolean {
        val selected = if (focused) field.selection else externalRange
        if (selected.collapsed) return false
        val value = text.substring(selected.min, selected.max)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel, value))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "✓ $copyLabel", Toast.LENGTH_SHORT).show()
        }
        onDismiss()
        return true
    }

    val toolbarColor = if (darkSurface) Color(0xF52B2D32) else Color(0xF5F7F8FA)
    val toolbarInk = if (darkSurface) Color.White else Color(0xFF181B20)
    val colors = remember(darkSurface) {
        val accent = if (darkSurface) Color(0xFF9DCCFF) else Color(0xFF1766AE)
        TextSelectionColors(accent, accent.copy(alpha = 0.36f))
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(toolbarColor)
                .horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionAction(copyLabel, toolbarInk, enabled = interactive && !active.collapsed) { copySelection() }
            SelectionAction(selectAllLabel, toolbarInk, enabled = interactive && text.isNotEmpty()) {
                val all = TextRange(0, text.length)
                focus.requestFocus()
                field.edit { this.selection = all }
                onSelectionChange(all)
            }
            SelectionAction(doneLabel, toolbarInk, enabled = interactive, onClick = onDismiss)
        }
        CompositionLocalProvider(
            LocalTextContextMenuToolbarProvider provides InlineSelectionToolbar,
            LocalTextSelectionColors provides colors
        ) {
            BasicTextField(
                state = field,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .onFocusChanged {
                        if (it.isFocused && !focused && field.selection != externalRange) {
                            field.edit { this.selection = externalRange }
                        }
                        focused = it.isFocused
                    }
                    .onPreviewKeyEvent {
                        interactive && it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.C && copySelection()
                    }
                    .semantics {
                        if (!active.collapsed) copyText(label = copyLabel) { copySelection() }
                    },
                readOnly = true,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = style.copy(color = foreground, textAlign = TextAlign.End)
            )
        }
    }
    BackHandler(enabled = interactive, onBack = onDismiss)
}

@Composable
private fun SelectionAction(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.clickable(enabled = enabled, role = Role.Button, indication = LocalIndication.current, interactionSource = source, onClick = onClick)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        if (focused) Spacer(Modifier.matchParentSize().padding(2.dp).border(1.dp, color, shape))
        BasicText(
            label, Modifier.padding(horizontal = 10.dp), style = TextStyle(color = if (enabled) color else color.copy(alpha = 0.5f), fontSize = 14.sp),
            maxLines = 1, softWrap = false
        )
    }
}
