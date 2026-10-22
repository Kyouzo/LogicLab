package com.bsit1c.logic

/**
 * Binary logical connectives, ordered from highest to lowest precedence.
 *
 * Precedence (high → low): ¬, ∧, ∨, →, ↔
 * ∧ and ∨ group from the left, → groups from the right (P → Q → R means P → (Q → R)),
 * ↔ groups from the left.
 */
enum class BinaryOp(
    val symbol: String,
    val displayName: String,
    /** Higher binds tighter. Negation is 5. */
    val precedence: Int,
    val rightAssociative: Boolean,
) {
    AND("∧", "Conjunction", 4, false),
    OR("∨", "Disjunction", 3, false),
    IMPLIES("→", "Implication", 2, true),
    IFF("↔", "Biconditional", 1, false);

    fun apply(a: Boolean, b: Boolean): Boolean = when (this) {
        AND -> a && b
        OR -> a || b
        IMPLIES -> !a || b
        IFF -> a == b
    }
}

/** Immutable propositional-logic expression tree. Structural equality via data classes. */
sealed class Expr {
    data class Var(val name: String) : Expr() {
        override fun toString(): String = Printer.print(this)
    }
    data class Const(val value: Boolean) : Expr() {
        override fun toString(): String = Printer.print(this)
    }
    data class Not(val operand: Expr) : Expr() {
        override fun toString(): String = Printer.print(this)
    }
    data class Binary(val op: BinaryOp, val left: Expr, val right: Expr) : Expr() {
        override fun toString(): String = Printer.print(this)
    }

    /** Precedence used when deciding where parentheses are needed. */
    val precedence: Int
        get() = when (this) {
            is Var, is Const -> 6
            is Not -> 5
            is Binary -> op.precedence
        }

    val children: List<Expr>
        get() = when (this) {
            is Var, is Const -> emptyList()
            is Not -> listOf(operand)
            is Binary -> listOf(left, right)
        }

    /** Number of nodes in the tree. */
    val size: Int by lazy(LazyThreadSafetyMode.PUBLICATION) { 1 + children.sumOf { it.size } }

    /** Distinct variable names, in natural order (P, Q, R … ; p1 before p2 before p10). */
    val variables: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val set = LinkedHashSet<String>()
        collectVars(this, set)
        set.sortedWith(VariableOrder)
    }

    companion object {
        private fun collectVars(e: Expr, out: MutableSet<String>) {
            when (e) {
                is Var -> out += e.name
                is Const -> Unit
                is Not -> collectVars(e.operand, out)
                is Binary -> { collectVars(e.left, out); collectVars(e.right, out) }
            }
        }
    }
}

/** Convenience builders (used heavily by tests and the rule/law tables). */
fun v(name: String): Expr = Expr.Var(name)
fun not(e: Expr): Expr = Expr.Not(e)
infix fun Expr.and(o: Expr): Expr = Expr.Binary(BinaryOp.AND, this, o)
infix fun Expr.or(o: Expr): Expr = Expr.Binary(BinaryOp.OR, this, o)
infix fun Expr.implies(o: Expr): Expr = Expr.Binary(BinaryOp.IMPLIES, this, o)
infix fun Expr.iff(o: Expr): Expr = Expr.Binary(BinaryOp.IFF, this, o)
val TRUE: Expr = Expr.Const(true)
val FALSE: Expr = Expr.Const(false)

/** Orders variable names by letter (case-insensitive, uppercase first), then numeric suffix. */
object VariableOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val la = a.takeWhile { !it.isDigit() }
        val lb = b.takeWhile { !it.isDigit() }
        val c = la.lowercase().compareTo(lb.lowercase())
        if (c != 0) return c
        val cc = la.compareTo(lb)
        if (cc != 0) return cc
        val na = a.drop(la.length).toIntOrNull() ?: -1
        val nb = b.drop(lb.length).toIntOrNull() ?: -1
        return na.compareTo(nb)
    }
}

fun Boolean.tf(): String = if (this) "T" else "F"

/** A path from the root to a node: each entry is a child index (0 = left/operand, 1 = right). */
typealias Path = List<Int>

fun Expr.at(path: Path): Expr {
    var cur = this
    for (i in path) cur = cur.children[i]
    return cur
}

