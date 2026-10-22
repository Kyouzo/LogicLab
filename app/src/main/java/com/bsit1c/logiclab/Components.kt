package com.bsit1c.logiclab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bsit1c.logic.tf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// Screen frame
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(LabIcons.Back, contentDescription = "Back") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// Cards and labels
// ---------------------------------------------------------------------------------------------

/** A numbered section of a solution ("1  Original expression"). */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    number: Int? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (number != null) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            number.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                trailing()
            }
            content()
        }
    }
}

/** Formula text with an optional amber highlight over one character range. */
@Composable
fun FormulaText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = FormulaStyle.medium,
    highlight: IntRange? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign? = null,
) {
    val lab = Lab.colors
    val annotated = remember(text, highlight, lab) { highlighted(text, highlight, lab.highlight, lab.onHighlight) }
    Text(annotated, modifier = modifier, style = style, color = color, textAlign = textAlign)
}

fun highlighted(text: String, range: IntRange?, bg: Color, fg: Color): AnnotatedString = buildAnnotatedString {
    if (range == null || range.isEmpty() || range.first < 0 || range.last >= text.length) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, range.first))
    withStyle(SpanStyle(background = bg, color = fg, fontWeight = FontWeight.Bold)) {
        append(text.substring(range.first, range.last + 1))
    }
    append(text.substring(range.last + 1))
}

/** A small T / F badge in the truth colours. */
@Composable
fun TruthBadge(value: Boolean, modifier: Modifier = Modifier, large: Boolean = false) {
    val lab = Lab.colors
    Box(
        modifier
            .clip(RoundedCornerShape(if (large) 10.dp else 6.dp))
            .background(if (value) lab.trueBg else lab.falseBg)
            .padding(horizontal = if (large) 14.dp else 8.dp, vertical = if (large) 6.dp else 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (large) (if (value) "TRUE" else "FALSE") else value.tf(),
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontFamily = FormulaFont,
            color = if (value) lab.trueFg else lab.falseFg,
        )
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, container: Color, content: Color) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

enum class BannerKind { SUCCESS, ERROR, WARNING, INFO }

/** A coloured result / status block with a title and explanation. */
@Composable
fun Banner(
    kind: BannerKind,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val lab = Lab.colors
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (kind) {
        BannerKind.SUCCESS -> lab.successContainer to lab.onSuccessContainer
        BannerKind.ERROR -> cs.errorContainer to cs.onErrorContainer
        BannerKind.WARNING -> lab.warningContainer to lab.onWarningContainer
        BannerKind.INFO -> cs.primaryContainer to cs.onPrimaryContainer
    }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = bg, contentColor = fg) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium)
            extra()
        }
    }
}

/** Centred placeholder for empty / idle states. */
@Composable
fun EmptyState(symbol: String, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, style = FormulaStyle.large, color = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun LoadingRow(text: String = "Working…") {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** A formula inside a softly tinted, outlined box. */
@Composable
fun FormulaBox(
    text: String,
    modifier: Modifier = Modifier,
    highlight: IntRange? = null,
    style: TextStyle = FormulaStyle.medium,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        FormulaText(text, style = style, highlight = highlight)
    }
}

// ---------------------------------------------------------------------------------------------
// Asynchronous computation (heavy work never runs on the main thread)
// ---------------------------------------------------------------------------------------------

@Immutable
sealed interface Async<out T> {
    data object Loading : Async<Nothing>
    data class Done<T>(val value: T) : Async<T>
    data class Failed(val message: String) : Async<Nothing>
}

@Composable
fun <T> rememberAsync(key: Any?, compute: () -> T): State<Async<T>> =
    produceState<Async<T>>(Async.Loading, key) {
        value = Async.Loading
        value = try {
            Async.Done(withContext(Dispatchers.Default) { compute() })
        } catch (e: IllegalArgumentException) {
            Async.Failed(e.message ?: "Could not compute a result.")
        }
    }

// ---------------------------------------------------------------------------------------------
// Copy / share
// ---------------------------------------------------------------------------------------------

fun copyText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share via"))
}

@Composable
fun CopyShareActions(subject: String, text: () -> String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    IconButton(onClick = { copyText(context, subject, text()) }) {
        Icon(LabIcons.Copy, contentDescription = "Copy")
    }
    IconButton(onClick = { shareText(context, subject, text()) }) {
        Icon(LabIcons.Share, contentDescription = "Share")
    }
}
