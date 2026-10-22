package com.bsit1c.logic

/** One compound sub-expression in the order it must be evaluated (innermost first). */
data class StructurePart(
    val number: Int,
    val expr: Expr,
    val text: String,
    val description: String,
    val isMain: Boolean,
)

/** Explains how an expression is built, which is also the column order of its truth table. */
object Structure {
    fun mainConnective(e: Expr): String? = when (e) {
        is Expr.Not -> "¬ (negation)"
        is Expr.Binary -> "${e.op.symbol} (${e.op.displayName.lowercase()})"
        else -> null
    }

    fun parts(root: Expr): List<StructurePart> {
        val seen = LinkedHashSet<Expr>()
        fun visit(e: Expr) {
            e.children.forEach { visit(it) }
            if (e is Expr.Not || e is Expr.Binary) seen += e
        }
        visit(root)
        return seen.mapIndexed { i, e ->
            StructurePart(i + 1, e, Printer.print(e), describe(e), e == root)
        }
    }

    fun describe(e: Expr): String = when (e) {
        is Expr.Not -> "Negation of ${Printer.print(e.operand)}: true exactly when ${Printer.print(e.operand)} is false."
        is Expr.Binary -> {
            val a = Printer.print(e.left)
            val b = Printer.print(e.right)
            when (e.op) {
                BinaryOp.AND -> "Conjunction of $a and $b: true only when both are true."
                BinaryOp.OR -> "Disjunction of $a and $b: true when at least one is true."
                BinaryOp.IMPLIES -> "Implication with hypothesis $a and conclusion $b: false only when $a is true and $b is false."
                BinaryOp.IFF -> "Biconditional of $a and $b: true when both have the same truth value."
            }
        }
        is Expr.Var -> "The variable ${e.name}."
        is Expr.Const -> "The constant ${e.value.tf()}."
    }
}
