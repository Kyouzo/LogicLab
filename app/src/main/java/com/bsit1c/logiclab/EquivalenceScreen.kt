package com.bsit1c.logiclab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bsit1c.logic.EquivalenceEngine
import com.bsit1c.logic.EquivalenceLaw
import com.bsit1c.logic.EquivalenceLaws
import com.bsit1c.logic.Examples
import com.bsit1c.logic.Expr
import com.bsit1c.logic.MAX_VARIABLES
import com.bsit1c.logic.ParseResult
import com.bsit1c.logic.Parser
import com.bsit1c.logic.Printer
import com.bsit1c.logic.ProofSearchResult
import com.bsit1c.logic.RewriteStep
import com.bsit1c.logic.Semantics
import com.bsit1c.logic.assignmentForRow
import com.bsit1c.logic.evaluate
import com.bsit1c.logic.formatAssignment
import com.bsit1c.logic.tf

@Composable
fun EquivalenceScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val controller = rememberEditorController()
    ScreenScaffold(
        title = "Equivalence Laws",
        subtitle = "Module 2 · Logical equivalence",
        onBack = onBack,
        bottomBar = { if (tab != 0) LogicKeyboardDock(controller) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Laws", "Proof assistant", "Check A ≡ B").forEachIndexed { i, t ->
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
                    0 -> LawsReference()
                    1 -> ProofAssistant(controller)
                    else -> EquivalenceChecker(controller)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Reference
// ---------------------------------------------------------------------------------------------

@Composable
private fun LawsReference() {
    Text(
        "Two propositions are logically equivalent (≡) when they have the same truth value in every row. " +
            "Each law below can replace any part of an expression without changing its meaning.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    EquivalenceLaws.all.forEach { LawCard(it) }
}

@Composable
private fun LawCard(law: EquivalenceLaw) {
    val cs = MaterialTheme.colorScheme
    val example = remember(law) { Parser.parseOrThrow(law.example) }
    val step = remember(law) { EquivalenceEngine.applyLaw(law, example) }
    val verified = remember(step) { step?.let { Semantics.equivalent(it.before, it.after).equivalent } }
    SectionCard(law.name, number = law.number) {
        law.shownForms.forEach { f -> FormulaBox(f.symbolic) }
        Text(law.explanation, style = MaterialTheme.typography.bodyMedium)
        Text("Example", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        if (step == null) {
            FormulaBox("${Printer.print(example)}  ≡  ${Printer.print(example)}")
        } else {
            val before = Printer.render(step.before)
            val after = Printer.render(step.after)
            FormulaBox(before.text, highlight = before.ranges[step.path])
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("≡", style = FormulaStyle.large, color = cs.primary)
                Spacer(Modifier.width(8.dp))
                Text("by ${law.name}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            FormulaBox(after.text, highlight = after.ranges[step.path])
            if (verified == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(LabIcons.Check, contentDescription = null, tint = Lab.colors.success, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Verified equal in every row of the truth table", style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Proof assistant
// ---------------------------------------------------------------------------------------------

@Composable
private fun ProofAssistant(controller: EditorController) {
    val startField = rememberExprField("¬(P ∨ ¬Q)")
    val targetField = rememberExprField("¬P ∧ Q")
    var start by remember { mutableStateOf<Expr?>(null) }
    var target by remember { mutableStateOf<Expr?>(null) }
    val steps = remember { mutableStateListOf<RewriteStep>() }
    var problem by remember { mutableStateOf<String?>(null) }

    if (start == null) {
        Text(
            "Transform the starting expression into the target one law at a time. Every step is labelled with the " +
                "law used and checked against the truth table.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Examples", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Examples.equivalences.forEach { e ->
                    FilterChip(
                        selected = false,
                        onClick = { startField.set(e.start); targetField.set(e.target) },
                        label = { Text("${e.start} ≡ ${e.target}", style = FormulaStyle.small) },
                    )
                }
            }
        }
        ExpressionInput(startField, controller, label = "Start with", compact = true)
        ExpressionInput(targetField, controller, label = "Target (optional)", placeholder = "leave empty to explore", compact = true)
        Button(
            onClick = {
                val s = startField.parse
                val t = if (targetField.text.isBlank()) null else targetField.parse
                problem = when {
                    s is ParseResult.Failure -> "Start: ${s.error.message}"
                    t is ParseResult.Failure -> "Target: ${t.error.message}"
                    else -> null
                }
                val se = (s as? ParseResult.Success)?.expr
                val te = (t as? ParseResult.Success)?.expr
                if (problem == null && se != null) {
                    if (Semantics.combinedVariables(*listOfNotNull(se, te).toTypedArray()).size > MAX_VARIABLES) {
                        problem = "Too many variables (max $MAX_VARIABLES)."
                    } else {
                        start = se; target = te; steps.clear(); controller.hide()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Begin") }
        problem?.let { Banner(BannerKind.ERROR, "Fix the input first", body = it) }
        return
    }

    val s0 = start!!
    val current = steps.lastOrNull()?.after ?: s0
    val goal = target
    val verification = remember(steps.toList(), goal) { EquivalenceEngine.verify(s0, goal, steps.toList()) }

    SectionCard("Derivation", trailing = {
        TextButton(onClick = { start = null }) { Text("Edit") }
    }) {
        ChainLine(0, Printer.render(s0).text, null, "Given")
        steps.forEachIndexed { i, st ->
            val r = Printer.render(st.after)
            ChainLine(i + 1, r.text, r.ranges[st.path], "${st.law.name}: ${st.description}")
        }
        if (goal != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Target  ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Printer.print(goal), style = FormulaStyle.medium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (steps.isNotEmpty()) steps.removeAt(steps.lastIndex) }, enabled = steps.isNotEmpty()) {
                Icon(LabIcons.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Undo")
            }
            OutlinedButton(onClick = { steps.clear() }, enabled = steps.isNotEmpty()) {
                Icon(LabIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Reset")
            }
        }
    }

    when {
        goal != null && verification.complete -> Banner(
            BannerKind.SUCCESS, "Proof complete", icon = LabIcons.Check,
            body = "${Printer.print(s0)} ≡ ${Printer.print(goal)}. All ${steps.size} step${if (steps.size == 1) "" else "s"} " +
                "apply a supported law, and each was confirmed equivalent by truth table.",
        )
        !verification.allStepsValid -> Banner(
            BannerKind.ERROR, "Invalid step ${verification.invalidStep}",
            body = "This step does not preserve equivalence, so the chain is not a proof.",
        )
        goal != null -> {
            val eq = remember(s0, goal) { Semantics.equivalent(s0, goal) }
            if (!eq.equivalent) {
                Banner(
                    BannerKind.ERROR, "These are not equivalent",
                    body = "No sequence of laws can connect them: when ${formatAssignment(eq.counterexample!!)}, the start is " +
                        "${eq.leftValue!!.tf()} but the target is ${eq.rightValue!!.tf()}.",
                )
            } else {
                AutoComplete(current, goal) { found -> steps.addAll(found) }
            }
        }
        else -> Banner(
            BannerKind.INFO, "Exploring",
            body = "Every expression in the chain is equivalent to ${Printer.print(s0)} (verified by truth table).",
        )
    }

    if (!(goal != null && verification.complete)) {
        ApplicableLaws(current) { steps += it }
    }
}

@Composable
private fun ChainLine(index: Int, text: String, highlight: IntRange?, reason: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Text(if (index == 0) "" else "≡", style = FormulaStyle.medium, color = cs.primary, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FormulaText(text, style = FormulaStyle.medium, highlight = highlight)
            Text(reason, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun AutoComplete(current: Expr, goal: Expr, onFound: (List<RewriteStep>) -> Unit) {
    var request by remember(current, goal) { mutableStateOf(false) }
    if (!request) {
        OutlinedButton(onClick = { request = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(LabIcons.Magic, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Finish the proof for me")
        }
        return
    }
    val state by rememberAsync(current to goal) { EquivalenceEngine.findProof(current, goal) }
    when (val s = state) {
        is Async.Loading -> LoadingRow("Searching for a chain of laws…")
        is Async.Failed -> Banner(BannerKind.ERROR, "Search failed", body = s.message)
        is Async.Done -> when (val r = s.value) {
            is ProofSearchResult.Found -> {
                Banner(BannerKind.SUCCESS, "Found a ${r.proof.steps.size}-step completion",
                    body = "Tap below to add the steps to your derivation.")
                Button(onClick = { onFound(r.proof.steps) }, modifier = Modifier.fillMaxWidth()) { Text("Add these steps") }
            }
            is ProofSearchResult.NotFound -> Banner(
                BannerKind.WARNING, "No chain found within the search limits",
                body = "The expressions ARE equivalent (truth tables agree), but the automatic search did not find a " +
                    "short chain of supported laws. Try applying a law or two yourself first.",
            )
            is ProofSearchResult.NotEquivalent -> Banner(BannerKind.ERROR, "Not equivalent")
        }
    }
}

@Composable
private fun ApplicableLaws(current: Expr, onApply: (RewriteStep) -> Unit) {
    var expanding by rememberSaveable { mutableStateOf(false) }
    val options = remember(current, expanding) { EquivalenceEngine.applicable(current, includeExpanding = expanding) }
    val currentRender = remember(current) { Printer.render(current) }
    SectionCard("Apply a law to the current expression") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Include “introduce” moves (p ⇒ ¬¬p, p ⇒ p ∧ T …)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = expanding, onCheckedChange = { expanding = it })
        }
        if (options.isEmpty()) {
            Text("No supported law applies here.", style = MaterialTheme.typography.bodyMedium)
        }
        options.groupBy { it.law }.toSortedMap(compareBy { it.number }).forEach { (law, list) ->
            Text("${law.number}. ${law.name}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            list.forEach { st ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(role = Role.Button) { onApply(st) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FormulaText(currentRender.text, style = FormulaStyle.small, highlight = currentRender.ranges[st.path])
                    Text("⇒ ${Printer.print(st.after)}", style = FormulaStyle.small, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Equivalence checker
// ---------------------------------------------------------------------------------------------

@Composable
private fun EquivalenceChecker(controller: EditorController) {
    val a = rememberExprField("P → Q")
    val b = rememberExprField("¬Q → ¬P")
    var request by remember { mutableStateOf<Pair<Expr, Expr>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    ExpressionInput(a, controller, label = "A", compact = true)
    ExpressionInput(b, controller, label = "B", compact = true)
    Button(
        onClick = {
            val pa = a.parse
            val pb = b.parse
            problem = when {
                pa is ParseResult.Failure -> "A: ${pa.error.message}"
                pb is ParseResult.Failure -> "B: ${pb.error.message}"
                else -> null
            }
            if (pa is ParseResult.Success && pb is ParseResult.Success) {
                if (Semantics.combinedVariables(pa.expr, pb.expr).size > MAX_VARIABLES) {
                    problem = "Too many variables (max $MAX_VARIABLES)."
                } else {
                    request = pa.expr to pb.expr
                    controller.hide()
                }
            }
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text("Check equivalence") }
    problem?.let { Banner(BannerKind.ERROR, "Fix the input first", body = it) }
    val (ea, eb) = request ?: return
    val result = remember(ea, eb) { Semantics.equivalent(ea, eb) }
    if (result.equivalent) {
        Banner(BannerKind.SUCCESS, "A ≡ B", icon = LabIcons.Check,
            body = "The columns for A and B agree in all ${1 shl result.variables.size} rows, so (A) ↔ (B) is a tautology.")
    } else {
        Banner(BannerKind.ERROR, "Not equivalent",
            body = "When ${formatAssignment(result.counterexample!!)}, A is ${result.leftValue!!.tf()} but B is " +
                "${result.rightValue!!.tf()}. Differing rows are marked in red.")
    }
    val vars = result.variables
    SectionCard("Side-by-side truth table") {
        TruthGrid(
            headers = vars + listOf("A: ${Printer.print(ea)}", "B: ${Printer.print(eb)}"),
            rowCount = 1 shl vars.size,
            value = { r, c ->
                val asg = assignmentForRow(vars, r)
                when {
                    c < vars.size -> asg.getValue(vars[c])
                    c == vars.size -> ea.evaluate(asg)
                    else -> eb.evaluate(asg)
                }
            },
            resultColumns = setOf(vars.size, vars.size + 1),
            inputColumns = vars.size,
            rowMark = { r ->
                val asg = assignmentForRow(vars, r)
                if (ea.evaluate(asg) != eb.evaluate(asg)) RowMark.PROBLEM else RowMark.NONE
            },
        )
    }
}
