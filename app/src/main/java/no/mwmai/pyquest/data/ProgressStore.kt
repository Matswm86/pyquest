package no.mwmai.pyquest.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Per-question Leitner box plus XP and streak, kept as one JSON blob in
 * SharedPreferences. The corpus is 300 questions, so a blob rewrite is cheap and
 * a database would only add build surface. Move to Room when per-tag queries
 * start mattering.
 */
@Serializable
data class Progress(
    /** Question id to Leitner box, 0 (just wrong) through 4 (mastered). */
    val boxes: Map<String, Int> = emptyMap(),
    val xp: Int = 0,
    val streakDays: Int = 0,
    /** Local date of the last answered question, as yyyy-MM-dd. */
    val lastPlayed: String = "",
) {
    fun box(id: String): Int = boxes[id] ?: 0

    fun masteryOf(ids: List<String>): Float {
        if (ids.isEmpty()) return 0f
        return ids.count { box(it) >= MASTERED_BOX }.toFloat() / ids.size
    }

    companion object {
        /** A question counts as mastered from box 3 upward. */
        const val MASTERED_BOX = 3
        const val MAX_BOX = 4
    }
}

class ProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences("pyquest", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Progress {
        val raw = prefs.getString(KEY, null) ?: return Progress()
        return runCatching { json.decodeFromString(Progress.serializer(), raw) }.getOrElse { Progress() }
    }

    fun save(progress: Progress) {
        prefs.edit { putString(KEY, json.encodeToString(Progress.serializer(), progress)) }
    }

    /**
     * Applies one graded answer. A correct answer promotes the question one box,
     * a wrong answer drops it straight to box 0 so it comes back this session.
     */
    fun record(question: String, correct: Boolean, xpGain: Int, today: String): Progress {
        val current = load()
        val box = current.box(question)
        val next = if (correct) minOf(box + 1, Progress.MAX_BOX) else 0
        val streak = when {
            current.lastPlayed == today -> maxOf(current.streakDays, 1)
            current.lastPlayed == yesterdayOf(today) -> current.streakDays + 1
            else -> 1
        }
        val updated = current.copy(
            boxes = current.boxes + (question to next),
            xp = current.xp + if (correct) xpGain else 0,
            streakDays = streak,
            lastPlayed = today,
        )
        save(updated)
        return updated
    }

    private fun yesterdayOf(today: String): String =
        runCatching {
            java.time.LocalDate.parse(today).minusDays(1).toString()
        }.getOrElse { "" }

    private companion object {
        const val KEY = "progress_v1"
    }
}
