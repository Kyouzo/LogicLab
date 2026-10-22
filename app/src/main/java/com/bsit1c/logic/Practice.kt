package com.bsit1c.logic

import kotlin.random.Random

enum class QuizTopic(val title: String, val module: Int) {
    EVALUATION("Evaluating propositions", 1),
    CLASSIFICATION("Tautology, contradiction, contingency", 1),
    STRUCTURE("Precedence & truth tables", 1),
    CONCEPTS("Concepts & vocabulary", 1),
    INFERENCE("Rules of inference", 2),
    VALIDITY("Argument validity", 2),
    LAWS("Equivalence laws", 2),
}

data class QuizQuestion(
    val id: String,
    val topic: QuizTopic,
    val prompt: String,
    /** Optional formula shown prominently under the prompt. */
    val formula: String?,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
) {
    val correctAnswer: String get() = options[correctIndex]
}

/**
 * Generates practice questions. Except for the small vocabulary bank, every question is built from
 * a random formula and its answer is computed by the logic engine — nothing is pre-solved.
 */
class QuizGenerator(private val random: Random = Random.Default) {

    fun generate(topics: Set<QuizTopic>, count: Int): List<QuizQuestion> {
        require(topics.isNotEmpty())
        val list = topics.toList()
        val out = ArrayList<QuizQuestion>()
        val usedConcepts = HashSet<Int>()
        var guard = 0
        while (out.size < count && guard++ < count * 20) {
            val topic = list[random.nextInt(list.size)]
            val q = runCatching { make(topic, usedConcepts) }.getOrNull() ?: continue
            if (out.none { it.prompt == q.prompt && it.formula == q.formula }) out += q
        }
        return out
    }

    private fun make(topic: QuizTopic, usedConcepts: MutableSet<Int>): QuizQuestion? = when (topic) {
        QuizTopic.EVALUATION -> evaluation()
        QuizTopic.CLASSIFICATION -> classification()
        QuizTopic.STRUCTURE -> if (random.nextBoolean()) mainConnective() else rowCount()
        QuizTopic.CONCEPTS -> concept(usedConcepts)
        QuizTopic.INFERENCE -> whichRule()
        QuizTopic.VALIDITY -> validity()
        QuizTopic.LAWS -> if (random.nextInt(3) == 0) equivalentPair() else whichLaw()
    }

    private var serial = 0
    private fun id(t: QuizTopic) = "${t.name}-${serial++}"

    // ---------- random formulas ----------

    private val varNames = listOf("P", "Q", "R", "S")

    fun randomExpr(vars: List<String>, depth: Int): Expr {
        if (depth <= 0 || (depth == 1 && random.nextInt(4) == 0)) {
            val v = Expr.Var(vars[random.nextInt(vars.size)])
            return if (random.nextInt(4) == 0) Expr.Not(v) else v
        }
        return when (random.nextInt(7)) {
            0 -> Expr.Not(randomExpr(vars, depth - 1)).let { if (it.operand is Expr.Not) it.operand else it }
            else -> {
                val op = BinaryOp.entries[random.nextInt(BinaryOp.entries.size)]
                Expr.Binary(op, randomExpr(vars, depth - 1), randomExpr(vars, depth - 1))
            }
        }
    }

    private fun formula(minVars: Int, maxVars: Int, minSize: Int, maxSize: Int): Expr {
        repeat(200) {
            val n = minVars + random.nextInt(maxVars - minVars + 1)
            val e = randomExpr(varNames.take(n), 2 + random.nextInt(2))
            if (e.size in minSize..maxSize && e.variables.size >= minVars) return e
        }
        return Parser.parseOrThrow("(P ∧ Q) → P")
    }

    private fun <T> shuffledWithAnswer(correct: T, distractors: List<T>): Pair<List<T>, Int> {
        val all = (distractors + correct).distinct().shuffled(random)
        return all to all.indexOf(correct)
    }

    // ---------- Module 1 ----------

    private fun evaluation(): QuizQuestion {
        val e = formula(2, 3, 5, 11)
        val a = assignmentForRow(e.variables, random.nextInt(1 shl e.variables.size))
        val ev = StepEvaluator.evaluate(e, a)
        val steps = ev.steps.filter { it.kind != StepKind.SUBSTITUTION && it.kind != StepKind.RESULT }
            .joinToString("; ") { it.computation }
        return QuizQuestion(
            id(QuizTopic.EVALUATION), QuizTopic.EVALUATION,
            "What is the truth value of this proposition when ${formatAssignment(a)}?",
            Printer.print(e), listOf("True (T)", "False (F)"), if (ev.result) 0 else 1,
            "Substituting gives ${ev.steps.first().after}. Evaluating innermost parts first: $steps. " +
                "So the proposition is ${if (ev.result) "true" else "false"}.",
        )
    }

