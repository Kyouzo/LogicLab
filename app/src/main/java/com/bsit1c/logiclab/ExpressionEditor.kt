package com.bsit1c.logiclab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bsit1c.logic.LogicError
import com.bsit1c.logic.MAX_VARIABLES
import com.bsit1c.logic.ParseResult
import com.bsit1c.logic.Parser
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------------------------

/** Text + selection of one expression input, with undo history. Parsing is derived (and cheap). */
@Stable
class ExprFieldState(initial: String = "") {
    var text by mutableStateOf(initial)
        private set
    var selection by mutableStateOf(TextRange(initial.length))
        private set

    private val history = ArrayList<Pair<String, TextRange>>()
    private var historySize by mutableIntStateOf(0)
    val canUndo: Boolean get() = historySize > 0

    val parse: ParseResult by derivedStateOf { Parser.parse(text) }

    val textFieldValue: TextFieldValue get() = TextFieldValue(text, selection)

    private fun record() {
        history += text to selection
        if (history.size > 100) history.removeAt(0)
        historySize = history.size
    }

    fun set(newText: String, recordUndo: Boolean = true) {
        if (newText == text) return
        if (recordUndo) record()
        text = newText
        selection = TextRange(newText.length)
    }

    fun onSystemEdit(v: TextFieldValue) {
        if (v.text != text) record()
        text = v.text
        selection = v.selection
    }

    fun placeCursor(offset: Int) {
        selection = TextRange(offset.coerceIn(0, text.length))
    }

    fun moveCursor(delta: Int) = placeCursor(selection.end + delta)

    /** Inserts a key's text at the cursor, avoiding doubled spaces around operators. */
    fun insert(raw: String) {
        record()
        val start = selection.min
        val end = selection.max
        var s = raw
        if (s.startsWith(" ") && (start == 0 || text[start - 1] == ' ' || text[start - 1] == '(')) s = s.trimStart()
        if (s.endsWith(" ") && end < text.length && text[end] == ' ') s = s.trimEnd()
        text = text.substring(0, start) + s + text.substring(end)
        selection = TextRange(start + s.length)
    }

    /** Deletes the selection, or the symbol before the cursor together with its padding spaces. */
    fun backspace() {
        if (!selection.collapsed) {
            record()
            text = text.removeRange(selection.min, selection.max)
            selection = TextRange(selection.min)
            return
        }
        val c = selection.end
        if (c == 0) return
        record()
        var j = c
        while (j > 0 && text[j - 1] == ' ') j--
        if (j > 0) {
            val removed = text[j - 1]
            j--
            if (removed in "∧∨→↔") while (j > 0 && text[j - 1] == ' ') j--
        }
        text = text.removeRange(j, c)
        selection = TextRange(j)
    }

    fun clear() {
        if (text.isEmpty()) return
        record()
        text = ""
        selection = TextRange(0)
    }

    fun undo() {
        if (history.isEmpty()) return
        val (t, s) = history.removeAt(history.lastIndex)
        historySize = history.size
        text = t
        selection = s
    }

    companion object {
        val Saver: Saver<ExprFieldState, String> = Saver(save = { it.text }, restore = { ExprFieldState(it) })
    }
}

@Composable
fun rememberExprField(initial: String = ""): ExprFieldState =
    rememberSaveable(saver = ExprFieldState.Saver) { ExprFieldState(initial) }

/** Which field the shared logic keyboard types into, and how input is currently being entered. */
@Stable
class EditorController {
    var active: ExprFieldState? by mutableStateOf(null)
    var keyboardVisible by mutableStateOf(true)
    /** When true the phone's own keyboard is used (for other letters); logic symbols stay in a strip. */
    var systemMode by mutableStateOf(false)

    fun activate(field: ExprFieldState) {
        active = field
        keyboardVisible = true
    }

    fun hide() {
        keyboardVisible = false
        systemMode = false
    }
}

@Composable
fun rememberEditorController(initial: ExprFieldState? = null): EditorController =
    remember { EditorController().also { it.active = initial } }

// ---------------------------------------------------------------------------------------------
// Expression display ("calculator screen")
// ---------------------------------------------------------------------------------------------

/**
 * A large, monospaced expression display with its own caret. Typing happens through the logic
 * keyboard; in system-keyboard mode it becomes a normal text field.
 */
