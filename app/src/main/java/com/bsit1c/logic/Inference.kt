package com.bsit1c.logic

/** One concrete shape of a rule: premise schemas and the conclusion schema. */
data class RuleForm(val premises: List<Expr>, val conclusion: Expr) {
    val symbolic: String
        get() = premises.joinToString(", ") { Printer.print(it) } + "  ∴ " + Printer.print(conclusion)

    /** Metavariables that occur only in the conclusion (e.g. q in Addition) and must be supplied. */
    val freeInConclusion: Set<String>
        get() = Matcher.metavariables(conclusion) - premises.flatMap { Matcher.metavariables(it) }.toSet()
}

data class InferenceRule(
    val id: String,
    val number: Int,
    val name: String,
    val abbreviation: String,
    /** Forms shown to students; the first is the canonical textbook form. */
    val forms: List<RuleForm>,
    val explanation: String,
) {
    val primary: RuleForm get() = forms.first()

    /** The tautology ((premise₁ ∧ … ∧ premiseₙ) → conclusion) that makes the primary form valid. */
    fun tautology(form: RuleForm = primary): Expr =
        form.premises.reduce { a, b -> a and b } implies form.conclusion
}

object InferenceRules {
    private fun form(conclusion: String, vararg premises: String) =
        RuleForm(premises.map { schema(it) }, schema(conclusion))

    val ADDITION = InferenceRule(
        "add", 1, "Addition", "Add",
        listOf(form("p ∨ q", "p")),
        "If p is true, then \"p or q\" is true no matter what q is, because a disjunction needs only one true part.",
    )
    val SIMPLIFICATION = InferenceRule(
        "simp", 2, "Simplification", "Simp",
        listOf(form("p", "p ∧ q"), form("q", "p ∧ q")),
        "A conjunction is true only when both parts are true, so from p ∧ q we may conclude either part.",
    )
    val CONJUNCTION = InferenceRule(
        "conj", 3, "Conjunction", "Conj",
        listOf(form("p ∧ q", "p", "q")),
        "If p is true and q is true separately, then their conjunction p ∧ q is true.",
    )
    val MODUS_PONENS = InferenceRule(
        "mp", 4, "Modus Ponens", "MP",
        listOf(form("q", "p → q", "p")),
        "When p → q is true and the hypothesis p is true, the conclusion q must be true: the only row where " +
            "an implication is false has a true hypothesis and false conclusion.",
    )
    val MODUS_TOLLENS = InferenceRule(
        "mt", 5, "Modus Tollens", "MT",
        listOf(form("¬p", "p → q", "¬q")),
        "If p → q holds but q is false, then p cannot be true — otherwise q would have to be true. So ¬p.",
    )
    val DISJUNCTIVE_SYLLOGISM = InferenceRule(
        "ds", 6, "Disjunctive Syllogism", "DS",
        listOf(form("q", "p ∨ q", "¬p"), form("p", "p ∨ q", "¬q")),
        "At least one part of p ∨ q is true. If one part is known to be false, the other part must be true.",
    )
    val HYPOTHETICAL_SYLLOGISM = InferenceRule(
        "hs", 7, "Hypothetical Syllogism", "HS",
        listOf(form("p → r", "p → q", "q → r")),
        "Implications chain together: if p leads to q and q leads to r, then p leads to r (transitivity).",
    )
    val CONSTRUCTIVE_DILEMMA = InferenceRule(
        "cd", 8, "Constructive Dilemma", "CD",
        listOf(
            form("q ∨ s", "(p → q) ∧ (r → s)", "p ∨ r"),
            form("q ∨ s", "p → q", "r → s", "p ∨ r"),
        ),
        "Either p or r is true. If p is, q follows; if r is, s follows. Either way q ∨ s is true.",
    )
    val DESTRUCTIVE_DILEMMA = InferenceRule(
        "dd", 9, "Destructive Dilemma", "DD",
        listOf(
            form("¬p ∨ ¬r", "(p → q) ∧ (r → s)", "¬q ∨ ¬s"),
            form("¬p ∨ ¬r", "p → q", "r → s", "¬q ∨ ¬s"),
        ),
        "At least one of q, s is false. If q is false then p is false (modus tollens); if s is false then r " +
            "is false. Either way ¬p ∨ ¬r is true.",
    )