    private fun classification(): QuizQuestion {
        val want = Classification.entries[random.nextInt(3)]
        val e = when (want) {
            Classification.CONTINGENCY -> generateSequence { formula(2, 3, 4, 9) }
                .first { Classifier.classify(it).classification == Classification.CONTINGENCY }
            Classification.TAUTOLOGY -> tautologyInstance()
            Classification.CONTRADICTION -> {
                val t = tautologyInstance()
                if (random.nextBoolean()) Expr.Not(t) else {
                    val x = formula(1, 2, 1, 4); x and Expr.Not(x)
                }
            }
        }
        val report = Classifier.classify(e)
        val opts = Classification.entries.map { it.title }
        return QuizQuestion(
            id(QuizTopic.CLASSIFICATION), QuizTopic.CLASSIFICATION,
            "Classify this proposition.", Printer.print(e), opts, report.classification.ordinal,
            report.explanation,
        )
    }

    /** A tautology built from a rule of inference or an equivalence law with random sub-formulas. */
    private fun tautologyInstance(): Expr {
        repeat(50) {
            val pattern: Expr = if (random.nextBoolean()) {
                val rule = InferenceRules.all[random.nextInt(InferenceRules.all.size)]
                rule.tautology()
            } else {
                val law = EquivalenceLaws.all.drop(1)[random.nextInt(EquivalenceLaws.all.size - 1)]
                val f = law.shownForms[random.nextInt(law.shownForms.size)]
                f.left iff f.right
            }
            val vars = varNames.shuffled(random)
            val b = Matcher.metavariables(pattern).sorted().withIndex().associate { (i, m) ->
                val x = Expr.Var(vars[i % vars.size])
                m to when (random.nextInt(5)) {
                    0 -> Expr.Not(x)
                    1 -> randomExpr(vars.take(3), 1)
                    else -> x
                }
            }
            val e = Matcher.substitute(pattern, b)!!
            if (e.size <= 15 && Semantics.isTautology(e)) return e
        }
        return Parser.parseOrThrow("P ∨ ¬P")
    }

    private fun mainConnective(): QuizQuestion {
        val e = generateSequence { formula(2, 4, 6, 11) }.first { it is Expr.Binary || it is Expr.Not }
        val correct = when (e) {
            is Expr.Not -> "¬ (negation)"
            is Expr.Binary -> "${e.op.symbol} (${e.op.displayName.lowercase()})"
            else -> error("atomic")
        }
        val opts = listOf("¬ (negation)") + BinaryOp.entries.map { "${it.symbol} (${it.displayName.lowercase()})" }
        // Show it with as few parentheses as possible so precedence matters.
        return QuizQuestion(
            id(QuizTopic.STRUCTURE), QuizTopic.STRUCTURE,
            "Using the precedence ¬, ∧, ∨, →, ↔, what is the main (last-evaluated) connective?",
            Printer.printMinimal(e), opts, opts.indexOf(correct),
            "Fully parenthesized, the proposition is ${Printer.fullyParenthesized(e)}. The connective applied " +
                "last is $correct.",
        )
    }

    private fun rowCount(): QuizQuestion {
        val e = formula(2, 4, 5, 12)
        val n = e.variables.size
        val correct = 1 shl n
        val (opts, idx) = shuffledWithAnswer(
            correct.toString(),
            listOf(n * 2, n * n + 1, correct * 2, (correct / 2).coerceAtLeast(1) + 1).filter { it != correct }
                .distinct().take(3).map { it.toString() },
        )
        return QuizQuestion(
            id(QuizTopic.STRUCTURE), QuizTopic.STRUCTURE,
            "How many rows does the complete truth table for this proposition have?",
            Printer.print(e), opts, idx,
            "It has $n distinct variables (${e.variables.joinToString()}), and each can be T or F, so there are " +
                "2^$n = $correct rows. Repeated variables are counted once.",
        )
    }

    private data class Concept(val prompt: String, val options: List<String>, val answer: Int, val why: String)