@Composable
fun ExpressionInput(
    state: ExprFieldState,
    controller: EditorController,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "e.g. (P ∧ ¬Q) → R",
    showStatus: Boolean = true,
    compact: Boolean = false,
) {
    val active = controller.active === state && controller.keyboardVisible
    val error = (state.parse as? ParseResult.Failure)?.error?.takeIf { state.text.isNotBlank() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (active && controller.systemMode) {
            SystemField(state, placeholder)
        } else {
            CaretDisplay(state, active, error, placeholder, compact) { controller.activate(state) }
        }
        if (showStatus) ParseStatus(state, error)
    }
}

@Composable
private fun SystemField(state: ExprFieldState, placeholder: String) {
    val focus = remember { FocusRequester() }
    OutlinedTextField(
        value = state.textFieldValue,
        onValueChange = { state.onSystemEdit(it) },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        textStyle = FormulaStyle.large.copy(color = MaterialTheme.colorScheme.onSurface),
        placeholder = { Text(placeholder, style = FormulaStyle.large) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
        shape = RoundedCornerShape(16.dp),
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun CaretDisplay(
    state: ExprFieldState,
    active: Boolean,
    error: LogicError?,
    placeholder: String,
    compact: Boolean,
    onActivate: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var caretOn by remember { mutableStateOf(true) }
    LaunchedEffect(active, state.text, state.selection) {
        caretOn = true
        while (active) {
            delay(530)
            caretOn = !caretOn
        }
    }
    val annotated = remember(state.text, error, cs) {
        buildAnnotatedString {
            val t = state.text
            if (error != null && error.hasPosition && error.start < t.length) {
                val s = error.start.coerceIn(0, t.length)
                val e = error.end.coerceIn(s, t.length)
                append(t.substring(0, s))
                withStyle(
                    SpanStyle(background = cs.errorContainer, color = cs.onErrorContainer, textDecoration = TextDecoration.Underline)
                ) { append(t.substring(s, e)) }
                append(t.substring(e))
            } else {
                append(t)
            }
        }
    }
    val borderColor = when {
        error != null -> cs.error
        active -> cs.primary
        else -> cs.outlineVariant
    }
    val caretColor = cs.primary
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 52.dp else 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceContainerLowest)
            .border(if (active) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .pointerInput(state) {
                detectTapGestures { pos ->
                    onActivate()
                    val l = layout
                    if (l != null) {
                        // The text sits inside 16dp horizontal / 14dp vertical padding.
                        val local = Offset(pos.x - 16.dp.toPx(), pos.y - 14.dp.toPx())
                        state.placeCursor(l.getOffsetForPosition(local))
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "Expression input: ${state.text.ifEmpty { "empty" }}" },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (state.text.isEmpty()) {
            Text(placeholder, style = if (compact) FormulaStyle.medium else FormulaStyle.large, color = cs.outline)
        }
        Text(
            annotated,
            style = if (compact) FormulaStyle.medium else FormulaStyle.large,
            color = cs.onSurface,
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    val l = layout
                    if (active && caretOn && l != null) {
                        val off = state.selection.end.coerceIn(0, state.text.length)
                        val r = l.getCursorRect(off)
                        drawLine(
                            caretColor,
                            Offset(r.left + 1f, r.top + 2f),
                            Offset(r.left + 1f, r.bottom - 2f),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                },
        )
    }
}

@Composable
private fun ParseStatus(state: ExprFieldState, error: LogicError?) {
    val cs = MaterialTheme.colorScheme
    val lab = Lab.colors
    val parsed = (state.parse as? ParseResult.Success)?.expr
    val (text, color) = when {
        state.text.isBlank() -> "Use the logic keys below — or tap ABC for the phone keyboard." to cs.onSurfaceVariant
        error != null -> error.message to cs.error
        parsed != null -> {
            val n = parsed.variables.size
            val vars = if (n == 0) "no variables" else "variables ${parsed.variables.joinToString(", ")}"
            if (n > MAX_VARIABLES) {
                "Well-formed, but $n variables exceeds the limit of $MAX_VARIABLES." to cs.error
            } else {
                "✓ Well-formed · $vars · ${1 shl n} row${if (n == 0) "" else "s"}" to lab.success
            }
        }
        else -> "" to cs.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(horizontal = 4.dp))
}

// ---------------------------------------------------------------------------------------------
// Logic keyboard
// ---------------------------------------------------------------------------------------------

private enum class KeyKind { VARIABLE, OPERATOR, CONSTANT, CONTROL }

/** Docked at the bottom of editor screens; types into [EditorController.active]. */
@Composable
fun LogicKeyboardDock(controller: EditorController, extraVariables: Boolean = false) {
    val focusManager = LocalFocusManager.current
    val field = controller.active
    Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
        AnimatedVisibility(
            visible = field != null && controller.keyboardVisible,
            enter = expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            if (field == null) return@AnimatedVisibility
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                if (controller.systemMode) {
                    SymbolStrip(field) {
                        controller.systemMode = false
                        focusManager.clearFocus()
                    }
                } else {
                    FullKeyboard(field, controller, extraVariables)
                }
            }
        }
        if (field != null && !controller.keyboardVisible) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                TextButton(
                    onClick = { controller.keyboardVisible = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Icon(LabIcons.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Show logic keyboard")
                }
            }
        }
    }
}

@Composable
private fun FullKeyboard(field: ExprFieldState, controller: EditorController, extraVariables: Boolean) {
    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyRow {
            for (v in if (extraVariables) listOf("P", "Q", "R", "S", "U") else listOf("P", "Q", "R", "S")) {
                Key(v, KeyKind.VARIABLE, "Variable $v") { field.insert(v) }
            }
            Key("(", KeyKind.CONTROL, "Open parenthesis") { field.insert("(") }
            Key(")", KeyKind.CONTROL, "Close parenthesis") { field.insert(")") }
            Key(icon = LabIcons.Backspace, kind = KeyKind.CONTROL, description = "Delete") { field.backspace() }
        }
        KeyRow {
            Key("¬", KeyKind.OPERATOR, "Not") { field.insert("¬") }
            Key("∧", KeyKind.OPERATOR, "And") { field.insert(" ∧ ") }
            Key("∨", KeyKind.OPERATOR, "Or") { field.insert(" ∨ ") }
            Key("→", KeyKind.OPERATOR, "Implies") { field.insert(" → ") }
            Key("↔", KeyKind.OPERATOR, "If and only if") { field.insert(" ↔ ") }
            Key("T", KeyKind.CONSTANT, "True") { field.insert("T") }
            Key("F", KeyKind.CONSTANT, "False") { field.insert("F") }
        }
        KeyRow {
            Key(icon = LabIcons.Left, kind = KeyKind.CONTROL, description = "Cursor left") { field.moveCursor(-1) }
            Key(icon = LabIcons.Right, kind = KeyKind.CONTROL, description = "Cursor right") { field.moveCursor(1) }
            Key(icon = LabIcons.Undo, kind = KeyKind.CONTROL, description = "Undo", enabled = field.canUndo) { field.undo() }
            Key("CLR", KeyKind.CONTROL, "Clear", enabled = field.text.isNotEmpty()) { field.clear() }
            Key("ABC", KeyKind.CONTROL, "Use phone keyboard") { controller.systemMode = true }
            Key(icon = LabIcons.KeyboardHide, kind = KeyKind.CONTROL, description = "Hide keyboard") {
                controller.keyboardVisible = false
            }
        }
    }
}

@Composable
private fun SymbolStrip(field: ExprFieldState, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("¬" to "¬", "∧" to " ∧ ", "∨" to " ∨ ", "→" to " → ", "↔" to " ↔ ", "(" to "(", ")" to ")")
                .forEach { (label, ins) ->
                    Box(
                        Modifier
                            .size(width = 44.dp, height = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { field.insert(ins) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = FormulaStyle.medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
        }
        TextButton(onClick = onDone) { Text("Logic keys") }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
private fun RowScope.Key(
    label: String? = null,
    kind: KeyKind,
    description: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (kind) {
        KeyKind.VARIABLE -> cs.surfaceContainerLowest to cs.onSurface
        KeyKind.OPERATOR -> cs.primaryContainer to cs.onPrimaryContainer
        KeyKind.CONSTANT -> cs.secondaryContainer to cs.onSecondaryContainer
        KeyKind.CONTROL -> cs.surfaceContainerHighest to cs.onSurfaceVariant
    }
    val alpha = if (enabled) 1f else 0.38f
    Box(
        Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(20.dp))
        } else if (label != null) {
            Text(
                label,
                style = if (label.length > 1) MaterialTheme.typography.labelLarge else FormulaStyle.large,
                color = fg.copy(alpha = alpha),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Example / recent chips
// ---------------------------------------------------------------------------------------------

@Composable
fun FormulaChips(title: String, items: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { e ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clickable { onPick(e) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(e, style = FormulaStyle.small, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** Subtle hairline used between stacked blocks. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
}

internal val Transparent = Color(0x00000000)