    val all: List<InferenceRule> = listOf(
        ADDITION, SIMPLIFICATION, CONJUNCTION, MODUS_PONENS, MODUS_TOLLENS,
        DISJUNCTIVE_SYLLOGISM, HYPOTHETICAL_SYLLOGISM, CONSTRUCTIVE_DILEMMA, DESTRUCTIVE_DILEMMA,
    )

    fun byId(id: String): InferenceRule = all.first { it.id == id }
}

/** The outcome of trying to apply one rule to chosen premises. */
sealed class RuleApplication {
    data class Derived(val rule: InferenceRule, val form: RuleForm, val conclusion: Expr, val premiseOrder: List<Int>) :
        RuleApplication()
    data class NeedsExtra(val rule: InferenceRule, val missing: Set<String>) : RuleApplication()
    data class NoMatch(val rule: InferenceRule, val reason: String) : RuleApplication()
}

data class ProofLine(
    val number: Int,
    val expr: Expr,
    /** "Premise" or the rule name. */
    val justification: String,
    val rule: InferenceRule?,
    /** 1-based line numbers this line was derived from. */
    val from: List<Int>,
) {
    val reason: String
        get() = if (rule == null) justification
        else "${rule.name} (${from.joinToString(", ")})"
}

enum class ArgumentStatus {
    /** A derivation was found using the supported rules (so the argument is valid). */
    PROOF_FOUND,
    /** Exhaustive truth-table check says valid, but the proof search found no derivation. */
    VALID_NO_PROOF,
    /** A row makes every premise true and the conclusion false. */
    INVALID,
    /** Too many variables for the truth-table check and no derivation was found. */
    UNDETERMINED,
}

data class ArgumentReport(
    val premises: List<Expr>,
    val conclusion: Expr,
    val status: ArgumentStatus,
    val proof: List<ProofLine>?,
    /** null when the truth-table check was skipped (too many variables). */
    val semanticallyValid: Boolean?,
    val counterexample: Map<String, Boolean>?,
    val variables: List<String>,
    val explanation: String,
)

object InferenceEngine {

    /** Tries [rule] on the given premises (any order). [extra] supplies conclusion-only metavariables. */
    fun apply(rule: InferenceRule, premises: List<Expr>, extra: Map<String, Expr> = emptyMap()): List<RuleApplication> {
        val results = ArrayList<RuleApplication>()
        var needs: Set<String>? = null
        for (form in rule.forms) {
            if (form.premises.size != premises.size) continue
            for (perm in permutations(premises.indices.toList())) {
                var b: Map<String, Expr> = emptyMap()
                var ok = true
                for ((k, idx) in perm.withIndex()) {
                    val nb = Matcher.match(form.premises[k], premises[idx], b)
                    if (nb == null) { ok = false; break }
                    b = nb
                }
                if (!ok) continue
                val full = b + extra
                val c = Matcher.substitute(form.conclusion, full)
                if (c == null) {
                    needs = form.freeInConclusion
                } else if (results.none { it is RuleApplication.Derived && it.conclusion == c }) {
                    results += RuleApplication.Derived(rule, form, c, perm)
                }
            }
        }
        if (results.isEmpty()) {
            if (needs != null) return listOf(RuleApplication.NeedsExtra(rule, needs))
            val sizes = rule.forms.map { it.premises.size }.distinct()
            val reason = if (premises.size !in sizes) {
                "${rule.name} needs ${sizes.joinToString(" or ")} premise${if (sizes.singleOrNull() == 1) "" else "s"}, " +
                    "but ${premises.size} ${if (premises.size == 1) "was" else "were"} selected."
            } else {
                "The selected premises do not have the shape ${rule.forms.joinToString(" or ") { it.premises.joinToString(", ") { p -> Printer.print(p) } }}."
            }
            return listOf(RuleApplication.NoMatch(rule, reason))
        }
        return results
    }

