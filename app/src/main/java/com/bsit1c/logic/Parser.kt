package com.bsit1c.logic

/** A user-facing syntax error. [start]..[end] is the offending character range in the input (end exclusive). */
data class LogicError(val message: String, val start: Int, val end: Int = start + 1) {
    val hasPosition: Boolean get() = start >= 0
}

sealed class ParseResult {
    data class Success(val expr: Expr) : ParseResult()
    data class Failure(val error: LogicError) : ParseResult()

    fun getOrNull(): Expr? = (this as? Success)?.expr
}

enum class TokenType { VAR, CONST, NOT, BINARY, LPAREN, RPAREN }

data class Token(
    val type: TokenType,
    val text: String,
    val start: Int,
    val end: Int,
    val op: BinaryOp? = null,
    val constValue: Boolean = false,
) {
    /** How the token is shown in messages: always the canonical symbol. */
    val display: String
        get() = when (type) {
            TokenType.BINARY -> op!!.symbol
            TokenType.NOT -> "¬"
            TokenType.CONST -> constValue.tf()
            else -> text
        }
}

class TokenizeException(val error: LogicError) : Exception(error.message)

/**
 * Converts raw input into tokens. Accepts the Unicode symbols and common ASCII spellings:
 *
 * | Meaning | Accepted |
 * |---|---|
 * | ¬ | ¬ ~ ! − (or the word NOT) |
 * | ∧ | ∧ & ^ * /\ (or AND) |
 * | ∨ | ∨ \| + \/ (or OR) |
 * | → | → -> => ⇒ ⊃ (or IMPLIES) |
 * | ↔ | ↔ <-> <=> ⇔ ≡ (or IFF) |
 * | true / false | T F ⊤ ⊥ 1 0 |
 *
 * Variables are a single letter optionally followed by digits (P, q, P1, R23).
 * The capital letters T and F are reserved for the constants true and false.
 */
object Tokenizer {
    private val words = mapOf(
        "NOT" to null, "AND" to BinaryOp.AND, "OR" to BinaryOp.OR,
        "IMPLIES" to BinaryOp.IMPLIES, "IFF" to BinaryOp.IFF,
        "TRUE" to null, "FALSE" to null,
    )

    fun tokenize(input: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        val n = input.length
        fun add(type: TokenType, len: Int, op: BinaryOp? = null, cv: Boolean = false) {
            out += Token(type, input.substring(i, i + len), i, i + len, op, cv)
            i += len
        }
        while (i < n) {
            val c = input[i]
            if (c.isWhitespace()) { i++; continue }
            val rest = input.substring(i)
            when {
                rest.startsWith("<->") || rest.startsWith("<=>") -> add(TokenType.BINARY, 3, BinaryOp.IFF)
                rest.startsWith("->") || rest.startsWith("=>") -> add(TokenType.BINARY, 2, BinaryOp.IMPLIES)
                rest.startsWith("/\\") -> add(TokenType.BINARY, 2, BinaryOp.AND)
                rest.startsWith("\\/") -> add(TokenType.BINARY, 2, BinaryOp.OR)
                c == '¬' || c == '~' || c == '!' || c == '−' || c == '-' -> add(TokenType.NOT, 1)
                c == '∧' || c == '&' || c == '^' || c == '*' || c == '·' -> add(TokenType.BINARY, 1, BinaryOp.AND)
                c == '∨' || c == '|' || c == '+' -> add(TokenType.BINARY, 1, BinaryOp.OR)
                c == '→' || c == '⇒' || c == '⊃' || c == '>' -> add(TokenType.BINARY, 1, BinaryOp.IMPLIES)
                c == '↔' || c == '⇔' || c == '≡' || c == '=' -> add(TokenType.BINARY, 1, BinaryOp.IFF)
                c == '(' || c == '[' || c == '{' -> add(TokenType.LPAREN, 1)
                c == ')' || c == ']' || c == '}' -> add(TokenType.RPAREN, 1)
                c == '⊤' || c == '1' -> add(TokenType.CONST, 1, cv = true)
                c == '⊥' || c == '0' -> add(TokenType.CONST, 1, cv = false)
                c.isLetter() -> {
                    // Whole-word keywords first (AND, OR, NOT, IMPLIES, IFF, TRUE, FALSE).
                    var j = i
                    while (j < n && input[j].isLetter()) j++
                    val word = input.substring(i, j)
                    val upper = word.uppercase()
                    if (word.length > 1 && upper in words) {
                        val len = word.length
                        when (upper) {
                            "NOT" -> add(TokenType.NOT, len)
                            "TRUE" -> add(TokenType.CONST, len, cv = true)
                            "FALSE" -> add(TokenType.CONST, len, cv = false)
                            else -> add(TokenType.BINARY, len, words.getValue(upper))
                        }
                    } else if (c == 'T' || c == 'F') {
                        val after = i + 1
                        if (after < n && input[after].isDigit()) {
                            // T1, F2 … are ordinary variables.
                            var k = after
                            while (k < n && input[k].isDigit()) k++
                            add(TokenType.VAR, k - i)
                        } else {
                            add(TokenType.CONST, 1, cv = c == 'T')
                        }
                    } else {
                        var k = i + 1
                        while (k < n && input[k].isDigit()) k++
                        // Unicode subscript digits (P₁) are also accepted and normalised.
                        while (k < n && input[k] in '₀'..'₉') k++
                        val raw = input.substring(i, k)
                        val name = raw.map { ch -> if (ch in '₀'..'₉') '0' + (ch - '₀') else ch }.joinToString("")
                        out += Token(TokenType.VAR, name, i, k)
                        i = k
                    }
                }
                else -> throw TokenizeException(
                    LogicError("Unrecognized symbol '$c' at position ${i + 1}.", i, i + 1)
                )
            }
        }
        return out
    }
}

