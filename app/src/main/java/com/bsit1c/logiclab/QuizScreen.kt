package com.bsit1c.logiclab

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bsit1c.logic.QuizGenerator
import com.bsit1c.logic.QuizQuestion
import com.bsit1c.logic.QuizTopic
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface QuizPhase {
    data object Setup : QuizPhase
    data object Generating : QuizPhase
    data class Running(val questions: List<QuizQuestion>) : QuizPhase
    data class Finished(val questions: List<QuizQuestion>, val answers: List<Int>) : QuizPhase
}

@Composable
fun QuizScreen(store: LabStore, onBack: () -> Unit) {
    var phase by remember { mutableStateOf<QuizPhase>(QuizPhase.Setup) }
    val topics = remember { mutableStateListOf(*QuizTopic.entries.toTypedArray()) }
    var count by remember { mutableIntStateOf(10) }

    if (phase is QuizPhase.Generating) {
        LaunchedEffect(Unit) {
            val qs = withContext(Dispatchers.Default) { QuizGenerator().generate(topics.toSet(), count) }
            phase = QuizPhase.Running(qs)
        }
    }

    ScreenScaffold(title = "Practice Quiz", subtitle = "Questions generated and graded on-device", onBack = onBack) { padding ->
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentKey = { it::class },
            modifier = Modifier.fillMaxSize().padding(padding),
            label = "quiz",
        ) { p ->
            when (p) {
                QuizPhase.Setup -> QuizSetup(store, topics, count, { count = it }) { phase = QuizPhase.Generating }
                QuizPhase.Generating -> LoadingRow("Generating questions…")
                is QuizPhase.Running -> QuizRunner(p.questions) { answers ->
                    val correct = p.questions.indices.count { answers[it] == p.questions[it].correctIndex }
                    store.addQuizRecord(
                        QuizRecord(System.currentTimeMillis(), correct, p.questions.size,
                            p.questions.map { it.topic.title }.distinct().joinToString(", "))
                    )
                    phase = QuizPhase.Finished(p.questions, answers)
                }
                is QuizPhase.Finished -> QuizResults(p.questions, p.answers,
                    onRetry = { phase = QuizPhase.Generating }, onSetup = { phase = QuizPhase.Setup })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizSetup(
    store: LabStore,
    topics: MutableList<QuizTopic>,
    count: Int,
    onCount: (Int) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatsCard(store)
        SectionCard("Topics") {
            listOf(1, 2).forEach { module ->
                Text(
                    if (module == 1) "Module 1 · Propositional Logic" else "Module 2 · Proving",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    QuizTopic.entries.filter { it.module == module }.forEach { t ->
                        FilterChip(
                            selected = t in topics,
                            onClick = { if (t in topics) topics.remove(t) else topics.add(t) },
                            label = { Text(t.title) },
                            leadingIcon = if (t in topics) {
                                { Icon(LabIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }
            }
        }
        SectionCard("Number of questions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 20).forEach { n ->
                    FilterChip(selected = count == n, onClick = { onCount(n) }, label = { Text("$n") })
                }
            }
        }
        Button(onClick = onStart, enabled = topics.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(if (topics.isEmpty()) "Choose at least one topic" else "Start quiz")
        }
        Text(
            "Questions are built from random formulas and graded by the same engine that solves your expressions, " +
                "so every quiz is different.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsCard(store: LabStore) {
    val history = store.quizHistory
    SectionCard("Your progress", trailing = {
        if (history.isNotEmpty()) TextButton(onClick = { store.clearQuizHistory() }) { Text("Clear") }
    }) {
        if (history.isEmpty()) {
            Text("No quizzes yet. Your scores will be tracked here.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Quizzes", history.size.toString(), Modifier.weight(1f))
            Stat("Best", "${history.maxOf { it.percent }}%", Modifier.weight(1f))
            Stat("Average", "${history.sumOf { it.percent } / history.size}%", Modifier.weight(1f))
        }
        val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
        history.take(5).forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmt.format(Date(r.timeMillis)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${r.correct}/${r.total}  (${r.percent}%)", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuizRunner(questions: List<QuizQuestion>, onFinish: (List<Int>) -> Unit) {
    if (questions.isEmpty()) {
        EmptyState("?", "No questions", "Could not generate questions for these topics. Try selecting more topics.")
        return
    }
    var index by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateListOf<Int>().apply { repeat(questions.size) { add(-1) } } }
    val q = questions[index]
    val chosen = answers[index]
    val answered = chosen >= 0
    val score = questions.indices.count { answers[it] >= 0 && answers[it] == questions[it].correctIndex }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Question ${index + 1} of ${questions.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("Score $score", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(
            progress = { (index + if (answered) 1 else 0) / questions.size.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        )
        Pill(q.topic.title, container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(q.prompt, style = MaterialTheme.typography.titleLarge)
        q.formula?.let { FormulaBox(it, style = FormulaStyle.large) }

        q.options.forEachIndexed { i, opt ->
            OptionCard(
                text = opt,
                state = when {
                    !answered -> OptionState.IDLE
                    i == q.correctIndex -> OptionState.CORRECT
                    i == chosen -> OptionState.WRONG
                    else -> OptionState.DIMMED
                },
                enabled = !answered,
            ) { answers[index] = i }
        }

        AnimatedVisibility(answered) {
            val right = chosen == q.correctIndex
            Banner(
                if (right) BannerKind.SUCCESS else BannerKind.ERROR,
                if (right) "Correct!" else "Not quite — the answer is ${q.correctAnswer}",
                body = q.explanation,
                icon = if (right) LabIcons.Check else LabIcons.Close,
            )
        }
        if (answered) {
            Button(
                onClick = { if (index < questions.lastIndex) index++ else onFinish(answers.toList()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (index < questions.lastIndex) "Next question" else "See results") }
        }
    }
}

private enum class OptionState { IDLE, CORRECT, WRONG, DIMMED }

@Composable
private fun OptionCard(text: String, state: OptionState, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val lab = Lab.colors
    val (bg, border, fg) = when (state) {
        OptionState.IDLE -> Triple(cs.surfaceContainerLowest, cs.outlineVariant, cs.onSurface)
        OptionState.CORRECT -> Triple(lab.successContainer, lab.success, lab.onSuccessContainer)
        OptionState.WRONG -> Triple(cs.errorContainer, cs.error, cs.onErrorContainer)
        OptionState.DIMMED -> Triple(cs.surfaceContainerLowest, cs.outlineVariant, cs.onSurface.copy(alpha = 0.5f))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(if (state == OptionState.IDLE) 1.dp else 2.dp, border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isFormula = text.any { it in "¬∧∨→↔" }
        Text(
            text,
            style = if (isFormula) FormulaStyle.medium else MaterialTheme.typography.bodyLarge,
            color = fg,
            modifier = Modifier.weight(1f),
        )
        when (state) {
            OptionState.CORRECT -> Icon(LabIcons.Check, contentDescription = "Correct answer", tint = lab.success)
            OptionState.WRONG -> Icon(LabIcons.Close, contentDescription = "Your answer", tint = cs.error)
            else -> Unit
        }
    }
}

@Composable
private fun QuizResults(questions: List<QuizQuestion>, answers: List<Int>, onRetry: () -> Unit, onSetup: () -> Unit) {
    val correct = questions.indices.count { answers[it] == questions[it].correctIndex }
    val pct = if (questions.isEmpty()) 0 else correct * 100 / questions.size
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
                trackColor = cs.surfaceContainerHigh,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$pct%", style = MaterialTheme.typography.headlineLarge, color = cs.primary)
                Text("$correct of ${questions.size}", style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(
            when {
                pct == 100 -> "Perfect score!"
                pct >= 80 -> "Great work."
                pct >= 50 -> "Good progress — review the mistakes below."
                else -> "Keep practising — the explanations below will help."
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onSetup, modifier = Modifier.weight(1f)) { Text("Change topics") }
            Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("New quiz") }
        }
        val mistakes = questions.indices.filter { answers[it] != questions[it].correctIndex }
        if (mistakes.isNotEmpty()) {
            Text("Review your mistakes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
            mistakes.forEach { i ->
                val q = questions[i]
                SectionCard(q.prompt, number = i + 1) {
                    q.formula?.let { FormulaBox(it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LabIcons.Close, contentDescription = null, tint = cs.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Your answer: ${q.options.getOrNull(answers[i]) ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LabIcons.Check, contentDescription = null, tint = Lab.colors.success, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Correct: ${q.correctAnswer}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(q.explanation, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        } else {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Lab.colors.successContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(LabIcons.Check, contentDescription = null, tint = Lab.colors.success) }
        }
    }
}