    private fun permutations(items: List<Int>): List<List<Int>> {
        if (items.size <= 1) return listOf(items)
        return items.flatMap { x -> permutations(items - x).map { listOf(x) + it } }
    }

    data class Inference(val rule: InferenceRule, val from: List<Int>, val conclusion: Expr)

    /**
     * All conclusions reachable in one step from [lines] (0-based indices in [Inference.from]).
     * Addition and Conjunction can produce infinitely many formulas, so they are only used to build
     * formulas listed in [targets] (normally the sub-formulas of the goal and premises).
     */
    fun oneStep(lines: List<Expr>, targets: Set<Expr>): List<Inference> {
        val out = ArrayList<Inference>()
        val seen = HashSet<Expr>(lines)
        val indexOf = HashMap<Expr, Int>()
        lines.forEachIndexed { i, e -> indexOf.putIfAbsent(e, i) }
        fun emit(rule: InferenceRule, from: List<Int>, c: Expr) {
            if (seen.add(c)) out += Inference(rule, from, c)
        }

        // Addition: p ⊢ p ∨ q, for target disjunctions whose left side is known.
        for (t in targets) {
            if (t is Expr.Binary && t.op == BinaryOp.OR) {
                indexOf[t.left]?.let { emit(InferenceRules.ADDITION, listOf(it), t) }
            }
        }
        // Conjunction: p, q ⊢ p ∧ q, for target conjunctions whose parts are both known.
        for (t in targets) {
            if (t is Expr.Binary && t.op == BinaryOp.AND) {
                val l = indexOf[t.left]
                val r = indexOf[t.right]
                if (l != null && r != null) emit(InferenceRules.CONJUNCTION, listOf(l, r), t)
            }
        }
        val fixedRules = listOf(
            InferenceRules.SIMPLIFICATION, InferenceRules.MODUS_PONENS, InferenceRules.MODUS_TOLLENS,
            InferenceRules.DISJUNCTIVE_SYLLOGISM, InferenceRules.HYPOTHETICAL_SYLLOGISM,
            InferenceRules.CONSTRUCTIVE_DILEMMA, InferenceRules.DESTRUCTIVE_DILEMMA,
        )
        for (rule in fixedRules) for (form in rule.forms) {
            matchPremises(form.premises, 0, emptyMap(), emptyList(), lines, indexOf) { b, used ->
                Matcher.substitute(form.conclusion, b)?.let { emit(rule, used, it) }
            }
        }
        return out
    }

    /** Matches premise schemas one at a time; fully-bound schemas are looked up directly. */
    private fun matchPremises(
        schemas: List<Expr>, k: Int, b: Map<String, Expr>, used: List<Int>,
        lines: List<Expr>, indexOf: Map<Expr, Int>, onMatch: (Map<String, Expr>, List<Int>) -> Unit,
    ) {
        if (k == schemas.size) { onMatch(b, used); return }
        val schema = schemas[k]
        val bound = Matcher.substitute(schema, b)
        if (bound != null) {
            val idx = indexOf[bound] ?: return
            matchPremises(schemas, k + 1, b, used + idx, lines, indexOf, onMatch)
            return
        }
        for ((i, line) in lines.withIndex()) {
            val nb = Matcher.match(schema, line, b) ?: continue
            matchPremises(schemas, k + 1, nb, used + i, lines, indexOf, onMatch)
        }
    }

    private const val MAX_LINES = 400
    private const val MAX_ROUNDS = 12

