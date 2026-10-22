package com.bsit1c.logic

/**
 * Pattern matching for rule and law schemas. In a pattern every [Expr.Var] is a metavariable
 * that stands for any sub-expression (consistently: the same metavariable must match equal
 * sub-expressions). Constants must match exactly.
 */
object Matcher {
    fun match(pattern: Expr, target: Expr, bindings: Map<String, Expr> = emptyMap()): Map<String, Expr>? {
        val out = HashMap(bindings)
        return if (go(pattern, target, out)) out else null
    }

    private fun go(p: Expr, t: Expr, b: MutableMap<String, Expr>): Boolean = when (p) {
        is Expr.Var -> {
            val bound = b[p.name]
            if (bound == null) { b[p.name] = t; true } else bound == t
        }
        is Expr.Const -> t is Expr.Const && t.value == p.value
        is Expr.Not -> t is Expr.Not && go(p.operand, t.operand, b)
        is Expr.Binary -> t is Expr.Binary && t.op == p.op && go(p.left, t.left, b) && go(p.right, t.right, b)
    }

    /** Instantiates [pattern] with [bindings]; returns null if some metavariable is unbound. */
    fun substitute(pattern: Expr, bindings: Map<String, Expr>): Expr? = when (pattern) {
        is Expr.Var -> bindings[pattern.name]
        is Expr.Const -> pattern
        is Expr.Not -> substitute(pattern.operand, bindings)?.let { Expr.Not(it) }
        is Expr.Binary -> {
            val l = substitute(pattern.left, bindings)
            val r = substitute(pattern.right, bindings)
            if (l == null || r == null) null else Expr.Binary(pattern.op, l, r)
        }
    }

    fun metavariables(pattern: Expr): Set<String> = pattern.variables.toSet()
}

/** Parses a built-in schema such as "p ∧ q"; built-in content is known to be well-formed. */
internal fun schema(text: String): Expr = Parser.parseOrThrow(text)