    private val concepts = listOf(
        Concept("Which of these is a proposition?",
            listOf("\"2 + 3 = 7\"", "\"Close the door.\"", "\"Is it raining?\"", "\"x + 1 = 5\""), 0,
            "A proposition is a declarative statement that is either true or false. \"2 + 3 = 7\" is false — but it is a proposition. Commands, questions and open sentences with unknown x are not."),
        Concept("In P → Q, what is P called?",
            listOf("Hypothesis (antecedent)", "Conclusion (consequent)", "Converse", "Contrapositive"), 0,
            "In an implication p → q, p is the hypothesis/antecedent and q is the conclusion/consequent."),
        Concept("What is the converse of P → Q?",
            listOf("Q → P", "¬P → ¬Q", "¬Q → ¬P", "P ∧ ¬Q"), 0,
            "The converse swaps hypothesis and conclusion: Q → P. ¬Q → ¬P is the contrapositive, ¬P → ¬Q the inverse."),
        Concept("Which statement is logically equivalent to P → Q?",
            listOf("¬Q → ¬P", "Q → P", "¬P → ¬Q", "P ∧ Q"), 0,
            "An implication is equivalent to its contrapositive ¬Q → ¬P (Contrapositive Law). The converse and inverse are not equivalent to it."),
        Concept("A compound proposition that is true for some assignments and false for others is a…",
            listOf("Contingency", "Tautology", "Contradiction", "Fallacy"), 0,
            "A contingency is neither always true (tautology) nor always false (contradiction)."),
        Concept("When is P ∨ Q false?",
            listOf("Only when both P and Q are false", "When exactly one is false", "When both are true", "Never"), 0,
            "Inclusive disjunction is true if at least one disjunct is true, so it is false only when both are false."),
        Concept("When is P → Q false?",
            listOf("When P is true and Q is false", "When P is false and Q is true", "When both are false", "When both are true"), 0,
            "An implication is false only when the hypothesis is true and the conclusion is false."),
        Concept("An argument is valid when…",
            listOf("the conclusion is true whenever all premises are true", "all premises are true",
                "the conclusion is true", "premises and conclusion have the same truth value in every row"), 0,
            "Validity is about form: there is no assignment making every premise true while the conclusion is false."),
        Concept("A proposition is satisfiable when…",
            listOf("at least one assignment makes it true", "every assignment makes it true",
                "no assignment makes it true", "it contains no variables"), 0,
            "Satisfiable means some row of its truth table is true. Tautologies and contingencies are satisfiable; contradictions are not."),
        Concept("Which connective has the highest precedence?",
            listOf("¬", "∧", "∨", "→"), 0,
            "The usual precedence is ¬, then ∧, then ∨, then →, then ↔. So ¬P ∧ Q means (¬P) ∧ Q."),
        Concept("P ↔ Q is true exactly when…",
            listOf("P and Q have the same truth value", "P is true", "at least one is true", "P and Q differ"), 0,
            "A biconditional asserts the two sides agree: both true or both false."),
        Concept("Proof by contradiction shows p by…",
            listOf("assuming ¬p and deriving a contradiction", "assuming p and deriving q",
                "proving the converse", "checking one example"), 0,
            "If ¬p leads to a contradiction (something always false), ¬p cannot be true, so p must be true."),
    )

    private fun concept(used: MutableSet<Int>): QuizQuestion? {
        val free = concepts.indices.filter { it !in used }
        if (free.isEmpty()) return null
        val i = free[random.nextInt(free.size)]
        used += i
        val c = concepts[i]
        val (opts, idx) = shuffledWithAnswer(c.options[c.answer], c.options.filterIndexed { k, _ -> k != c.answer })
        return QuizQuestion(id(QuizTopic.CONCEPTS), QuizTopic.CONCEPTS, c.prompt, null, opts, idx, c.why)
    }

    // ---------- Module 2 ----------

    private fun smallFormula(vars: List<String>): Expr =
        if (random.nextInt(3) == 0) randomExpr(vars, 1) else Expr.Var(vars[random.nextInt(vars.size)])