    /**
     * Forward-chaining proof search using the nine rules. Returns a pruned proof (only the lines the
     * conclusion depends on), or null if none was found within the search limits.
     */
    fun findProof(premises: List<Expr>, conclusion: Expr): List<ProofLine>? {
        data class Node(val expr: Expr, val rule: InferenceRule?, val from: List<Int>)
        val nodes = ArrayList<Node>()
        val known = HashSet<Expr>()
        for (p in premises) if (known.add(p)) nodes += Node(p, null, emptyList())

        val targets = HashSet<Expr>()
        (premises + conclusion).forEach { e -> e.nodesWithPaths().forEach { targets += it.second } }

        var round = 0
        while (conclusion !in known && round < MAX_ROUNDS && nodes.size < MAX_LINES) {
            round++
            val fresh = oneStep(nodes.map { it.expr }, targets)
            if (fresh.isEmpty()) break
            for (inf in fresh) {
                if (nodes.size >= MAX_LINES) break
                if (known.add(inf.conclusion)) nodes += Node(inf.conclusion, inf.rule, inf.from)
                if (inf.conclusion == conclusion) break
            }
        }
        if (conclusion !in known) return null

        // Keep only the lines the conclusion depends on, plus every premise (students list them all).
        val goalIdx = nodes.indexOfFirst { it.expr == conclusion }
        val needed = sortedSetOf<Int>()
        fun mark(i: Int) {
            if (!needed.add(i)) return
            nodes[i].from.forEach { mark(it) }
        }
        mark(goalIdx)
        nodes.indices.forEach { i -> if (nodes[i].rule == null) needed += i }
        val renumber = HashMap<Int, Int>()
        val lines = ArrayList<ProofLine>()
        for (i in needed) {
            val n = nodes[i]
            val num = lines.size + 1
            renumber[i] = num
            lines += ProofLine(
                num, n.expr, n.rule?.name ?: "Premise", n.rule, n.from.map { renumber.getValue(it) },
            )
        }
        return lines
    }

    fun checkArgument(premises: List<Expr>, conclusion: Expr): ArgumentReport {
        val vars = Semantics.combinedVariables(*(premises + conclusion).toTypedArray())
        var semantic: Boolean? = null
        var counter: Map<String, Boolean>? = null
        if (vars.size <= MAX_VARIABLES) {
            semantic = true
            for (r in 0 until (1 shl vars.size)) {
                val a = assignmentForRow(vars, r)
                if (premises.all { it.evaluate(a) } && !conclusion.evaluate(a)) {
                    semantic = false; counter = a; break
                }
            }
        }
        if (semantic == false) {
            return ArgumentReport(
                premises, conclusion, ArgumentStatus.INVALID, null, false, counter, vars,
                "The argument is INVALID. When ${formatAssignment(counter!!)}, every premise is true but the " +
                    "conclusion ${Printer.print(conclusion)} is false. A valid argument can never have true " +
                    "premises and a false conclusion, so no correct proof exists.",
            )
        }
        val proof = findProof(premises, conclusion)
        return when {
            proof != null -> ArgumentReport(
                premises, conclusion, ArgumentStatus.PROOF_FOUND, proof, semantic, null, vars,
                "The conclusion follows from the premises using the rules of inference shown. " +
                    (if (semantic == true) "The truth-table check agrees: in every row where all premises " +
                        "are true, the conclusion is also true." else ""),
            )
            semantic == true -> ArgumentReport(
                premises, conclusion, ArgumentStatus.VALID_NO_PROOF, null, true, null, vars,
                "The argument is VALID: all ${1 shl vars.size} rows of the truth table were checked, and in " +
                    "every row where the premises are all true the conclusion is true. However, LogicLab's " +
                    "proof search could not build a derivation using only the nine supported rules (it may " +
                    "need equivalence laws such as De Morgan's or Double Negation). This does not make the " +
                    "argument invalid.",
            )
            else -> ArgumentReport(
                premises, conclusion, ArgumentStatus.UNDETERMINED, null, null, null, vars,
                "No derivation was found with the supported rules, and the argument has ${vars.size} variables, " +
                    "more than the $MAX_VARIABLES that the truth-table check supports. Its validity could not " +
                    "be determined.",
            )
        }
    }
}
