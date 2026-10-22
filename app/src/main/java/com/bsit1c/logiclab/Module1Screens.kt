package com.bsit1c.logiclab

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bsit1c.logic.Classification
import com.bsit1c.logic.Classifier
import com.bsit1c.logic.Examples
import com.bsit1c.logic.Expr
import com.bsit1c.logic.MAX_VARIABLES
import com.bsit1c.logic.ParseResult
import com.bsit1c.logic.Parser
import com.bsit1c.logic.Printer
import com.bsit1c.logic.PropertyReport
import com.bsit1c.logic.StepEvaluator
import com.bsit1c.logic.StepKind
import com.bsit1c.logic.Structure
import com.bsit1c.logic.StructurePart
import com.bsit1c.logic.TruthTable
import com.bsit1c.logic.formatAssignment
import com.bsit1c.logic.tf
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Shared frame for single-expression screens
// ---------------------------------------------------------------------------------------------

@Composable
private fun EditorFrame(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    field: ExprFieldState,
    controller: EditorController,
    store: LabStore,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    belowInput: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenScaffold(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
        actions = actions,
        bottomBar = { LogicKeyboardDock(controller) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExpressionInput(field, controller)
            belowInput()
            FormulaChips("Examples", Examples.expressions, onPick = { field.set(it) })
            val history = (store.saved + store.recent).distinct().filter { it !in Examples.expressions }.take(10)
            FormulaChips("Saved & recent", history, onPick = { field.set(it) })
            content()
        }
    }
}

/** Returns the most recent well-formed expression (debounced), so results do not flicker while typing. */
@Composable
private fun rememberLiveExpr(field: ExprFieldState, store: LabStore): Expr? {
    val current = (field.parse as? ParseResult.Success)?.expr
    var shown by remember { mutableStateOf(current) }
    LaunchedEffect(current) {
        if (current != null && current != shown) {
            delay(250)
            shown = current
            store.addRecent(field.text)
        }
    }
    return shown
}

@Composable
private fun SaveAction(store: LabStore, text: String) {
    if (text.isBlank() || Parser.parse(text) !is ParseResult.Success) return
    val saved = store.isSaved(text)
    IconButton(onClick = { store.toggleSaved(text) }) {
        Icon(
            if (saved) LabIcons.BookmarkFilled else LabIcons.Bookmark,
            contentDescription = if (saved) "Remove from saved" else "Save expression",
        )
    }
}

@Composable
private fun TooManyVariables(expr: Expr) {
    Banner(
        BannerKind.WARNING,
        "Too many variables",
        body = "This expression has ${expr.variables.size} variables. LogicLab evaluates every row of the " +
            "truth table, so it supports up to $MAX_VARIABLES variables (${1 shl MAX_VARIABLES} rows) to stay responsive.",
    )
}

// ---------------------------------------------------------------------------------------------
// Logic Solver: the full worked solution
// ---------------------------------------------------------------------------------------------

private class Solution(
    val raw: String,
    val expr: Expr,
    val table: TruthTable,
    val report: PropertyReport,
    val parts: List<StructurePart>,
)

