package com.bsit1c.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InferenceTest {
    private fun parse(s: String) = Parser.parseOrThrow(s)
    private fun derived(rule: InferenceRule, vararg premises: String, extra: Map<String, Expr> = emptyMap()) =
        InferenceEngine.apply(rule, premises.map { parse(it) }, extra)
            .filterIsInstance<RuleApplication.Derived>().map { it.conclusion }

    @Test fun nineRulesInCourseOrder() {
        assertEquals(
            listOf("Addition", "Simplification", "Conjunction", "Modus Ponens", "Modus Tollens",
                "Disjunctive Syllogism", "Hypothetical Syllogism", "Constructive Dilemma", "Destructive Dilemma"),
            InferenceRules.all.map { it.name },
        )
    }

    @Test fun everyRuleFormIsATautology() {
        for (rule in InferenceRules.all) for (form in rule.forms) {
            assertTrue("${rule.name}: ${form.symbolic}", Semantics.isTautology(rule.tautology(form)))
        }
    }

    @Test fun addition() {
        assertEquals(listOf(parse("P ∨ (Q ∧ R)")), derived(InferenceRules.ADDITION, "P", extra = mapOf("q" to parse("Q ∧ R"))))
        val needs = InferenceEngine.apply(InferenceRules.ADDITION, listOf(parse("P"))).single()
        assertTrue(needs is RuleApplication.NeedsExtra)
        assertEquals(setOf("q"), (needs as RuleApplication.NeedsExtra).missing)
    }

    @Test fun simplification() =
        assertEquals(listOf(parse("P → Q"), parse("¬R")), derived(InferenceRules.SIMPLIFICATION, "(P → Q) ∧ ¬R"))

    @Test fun conjunction() {
        val r = derived(InferenceRules.CONJUNCTION, "P", "Q ∨ R")
        assertTrue(parse("P ∧ (Q ∨ R)") in r)
    }

    @Test fun modusPonensInEitherPremiseOrder() {
        assertEquals(listOf(parse("Q ∨ R")), derived(InferenceRules.MODUS_PONENS, "¬P → Q ∨ R", "¬P"))
        assertEquals(listOf(parse("Q ∨ R")), derived(InferenceRules.MODUS_PONENS, "¬P", "¬P → Q ∨ R"))
        assertTrue(derived(InferenceRules.MODUS_PONENS, "P → Q", "Q").isEmpty())
    }

    @Test fun modusTollens() {
        assertEquals(listOf(parse("¬P")), derived(InferenceRules.MODUS_TOLLENS, "P → Q", "¬Q"))
        assertEquals(listOf(parse("¬(P ∧ R)")), derived(InferenceRules.MODUS_TOLLENS, "P ∧ R → ¬Q", "¬¬Q"))
        // Denying the antecedent is not modus tollens.
        assertTrue(derived(InferenceRules.MODUS_TOLLENS, "P → Q", "¬P").isEmpty())
    }

    @Test fun disjunctiveSyllogismBothSides() {
        assertEquals(listOf(parse("Q")), derived(InferenceRules.DISJUNCTIVE_SYLLOGISM, "P ∨ Q", "¬P"))
        assertEquals(listOf(parse("P")), derived(InferenceRules.DISJUNCTIVE_SYLLOGISM, "P ∨ Q", "¬Q"))
        assertTrue(derived(InferenceRules.DISJUNCTIVE_SYLLOGISM, "P ∨ Q", "P").isEmpty())
    }

    @Test fun hypotheticalSyllogism() {
        assertEquals(listOf(parse("P → R")), derived(InferenceRules.HYPOTHETICAL_SYLLOGISM, "P → Q", "Q → R"))
        assertEquals(listOf(parse("P → R")), derived(InferenceRules.HYPOTHETICAL_SYLLOGISM, "Q → R", "P → Q"))
    }

    @Test fun constructiveDilemma() {
        assertEquals(listOf(parse("Q ∨ S")), derived(InferenceRules.CONSTRUCTIVE_DILEMMA, "(P → Q) ∧ (R → S)", "P ∨ R"))
        assertEquals(listOf(parse("Q ∨ S")), derived(InferenceRules.CONSTRUCTIVE_DILEMMA, "P → Q", "R → S", "P ∨ R"))
    }

    @Test fun destructiveDilemma() {
        assertEquals(listOf(parse("¬P ∨ ¬R")), derived(InferenceRules.DESTRUCTIVE_DILEMMA, "(P → Q) ∧ (R → S)", "¬Q ∨ ¬S"))
        assertEquals(listOf(parse("¬P ∨ ¬R")), derived(InferenceRules.DESTRUCTIVE_DILEMMA, "P → Q", "R → S", "¬Q ∨ ¬S"))
    }

    @Test fun mismatchExplained() {
        val r = InferenceEngine.apply(InferenceRules.MODUS_PONENS, listOf(parse("P")))
        assertTrue((r.single() as RuleApplication.NoMatch).reason.contains("needs 2 premises"))
    }

    /** Every conclusion produced by the engine must be semantically entailed by its premises. */
    @Test fun oneStepInferencesAreSound() {
        val gen = QuizGenerator(Random(3))
        repeat(200) {
            val lines = List(4) { gen.randomExpr(listOf("P", "Q", "R"), 2) } + listOf(parse("P → Q"), parse("P"))
            for (inf in InferenceEngine.oneStep(lines, emptySet())) {
                val premises = inf.from.map { lines[it] }
                val report = InferenceEngine.checkArgument(premises, inf.conclusion)
                assertEquals(true, report.semanticallyValid)
            }
        }
    }

    // ---------- argument checking ----------

    private fun check(conclusion: String, vararg premises: String) =
        InferenceEngine.checkArgument(premises.map { parse(it) }, parse(conclusion))

    @Test fun simpleProofFound() {
        val r = check("Q", "P → Q", "P")
        assertEquals(ArgumentStatus.PROOF_FOUND, r.status)
        val proof = r.proof!!
        assertEquals(3, proof.size)
        assertEquals("Premise", proof[0].justification)
        assertEquals(InferenceRules.MODUS_PONENS, proof[2].rule)
        assertEquals(listOf(1, 2), proof[2].from)
        assertEquals(true, r.semanticallyValid)
    }

    @Test fun multiStepProof() {
        val r = check("R ∧ S", "P ∧ Q", "P → R", "Q → S")
        assertEquals(ArgumentStatus.PROOF_FOUND, r.status)
        assertProofIsCorrect(r)
        assertEquals(parse("R ∧ S"), r.proof!!.last().expr)
    }

    @Test fun chainedProofs() {
        listOf(
            arrayOf("R", "P → Q", "Q → R", "P"),
            arrayOf("¬P", "P → Q", "Q → R", "¬R"),
            arrayOf("R", "P ∨ Q", "¬P", "Q → R"),
            arrayOf("Q ∨ S", "(P → Q) ∧ (R → S)", "P ∨ R"),
            arrayOf("¬P ∨ ¬R", "(P → Q) ∧ (R → S)", "¬Q ∨ ¬S"),
            arrayOf("P ∨ R", "P"),
            arrayOf("S", "P", "P → Q", "Q → R ∧ S"),
            arrayOf("T1 ∨ Q", "P → T1", "P"),
        ).forEach {
            val r = check(it[0], *it.drop(1).toTypedArray())
            assertEquals(it.joinToString(), ArgumentStatus.PROOF_FOUND, r.status)
            assertProofIsCorrect(r)
        }
    }

    @Test fun conclusionThatIsAPremise() {
        val r = check("P", "P", "Q")
        assertEquals(ArgumentStatus.PROOF_FOUND, r.status)
        assertEquals(2, r.proof!!.size)
    }

    @Test fun invalidArgumentsHaveCounterexamples() {
        listOf(
            arrayOf("P", "P → Q", "Q"),          // affirming the consequent
            arrayOf("¬Q", "P → Q", "¬P"),        // denying the antecedent
            arrayOf("R", "P ∨ Q", "P → R"),
        ).forEach {
            val premises = it.drop(1).map { s -> parse(s) }
            val conclusion = parse(it[0])
            val r = InferenceEngine.checkArgument(premises, conclusion)
            assertEquals(ArgumentStatus.INVALID, r.status)
            assertNull(r.proof)
            val c = r.counterexample!!
            assertTrue(premises.all { p -> p.evaluate(c) })
            assertFalse(conclusion.evaluate(c))
        }
    }

    /** Valid, but needs an equivalence law (De Morgan) — must not be called invalid. */
    @Test fun validWithoutSupportedDerivation() {
        val r = check("¬Q", "¬(P ∧ Q)", "P")
        assertEquals(ArgumentStatus.VALID_NO_PROOF, r.status)
        assertEquals(true, r.semanticallyValid)
        assertTrue(r.explanation.contains("does not make the argument invalid"))
    }

    private fun assertProofIsCorrect(r: ArgumentReport) {
        val proof = r.proof!!
        assertEquals(r.conclusion, proof.last().expr)
        for (line in proof) {
            val rule = line.rule
            if (rule == null) {
                assertTrue(line.expr in r.premises)
            } else {
                assertTrue(line.from.all { it < line.number })
                val premises = line.from.map { proof[it - 1].expr }
                val extra = HashMap<String, Expr>()
                if (rule == InferenceRules.ADDITION) extra["q"] = (line.expr as Expr.Binary).right
                val results = InferenceEngine.apply(rule, premises, extra)
                assertTrue(
                    "line ${line.number} ${line.expr} by ${line.reason}",
                    results.any { it is RuleApplication.Derived && it.conclusion == line.expr },
                )
            }
        }
    }
}