    private fun whichRule(): QuizQuestion? {
        val rule = InferenceRules.all[random.nextInt(InferenceRules.all.size)]
        val form = rule.primary
        val vars = varNames.shuffled(random)
        val metas = (form.premises + form.conclusion).flatMap { Matcher.metavariables(it) }.distinct().sorted()
        val b = HashMap<String, Expr>()
        metas.forEachIndexed { i, m ->
            b[m] = if (random.nextInt(4) == 0) Expr.Not(Expr.Var(vars[i % vars.size])) else Expr.Var(vars[i % vars.size])
        }
        val premises = form.premises.map { Matcher.substitute(it, b)!! }
        val conclusion = Matcher.substitute(form.conclusion, b)!!
        // Distractors must not also justify this step.
        val justifies = InferenceRules.all.filter { r ->
            InferenceEngine.apply(r, premises, b).any { it is RuleApplication.Derived && it.conclusion == conclusion }
        }
        if (rule !in justifies) return null
        if (justifies.size > 1) return null
        val distractors = InferenceRules.all.filter { it != rule }.shuffled(random).take(3).map { it.name }
        val (opts, idx) = shuffledWithAnswer(rule.name, distractors)
        val shown = premises.joinToString("\n") { Printer.print(it) } + "\n∴ " + Printer.print(conclusion)
        return QuizQuestion(
            id(QuizTopic.INFERENCE), QuizTopic.INFERENCE,
            "Which rule of inference justifies this step?", shown, opts, idx,
            "This matches ${rule.name}: ${form.symbolic}, with " +
                metas.joinToString(", ") { "$it = ${Printer.print(b.getValue(it))}" } + ". ${rule.explanation}",
        )
    }

    private fun validity(): QuizQuestion {
        val valid = random.nextBoolean()
        val vars = varNames.take(3)
        repeat(100) {
            val (premises, conclusion) = if (valid) {
                val rule = InferenceRules.all.drop(1)[random.nextInt(InferenceRules.all.size - 1)]
                val metas = (rule.primary.premises + rule.primary.conclusion).flatMap { Matcher.metavariables(it) }.distinct()
                val shuffled = varNames.shuffled(random)
                val b = metas.withIndex().associate { (i, m) -> m to (Expr.Var(shuffled[i % 4]) as Expr) }
                rule.primary.premises.map { Matcher.substitute(it, b)!! } to Matcher.substitute(rule.primary.conclusion, b)!!
            } else {
                // Common fallacies (affirming the consequent, denying the antecedent) or random arguments.
                val p = Expr.Var(vars[0]); val q = Expr.Var(vars[1])
                when (random.nextInt(3)) {
                    0 -> listOf(p implies q, q) to p
                    1 -> listOf(p implies q, Expr.Not(p)) to Expr.Not(q)
                    else -> List(2) { smallFormula(vars) implies smallFormula(vars) } to smallFormula(vars)
                }
            }
            val report = InferenceEngine.checkArgument(premises, conclusion)
            if (report.semanticallyValid == valid) {
                val shown = premises.joinToString("\n") { Printer.print(it) } + "\n∴ " + Printer.print(conclusion)
                val why = if (valid) {
                    "Valid. In every row of the truth table where all premises are true, the conclusion is true." +
                        (report.proof?.let { proof -> " A derivation: " + proof.filter { it.rule != null }
                            .joinToString("; ") { "${Printer.print(it.expr)} by ${it.reason}" } + "." } ?: "")
                } else report.explanation
                return QuizQuestion(
                    id(QuizTopic.VALIDITY), QuizTopic.VALIDITY, "Is this argument valid?", shown,
                    listOf("Valid", "Invalid"), if (valid) 0 else 1, why,
                )
            }
        }
        error("could not build")
    }

    private fun whichLaw(): QuizQuestion? {
        val law = EquivalenceLaws.all.drop(1)[random.nextInt(EquivalenceLaws.all.size - 1)]
        val form = law.shownForms[random.nextInt(law.shownForms.size)]
        val vars = varNames.shuffled(random)
        val metas = (Matcher.metavariables(form.left) + Matcher.metavariables(form.right)).sorted()
        val b = metas.withIndex().associate { (i, m) ->
            m to (if (random.nextInt(4) == 0) Expr.Not(Expr.Var(vars[i])) else Expr.Var(vars[i]))
        }
        val (l, r) = if (random.nextBoolean()) form.left to form.right else form.right to form.left
        val a = Matcher.substitute(l, b)!!
        val c = Matcher.substitute(r, b)!!
        val justifying = EquivalenceEngine.applicable(a, includeExpanding = true)
            .filter { it.after == c }.map { it.law }.distinct()
        if (law !in justifying || justifying.size > 1) return null
        val distractors = EquivalenceLaws.all.filter { it != law && it != EquivalenceLaws.REFLEXIVITY }
            .shuffled(random).take(3).map { it.name }
        val (opts, idx) = shuffledWithAnswer(law.name, distractors)
        return QuizQuestion(
            id(QuizTopic.LAWS), QuizTopic.LAWS, "Which equivalence law justifies this transformation?",
            "${Printer.print(a)}  ≡  ${Printer.print(c)}", opts, idx,
            "${law.name}: ${form.symbolic}. ${law.explanation}",
        )
    }

