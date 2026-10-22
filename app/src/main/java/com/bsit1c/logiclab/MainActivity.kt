package com.bsit1c.logiclab

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList

private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = LabStore(this)
        setContent {
            val dark = when (store.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keep status / navigation bar icons readable when the in-app theme differs from the system's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            LabTheme(store.themeMode) {
                LogicLabApp(store)
            }
        }
    }
}

/** The app's sections. [description] is shown on the home screen cards. */
enum class Module(val title: String, val symbol: String, val description: String) {
    SOLVER("Logic Solver", "⊨", "Parse any proposition and get the full worked solution."),
    TABLES("Truth Tables", "TF", "Every assignment, with a column for each sub-expression."),
    PROPERTIES("Logical Properties", "⊤", "Tautology, contradiction, contingency, satisfiable."),
    STEPS("Step-by-Step", "1·2", "Pick truth values and watch each connective evaluate."),
    INFERENCE("Rules of Inference", "∴", "Nine rules, argument checking and guided proofs."),
    LAWS("Equivalence Laws", "≡", "Fifteen laws and a proof assistant for rewriting."),
    QUIZ("Practice Quiz", "?", "Randomized questions with instant feedback and scores."),
}

/** A destination: a module (or home when null) plus an optional expression to preload. */
data class Route(val module: Module?, val arg: String? = null, val id: Long = nextRouteId++)

private var nextRouteId = 1L

private val RouteStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { "${it.module?.name ?: "HOME"}\u0001${it.id}\u0001${it.arg ?: ""}" } },
    restore = { saved ->
        mutableStateListOf<Route>().apply {
            saved.forEach { s ->
                val parts = s.split('\u0001', limit = 3)
                val m = parts[0]
                val id = parts.getOrNull(1)?.toLongOrNull() ?: nextRouteId++
                if (id >= nextRouteId) nextRouteId = id + 1
                val a = parts.getOrElse(2) { "" }
                add(Route(if (m == "HOME") null else Module.valueOf(m), a.ifEmpty { null }, id))
            }
        }
    },
)

@Composable
fun LogicLabApp(store: LabStore) {
    val stack = rememberSaveable(saver = RouteStackSaver) { mutableStateListOf(Route(null)) }
    // Keeps each screen's saveable state (typed expressions, scroll) while it is on the back stack.
    val holder = rememberSaveableStateHolder()
    val navigate: (Route) -> Unit = remember(stack) { { r -> stack.add(r) } }
    val back: () -> Unit = remember(stack) {
        {
            if (stack.size > 1) {
                val popped = stack.removeAt(stack.lastIndex)
                holder.removeState(popped.id)
            }
        }
    }

    BackHandler(enabled = stack.size > 1, onBack = back)

    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = {
            val forward = targetState.module != null && initialState.module == null
            (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { if (forward) it / 6 else -it / 6 }) togetherWith
                (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { if (forward) -it / 8 else it / 8 })
        },
        label = "screen",
    ) { route ->
        holder.SaveableStateProvider(route.id) { Screen(route, store, navigate, back) }
    }
}

@Composable
private fun Screen(route: Route, store: LabStore, navigate: (Route) -> Unit, back: () -> Unit) {
    when (route.module) {
        null -> HomeScreen(store, onOpen = navigate)
        Module.SOLVER -> SolverScreen(store, route.arg, onBack = back, onNavigate = navigate)
        Module.TABLES -> TruthTableScreen(store, route.arg, onBack = back)
        Module.PROPERTIES -> PropertiesScreen(store, route.arg, onBack = back, onNavigate = navigate)
        Module.STEPS -> StepScreen(store, route.arg, onBack = back)
        Module.INFERENCE -> InferenceScreen(onBack = back)
        Module.LAWS -> EquivalenceScreen(onBack = back)
        Module.QUIZ -> QuizScreen(store, onBack = back)
    }
}