/**
 * Recursive-descent parser implementing the precedence ¬ > ∧ > ∨ > → > ↔.
 *
 * Grammar:
 * ```
 * iff     := implies ( '↔' implies )*          // left-associative
 * implies := or ( '→' implies )?              // right-associative
 * or      := and ( '∨' and )*
 * and     := unary ( '∧' unary )*
 * unary   := '¬' unary | atom
 * atom    := VAR | CONST | '(' iff ')'
 * ```
 */
object Parser {
    const val MAX_LENGTH = 400

    fun parse(input: String): ParseResult {
        if (input.isBlank()) return ParseResult.Failure(LogicError("Enter an expression to begin.", -1, -1))
        if (input.length > MAX_LENGTH) {
            return ParseResult.Failure(LogicError("Expression is too long (max $MAX_LENGTH characters).", -1, -1))
        }
        val tokens = try {
            Tokenizer.tokenize(input)
        } catch (e: TokenizeException) {
            return ParseResult.Failure(e.error)
        }
        if (tokens.isEmpty()) return ParseResult.Failure(LogicError("Enter an expression to begin.", -1, -1))
        return try {
            val p = State(tokens, input.length)
            val e = p.parseIff()
            if (!p.atEnd()) p.unexpectedTrailing()
            ParseResult.Success(e)
        } catch (e: TokenizeException) {
            ParseResult.Failure(e.error)
        }
    }

    /** Parses or throws [IllegalArgumentException]; intended for tests and built-in content. */
    fun parseOrThrow(input: String): Expr = when (val r = parse(input)) {
        is ParseResult.Success -> r.expr
        is ParseResult.Failure -> throw IllegalArgumentException("${r.error.message} in \"$input\"")
    }

    private class State(val tokens: List<Token>, val inputLength: Int) {
        var pos = 0

        fun atEnd() = pos >= tokens.size
        fun peek(): Token? = tokens.getOrNull(pos)
        fun fail(msg: String, start: Int, end: Int = start + 1): Nothing =
            throw TokenizeException(LogicError(msg, start, end))

        fun parseIff(): Expr {
            var left = parseImplies()
            while (peek()?.op == BinaryOp.IFF) {
                val opTok = tokens[pos++]
                val right = operand(opTok) { parseImplies() }
                left = Expr.Binary(BinaryOp.IFF, left, right)
            }
            return left
        }

