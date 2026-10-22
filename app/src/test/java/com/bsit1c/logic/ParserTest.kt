package com.bsit1c.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ParserTest {
    private val P = v("P")
    private val Q = v("Q")
    private val R = v("R")

    private fun parse(s: String) = Parser.parseOrThrow(s)

    private fun error(s: String): LogicError = when (val r = Parser.parse(s)) {
        is ParseResult.Failure -> r.error
        is ParseResult.Success -> { fail("Expected an error for \"$s\" but got ${r.expr}"); throw AssertionError() }
    }

    @Test fun singleVariable() = assertEquals(P, parse("P"))

    @Test fun constants() {
        assertEquals(TRUE, parse("T"))
        assertEquals(FALSE, parse("F"))
        assertEquals(TRUE and FALSE, parse("⊤ ∧ ⊥"))
        assertEquals(v("T1"), parse("T1"))
    }

    @Test fun negationBindsTighterThanConjunction() = assertEquals(not(P) and Q, parse("¬P ∧ Q"))

    @Test fun conjunctionBindsTighterThanDisjunction() {
        assertEquals(P or (Q and R), parse("P ∨ Q ∧ R"))
        assertEquals((P and Q) or R, parse("P ∧ Q ∨ R"))
    }

    @Test fun disjunctionBindsTighterThanImplication() {
        assertEquals(P implies (Q or R), parse("P → Q ∨ R"))
        assertEquals((P or Q) implies R, parse("P ∨ Q → R"))
    }

    @Test fun implicationBindsTighterThanBiconditional() {
        assertEquals((P implies Q) iff R, parse("P → Q ↔ R"))
        assertEquals(P iff (Q implies R), parse("P ↔ Q → R"))
    }

    @Test fun implicationIsRightAssociative() = assertEquals(P implies (Q implies R), parse("P → Q → R"))

    @Test fun conjunctionAndDisjunctionAreLeftAssociative() {
        assertEquals((P and Q) and R, parse("P ∧ Q ∧ R"))
        assertEquals((P or Q) or R, parse("P ∨ Q ∨ R"))
        assertEquals((P iff Q) iff R, parse("P ↔ Q ↔ R"))
    }

    @Test fun parenthesesOverridePrecedence() {
        assertEquals((P or Q) and R, parse("(P ∨ Q) ∧ R"))
        assertEquals((P implies Q) implies R, parse("(P → Q) → R"))
        assertEquals(not(P and Q), parse("¬(P ∧ Q)"))
        assertEquals(P, parse("((P))"))
    }

    @Test fun repeatedNegation() = assertEquals(not(not(not(P))), parse("¬¬¬P"))

    @Test fun asciiAlternatives() {
        val expected = (not(P) and Q) implies (R iff (P or Q))
        listOf(
            "(~P & Q) -> (R <-> (P | Q))",
            "(!P ^ Q) => (R <=> (P + Q))",
            "(¬P /\\ Q) → (R ↔ (P \\/ Q))",
            "(NOT P AND Q) IMPLIES (R IFF (P OR Q))",
            "(not P and Q) implies (R iff (P or Q))",
            "[~P & Q] > {R = (P | Q)}",
        ).forEach { assertEquals(it, expected, parse(it)) }
    }

    @Test fun lowercaseAndIndexedVariables() {
        assertEquals(v("p") and v("q1"), parse("p ∧ q1"))
        assertEquals(v("P1") or v("P2"), parse("P₁ ∨ P₂"))
    }

    @Test fun whitespaceIsIgnored() = assertEquals(P implies Q, parse("   P→Q  "))

    @Test fun variablesAreDistinctAndSorted() {
        assertEquals(listOf("P", "Q", "R"), parse("(R ∧ P) ∨ (Q → P) ∨ R").variables)
        assertEquals(listOf("p1", "p2", "p10"), parse("p10 ∧ p2 ∧ p1").variables)
        assertEquals(emptyList<String>(), parse("T ∨ F").variables)
    }

    @Test fun normalizedPrintingRoundTrips() {
        listOf(
            "P ∧ Q → R", "¬(P ∨ Q) ↔ ¬P ∧ ¬Q", "P → (Q → R)", "(P → Q) → R", "P ∧ (Q ∧ R)",
            "¬¬P", "(P ↔ Q) ↔ R", "P ↔ (Q ↔ R)", "¬(P → Q) ∨ T",
        ).forEach { src ->
            val e = parse(src)
            assertEquals(src, e, parse(Printer.print(e)))
            assertEquals(src, e, parse(Printer.printMinimal(e)))
        }
        // Default printing adds clarifying parentheses between different connectives.
        assertEquals("(P ∧ Q) ∨ R", parse("P ∧ Q ∨ R").toString())
        assertEquals("(P ∧ Q) → P", parse("P ∧ Q → P").toString())
        assertEquals("P → (Q → R)", parse("P → Q → R").toString())
        assertEquals("(P → Q) → R", parse("(P → Q) → R").toString())
        assertEquals("P ∧ Q ∧ R", parse("(P ∧ Q) ∧ R").toString())
        assertEquals("P ∧ (Q ∧ R)", parse("P ∧ (Q ∧ R)").toString())
        // Minimal printing uses only the parentheses precedence requires.
        assertEquals("P ∧ Q ∨ R", Printer.printMinimal(parse("(P ∧ Q) ∨ R")))
        assertEquals("P → Q → R", Printer.printMinimal(parse("P → (Q → R)")))
        assertEquals("(P → Q) → R", Printer.printMinimal(parse("(P → Q) → R")))
        assertEquals("P ∨ Q ∧ R ↔ ¬P", Printer.printMinimal(parse("(P ∨ (Q ∧ R)) ↔ ¬P")))
        assertEquals("¬(P ∧ Q)", parse("~(P&Q)").toString())
    }

    @Test fun fullyParenthesized() {
        assertEquals("(¬P ∧ Q) → (R ∨ S)", Printer.fullyParenthesized(parse("¬P ∧ Q → R ∨ S")))
    }

    // ---------- errors ----------

    @Test fun emptyInput() {
        assertTrue(error("").message.contains("Enter an expression"))
        assertTrue(error("   ").message.contains("Enter an expression"))
    }

    @Test fun missingRightOperand() {
        val e = error("P ∧")
        assertTrue(e.message, e.message.contains("missing its right operand"))
        assertEquals(2, e.start)
    }

    @Test fun missingLeftOperand() {
        val e = error("∨ Q")
        assertTrue(e.message, e.message.contains("missing its left operand"))
        assertEquals(0, e.start)
    }

    @Test fun twoOperatorsInARow() {
        val e = error("P ∧ ∨ Q")
        assertTrue(e.message, e.message.contains("missing its right operand"))
    }

    @Test fun missingOperatorBetweenOperands() {
        val e = error("P Q")
        assertTrue(e.message, e.message.contains("Missing operator between 'P' and 'Q'"))
        assertEquals(2, e.start)
        assertTrue(error("PQ").message.contains("Missing operator"))
        assertTrue(error("(P ∧ Q)(R)").message.contains("Missing operator"))
        assertTrue(error("(P Q)").message.contains("Missing operator"))
    }

    @Test fun unbalancedParentheses() {
        assertTrue(error("(P ∧ Q").message.contains("never closed"))
        assertTrue(error("P ∧ Q)").message.contains("no matching '('"))
        assertTrue(error(")P").message.contains("Unexpected ')'"))
        assertTrue(error("()").message.contains("Empty parentheses"))
        assertTrue(error("((P)").message.contains("never closed"))
    }

    @Test fun danglingNegation() {
        assertTrue(error("¬").message.contains("must be followed"))
        assertTrue(error("P ∧ ¬").message.contains("must be followed"))
        assertTrue(error("¬)").message.contains("must be followed"))
    }

    @Test fun unknownSymbol() {
        val e = error("P # Q")
        assertTrue(e.message.contains("Unrecognized symbol '#'"))
        assertEquals(2, e.start)
        assertTrue(error("P ∧ Q?").message.contains("Unrecognized"))
    }

    @Test fun tooLong() {
        assertTrue(error("P ∧ ".repeat(200) + "P").message.contains("too long"))
    }
}
