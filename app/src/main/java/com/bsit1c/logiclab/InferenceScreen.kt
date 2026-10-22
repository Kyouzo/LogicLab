package com.bsit1c.logiclab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bsit1c.logic.ArgumentReport
import com.bsit1c.logic.ArgumentStatus
import com.bsit1c.logic.Classifier
import com.bsit1c.logic.Classification
import com.bsit1c.logic.Examples
import com.bsit1c.logic.Expr
import com.bsit1c.logic.InferenceEngine
import com.bsit1c.logic.InferenceRule
import com.bsit1c.logic.InferenceRules
import com.bsit1c.logic.MAX_VARIABLES
import com.bsit1c.logic.ParseResult
import com.bsit1c.logic.Printer
import com.bsit1c.logic.RuleApplication
import com.bsit1c.logic.TruthTable
import com.bsit1c.logic.assignmentForRow
import com.bsit1c.logic.evaluate
import com.bsit1c.logic.formatAssignment
import com.bsit1c.logic.nodesWithPaths

/** Premises and conclusion shared by the "Check argument" and "Build a proof" tabs. */
@Stable
private class ArgumentInputs {
    val premises = mutableStateListOf(ExprFieldState("P → Q"), ExprFieldState("P"))
    val conclusion = ExprFieldState("Q")

    fun load(a: Examples.Argument) {
        premises.clear()
        a.premises.forEach { premises += ExprFieldState(it) }
        conclusion.set(a.conclusion)
    }

    /** Parsed premises and conclusion, or a message saying what is wrong. */
    fun parsed(): Pair<Pair<List<Expr>, Expr>?, String?> {
        val ps = ArrayList<Expr>()
        premises.forEachIndexed { i, f ->
            when (val r = f.parse) {
                is ParseResult.Success -> ps += r.expr
                is ParseResult.Failure -> return null to "Premise ${i + 1}: ${r.error.message}"
            }
        }
        if (ps.isEmpty()) return null to "Add at least one premise."
        val c = when (val r = conclusion.parse) {
            is ParseResult.Success -> r.expr
            is ParseResult.Failure -> return null to "Conclusion: ${r.error.message}"
        }
        return (ps to c) to null
    }
}

@Composable
fun InferenceScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val inputs = remember { ArgumentInputs() }
    val controller = rememberEditorController()
    ScreenScaffold(
        title = "Rules of Inference",
        subtitle = "Module 2 · Proving",
        onBack = onBack,
        bottomBar = { if (tab != 0) LogicKeyboardDock(controller) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Rules", "Check argument", "Build a proof").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (tab) {
                    0 -> RulesReference()
                    1 -> ArgumentChecker(inputs, controller)
                    else -> ProofBuilder(inputs, controller)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Reference
// ---------------------------------------------------------------------------------------------

@Composable
private fun RulesReference() {
    Text(
        "Each rule is a valid argument form: whenever the premises are true, the conclusion must be true. " +
            "The letters p, q, r, s stand for any propositions.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    InferenceRules.all.forEach { RuleCard(it) }
}

@Composable
private fun RuleCard(rule: InferenceRule) {
    var showTable by rememberSaveable(rule.id) { mutableStateOf(false) }
    val taut = remember(rule) { rule.tautology() }
    val report = remember(taut) { Classifier.classify(taut) }
    val cs = MaterialTheme.colorScheme
    SectionCard("${rule.name} (${rule.abbreviation})", number = rule.number, modifier = Modifier.animateContentSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            rule.forms.forEachIndexed { i, form ->
                ArgumentForm(form.premises.map { Printer.print(it) }, Printer.print(form.conclusion),
                    label = if (rule.forms.size > 1) (if (i == 0) "Form" else "Also") else null)
            }
        }
        KeyValue("Premises", rule.primary.premises.joinToString(",  ") { Printer.print(it) })
        KeyValue("Conclusion", Printer.print(rule.primary.conclusion))
        Text(rule.explanation, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                LabIcons.Check, contentDescription = null, tint = Lab.colors.success, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${Printer.print(taut)} is a ${if (report.classification == Classification.TAUTOLOGY) "tautology" else "NON-tautology"} " +
                    "(all ${report.totalRows} rows checked).",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(onClick = { showTable = !showTable }) {
            Text(if (showTable) "Hide truth table" else "Show why: truth table")
        }
        AnimatedVisibility(showTable) {
            val table = remember(taut) { TruthTable.build(taut) }
            TruthTableView(table)
        }
    }
}

/** Premises stacked over a rule line and "∴ conclusion", the way textbooks print arguments. */
@Composable
private fun ArgumentForm(premises: List<String>, conclusion: String, label: String? = null) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        premises.forEach { Text(it, style = FormulaStyle.medium) }
        Box(Modifier.width(120.dp).height(1.5.dp).background(cs.onSurface))
        Text("∴ $conclusion", style = FormulaStyle.medium, color = cs.primary)
    }
}

// ---------------------------------------------------------------------------------------------
// Argument checker
// ---------------------------------------------------------------------------------------------

@Composable
private fun ArgumentEditor(inputs: ArgumentInputs, controller: EditorController) {
    FormulaChipsLabeled(inputs) { controller.active = null }
    inputs.premises.forEachIndexed { i, f ->
        Row(verticalAlignment = Alignment.Top) {
            ExpressionInput(
                f, controller, modifier = Modifier.weight(1f), label = "Premise ${i + 1}",
                placeholder = "premise", compact = true,
            )
            if (inputs.premises.size > 1) {
                IconButton(onClick = {
                    if (controller.active === f) controller.active = null
                    inputs.premises.removeAt(i)
                }, modifier = Modifier.padding(top = 22.dp)) {
                    Icon(LabIcons.Delete, contentDescription = "Remove premise ${i + 1}")
                }
            }
        }
    }
    OutlinedButton(
        onClick = {
            val f = ExprFieldState()
            inputs.premises += f
            controller.activate(f)
        },
        enabled = inputs.premises.size < 8,
    ) {
        Icon(LabIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("  Add premise")
    }
    ExpressionInput(inputs.conclusion, controller, label = "Conclusion  ∴", placeholder = "conclusion", compact = true)
}

@Composable
private fun FormulaChipsLabeled(inputs: ArgumentInputs, onLoaded: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Examples", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Examples.arguments.forEach { a ->
                FilterChip(selected = false, onClick = { inputs.load(a); onLoaded() }, label = { Text(a.title) })
            }
        }
    }
}

