package com.bsit1c.logic

/** A directed rewrite derived from one side of a law: whatever matches [from] may be replaced by [to]. */
data class Rewrite(
    val law: EquivalenceLaw,
    val from: Expr,
    val to: Expr,
    /** True when [from] is a bare metavariable, so the rewrite applies anywhere (e.g. p ⇒ ¬¬p). */
    val expanding: Boolean,
)

/** A pair of equivalent schemas, e.g. ¬(p ∧ q) ≡ ¬p ∨ ¬q. */
data class LawForm(val left: Expr, val right: Expr, val shown: Boolean = true) {
    val symbolic: String get() = "${Printer.print(left)} ≡ ${Printer.print(right)}"
}

data class EquivalenceLaw(
    val id: String,
    val number: Int,
    val name: String,
    val forms: List<LawForm>,
    val explanation: String,
    /** An expression (over P, Q, R) the reference screen transforms to illustrate the law. */
    val example: String,
) {
    val shownForms: List<LawForm> get() = forms.filter { it.shown }

    /** Both directions of every form, skipping directions that would invent new sub-expressions. */
    val rewrites: List<Rewrite> by lazy {
        val out = ArrayList<Rewrite>()
        for (f in forms) {
            if (f.left == f.right) continue
            listOf(f.left to f.right, f.right to f.left).forEach { (a, b) ->
                val invents = Matcher.metavariables(b) - Matcher.metavariables(a)
                if (invents.isEmpty()) {
                    // A bare metavariable matches everything (p ⇒ ¬¬p, p ⇒ p ∧ p, p ⇒ p ∧ T): "expanding".
                    out += Rewrite(this, a, b, expanding = a is Expr.Var)
                }
            }
        }
        out
    }
}

object EquivalenceLaws {
    private fun f(l: String, r: String, shown: Boolean = true) = LawForm(schema(l), schema(r), shown)

    val REFLEXIVITY = EquivalenceLaw(
        "refl", 1, "Reflexivity", listOf(f("p", "p")),
        "Every proposition is equivalent to itself: p and p have identical truth values in every row.",
        "P → Q",
    )
    val DOUBLE_NEGATION = EquivalenceLaw(
        "dn", 2, "Double Negation", listOf(f("¬¬p", "p")),
        "Negating twice returns the original truth value.",
        "¬¬(P ∧ Q)",
    )
    val COMMUTATIVITY = EquivalenceLaw(
        "comm", 3, "Commutativity", listOf(f("p ∧ q", "q ∧ p"), f("p ∨ q", "q ∨ p")),
        "The order of the operands of ∧ and of ∨ does not matter.",
        "P ∧ ¬Q",
    )
    val ASSOCIATIVITY = EquivalenceLaw(
        "assoc", 4, "Associativity", listOf(f("(p ∧ q) ∧ r", "p ∧ (q ∧ r)"), f("(p ∨ q) ∨ r", "p ∨ (q ∨ r)")),
        "When the same connective (∧ or ∨) is repeated, the grouping does not matter.",
        "(P ∨ Q) ∨ R",
    )
    val DISTRIBUTIVITY = EquivalenceLaw(
        "dist", 5, "Distributivity",
        listOf(f("p ∧ (q ∨ r)", "(p ∧ q) ∨ (p ∧ r)"), f("p ∨ (q ∧ r)", "(p ∨ q) ∧ (p ∨ r)")),
        "∧ distributes over ∨, and ∨ distributes over ∧ — like multiplication over addition, but both ways. " +
            "Read right-to-left it factors out a common part.",
        "P ∧ (Q ∨ R)",
    )
    val IDEMPOTENCY = EquivalenceLaw(
        "idem", 6, "Idempotency", listOf(f("p ∧ p", "p"), f("p ∨ p", "p")),
        "Combining a proposition with itself using ∧ or ∨ gives back the same proposition.",
        "(P → Q) ∨ (P → Q)",
    )
    val IDENTITY = EquivalenceLaw(
        "ident", 7, "Identity",
        listOf(f("p ∧ T", "p"), f("p ∨ F", "p"), f("T ∧ p", "p", false), f("F ∨ p", "p", false)),
        "T is the identity for ∧ and F is the identity for ∨: they leave the other operand unchanged.",
        "(P ∨ Q) ∧ T",
    )
    val INVERSE = EquivalenceLaw(
        "inv", 8, "Inverse",
        listOf(f("p ∨ ¬p", "T"), f("p ∧ ¬p", "F"), f("¬p ∨ p", "T", false), f("¬p ∧ p", "F", false)),
        "A proposition or its negation is always true; a proposition and its negation is always false. " +
            "(Also called the Negation or Complement laws.)",
        "Q ∨ ¬Q",
    )
    val DOMINANCE = EquivalenceLaw(
        "dom", 9, "Dominance",
        listOf(f("p ∨ T", "T"), f("p ∧ F", "F"), f("T ∨ p", "T", false), f("F ∧ p", "F", false)),
        "T dominates ∨ and F dominates ∧: the result is fixed regardless of p. (Also called Domination.)",
        "(P → Q) ∧ F",
    )
    val ABSORPTION = EquivalenceLaw(
        "abs", 10, "Absorption", listOf(f("p ∨ (p ∧ q)", "p"), f("p ∧ (p ∨ q)", "p")),
        "The longer part is redundant: whenever p decides the outer connective, q makes no difference.",
        "P ∨ (P ∧ Q)",
    )
    val DE_MORGAN = EquivalenceLaw(
        "dem", 11, "De Morgan's Laws", listOf(f("¬(p ∧ q)", "¬p ∨ ¬q"), f("¬(p ∨ q)", "¬p ∧ ¬q")),
        "To negate a conjunction or disjunction, negate each part and switch ∧ with ∨.",
        "¬(P ∧ Q)",
    )
    val CONTRAPOSITIVE = EquivalenceLaw(
        "contra", 12, "Contrapositive Law", listOf(f("p → q", "¬q → ¬p")),
        "An implication is equivalent to its contrapositive: swap hypothesis and conclusion and negate both. " +
            "(Also called Transposition.)",
        "P → Q",
    )
    val MATERIAL_IMPLICATION = EquivalenceLaw(
        "impl", 13, "Material Implication", listOf(f("p → q", "¬p ∨ q")),
        "p → q is false only when p is true and q is false, which is exactly when ¬p ∨ q is false.",
        "P → Q",
    )
    val MATERIAL_EQUIVALENCE = EquivalenceLaw(
        "equiv", 14, "Material Equivalence",
        listOf(f("p ↔ q", "(p → q) ∧ (q → p)"), f("p ↔ q", "(p ∧ q) ∨ (¬p ∧ ¬q)")),
        "A biconditional means the implication holds in both directions; equivalently both sides are true " +
            "or both are false.",
        "P ↔ Q",
    )
    val EXPORTATION = EquivalenceLaw(
        "exp", 15, "Exportation", listOf(f("(p ∧ q) → r", "p → (q → r)")),
        "Assuming p and q together to conclude r is the same as assuming p, then assuming q, to conclude r.",
        "(P ∧ Q) → R",
    )

