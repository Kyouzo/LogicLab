package com.bsit1c.logiclab

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** One finished quiz, kept for score tracking. */
data class QuizRecord(val timeMillis: Long, val correct: Int, val total: Int, val topics: String) {
    val percent: Int get() = if (total == 0) 0 else (correct * 100 + total / 2) / total

    fun encode(): String = listOf(timeMillis, correct, total, topics.replace('|', '/')).joinToString("|")

    companion object {
        fun decode(s: String): QuizRecord? {
            val p = s.split('|')
            if (p.size < 4) return null
            return QuizRecord(p[0].toLongOrNull() ?: return null, p[1].toIntOrNull() ?: return null,
                p[2].toIntOrNull() ?: return null, p[3])
        }
    }
}

/**
 * Local data layer: user preferences, recent and saved expressions, quiz history.
 * Everything lives in SharedPreferences on the device — no accounts or network. Values are mirrored
 * in Compose state so screens update immediately.
 */
class LabStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("logiclab", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    )
        private set

    var recent by mutableStateOf(readList(KEY_RECENT))
        private set

    var saved by mutableStateOf(readList(KEY_SAVED))
        private set

    var quizHistory by mutableStateOf(readList(KEY_QUIZ).mapNotNull { QuizRecord.decode(it) })
        private set

    fun updateTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.edit { putString(KEY_THEME, mode.name) }
    }

    /** Records a successfully parsed expression (most recent first, no duplicates). */
    fun addRecent(expr: String) {
        val e = expr.trim()
        if (e.isEmpty()) return
        recent = (listOf(e) + recent.filter { it != e }).take(MAX_RECENT)
        writeList(KEY_RECENT, recent)
    }

    fun isSaved(expr: String) = expr.trim() in saved

    fun toggleSaved(expr: String) {
        val e = expr.trim()
        if (e.isEmpty()) return
        saved = if (e in saved) saved - e else (listOf(e) + saved).take(MAX_SAVED)
        writeList(KEY_SAVED, saved)
    }

    fun addQuizRecord(r: QuizRecord) {
        quizHistory = (listOf(r) + quizHistory).take(MAX_HISTORY)
        writeList(KEY_QUIZ, quizHistory.map { it.encode() })
    }

    fun clearQuizHistory() {
        quizHistory = emptyList()
        writeList(KEY_QUIZ, emptyList())
    }

    private fun readList(key: String): List<String> =
        prefs.getString(key, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    private fun writeList(key: String, list: List<String>) {
        prefs.edit { putString(key, list.joinToString("\n")) }
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_RECENT = "recent"
        const val KEY_SAVED = "saved"
        const val KEY_QUIZ = "quiz_history"
        const val MAX_RECENT = 12
        const val MAX_SAVED = 40
        const val MAX_HISTORY = 50
    }
}
