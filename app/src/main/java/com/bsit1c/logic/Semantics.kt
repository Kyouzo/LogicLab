package com.bsit1c.logic

/** Upper bound on distinct variables for exhaustive (truth-table) work: 2^10 = 1024 rows. */
const val MAX_VARIABLES = 10

class TooManyVariablesException(val count: Int) :
    IllegalArgumentException(
        "This uses $count variables. LogicLab checks every row of the truth table, so it supports at most " +
            "$MAX_VARIABLES variables (${1 shl MAX_VARIABLES} rows)."
    )

/** Evaluates [this] under [assignment]. Every variable in the expression must be assigned. */
fun Expr.evaluate(assignment: Map<String, Boolean>): Boolean = when (this) {
    is Expr.Var -> assignment[name] ?: throw IllegalArgumentException("No truth value given for $name")
    is Expr.Const -> value
    is Expr.Not -> !operand.evaluate(assignment)
    is Expr.Binary -> op.apply(left.evaluate(assignment), right.evaluate(assignment))
}

/**
 * Builds the assignment for row [row] of a truth table over [variables].
 * Row 0 is all-true and the last variable alternates fastest (T T, T F, F T, F F), the textbook order.
 */
fun assignmentForRow(variables: List<String>, row: Int): Map<String, Boolean> {
    val n = variables.size
    val m = LinkedHashMap<String, Boolean>(n * 2)
    for (i in 0 until n) m[variables[i]] = (row shr (n - 1 - i)) and 1 == 0
    return m
}

/**
 * A complete truth table: every one of the 2^n assignments, with a column for each variable and each
 * distinct compound sub-expression (in evaluation order). The last column is always the full expression.
 */
class TruthTable private constructor(
    val expr: Expr,
    val variables: List<String>,
    val columns: List<Column>,
    /** values[row][column] */
    private val values: Array<BooleanArray>,
) {
    data class Column(val expr: Expr, val label: String, val isVariable: Boolean, val isFinal: Boolean)

    val rowCount: Int get() = values.size
    val finalColumnIndex: Int get() = columns.lastIndex

    fun value(row: Int, column: Int): Boolean = values[row][column]
    fun result(row: Int): Boolean = values[row][finalColumnIndex]
    fun row(row: Int): BooleanArray = values[row].copyOf()
    fun assignment(row: Int): Map<String, Boolean> = assignmentForRow(variables, row)

    /** Plain-text rendering, used for copy / share. */
    fun toPlainText(): String {
        val widths = columns.map { maxOf(it.label.length, 1) }
        val sb = StringBuilder()
        fun line(cells: List<String>) {
            sb.append("| ")
            cells.forEachIndexed { i, c -> sb.append(c.padEnd(widths[i])).append(" | ") }
            sb.setLength(sb.length - 1)
            sb.append('\n')
        }
        line(columns.map { it.label })
        sb.append("|").append(widths.joinToString("|") { "-".repeat(it + 2) }).append("|\n")
        for (r in 0 until rowCount) line(columns.indices.map { values[r][it].tf() })
        return sb.toString()
    }

    companion object {
        fun build(expr: Expr): TruthTable {
            val vars = expr.variables
            if (vars.size > MAX_VARIABLES) throw TooManyVariablesException(vars.size)
            val program = Program.compile(expr, vars)
            val cols = ArrayList<Column>()
            val colNodes = ArrayList<Int>()
            vars.forEachIndexed { i, name ->
                cols += Column(Expr.Var(name), name, isVariable = true, isFinal = expr == Expr.Var(name))
                colNodes += program.varNode[i]
            }
            for ((idx, node) in program.nodes.withIndex()) {
                val isRoot = idx == program.nodes.lastIndex
                if (node is Expr.Var) continue
                if (node is Expr.Const && !isRoot) continue
                cols += Column(node, Printer.print(node), isVariable = false, isFinal = isRoot)
                colNodes += idx
            }
            val rows = 1 shl vars.size
            val values = Array(rows) { r ->
                val nodeVals = program.run(r)
                BooleanArray(colNodes.size) { c -> nodeVals[colNodes[c]] }
            }
            return TruthTable(expr, vars, cols, values)
        }
    }
}

/**
 * The expression flattened into distinct sub-expressions in post-order, so each row can be
 * evaluated with a single pass over an array (shared sub-expressions are computed once).
 */