@Composable
fun SolverScreen(store: LabStore, initial: String?, onBack: () -> Unit, onNavigate: (Route) -> Unit) {
    val field = rememberExprField(initial ?: "")
    val controller = rememberEditorController(field)
    var submitted by rememberSaveable { mutableStateOf(initial ?: "") }
    LaunchedEffect(Unit) { if (initial != null) controller.keyboardVisible = false }

    val canSolve = (field.parse as? ParseResult.Success)?.expr?.let { it.variables.size <= MAX_VARIABLES } == true
    val solve = {
        if (canSolve) {
            submitted = field.text
            store.addRecent(field.text)
            controller.keyboardVisible = false
        }
    }

    EditorFrame(
        title = "Logic Solver",
        subtitle = "Worked solution with reasoning",
        onBack = onBack,
        field = field,
        controller = controller,
        store = store,
        actions = { SaveAction(store, submitted) },
        belowInput = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = solve, enabled = canSolve, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Solve")
                }
                if (submitted.isNotEmpty() && field.text != submitted) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Edited — tap Solve to update",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) {
        if (submitted.isBlank()) {
            EmptyState(
                "⊨",
                "Enter a proposition",
                "Type an expression with the logic keys, or load an example, then tap Solve. You'll get its " +
                    "structure, full truth table, classification and an explanation.",
            )
            return@EditorFrame
        }
        val parsed = Parser.parse(submitted).getOrNull() ?: return@EditorFrame
        if (parsed.variables.size > MAX_VARIABLES) { TooManyVariables(parsed); return@EditorFrame }
        val state by rememberAsync(submitted) {
            val t = TruthTable.build(parsed)
            Solution(submitted, parsed, t, Classifier.classify(t), Structure.parts(parsed))
        }
        when (val s = state) {
            is Async.Loading -> LoadingRow("Building the truth table…")
            is Async.Failed -> Banner(BannerKind.ERROR, "Could not solve", body = s.message)
            is Async.Done -> SolutionView(s.value) { onNavigate(Route(Module.STEPS, submitted)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SolutionView(s: Solution, onStepByStep: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard("Original expression", number = 1) {
            FormulaBox(s.raw.trim(), style = FormulaStyle.large)
            KeyValue("Read as", Printer.print(s.expr))
            KeyValue("Fully grouped", Printer.fullyParenthesized(s.expr))
            Structure.mainConnective(s.expr)?.let { KeyValue("Main connective", it) }
        }
        SectionCard("Identified variables", number = 2) {
            if (s.table.variables.isEmpty()) {
                Text("No variables — only the constants T and F appear, so there is a single row.",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.table.variables.forEach {
                        Pill(it, container = cs.secondaryContainer, content = cs.onSecondaryContainer)
                    }
                }
                val n = s.table.variables.size
                Text(
                    "$n distinct variable${if (n == 1) "" else "s"}, each either T or F, gives 2^$n = ${s.table.rowCount} " +
                        "possible assignments (rows).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        SectionCard("Evaluation steps", number = 3) {
            if (s.parts.isEmpty()) {
                Text("The expression is a single variable or constant, so there is nothing to combine.",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "Following precedence (¬, ∧, ∨, →, ↔) and parentheses, the expression is built from these " +
                        "parts, innermost first. Each becomes a column of the truth table.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                s.parts.forEach { p -> StructureRow(p) }
            }
            OutlinedButton(onClick = onStepByStep, modifier = Modifier.fillMaxWidth()) {
                Text("Evaluate step by step for one row")
            }
        }
        SectionCard(
            "Truth table",
            number = 4,
            trailing = { CopyShareActions("Truth table: ${Printer.print(s.expr)}") { s.table.toPlainText() } },
        ) {
            TruthTableView(s.table)
            TableLegend()
        }
        SectionCard("Final result", number = 5) {
            ClassificationBanner(s.report)
        }
        SectionCard("Explanation", number = 6) {
            Text(s.report.explanation, style = MaterialTheme.typography.bodyMedium)
            Text(
                "A tautology is true in every row, a contradiction is false in every row, and a contingency is " +
                    "neither. An expression is satisfiable when at least one row is true.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StructureRow(p: StructurePart) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (p.isMain) cs.primary else cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                p.number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (p.isMain) cs.onPrimary else cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FormulaText(p.text, style = FormulaStyle.medium)
            Text(
                p.description + if (p.isMain) " This is the main connective." else "",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ClassificationBanner(report: PropertyReport) {
    val (kind, symbol) = when (report.classification) {
        Classification.TAUTOLOGY -> BannerKind.SUCCESS to "⊤"
        Classification.CONTRADICTION -> BannerKind.ERROR to "⊥"
        Classification.CONTINGENCY -> BannerKind.INFO to "~"
    }
    Banner(
        kind,
        "$symbol  ${report.classification.title}",
        body = "True in ${report.trueRows} of ${report.totalRows} rows · " +
            if (report.satisfiable) "Satisfiable" else "Not satisfiable",
    )
}

// ---------------------------------------------------------------------------------------------
// Truth Tables
// ---------------------------------------------------------------------------------------------

@Composable
fun TruthTableScreen(store: LabStore, initial: String?, onBack: () -> Unit) {
    val field = rememberExprField(initial ?: "")
    val controller = rememberEditorController(field)
    val expr = rememberLiveExpr(field, store)
    EditorFrame("Truth Tables", "All 2ⁿ assignments", onBack, field, controller, store,
        actions = { SaveAction(store, field.text) }) {
        if (expr == null) {
            EmptyState("TF", "No table yet", "Enter a well-formed expression to generate its complete truth table.")
            return@EditorFrame
        }
        if (expr.variables.size > MAX_VARIABLES) { TooManyVariables(expr); return@EditorFrame }
        val state by rememberAsync(expr) { TruthTable.build(expr) }
        when (val s = state) {
            is Async.Loading -> LoadingRow("Generating rows…")
            is Async.Failed -> Banner(BannerKind.ERROR, "Could not build the table", body = s.message)
            is Async.Done -> {
                val t = s.value
                SectionCard(
                    Printer.print(t.expr),
                    trailing = { CopyShareActions("Truth table: ${Printer.print(t.expr)}") { t.toPlainText() } },
                ) {
                    Text(
                        "${t.variables.size} variable${if (t.variables.size == 1) "" else "s"} · ${t.rowCount} rows · " +
                            "${t.columns.size - t.variables.size} computed column${if (t.columns.size - t.variables.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TruthTableView(t, maxBodyHeight = 520.dp)
                    TableLegend()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Logical Properties
// ---------------------------------------------------------------------------------------------

@Composable
fun PropertiesScreen(store: LabStore, initial: String?, onBack: () -> Unit, onNavigate: (Route) -> Unit) {
    val field = rememberExprField(initial ?: "")
    val controller = rememberEditorController(field)
    val expr = rememberLiveExpr(field, store)
    EditorFrame("Logical Properties", "Classified from the full table", onBack, field, controller, store,
        actions = { SaveAction(store, field.text) }) {
        if (expr == null) {
            EmptyState("⊤", "Classify a proposition",
                "LogicLab checks every row of the truth table to decide whether it is a tautology, a " +
                    "contradiction or a contingency — never just a few sample rows.")
            return@EditorFrame
        }
        if (expr.variables.size > MAX_VARIABLES) { TooManyVariables(expr); return@EditorFrame }
        val state by rememberAsync(expr) { TruthTable.build(expr).let { it to Classifier.classify(it) } }
        when (val s = state) {
            is Async.Loading -> LoadingRow("Checking every row…")
            is Async.Failed -> Banner(BannerKind.ERROR, "Could not classify", body = s.message)
            is Async.Done -> {
                val (table, report) = s.value
                ClassificationBanner(report)
                SectionCard("Properties") {
                    PropertyLine("Tautology (valid)", report.classification == Classification.TAUTOLOGY, "true in every row")
                    PropertyLine("Contradiction", report.classification == Classification.CONTRADICTION, "false in every row")
                    PropertyLine("Contingency", report.classification == Classification.CONTINGENCY, "true in some rows, false in others")
                    PropertyLine("Satisfiable", report.satisfiable, "true in at least one row")
                }
                SectionCard("Why") {
                    Text(report.explanation, style = MaterialTheme.typography.bodyMedium)
                    report.exampleTrue?.let { WitnessRow("Makes it true", it, true) }
                    report.exampleFalse?.let { WitnessRow("Makes it false", it, false) }
                }
                SectionCard(
                    "Supporting truth table",
                    trailing = { CopyShareActions("Truth table: ${Printer.print(table.expr)}") { table.toPlainText() } },
                ) {
                    TruthTableView(table, rowMark = { r -> if (table.result(r)) RowMark.EMPHASIS else RowMark.NONE })
                    Text(
                        "Highlighted rows are the assignments that make the expression true.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onNavigate(Route(Module.SOLVER, field.text)) }) {
                        Text("Open full worked solution")
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyLine(name: String, holds: Boolean, meaning: String) {
    val lab = Lab.colors
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (holds) lab.successContainer else cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (holds) LabIcons.Check else LabIcons.Close,
                contentDescription = if (holds) "Yes" else "No",
                tint = if (holds) lab.success else cs.outline,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(meaning, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun WitnessRow(label: String, assignment: Map<String, Boolean>, result: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TruthBadge(result)
        Spacer(Modifier.width(10.dp))
        Text("$label: ", style = MaterialTheme.typography.bodyMedium)
        Text(formatAssignment(assignment), style = FormulaStyle.small)
    }
}

// ---------------------------------------------------------------------------------------------
// Step-by-step evaluation
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepScreen(store: LabStore, initial: String?, onBack: () -> Unit) {
    val field = rememberExprField(initial ?: "(P ∧ ¬Q) → R")
    val controller = rememberEditorController(field)
    LaunchedEffect(Unit) { if (initial != null) controller.keyboardVisible = false }
    val expr = rememberLiveExpr(field, store)
    // Truth values chosen by the student; variables not yet chosen default to T.
    var values by rememberSaveable { mutableStateOf(mapOf("Q" to false, "R" to false)) }

    EditorFrame("Step-by-Step", "Evaluate under chosen values", onBack, field, controller, store) {
        if (expr == null) {
            EmptyState("1·2", "Nothing to evaluate", "Enter a well-formed expression, choose truth values for its " +
                "variables, and every stage of the evaluation appears here.")
            return@EditorFrame
        }
        val vars = expr.variables
        val assignment = vars.associateWith { values[it] ?: true }
        SectionCard("Assign truth values") {
            if (vars.isEmpty()) {
                Text("No variables to assign.", style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vars.forEach { v ->
                    val b = assignment.getValue(v)
                    ValueToggle(v, b) { values = values + (v to !b) }
                }
            }
            if (vars.isNotEmpty()) {
                Text("Tap a variable to switch it between T and F.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val evaluation = remember(expr, assignment) { StepEvaluator.evaluate(expr, assignment) }
        evaluation.steps.forEach { step ->
            if (step.kind == StepKind.RESULT) {
                ResultCard(step.before, evaluation.result, formatAssignment(assignment))
            } else {
                StepCard(step)
            }
        }
    }
}

@Composable
private fun ValueToggle(name: String, value: Boolean, onToggle: () -> Unit) {
    val lab = Lab.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (value) lab.trueBg else lab.falseBg)
            .clickable(role = Role.Switch, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = "$name is ${if (value) "true" else "false"}. Tap to change." },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$name = ", style = FormulaStyle.medium, color = MaterialTheme.colorScheme.onSurface)
        Text(value.tf(), style = FormulaStyle.medium, color = if (value) lab.trueFg else lab.falseFg)
    }
}

@Composable
private fun StepCard(step: com.bsit1c.logic.EvalStep) {
    val cs = MaterialTheme.colorScheme
    SectionCard(step.kind.title, number = step.number) {
        FormulaBox(step.before, highlight = step.highlightBefore)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⟹", style = FormulaStyle.medium, color = cs.primary)
            Spacer(Modifier.width(8.dp))
            Pill(step.computation, container = cs.secondaryContainer, content = cs.onSecondaryContainer)
        }
        FormulaBox(step.after, highlight = step.highlightAfter)
        Text(step.rule, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun ResultCard(expr: String, result: Boolean, assignment: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.primaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Final truth value", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
        Text("When $assignment", style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
        FormulaText(expr, style = FormulaStyle.medium, color = cs.onPrimaryContainer)
        TruthBadge(result, large = true)
    }
}