    val all: List<EquivalenceLaw> = listOf(
        REFLEXIVITY, DOUBLE_NEGATION, COMMUTATIVITY, ASSOCIATIVITY, DISTRIBUTIVITY, IDEMPOTENCY, IDENTITY,
        INVERSE, DOMINANCE, ABSORPTION, DE_MORGAN, CONTRAPOSITIVE, MATERIAL_IMPLICATION,
        MATERIAL_EQUIVALENCE, EXPORTATION,
    )

    val allRewrites: List<Rewrite> by lazy { all.flatMap { it.rewrites } }
}

/** A rewrite applied at a specific place in a specific expression. */
data class RewriteStep(
    val law: EquivalenceLaw,
    val rewrite: Rewrite,
    val path: Path,
    val before: Expr,
    val after: Expr,
) {
    /** The sub-expression that was replaced and what replaced it. */
    val replaced: Expr get() = before.at(path)
    val replacement: Expr get() = after.at(path)
    val description: String get() = "${Printer.print(replaced)}  ⇒  ${Printer.print(replacement)}"
}

data class EquivalenceProof(
    val start: Expr,
    val target: Expr,
    val steps: List<RewriteStep>,
)

sealed class ProofSearchResult {
    data class Found(val proof: EquivalenceProof) : ProofSearchResult()
    data class NotEquivalent(val result: EquivalenceResult) : ProofSearchResult()
    /** Equivalent by truth table, but no chain of law applications was found within the limits. */
    object NotFound : ProofSearchResult()
}

data class ProofVerification(
    val allStepsValid: Boolean,
    val invalidStep: Int?,
    val reachesTarget: Boolean,
    val finalEquivalentToStart: Boolean,
) {
    val complete: Boolean get() = allStepsValid && reachesTarget && finalEquivalentToStart
}

object EquivalenceEngine {

    /** Every way a supported law can rewrite some sub-expression of [e]. */
    fun applicable(e: Expr, includeExpanding: Boolean = false): List<RewriteStep> {
        val out = ArrayList<RewriteStep>()
        val seen = HashSet<Pair<Path, Expr>>()
        for ((path, node) in e.nodesWithPaths()) {
            for (rw in EquivalenceLaws.allRewrites) {
                if (rw.expanding && !includeExpanding) continue
                val b = Matcher.match(rw.from, node) ?: continue
                val replacement = Matcher.substitute(rw.to, b) ?: continue
                if (replacement == node) continue
                val after = e.replaceAt(path, replacement)
                if (seen.add(path to after)) out += RewriteStep(rw.law, rw, path, e, after)
            }
        }
        return out
    }