class EquivalenceTest {
    private fun parse(s: String) = Parser.parseOrThrow(s)

    @Test fun fifteenLawsInCourseOrder() {
        assertEquals(15, EquivalenceLaws.all.size)
        assertEquals((1..15).toList(), EquivalenceLaws.all.map { it.number })
    }

    @Test fun everyLawFormIsALogicalEquivalence() {
        for (law in EquivalenceLaws.all) for (f in law.forms) {
            assertTrue("${law.name}: ${f.symbolic}", Semantics.equivalent(f.left, f.right).equivalent)
        }
    }

    @Test fun everyLawExampleTransformsCorrectly() {
        for (law in EquivalenceLaws.all) {
            val e = parse(law.example)
            val step = EquivalenceEngine.applyLaw(law, e)
            if (law == EquivalenceLaws.REFLEXIVITY) { assertNull(step); continue }
            assertNotNull(law.name, step)
            assertTrue(law.name, Semantics.equivalent(step!!.before, step.after).equivalent)
        }
    }

    @Test fun specificTransformations() {
        fun apply(law: EquivalenceLaw, s: String) = EquivalenceEngine.applyLaw(law, parse(s))!!.after
        assertEquals(parse("¬P ∨ ¬Q"), apply(EquivalenceLaws.DE_MORGAN, "¬(P ∧ Q)"))
        assertEquals(parse("¬P ∧ ¬Q"), apply(EquivalenceLaws.DE_MORGAN, "¬(P ∨ Q)"))
        assertEquals(parse("¬P ∨ Q"), apply(EquivalenceLaws.MATERIAL_IMPLICATION, "P → Q"))
        assertEquals(parse("¬Q → ¬P"), apply(EquivalenceLaws.CONTRAPOSITIVE, "P → Q"))
        assertEquals(parse("P → (Q → R)"), apply(EquivalenceLaws.EXPORTATION, "(P ∧ Q) → R"))
        assertEquals(parse("P ∧ Q"), apply(EquivalenceLaws.DOUBLE_NEGATION, "¬¬(P ∧ Q)"))
        assertEquals(parse("(P ∧ Q) ∨ (P ∧ R)"), apply(EquivalenceLaws.DISTRIBUTIVITY, "P ∧ (Q ∨ R)"))
        assertEquals(parse("P"), apply(EquivalenceLaws.ABSORPTION, "P ∨ (P ∧ Q)"))
        assertEquals(parse("T"), apply(EquivalenceLaws.INVERSE, "Q ∨ ¬Q"))
        assertEquals(parse("F"), apply(EquivalenceLaws.DOMINANCE, "(P → Q) ∧ F"))
        assertEquals(parse("P ∨ Q"), apply(EquivalenceLaws.IDENTITY, "(P ∨ Q) ∧ T"))
        assertEquals(parse("P → Q"), apply(EquivalenceLaws.IDEMPOTENCY, "(P → Q) ∨ (P → Q)"))
        assertEquals(parse("¬P ∨ Q"), apply(EquivalenceLaws.MATERIAL_IMPLICATION, "P → Q"))
        assertEquals(parse("(P → Q) ∧ (Q → P)"), apply(EquivalenceLaws.MATERIAL_EQUIVALENCE, "P ↔ Q"))
        assertEquals(parse("P ∨ (Q ∨ R)"), apply(EquivalenceLaws.ASSOCIATIVITY, "(P ∨ Q) ∨ R"))
        assertEquals(parse("¬Q ∧ P"), apply(EquivalenceLaws.COMMUTATIVITY, "P ∧ ¬Q"))
    }