    private fun equivalentPair(): QuizQuestion {
        val p = Expr.Var("P"); val q = Expr.Var("Q")
        val pairs = listOf(
            Expr.Not(p and q) to (Expr.Not(p) and Expr.Not(q)),
            Expr.Not(p and q) to (Expr.Not(p) or Expr.Not(q)),
            Expr.Not(p or q) to (Expr.Not(p) and Expr.Not(q)),
            (p implies q) to (q implies p),
            (p implies q) to (Expr.Not(q) implies Expr.Not(p)),
            (p implies q) to (Expr.Not(p) implies Expr.Not(q)),
            (p implies q) to (Expr.Not(p) or q),
            (p implies q) to (p or Expr.Not(q)),
            Expr.Not(p implies q) to (p and Expr.Not(q)),
            (p iff q) to ((p implies q) and (q implies p)),
            (p iff q) to ((p and q) or (Expr.Not(p) and Expr.Not(q))),
            (p or (p and q)) to p,
            (p and (q or p)) to q,
        )
        val (a, b) = pairs[random.nextInt(pairs.size)]
        val res = Semantics.equivalent(a, b)
        val why = if (res.equivalent) {
            "Equivalent: their truth tables agree on all ${1 shl res.variables.size} rows."
        } else {
            "Not equivalent: when ${formatAssignment(res.counterexample!!)}, the left side is " +
                "${res.leftValue!!.tf()} but the right side is ${res.rightValue!!.tf()}."
        }
        return QuizQuestion(
            id(QuizTopic.LAWS), QuizTopic.LAWS, "Are these two propositions logically equivalent?",
            "${Printer.print(a)}   vs   ${Printer.print(b)}", listOf("Equivalent", "Not equivalent"),
            if (res.equivalent) 0 else 1, why,
        )
    }
}

/** Ready-made inputs the editor can load instantly. Their results are always computed, never stored. */
object Examples {
    val expressions = listOf(
        "(P ∧ Q) → P",
        "(P ∧ ¬Q) → R",
        "P ∨ ¬P",
        "P ∧ ¬P",
        "(P → Q) ↔ (¬Q → ¬P)",
        "¬(P ∨ Q) ↔ (¬P ∧ ¬Q)",
        "(P → Q) ∧ (Q → R) → (P → R)",
        "(P ∨ Q) ∧ (¬P ∨ R) → (Q ∨ R)",
        "P → Q ∨ R",
        "(P ↔ Q) ∧ ¬(P ∧ Q)",
    )

    data class Argument(val title: String, val premises: List<String>, val conclusion: String)

    val arguments = listOf(
        Argument("Modus ponens", listOf("P → Q", "P"), "Q"),
        Argument("Chain of reasoning", listOf("P → Q", "Q → R", "P"), "R"),
        Argument("Disjunctive syllogism", listOf("P ∨ Q", "¬P", "Q → R"), "R"),
        Argument("Multi-step proof", listOf("P ∧ Q", "P → R", "Q → S"), "R ∧ S"),
        Argument("Constructive dilemma", listOf("(P → Q) ∧ (R → S)", "P ∨ R"), "Q ∨ S"),
        Argument("Modus tollens chain", listOf("P → Q", "Q → R", "¬R"), "¬P"),
        Argument("Affirming the consequent", listOf("P → Q", "Q"), "P"),
        Argument("Needs De Morgan", listOf("¬(P ∧ Q)", "P"), "¬Q"),
    )

    data class EquivalencePair(val start: String, val target: String)

    val equivalences = listOf(
        EquivalencePair("¬(P ∨ ¬Q)", "¬P ∧ Q"),
        EquivalencePair("P → Q", "¬Q → ¬P"),
        EquivalencePair("¬(P → Q)", "P ∧ ¬Q"),
        EquivalencePair("(P ∧ Q) → R", "P → (Q → R)"),
        EquivalencePair("P ∨ (P ∧ Q)", "P"),
        EquivalencePair("¬(¬P ∧ ¬Q)", "P ∨ Q"),
        EquivalencePair("(P → Q) ∧ (P → R)", "P → Q ∧ R"),
    )
}