private class ArgumentResult(
    val report: ArgumentReport,
    /** values[row] = premise values followed by the conclusion value; null if too many variables. */
    val rows: List<BooleanArray>?,
)

@Composable
private fun ArgumentChecker(inputs: ArgumentInputs, controller: EditorController) {
    ArgumentEditor(inputs, controller)
    var request by remember { mutableStateOf<Pair<List<Expr>, Expr>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    Button(
        onClick = {
            val (ok, err) = inputs.parsed()
            request = ok
            problem = err
            if (ok != null) controller.hide()
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text("Check argument") }

    problem?.let { Banner(BannerKind.ERROR, "Fix the input first", body = it) }
    val req = request ?: return
    val state by rememberAsync(req) {
        val (premises, conclusion) = req
        val report = InferenceEngine.checkArgument(premises, conclusion)
        val rows = if (report.variables.size <= MAX_VARIABLES) {
            (0 until (1 shl report.variables.size)).map { r ->
                val a = assignmentForRow(report.variables, r)
                BooleanArray(premises.size + 1) { i -> if (i < premises.size) premises[i].evaluate(a) else conclusion.evaluate(a) }
            }
        } else null
        ArgumentResult(report, rows)
    }
    when (val s = state) {
        is Async.Loading -> LoadingRow("Searching for a proof and checking every row…")
        is Async.Failed -> Banner(BannerKind.ERROR, "Could not check", body = s.message)
        is Async.Done -> ArgumentResultView(s.value)
    }
}

@Composable
private fun ArgumentResultView(result: ArgumentResult) {
    val r = result.report
    val (kind, title) = when (r.status) {
        ArgumentStatus.PROOF_FOUND -> BannerKind.SUCCESS to "Valid — proof found"
        ArgumentStatus.VALID_NO_PROOF -> BannerKind.WARNING to "Valid by truth table — no derivation found"
        ArgumentStatus.INVALID -> BannerKind.ERROR to "Invalid argument"
        ArgumentStatus.UNDETERMINED -> BannerKind.WARNING to "Undetermined"
    }
    Banner(kind, title, body = r.explanation)

    r.proof?.let { proof ->
        SectionCard("Derivation") {
            proof.forEach { line ->
                ProofLineRow(line.number, Printer.print(line.expr), line.reason, isPremise = line.rule == null,
                    isGoal = line.number == proof.size)
            }
        }
    }

    val rows = result.rows
    if (rows != null) {
        val vars = r.variables
        val headers = vars + r.premises.mapIndexed { i, p -> "P${i + 1}: ${Printer.print(p)}" } +
            "C: ${Printer.print(r.conclusion)}"
        val nP = r.premises.size
        SectionCard("Truth-table check") {
            Text(
                "Highlighted rows have every premise true; the argument is valid exactly when the conclusion is " +
                    "also true in all of them." + (r.counterexample?.let { " The red row is a counterexample." } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TruthGrid(
                headers = headers,
                rowCount = rows.size,
                value = { row, c ->
                    if (c < vars.size) assignmentForRow(vars, row).getValue(vars[c]) else rows[row][c - vars.size]
                },
                resultColumns = setOf(headers.lastIndex),
                inputColumns = vars.size,
                rowMark = { row ->
                    val v = rows[row]
                    val allPremises = (0 until nP).all { v[it] }
                    when {
                        allPremises && !v[nP] -> RowMark.PROBLEM
                        allPremises -> RowMark.EMPHASIS
                        else -> RowMark.NONE
                    }
                },
            )
            r.counterexample?.let {
                Text("Counterexample: ${formatAssignment(it)}", style = FormulaStyle.small, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ProofLineRow(number: Int, formula: String, reason: String, isPremise: Boolean, isGoal: Boolean) {
    val cs = MaterialTheme.colorScheme
    val lab = Lab.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isGoal -> lab.successContainer
                    isPremise -> cs.surfaceContainer
                    else -> cs.surfaceContainerLowest
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number.", style = FormulaStyle.small, color = cs.onSurfaceVariant, modifier = Modifier.width(30.dp))
        Text(formula, style = FormulaStyle.medium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            reason,
            style = MaterialTheme.typography.labelMedium,
            color = if (isPremise) cs.onSurfaceVariant else cs.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(132.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Proof builder: apply rules yourself, with suggestions
// ---------------------------------------------------------------------------------------------

private data class BuiltLine(val expr: Expr, val reason: String, val isPremise: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProofBuilder(inputs: ArgumentInputs, controller: EditorController) {
    val lines = remember { mutableStateListOf<BuiltLine>() }
    var goal by remember { mutableStateOf<Expr?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    if (goal == null) {
        Text(
            "Enter premises and a goal, then build the derivation one rule at a time. LogicLab suggests every " +
                "rule that applies and checks each step you choose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ArgumentEditor(inputs, controller)
        Button(
            onClick = {
                val (ok, err) = inputs.parsed()
                problem = err
                if (ok != null) {
                    lines.clear()
                    ok.first.distinct().forEach { lines += BuiltLine(it, "Premise", true) }
                    goal = ok.second
                    controller.hide()
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Start proof") }
        problem?.let { Banner(BannerKind.ERROR, "Fix the input first", body = it) }
        return
    }

    val target = goal!!
    val reached = lines.any { it.expr == target }
    SectionCard("Goal", trailing = {
        TextButton(onClick = { goal = null; lines.clear() }) { Text("Edit") }
    }) {
        FormulaBox("∴ ${Printer.print(target)}")
    }
    SectionCard("Derivation", trailing = {
        TextButton(onClick = { if (lines.lastOrNull()?.isPremise == false) lines.removeAt(lines.lastIndex) },
            enabled = lines.lastOrNull()?.isPremise == false) { Text("Undo") }
    }) {
        lines.forEachIndexed { i, l ->
            ProofLineRow(i + 1, Printer.print(l.expr), l.reason, l.isPremise, isGoal = l.expr == target)
        }
    }
    if (reached) {
        Banner(BannerKind.SUCCESS, "Goal reached", icon = LabIcons.Check,
            body = "${Printer.print(target)} has been derived from the premises. Every line is justified by a premise or " +
                "one of the nine rules, so the argument is valid.")
    }

    // Suggestions computed by the engine (Addition / Conjunction only toward formulas in the goal or premises).
    val exprs = lines.map { it.expr }
    val suggestions = remember(exprs, target) {
        val targets = HashSet<Expr>()
        (exprs + target).forEach { e -> e.nodesWithPaths().forEach { targets += it.second } }
        InferenceEngine.oneStep(exprs, targets)
    }
    if (!reached) {
        SectionCard("Possible next steps") {
            if (suggestions.isEmpty()) {
                Text(
                    "No supported rule produces a new line. If the argument is still valid, it needs an equivalence " +
                        "law (try the Equivalence Laws module).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            suggestions.sortedByDescending { it.conclusion == target }.take(12).forEach { inf ->
                SuggestionRow(
                    rule = inf.rule.name,
                    from = inf.from.map { it + 1 },
                    result = Printer.print(inf.conclusion),
                    leadsToGoal = inf.conclusion == target,
                ) {
                    lines += BuiltLine(inf.conclusion, "${inf.rule.name} (${inf.from.joinToString(", ") { (it + 1).toString() }})", false)
                }
            }
        }
        ManualRule(lines, controller)
    }
}

@Composable
private fun SuggestionRow(rule: String, from: List<Int>, result: String, leadsToGoal: Boolean, onAdd: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (leadsToGoal) Lab.colors.successContainer else cs.surfaceContainer)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(result, style = FormulaStyle.medium)
            Text(
                "$rule on line${if (from.size > 1) "s" else ""} ${from.joinToString(", ")}" + if (leadsToGoal) " · reaches the goal" else "",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAdd) { Text("Add") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualRule(lines: MutableList<BuiltLine>, controller: EditorController) {
    var ruleId by rememberSaveable { mutableStateOf(InferenceRules.MODUS_PONENS.id) }
    val selected = remember { mutableStateListOf<Int>() }
    val extra = rememberExprField("")
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val rule = InferenceRules.byId(ruleId)
    LaunchedEffect(lines.size) { if (selected.any { it !in lines.indices }) selected.removeAll { it !in lines.indices } }
    val needsExtra = rule.primary.freeInConclusion.isNotEmpty()

    SectionCard("Apply a rule yourself") {
        Text("1. Choose a rule", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InferenceRules.all.forEach { r ->
                FilterChip(
                    selected = r.id == ruleId,
                    onClick = { ruleId = r.id; message = null },
                    label = { Text(r.name) },
                )
            }
        }
        Text(rule.forms.joinToString("   or   ") { it.symbolic }, style = FormulaStyle.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("2. Select the line(s) to use, in any order", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lines.forEachIndexed { i, l ->
                FilterChip(
                    selected = i in selected,
                    onClick = { if (i in selected) selected.remove(i) else selected.add(i); message = null },
                    label = { Text("${i + 1}. ${Printer.print(l.expr)}", style = FormulaStyle.small) },
                )
            }
        }
        if (needsExtra) {
            Text("3. Addition introduces a new disjunct q — enter it", style = MaterialTheme.typography.labelLarge)
            ExpressionInput(extra, controller, placeholder = "q", compact = true)
        }
        Button(
            onClick = {
                val picked = selected.filter { it in lines.indices }
                val premises = picked.map { lines[it].expr }
                val extraMap = if (needsExtra) {
                    when (val p = extra.parse) {
                        is ParseResult.Success -> mapOf(rule.primary.freeInConclusion.first() to p.expr)
                        is ParseResult.Failure -> { message = false to "Enter the proposition to add (q)."; return@Button }
                    }
                } else emptyMap()
                if (premises.isEmpty()) { message = false to "Select at least one line."; return@Button }
                when (val res = InferenceEngine.apply(rule, premises, extraMap).first()) {
                    is RuleApplication.Derived -> {
                        val from = res.premiseOrder.map { picked[it] + 1 }.sorted()
                        if (lines.any { it.expr == res.conclusion }) {
                            message = false to "${Printer.print(res.conclusion)} is already line ${lines.indexOfFirst { it.expr == res.conclusion } + 1}."
                        } else {
                            lines += BuiltLine(res.conclusion, "${rule.name} (${from.joinToString(", ")})", false)
                            message = true to "Added ${Printer.print(res.conclusion)} by ${rule.name}."
                            selected.clear()
                        }
                    }
                    is RuleApplication.NeedsExtra -> message = false to "Enter the proposition to add."
                    is RuleApplication.NoMatch -> message = false to res.reason
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply ${rule.name}") }
        message?.let { (ok, text) ->
            Banner(if (ok) BannerKind.SUCCESS else BannerKind.ERROR, if (ok) "Step added" else "Rule does not apply", body = text)
        }
    }
}
