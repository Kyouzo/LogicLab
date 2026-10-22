package com.bsit1c.logic

/** Which group an evaluation step belongs to, in the order the evaluator prefers them. */
enum class StepKind(val title: String) {
    SUBSTITUTION("Substitute truth values"),
    NEGATION("Evaluate negation"),
    CONJUNCTION("Evaluate conjunction"),
    DISJUNCTION("Evaluate disjunction"),
    IMPLICATION("Evaluate implication"),
    BICONDITIONAL("Evaluate biconditional"),
    RESULT("Final truth value"),
}

/**
 * One stage of a step-by-step evaluation.
 *
 * [before] / [after] are the expression texts. [highlightBefore] is the character range of the part
 * being evaluated in [before]; [highlightAfter] is where its value appears in [after].
 */
data class EvalStep(
    val number: Int,
    val kind: StepKind,
    val before: String,
    val after: String,
    val highlightBefore: IntRange?,
    val highlightAfter: IntRange?,
    /** The local computation, e.g. "T ∧ F = F". */
    val computation: String,
    /** The logical rule that justifies the computation. */
    val rule: String,
)

data class Evaluation(
    val expr: Expr,
    val assignment: Map<String, Boolean>,
    val steps: List<EvalStep>,
    val result: Boolean,
)

/**
 * Produces a step-by-step evaluation directly from the expression tree.
 *
 * After substituting values, the evaluator repeatedly picks a node whose operands are already
 * truth values (an innermost operation, so parentheses are respected) and reduces it. When several
 * are ready it prefers, in order, ¬, ∧, ∨, →, ↔ — the precedence order — and then the leftmost.
 */
object StepEvaluator {
    fun evaluate(expr: Expr, assignment: Map<String, Boolean>): Evaluation {
        val missing = expr.variables.filter { it !in assignment }
        require(missing.isEmpty()) { "No truth value given for ${missing.joinToString()}" }

        val steps = ArrayList<EvalStep>()
        val original = Printer.render(expr)
        var current = substitute(expr, assignment)
        val substituted = Printer.render(current)

        if (expr.variables.isNotEmpty()) {
            steps += EvalStep(
                number = 1,
                kind = StepKind.SUBSTITUTION,
                before = original.text,
                after = substituted.text,
                highlightBefore = null,
                highlightAfter = null,
                computation = formatAssignment(expr.variables.associateWith { assignment.getValue(it) }),
                rule = "Replace every occurrence of each variable with its assigned truth value " +
                    "(T for true, F for false).",
            )
        }

        while (current !is Expr.Const) {
            val (path, node) = pickNext(current)
            val value = reduce(node)
            val before = Printer.render(current)
            val next = current.replaceAt(path, Expr.Const(value))
            val after = Printer.render(next)
            steps += EvalStep(
                number = steps.size + 1,
                kind = kindOf(node),
                before = before.text,
                after = after.text,
                highlightBefore = before.ranges[path],
                highlightAfter = after.ranges[path],
                computation = "${Printer.print(node)} = ${value.tf()}",
                rule = ruleFor(node, value),
            )
            current = next
        }

        val result = current.value
        steps += EvalStep(
            number = steps.size + 1,
            kind = StepKind.RESULT,
            before = original.text,
            after = result.tf(),
            highlightBefore = null,
            highlightAfter = null,
            computation = "${original.text} = ${result.tf()}",
            rule = if (result) "Under this assignment the expression is TRUE."
            else "Under this assignment the expression is FALSE.",
        )
        return Evaluation(expr, assignment, steps, result)
    }

    private fun substitute(e: Expr, a: Map<String, Boolean>): Expr = when (e) {
        is Expr.Var -> Expr.Const(a.getValue(e.name))
        is Expr.Const -> e
        is Expr.Not -> Expr.Not(substitute(e.operand, a))
        is Expr.Binary -> e.copy(left = substitute(e.left, a), right = substitute(e.right, a))
    }

    private fun isReady(e: Expr) = when (e) {
        is Expr.Not -> e.operand is Expr.Const
        is Expr.Binary -> e.left is Expr.Const && e.right is Expr.Const
        else -> false
    }

    private fun priority(e: Expr): Int = when (e) {
        is Expr.Not -> 0
        is Expr.Binary -> when (e.op) {
            BinaryOp.AND -> 1
            BinaryOp.OR -> 2
            BinaryOp.IMPLIES -> 3
            BinaryOp.IFF -> 4
        }
        else -> 9
    }

    /** Pre-order traversal visits nodes left to right, so the first candidate of best priority is leftmost. */
    private fun pickNext(e: Expr): Pair<Path, Expr> =
        e.nodesWithPaths()
            .filter { isReady(it.second) }
            .minWith(compareBy<Pair<Path, Expr>> { priority(it.second) })

    private fun reduce(e: Expr): Boolean = when (e) {
        is Expr.Not -> !(e.operand as Expr.Const).value
        is Expr.Binary -> e.op.apply((e.left as Expr.Const).value, (e.right as Expr.Const).value)
        else -> error("not reducible")
    }

    private fun kindOf(e: Expr): StepKind = when (e) {
        is Expr.Not -> StepKind.NEGATION
        is Expr.Binary -> when (e.op) {
            BinaryOp.AND -> StepKind.CONJUNCTION
            BinaryOp.OR -> StepKind.DISJUNCTION
            BinaryOp.IMPLIES -> StepKind.IMPLICATION
            BinaryOp.IFF -> StepKind.BICONDITIONAL
        }
        else -> StepKind.RESULT
    }

    fun ruleFor(e: Expr, value: Boolean): String = when (e) {
        is Expr.Not -> "Negation reverses a truth value: ¬T = F and ¬F = T."
        is Expr.Binary -> when (e.op) {
            BinaryOp.AND -> if (value) "A conjunction is true when both operands are true."
            else "A conjunction is false when at least one operand is false."
            BinaryOp.OR -> if (value) "A disjunction is true when at least one operand is true."
            else "A disjunction is false only when both operands are false."
            BinaryOp.IMPLIES -> if (value) {
                if (!(e.left as Expr.Const).value) {
                    "An implication with a false hypothesis is true (vacuously true)."
                } else {
                    "An implication with a true hypothesis and a true conclusion is true."
                }
            } else {
                "An implication is false only when the hypothesis is true and the conclusion is false."
            }
            BinaryOp.IFF -> if (value) "A biconditional is true when both sides have the same truth value."
            else "A biconditional is false when the two sides have different truth values."
        }
        else -> ""
    }
}