    @Test fun rewritesApplyInsideSubexpressions() {
        val steps = EquivalenceEngine.applicable(parse("R ∧ ¬(P ∨ Q)"))
        val dm = steps.first { it.law == EquivalenceLaws.DE_MORGAN }
        assertEquals(listOf(1), dm.path)
        assertEquals(parse("R ∧ (¬P ∧ ¬Q)"), dm.after)
    }

    @Test fun allApplicableRewritesAreSound() {
        val gen = QuizGenerator(Random(11))
        repeat(150) {
            val e = gen.randomExpr(listOf("P", "Q", "R"), 3)
            for (s in EquivalenceEngine.applicable(e, includeExpanding = true)) {
                assertTrue("${s.law.name}: $e ⇒ ${s.after}", Semantics.equivalent(e, s.after).equivalent)
            }
        }
    }

    @Test fun expandingRewritesHiddenByDefault() {
        val steps = EquivalenceEngine.applicable(parse("P"))
        assertTrue(steps.isEmpty())
        assertTrue(EquivalenceEngine.applicable(parse("P"), includeExpanding = true).any { it.law == EquivalenceLaws.DOUBLE_NEGATION })
    }

    @Test fun automaticProofs() {
        Examples.equivalences.forEach { pair ->
            val start = parse(pair.start)
            val target = parse(pair.target)
            val r = EquivalenceEngine.findProof(start, target)
            assertTrue("${pair.start} ≡ ${pair.target}: $r", r is ProofSearchResult.Found)
            val proof = (r as ProofSearchResult.Found).proof
            val v = EquivalenceEngine.verify(start, target, proof.steps)
            assertTrue("${pair.start}: $v", v.complete)
        }
    }

