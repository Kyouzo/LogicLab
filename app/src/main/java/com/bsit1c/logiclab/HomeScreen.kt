package com.bsit1c.logiclab

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Section(val title: String, val modules: List<Module>)

private val sections = listOf(
    Section("Module 1 · Propositional Logic", listOf(Module.SOLVER, Module.TABLES, Module.PROPERTIES, Module.STEPS)),
    Section("Module 2 · Proving", listOf(Module.INFERENCE, Module.LAWS)),
    Section("Practice", listOf(Module.QUIZ)),
)

@Composable
fun HomeScreen(store: LabStore, onOpen: (Route) -> Unit) {
    val cs = MaterialTheme.colorScheme
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Header(store) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            QuickStart(store, onOpen)
        }
        sections.forEach { section ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    section.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, start = 2.dp),
                )
            }
            items(section.modules, key = { it.name }) { m -> ModuleCard(m) { onOpen(Route(m)) } }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Everything is computed on your device — no internet needed.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Header(store: LabStore) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppLogo(size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("LogicLab", style = MaterialTheme.typography.headlineMedium, color = cs.onBackground)
            Text(
                "Propositional logic, worked step by step",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
        val next = when (store.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        IconButton(onClick = { store.updateTheme(next) }) {
            Icon(
                if (store.themeMode == ThemeMode.DARK) LabIcons.Sun else LabIcons.Moon,
                contentDescription = "Theme: ${store.themeMode.name.lowercase()}. Tap to switch to ${next.name.lowercase()}.",
            )
        }
    }
}

@Composable
private fun QuickStart(store: LabStore, onOpen: (Route) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cs.primaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Solve an expression", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cs.surfaceContainerLowest)
                .border(1.dp, cs.outlineVariant, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button) { onOpen(Route(Module.SOLVER)) }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics { contentDescription = "Open the logic solver" },
        ) {
            Text("(P ∧ ¬Q) → R", style = FormulaStyle.large, color = cs.outline)
        }
        val chips = (store.saved + store.recent).distinct().take(8)
        if (chips.isNotEmpty()) {
            FormulaChips(
                title = if (store.saved.isNotEmpty()) "Saved & recent" else "Recent",
                items = chips,
                onPick = { onOpen(Route(Module.SOLVER, it)) },
            )
        }
    }
}

@Composable
private fun ModuleCard(module: Module, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (tileBg, tileFg) = when (module) {
        Module.SOLVER, Module.TABLES, Module.PROPERTIES, Module.STEPS -> cs.primary to cs.onPrimary
        Module.INFERENCE, Module.LAWS -> cs.tertiary to cs.onTertiary
        Module.QUIZ -> cs.secondary to cs.onSecondary
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 168.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surfaceContainerLowest)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tileBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                module.symbol,
                color = tileFg,
                fontFamily = if (module.symbol.length > 1) FontFamily.Monospace else FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = if (module.symbol.length > 2) 15.sp else if (module.symbol.length > 1) 17.sp else 24.sp,
            )
        }
        Text(module.title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
        Text(
            module.description,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

/**
 * The launcher icon, drawn from the same two adaptive-icon layers (res/drawable/ic_launcher_*.xml)
 * so the in-app logo always matches the home-screen icon. The layers are 108dp canvases whose visible
 * part is the central 72dp, so they are drawn at 1.5× and clipped to a rounded square.
 */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .semantics { contentDescription = "LogicLab logo" },
        contentAlignment = Alignment.Center,
    ) {
        val layer = Modifier.requiredSize(size * 1.5f)
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = layer)
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = layer)
    }
}