    /** Applies the first matching rewrite of [law] (at the shallowest position) — used for examples. */
    fun applyLaw(law: EquivalenceLaw, e: Expr): RewriteStep? {
        val nodes = e.nodesWithPaths()
        // Prefer rewrites that match real structure over ones that apply anywhere (p ⇒ ¬¬p).
        for (rw in law.rewrites.sortedBy { it.expanding }) {
            for ((path, node) in nodes) {
                val b = Matcher.match(rw.from, node) ?: continue
                val replacement = Matcher.substitute(rw.to, b) ?: continue
                if (replacement == node) continue
                return RewriteStep(law, rw, path, e, e.replaceAt(path, replacement))
            }
        }
        return null
    }

    /**
     * Checks a chain of rewrites independently of how it was produced: every step's before/after
     * must be truth-table equivalent, consecutive steps must connect, and the last must equal [target].
     */
    fun verify(start: Expr, target: Expr?, steps: List<RewriteStep>): ProofVerification {
        var cur = start
        steps.forEachIndexed { i, s ->
            if (s.before != cur || !Semantics.equivalent(s.before, s.after).equivalent) {
                return ProofVerification(false, i + 1, false, false)
            }
            cur = s.after
        }
        val reaches = target != null && cur == target
        return ProofVerification(true, null, reaches, Semantics.equivalent(start, cur).equivalent)
    }

    /**
     * Bidirectional breadth-first search for a chain of law applications from [start] to [target].
     * Laws are symmetric, so a step found from the target side is reversed and appended.
     */
    fun findProof(start: Expr, target: Expr, maxStates: Int = 6000, extraSize: Int = 4): ProofSearchResult {
        val eq = Semantics.equivalent(start, target)
        if (!eq.equivalent) return ProofSearchResult.NotEquivalent(eq)
        if (start == target) return ProofSearchResult.Found(EquivalenceProof(start, target, emptyList()))

        val sizeLimit = maxOf(start.size, target.size) + extraSize
        val fwdParent = HashMap<Expr, RewriteStep?>().apply { put(start, null) }
        val bwdParent = HashMap<Expr, RewriteStep?>().apply { put(target, null) }
        var fwdFrontier = listOf(start)
        var bwdFrontier = listOf(target)

        fun expand(
            frontier: List<Expr>, mine: HashMap<Expr, RewriteStep?>, other: Map<Expr, RewriteStep?>,
        ): Pair<List<Expr>, Expr?> {
            val next = ArrayList<Expr>()
            for (e in frontier) {
                for (step in applicable(e, includeExpanding = true)) {
                    val a = step.after
                    if (a.size > sizeLimit || a in mine) continue
                    mine[a] = step
                    if (a in other) return next to a
                    next += a
                    if (mine.size > maxStates) return next to null
                }
            }
            return next to null
        }

        while (fwdFrontier.isNotEmpty() && bwdFrontier.isNotEmpty() &&
            fwdParent.size + bwdParent.size < maxStates * 2
        ) {
            val meet: Expr?
            if (fwdFrontier.size <= bwdFrontier.size) {
                val (n, m) = expand(fwdFrontier, fwdParent, bwdParent); fwdFrontier = n; meet = m
            } else {
                val (n, m) = expand(bwdFrontier, bwdParent, fwdParent); bwdFrontier = n; meet = m
            }
            if (meet != null) {
                return ProofSearchResult.Found(EquivalenceProof(start, target, buildPath(meet, fwdParent, bwdParent)))
            }
        }
        return ProofSearchResult.NotFound
    }

    private fun buildPath(
        meet: Expr, fwd: Map<Expr, RewriteStep?>, bwd: Map<Expr, RewriteStep?>,
    ): List<RewriteStep> {
        val first = ArrayList<RewriteStep>()
        var cur = meet
        while (true) {
            val s = fwd[cur] ?: break
            first += s
            cur = s.before
        }
        first.reverse()
        // Backward side: each recorded step went (closer to target) → (further). Reverse it.
        val second = ArrayList<RewriteStep>()
        cur = meet
        while (true) {
            val s = bwd[cur] ?: break
            val reversed = Rewrite(s.law, s.rewrite.to, s.rewrite.from, expanding = false)
            second += RewriteStep(s.law, reversed, s.path, s.after, s.before)
            cur = s.before
        }
        return first + second
    }
}
