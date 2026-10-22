package com.bsit1c.logiclab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bsit1c.logic.TruthTable
import com.bsit1c.logic.tf

/** How a whole row should be tinted (e.g. rows where every premise is true). */
enum class RowMark { NONE, EMPHASIS, PROBLEM }

/**
 * A truth table that stays usable on a phone: columns size to their headers, the grid scrolls
 * horizontally as one piece, and long tables scroll vertically inside a bounded, lazily-drawn body.
 */
@Composable
fun TruthGrid(
    headers: List<String>,
    rowCount: Int,
    value: (row: Int, col: Int) -> Boolean,
    modifier: Modifier = Modifier,
    /** Columns drawn as "result" columns (bold, tinted). */
    resultColumns: Set<Int> = setOf(headers.lastIndex),
    /** Columns before this index are input (variable) columns, separated by a divider. */
    inputColumns: Int = 0,
    rowMark: (row: Int) -> RowMark = { RowMark.NONE },
    maxBodyHeight: Dp = 420.dp,
) {
    val cs = MaterialTheme.colorScheme
    val lab = Lab.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headerStyle = FormulaStyle.small.copy(fontWeight = FontWeight.SemiBold)
    val widths: List<Dp> = remember(headers, density) {
        headers.map { h ->
            val px = measurer.measure(h, headerStyle).size.width
            with(density) { px.toDp() } + 24.dp
        }.map { if (it < 44.dp) 44.dp else it }
    }
    val total = widths.fold(0.dp) { a, b -> a + b }
    val rowHeight = 36.dp
    val dividerColor = cs.outline
    val divider = Modifier.drawBehind {
        val x = size.width - 1.dp.toPx()
        drawLine(dividerColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
    }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(Modifier.width(total)) {
            // Header
            Row(Modifier.background(cs.surfaceContainerHigh)) {
                headers.forEachIndexed { c, h ->
                    Box(
                        Modifier
                            .width(widths[c])
                            .height(40.dp)
                            .background(if (c in resultColumns) cs.primaryContainer else Transparent)
                            .then(if (c == inputColumns - 1) divider else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            h,
                            style = headerStyle,
                            color = if (c in resultColumns) cs.onPrimaryContainer else cs.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
            val bodyRow: @Composable (Int) -> Unit = { r ->
                val mark = rowMark(r)
                val rowBg = when (mark) {
                    RowMark.EMPHASIS -> lab.highlight.copy(alpha = 0.22f)
                    RowMark.PROBLEM -> cs.errorContainer
                    RowMark.NONE -> if (r % 2 == 1) cs.surfaceContainer else cs.surfaceContainerLowest
                }
                Row(Modifier.background(rowBg)) {
                    for (c in headers.indices) {
                        val v = value(r, c)
                        val isResult = c in resultColumns
                        Box(
                            Modifier
                                .width(widths[c])
                                .height(rowHeight)
                                .background(if (isResult) cs.primaryContainer.copy(alpha = 0.35f) else Transparent)
                                .then(if (c == inputColumns - 1) divider else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                v.tf(),
                                style = FormulaStyle.small.copy(
                                    fontWeight = if (isResult) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = when {
                                    c < inputColumns -> cs.onSurface
                                    v -> lab.trueFg
                                    else -> lab.falseFg
                                },
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            if (rowCount <= 16) {
                for (r in 0 until rowCount) bodyRow(r)
            } else {
                LazyColumn(Modifier.heightIn(max = maxBodyHeight)) {
                    items(rowCount) { r -> bodyRow(r) }
                }
            }
        }
    }
}

/** Convenience overload for an engine [TruthTable]. */
@Composable
fun TruthTableView(
    table: TruthTable,
    modifier: Modifier = Modifier,
    rowMark: (Int) -> RowMark = { RowMark.NONE },
    maxBodyHeight: Dp = 420.dp,
) {
    TruthGrid(
        headers = table.columns.map { it.label },
        rowCount = table.rowCount,
        value = { r, c -> table.value(r, c) },
        modifier = modifier,
        resultColumns = setOf(table.finalColumnIndex),
        inputColumns = table.variables.size,
        rowMark = rowMark,
        maxBodyHeight = maxBodyHeight,
    )
}

@Composable
fun TableLegend() {
    Text(
        "Columns build up from the variables to the full expression (highlighted). " +
            "Rows list every assignment, T before F.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