internal class Program private constructor(
    val nodes: List<Expr>,
    private val kind: IntArray,      // 0 var, 1 const, 2 not, 3 binary
    private val a: IntArray,         // var index / const value / operand / left
    private val b: IntArray,         // right
    private val ops: Array<BinaryOp?>,
    val varNode: IntArray,
    private val varCount: Int,
) {
    fun run(row: Int): BooleanArray {
        val out = BooleanArray(nodes.size)
        for (i in nodes.indices) {
            out[i] = when (kind[i]) {
                0 -> (row shr (varCount - 1 - a[i])) and 1 == 0
                1 -> a[i] == 1
                2 -> !out[a[i]]
                else -> ops[i]!!.apply(out[a[i]], out[b[i]])
            }
        }
        return out
    }

    companion object {
        fun compile(root: Expr, vars: List<String>): Program {
            val index = HashMap<Expr, Int>()
            val nodes = ArrayList<Expr>()
            val kind = ArrayList<Int>()
            val a = ArrayList<Int>()
            val b = ArrayList<Int>()
            val ops = ArrayList<BinaryOp?>()
            val varIdx = vars.withIndex().associate { it.value to it.index }
            // Variables first, in table order, so they always exist even if repeated.
            val varNode = IntArray(vars.size)
            fun add(e: Expr, k: Int, x: Int, y: Int, op: BinaryOp?): Int {
                index[e]?.let { return it }
                nodes += e; kind += k; a += x; b += y; ops += op
                return (nodes.size - 1).also { index[e] = it }
            }
            vars.forEachIndexed { i, name -> varNode[i] = add(Expr.Var(name), 0, i, 0, null) }
            fun visit(e: Expr): Int = index[e] ?: when (e) {
                is Expr.Var -> add(e, 0, varIdx.getValue(e.name), 0, null)
                is Expr.Const -> add(e, 1, if (e.value) 1 else 0, 0, null)
                is Expr.Not -> { val o = visit(e.operand); add(e, 2, o, 0, null) }
                is Expr.Binary -> {
                    val l = visit(e.left); val r = visit(e.right)
                    add(e, 3, l, r, e.op)
                }
            }
            val rootIdx = visit(root)
            // The root must be the last node so callers can find the final column.
            if (rootIdx != nodes.lastIndex) {
                // Happens only when the root is a variable (already added up front): append a duplicate.
                nodes += root; kind += kind[rootIdx]; a += a[rootIdx]; b += b[rootIdx]; ops += ops[rootIdx]
            }
            return Program(
                nodes, kind.toIntArray(), a.toIntArray(), b.toIntArray(), ops.toTypedArray(), varNode, vars.size,
            )
        }
    }
}

enum class Classification(val title: String) {
    TAUTOLOGY("Tautology"),
    CONTRADICTION("Contradiction"),
    CONTINGENCY("Contingency"),
}

data class PropertyReport(
    val classification: Classification,
    val satisfiable: Boolean,
    val trueRows: Int,
    val totalRows: Int,
    val exampleTrue: Map<String, Boolean>?,
    val exampleFalse: Map<String, Boolean>?,
    val explanation: String,
)

fun formatAssignment(a: Map<String, Boolean>): String =
    if (a.isEmpty()) "(no variables)" else a.entries.joinToString(", ") { "${it.key} = ${it.value.tf()}" }

object Classifier {
    /** Classifies by inspecting every row of the complete truth table. */
    fun classify(table: TruthTable): PropertyReport {
        val total = table.rowCount
        var trueRows = 0
        var firstTrue = -1
        var firstFalse = -1
        for (r in 0 until total) {
            if (table.result(r)) { trueRows++; if (firstTrue < 0) firstTrue = r }
            else if (firstFalse < 0) firstFalse = r
        }
        val cls = when (trueRows) {
            total -> Classification.TAUTOLOGY
            0 -> Classification.CONTRADICTION
            else -> Classification.CONTINGENCY
        }
        val exT = if (firstTrue >= 0) table.assignment(firstTrue) else null
        val exF = if (firstFalse >= 0) table.assignment(firstFalse) else null
        val rowsWord = if (total == 1) "row" else "rows"
        val explanation = when (cls) {
            Classification.TAUTOLOGY ->
                "The final column is T in all $total $rowsWord of the truth table, so the expression is true under " +
                    "every possible assignment. It is a tautology (logically valid), and therefore also satisfiable."
            Classification.CONTRADICTION ->
                "The final column is F in all $total $rowsWord, so no assignment makes the expression true. " +
                    "It is a contradiction and is not satisfiable."
            Classification.CONTINGENCY ->
                "The final column is T in $trueRows of $total rows and F in ${total - trueRows}. " +
                    "For example, it is true when ${formatAssignment(exT!!)} and false when " +
                    "${formatAssignment(exF!!)}. Because its truth value depends on the assignment, it is a " +
                    "contingency. It is satisfiable since at least one row is true."
        }
        return PropertyReport(cls, trueRows > 0, trueRows, total, exT, exF, explanation)
    }

    fun classify(expr: Expr): PropertyReport = classify(TruthTable.build(expr))
}

/** Result of comparing two expressions over every assignment of their combined variables. */
data class EquivalenceResult(
    val equivalent: Boolean,
    val variables: List<String>,
    /** An assignment where the two sides differ, if any. */
    val counterexample: Map<String, Boolean>?,
    val leftValue: Boolean?,
    val rightValue: Boolean?,
)

object Semantics {
    fun combinedVariables(vararg exprs: Expr): List<String> =
        exprs.flatMap { it.variables }.distinct().sortedWith(VariableOrder)

    fun equivalent(a: Expr, b: Expr): EquivalenceResult {
        val vars = combinedVariables(a, b)
        if (vars.size > MAX_VARIABLES) throw TooManyVariablesException(vars.size)
        for (r in 0 until (1 shl vars.size)) {
            val asg = assignmentForRow(vars, r)
            val x = a.evaluate(asg)
            val y = b.evaluate(asg)
            if (x != y) return EquivalenceResult(false, vars, asg, x, y)
        }
        return EquivalenceResult(true, vars, null, null, null)
    }

    fun isTautology(e: Expr): Boolean = Classifier.classify(e).classification == Classification.TAUTOLOGY
}
