package com.bsit1c.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SemanticsTest {
    private fun parse(s: String) = Parser.parseOrThrow(s)
    private fun col(t: TruthTable, label: String): List<Boolean> {
        val c = t.columns.indexOfFirst { it.label == label }
        assertTrue("no column $label in ${t.columns.map { it.label }}", c >= 0)
        return (0 until t.rowCount).map { t.value(it, c) }
    }
    private fun tf(s: String) = s.map { it == 'T' }

    @Test fun connectiveTruthTables() {
        // Rows: TT, TF, FT, FF
        assertEquals(tf("TFFF"), col(TruthTable.build(parse("P ∧ Q")), "P ∧ Q"))
        assertEquals(tf("TTTF"), col(TruthTable.build(parse("P ∨ Q")), "P ∨ Q"))
        assertEquals(tf("TFTT"), col(TruthTable.build(parse("P → Q")), "P → Q"))
        assertEquals(tf("TFFT"), col(TruthTable.build(parse("P ↔ Q")), "P ↔ Q"))
        assertEquals(tf("FT"), col(TruthTable.build(parse("¬P")), "¬P"))
    }

    @Test fun specExampleTable() {
        val t = TruthTable.build(parse("(P ∧ Q) → P"))
        assertEquals(listOf("P", "Q", "P ∧ Q", "(P ∧ Q) → P"), t.columns.map { it.label })
        assertEquals(tf("TTFF"), col(t, "P"))
        assertEquals(tf("TFTF"), col(t, "Q"))
        assertEquals(tf("TFFF"), col(t, "P ∧ Q"))
        assertEquals(tf("TTTT"), col(t, "(P ∧ Q) → P"))
        assertTrue(t.columns.last().isFinal)
        assertEquals(1, t.columns.count { it.isFinal })
    }

    @Test fun rowCountIsTwoToTheN() {
        for (n in 1..MAX_VARIABLES) {
            val e = (1..n).map { v("P$it") as Expr }.reduce { a, b -> a or b }
            val t = TruthTable.build(e)
            assertEquals(1 shl n, t.rowCount)
            // Every assignment appears exactly once.
            val seen = (0 until t.rowCount).map { r -> (0 until n).map { t.value(r, it) } }.toSet()
            assertEquals(1 shl n, seen.size)
        }
    }

    @Test fun repeatedVariablesCountedOnce() {
        val t = TruthTable.build(parse("(P ∧ P) ∨ (¬P ∧ P)"))
        assertEquals(listOf("P"), t.variables)
        assertEquals(2, t.rowCount)
    }

    @Test fun sharedSubexpressionsGetOneColumn() {
        val t = TruthTable.build(parse("(P ∧ Q) ∨ ¬(P ∧ Q)"))
        assertEquals(listOf("P", "Q", "P ∧ Q", "¬(P ∧ Q)", "(P ∧ Q) ∨ ¬(P ∧ Q)"), t.columns.map { it.label })
    }

    @Test fun singleVariableAndConstantExpressions() {
        val t = TruthTable.build(parse("P"))
        assertEquals(1, t.columns.size)
        assertTrue(t.columns[0].isFinal)
        assertEquals(tf("TF"), (0 until 2).map { t.result(it) })

        val c = TruthTable.build(parse("T ∧ F"))
        assertEquals(1, c.rowCount)
        assertFalse(c.result(0))
        assertEquals(Classification.CONTRADICTION, Classifier.classify(c).classification)
        assertEquals(Classification.TAUTOLOGY, Classifier.classify(parse("T")).classification)
    }

    @Test(expected = TooManyVariablesException::class)
    fun variableLimitEnforced() {
        TruthTable.build((1..MAX_VARIABLES + 1).map { v("P$it") as Expr }.reduce { a, b -> a and b })
    }

    /** The compiled table evaluator must agree with direct recursive evaluation on random formulas. */
    @Test fun tableMatchesDirectEvaluationOnRandomFormulas() {
        val gen = QuizGenerator(Random(42))
        repeat(500) {
            val e = gen.randomExpr(listOf("P", "Q", "R", "S"), 4)
            val t = TruthTable.build(e)
            for (r in 0 until t.rowCount) {
                val a = t.assignment(r)
                assertEquals(e.toString(), e.evaluate(a), t.result(r))
                t.columns.forEachIndexed { c, column -> assertEquals(column.expr.evaluate(a), t.value(r, c)) }
            }
        }
    }

    @Test fun classification() {
        listOf("P ∨ ¬P", "(P ∧ Q) → P", "(P → Q) ↔ (¬Q → ¬P)", "¬(P ∧ Q) ↔ ¬P ∨ ¬Q",
            "((P → Q) ∧ (Q → R)) → (P → R)", "P → P", "T").forEach {
            assertEquals(it, Classification.TAUTOLOGY, Classifier.classify(parse(it)).classification)
        }
        listOf("P ∧ ¬P", "¬(P ∨ ¬P)", "(P ↔ Q) ∧ (P ↔ ¬Q)", "F", "(P → Q) ∧ P ∧ ¬Q").forEach {
            assertEquals(it, Classification.CONTRADICTION, Classifier.classify(parse(it)).classification)
        }
        listOf("P", "P ∧ Q", "P → Q", "(P ∨ Q) → R", "¬P ↔ Q").forEach {
            assertEquals(it, Classification.CONTINGENCY, Classifier.classify(parse(it)).classification)
        }
    }

    @Test fun classificationReportDetails() {
        val r = Classifier.classify(parse("P → Q"))
        assertEquals(3, r.trueRows)
        assertEquals(4, r.totalRows)
        assertTrue(r.satisfiable)
        assertEquals(mapOf("P" to true, "Q" to false), r.exampleFalse)
        assertNotNull(r.exampleTrue)
        assertFalse(Classifier.classify(parse("P ∧ ¬P")).satisfiable)
        assertNull(Classifier.classify(parse("P ∨ ¬P")).exampleFalse)
    }

    @Test fun equivalenceCheck() {
        assertTrue(Semantics.equivalent(parse("P → Q"), parse("¬P ∨ Q")).equivalent)
        assertTrue(Semantics.equivalent(parse("¬(P ∨ Q)"), parse("¬P ∧ ¬Q")).equivalent)
        val r = Semantics.equivalent(parse("P → Q"), parse("Q → P"))
        assertFalse(r.equivalent)
        val a = r.counterexample!!
        assertTrue(parse("P → Q").evaluate(a) != parse("Q → P").evaluate(a))
        // Different variable sets are compared over the union.
        assertTrue(Semantics.equivalent(parse("P ∨ (Q ∧ ¬Q)"), parse("P")).equivalent)
    }

    @Test fun plainTextExport() {
        val text = TruthTable.build(parse("P ∧ Q")).toPlainText()
        val lines = text.trim().lines()
        assertEquals(6, lines.size)
        assertTrue(lines[0].contains("P ∧ Q"))
        assertTrue(lines[2].startsWith("| T | T | T"))
    }

    // ---------- step-by-step ----------

    @Test fun specStepByStepExample() {
        val ev = StepEvaluator.evaluate(parse("(P ∧ ¬Q) → R"), mapOf("P" to true, "Q" to false, "R" to false))
        assertFalse(ev.result)
        val kinds = ev.steps.map { it.kind }
        assertEquals(
            listOf(StepKind.SUBSTITUTION, StepKind.NEGATION, StepKind.CONJUNCTION, StepKind.IMPLICATION, StepKind.RESULT),
            kinds,
        )
        assertEquals("(T ∧ ¬F) → F", ev.steps[0].after)
        assertEquals("(T ∧ T) → F", ev.steps[1].after)
        assertEquals("¬F = T", ev.steps[1].computation)
        assertEquals("T → F", ev.steps[2].after)
        assertEquals("F", ev.steps[3].after)
        // Highlight covers exactly the evaluated part.
        val s = ev.steps[1]
        assertEquals("¬F", s.before.substring(s.highlightBefore!!))
        assertEquals("T", s.after.substring(s.highlightAfter!!))
        assertEquals("T ∧ T", ev.steps[2].before.substring(ev.steps[2].highlightBefore!!))
    }

    @Test fun parenthesesForceInnerEvaluationFirst() {
        val ev = StepEvaluator.evaluate(parse("¬(P ∨ Q)"), mapOf("P" to false, "Q" to true))
        assertEquals(listOf(StepKind.SUBSTITUTION, StepKind.DISJUNCTION, StepKind.NEGATION, StepKind.RESULT),
            ev.steps.map { it.kind })
        assertFalse(ev.result)
    }

    @Test fun stepsFollowPrecedenceWhenSeveralAreReady() {
        val ev = StepEvaluator.evaluate(parse("P ↔ Q ∨ R ∧ ¬S"),
            mapOf("P" to true, "Q" to false, "R" to true, "S" to false))
        assertEquals(
            listOf(StepKind.SUBSTITUTION, StepKind.NEGATION, StepKind.CONJUNCTION, StepKind.DISJUNCTION,
                StepKind.BICONDITIONAL, StepKind.RESULT),
            ev.steps.map { it.kind },
        )
        assertTrue(ev.result)
    }

    @Test fun stepEvaluationAgreesWithEvaluatorEverywhere() {
        val gen = QuizGenerator(Random(7))
        repeat(300) {
            val e = gen.randomExpr(listOf("P", "Q", "R"), 3)
            for (r in 0 until (1 shl e.variables.size)) {
                val a = assignmentForRow(e.variables, r)
                val ev = StepEvaluator.evaluate(e, a)
                assertEquals(e.evaluate(a), ev.result)
                // One reduction step per operator node.
                val ops = e.nodesWithPaths().count { it.second is Expr.Not || it.second is Expr.Binary }
                val reductions = ev.steps.count { it.kind != StepKind.SUBSTITUTION && it.kind != StepKind.RESULT }
                assertEquals(ops, reductions)
                ev.steps.filter { it.highlightBefore != null }.forEach { step ->
                    assertEquals(step.computation.substringBefore(" = "), step.before.substring(step.highlightBefore!!))
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingAssignmentRejected() {
        StepEvaluator.evaluate(parse("P ∧ Q"), mapOf("P" to true))
    }

    @Test fun assignmentRowOrderIsTextbookOrder() {
        val vars = listOf("P", "Q")
        assertEquals(mapOf("P" to true, "Q" to true), assignmentForRow(vars, 0))
        assertEquals(mapOf("P" to true, "Q" to false), assignmentForRow(vars, 1))
        assertEquals(mapOf("P" to false, "Q" to true), assignmentForRow(vars, 2))
        assertEquals(mapOf("P" to false, "Q" to false), assignmentForRow(vars, 3))
        assertArrayEquals(booleanArrayOf(true, true, true), TruthTable.build(parse("P ∧ Q")).row(0))
        assertArrayEquals(booleanArrayOf(true, false, false), TruthTable.build(parse("P ∧ Q")).row(1))
    }

    @Test fun structurePartsAreInnermostFirstAndMatchTableColumns() {
        val e = parse("(P ∧ ¬Q) → R")
        val parts = Structure.parts(e)
        assertEquals(listOf("¬Q", "P ∧ ¬Q", "(P ∧ ¬Q) → R"), parts.map { it.text })
        assertTrue(parts.last().isMain)
        assertEquals("→ (implication)", Structure.mainConnective(e))
        val computed = TruthTable.build(e).columns.filter { !it.isVariable }.map { it.label }
        assertEquals(computed, parts.map { it.text })
    }
}