fun Expr.replaceAt(path: Path, replacement: Expr): Expr {
    if (path.isEmpty()) return replacement
    val head = path.first()
    val rest = path.drop(1)
    return when (this) {
        is Expr.Not -> Expr.Not(operand.replaceAt(rest, replacement))
        is Expr.Binary -> if (head == 0) copy(left = left.replaceAt(rest, replacement))
        else copy(right = right.replaceAt(rest, replacement))
        else -> error("Path $path does not exist")
    }
}

/** All (path, node) pairs in pre-order. */
fun Expr.nodesWithPaths(): List<Pair<Path, Expr>> {
    val out = ArrayList<Pair<Path, Expr>>()
    fun go(e: Expr, p: Path) {
        out += p to e
        e.children.forEachIndexed { i, c -> go(c, p + i) }
    }
    go(this, emptyList())
    return out
}

/**
 * Converts expressions to text. Two styles are available:
 *
 * * [print] (the default, used throughout the app) adds clarifying parentheses whenever a binary
 *   connective appears inside a different binary connective — "(P ∧ Q) → P" rather than "P ∧ Q → P" —
 *   matching how textbooks write formulas. Chains of the same ∧ or ∨ are left unbracketed.
 * * [printMinimal] uses only the parentheses that precedence requires (for precedence exercises).
 *
 * Both can report the character range occupied by each node, which the UI uses to highlight the
 * part currently being evaluated or rewritten.
 */
object Printer {
    data class Rendered(val text: String, val ranges: Map<Path, IntRange>)

    fun print(e: Expr): String = render(e, trackRanges = false).text

    fun printMinimal(e: Expr): String = render(e, trackRanges = false, minimal = true).text

    /** Every compound sub-expression wrapped in parentheses (except the whole expression). */
    fun fullyParenthesized(e: Expr): String {
        fun go(x: Expr, top: Boolean): String = when (x) {
            is Expr.Var -> x.name
            is Expr.Const -> x.value.tf()
            is Expr.Not -> "¬" + go(x.operand, false)
            is Expr.Binary -> {
                val inner = "${go(x.left, false)} ${x.op.symbol} ${go(x.right, false)}"
                if (top) inner else "($inner)"
            }
        }
        return go(e, true)
    }

    /** Renders and records the character range of every node (ranges exclude wrapping parens). */
    fun render(e: Expr, trackRanges: Boolean = true, minimal: Boolean = false): Rendered {
        val sb = StringBuilder()
        val ranges = if (trackRanges) HashMap<Path, IntRange>() else null
        write(e, sb, emptyList(), ranges, minimal)
        return Rendered(sb.toString(), ranges ?: emptyMap())
    }

    private fun needsParens(parent: Expr.Binary, child: Expr, isLeft: Boolean, minimal: Boolean): Boolean {
        if (child !is Expr.Binary) return false
        val p = parent.op.precedence
        val required = child.precedence < p ||
            (child.precedence == p && (if (isLeft) parent.op.rightAssociative else !parent.op.rightAssociative))
        if (required || minimal) return required
        // Clarifying style: only an unbracketed left-leaning chain of the same ∧ / ∨ stays bare.
        val sameChain = child.op == parent.op && isLeft &&
            (parent.op == BinaryOp.AND || parent.op == BinaryOp.OR)
        return !sameChain
    }

    private fun write(e: Expr, sb: StringBuilder, path: Path, ranges: MutableMap<Path, IntRange>?, minimal: Boolean) {
        val start = sb.length
        when (e) {
            is Expr.Var -> sb.append(e.name)
            is Expr.Const -> sb.append(e.value.tf())
            is Expr.Not -> {
                sb.append('¬')
                child(e.operand, needsParens = e.operand is Expr.Binary, sb, path + 0, ranges, minimal)
            }
            is Expr.Binary -> {
                child(e.left, needsParens(e, e.left, true, minimal), sb, path + 0, ranges, minimal)
                sb.append(' ').append(e.op.symbol).append(' ')
                child(e.right, needsParens(e, e.right, false, minimal), sb, path + 1, ranges, minimal)
            }
        }
        ranges?.put(path, start until sb.length)
    }

    private fun child(
        e: Expr, needsParens: Boolean, sb: StringBuilder, path: Path, ranges: MutableMap<Path, IntRange>?,
        minimal: Boolean,
    ) {
        if (needsParens) sb.append('(')
        write(e, sb, path, ranges, minimal)
        if (needsParens) sb.append(')')
    }
}