        fun parseImplies(): Expr {
            val left = parseOr()
            if (peek()?.op == BinaryOp.IMPLIES) {
                val opTok = tokens[pos++]
                val right = operand(opTok) { parseImplies() }
                return Expr.Binary(BinaryOp.IMPLIES, left, right)
            }
            return left
        }

        fun parseOr(): Expr {
            var left = parseAnd()
            while (peek()?.op == BinaryOp.OR) {
                val opTok = tokens[pos++]
                val right = operand(opTok) { parseAnd() }
                left = Expr.Binary(BinaryOp.OR, left, right)
            }
            return left
        }

        fun parseAnd(): Expr {
            var left = parseUnary()
            while (peek()?.op == BinaryOp.AND) {
                val opTok = tokens[pos++]
                val right = operand(opTok) { parseUnary() }
                left = Expr.Binary(BinaryOp.AND, left, right)
            }
            return left
        }

        /** Parses the right operand of [opTok], producing a precise error if it is missing. */
        fun operand(opTok: Token, parse: () -> Expr): Expr {
            val next = peek()
            if (next == null || next.type == TokenType.RPAREN || next.type == TokenType.BINARY) {
                val where = if (next == null) "at the end of the expression"
                else "before '${next.display}'"
                fail(
                    "The ${opTok.op!!.displayName.lowercase()} '${opTok.display}' at position ${opTok.start + 1} " +
                        "is missing its right operand ($where).",
                    opTok.start, opTok.end,
                )
            }
            return parse()
        }

        fun parseUnary(): Expr {
            val t = peek()
            if (t != null && t.type == TokenType.NOT) {
                pos++
                val next = peek()
                if (next == null || next.type == TokenType.RPAREN || next.type == TokenType.BINARY) {
                    fail("The negation '¬' at position ${t.start + 1} must be followed by a proposition.", t.start, t.end)
                }
                return Expr.Not(parseUnary())
            }
            return parseAtom()
        }

        fun parseAtom(): Expr {
            val t = peek() ?: fail("The expression ended unexpectedly.", inputLength - 1, inputLength)
            return when (t.type) {
                TokenType.VAR -> { pos++; Expr.Var(t.text) }
                TokenType.CONST -> { pos++; Expr.Const(t.constValue) }
                TokenType.LPAREN -> {
                    pos++
                    val nxt = peek()
                    if (nxt == null) fail("The '(' at position ${t.start + 1} is never closed.", t.start, t.end)
                    if (nxt.type == TokenType.RPAREN) {
                        fail("Empty parentheses at position ${t.start + 1}: put a proposition inside.", t.start, nxt.end)
                    }
                    val inner = parseIff()
                    val close = peek()
                    if (close == null) fail("The '(' at position ${t.start + 1} is never closed.", t.start, t.end)
                    if (close.type != TokenType.RPAREN) {
                        missingOperator(tokens[pos - 1], close)
                    }
                    pos++
                    inner
                }
                TokenType.RPAREN -> fail("Unexpected ')' at position ${t.start + 1}.", t.start, t.end)
                TokenType.BINARY -> fail(
                    "The ${t.op!!.displayName.lowercase()} '${t.display}' at position ${t.start + 1} " +
                        "is missing its left operand.",
                    t.start, t.end,
                )
                TokenType.NOT -> error("unreachable")
            }
        }

        fun missingOperator(prev: Token, next: Token): Nothing {
            fail(
                "Missing operator between '${prev.display}' and '${next.display}' " +
                    "(position ${next.start + 1}). Did you mean ${prev.display} ∧ ${next.display}?",
                next.start, next.end,
            )
        }

        fun unexpectedTrailing() {
            val t = tokens[pos]
            if (t.type == TokenType.RPAREN) {
                fail("Unexpected ')' at position ${t.start + 1} — it has no matching '('.", t.start, t.end)
            }
            missingOperator(tokens[pos - 1], t)
        }
    }
}