    @Test fun nonEquivalentPairsAreRejectedWithCounterexample() {
        val r = EquivalenceEngine.findProof(parse("P → Q"), parse("Q → P"))
        assertTrue(r is ProofSearchResult.NotEquivalent)
        assertNotNull((r as ProofSearchResult.NotEquivalent).result.counterexample)
    }

    @Test fun verifierRejectsBrokenChains() {
        val start = parse("¬(P ∧ Q)")
        val good = EquivalenceEngine.applyLaw(EquivalenceLaws.DE_MORGAN, start)!!
        assertTrue(EquivalenceEngine.verify(start, parse("¬P ∨ ¬Q"), listOf(good)).complete)
        // A target that was not reached is not a complete proof.
        assertFalse(EquivalenceEngine.verify(start, parse("¬Q ∨ ¬P"), listOf(good)).complete)
        // A forged step (wrong "law") fails.
        val forged = good.copy(after = parse("¬P ∧ ¬Q"))
        val v = EquivalenceEngine.verify(start, parse("¬P ∧ ¬Q"), listOf(forged))
        assertFalse(v.complete)
        assertEquals(1, v.invalidStep)
    }
}

class PracticeTest {
    @Test fun generatedQuestionsAreWellFormedAndCorrect() {
        val gen = QuizGenerator(Random(2024))
        val qs = gen.generate(QuizTopic.entries.toSet(), 300)
        assertTrue(qs.size >= 100)
        assertTrue(QuizTopic.entries.all { t -> qs.any { it.topic == t } })
        for (q in qs) {
            assertTrue(q.prompt, q.correctIndex in q.options.indices)
            assertEquals(q.options.size, q.options.distinct().size)
            assertTrue(q.explanation.isNotBlank())
            // Re-verify computed answers independently.
            when (q.topic) {
                QuizTopic.CLASSIFICATION -> {
                    val c = Classifier.classify(Parser.parseOrThrow(q.formula!!)).classification
                    assertEquals(c.title, q.correctAnswer)
                }
                QuizTopic.VALIDITY -> {
                    val lines = q.formula!!.lines()
                    val premises = lines.dropLast(1).map { Parser.parseOrThrow(it) }
                    val conclusion = Parser.parseOrThrow(lines.last().removePrefix("∴ "))
                    val valid = InferenceEngine.checkArgument(premises, conclusion).semanticallyValid
                    assertEquals(if (valid == true) "Valid" else "Invalid", q.correctAnswer)
                }
                else -> Unit
            }
        }
    }
}
