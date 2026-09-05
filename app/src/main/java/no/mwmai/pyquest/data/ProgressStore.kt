package no.mwmai.pyquest.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Per-question Leitner box plus XP, streak and per-tag misses, kept as one JSON
 * blob in SharedPreferences. The corpus is a few hundred questions, so a blob
 * rewrite is cheap and a database would only add build surface.
 *
 * Everything time-dependent is a function of the date you pass in, never of
 * the stored fields alone. The first build read [xpToday] and [streakDays]
 * straight off the blob, so the daily ring showed yesterday's XP until you
 * answered something and a streak that had already lapsed kept its old number.
 */
@Serializable
data class Progress(
    /** Question id to Leitner box, 0 (just wrong) through 4 (mastered). */
    val boxes: Map<String, Int> = emptyMap(),
    val xp: Int = 0,
    /** XP earned on [lastPlayed] only. Read it through [xpOn]. */
    val xpToday: Int = 0,
    /** Consecutive days ending on [lastPlayed]. Read it through [streakOn]. */
    val streakDays: Int = 0,
    /** Local date of the last answered question, as yyyy-MM-dd. */
    val lastPlayed: String = "",
    /** How many times a question carrying this tag has been answered. */
    val tagSeen: Map<String, Int> = emptyMap(),
    /** How many of those answers were wrong. Weak tags are the high ratios. */
    val tagMissed: Map<String, Int> = emptyMap(),
    val answered: Int = 0,
    val correct: Int = 0,
    /** Levels finished at least once, as "t3.l2". The track's tick marks. */
    val clearedLevels: List<String> = emptyList(),
) {
    fun box(id: String): Int = boxes[id] ?: 0

    /**
     * Share of questions in the mastered box or higher. Reaching it takes three
     * correct answers on separate sittings, so this is the long-term number.
     */
    fun masteryOf(ids: List<String>): Float {
        if (ids.isEmpty()) return 0f
        return ids.count { box(it) >= MASTERED_BOX }.toFloat() / ids.size
    }

    /**
     * Share of questions answered correctly at least once. This is what a
     * player means by progress; mastery alone read as 0% after a level they had
     * just finished, which is the tracker bug reported on the first build.
     */
    fun solvedOf(ids: List<String>): Float {
        if (ids.isEmpty()) return 0f
        return ids.count { box(it) >= 1 }.toFloat() / ids.size
    }

    fun isCleared(tier: Int, level: Int): Boolean = levelKey(tier, level) in clearedLevels

    fun clearing(tier: Int, level: Int): Progress {
        val key = levelKey(tier, level)
        return if (key in clearedLevels) this else copy(clearedLevels = clearedLevels + key)
    }

    /** XP earned today, which is zero on a day nothing has been played yet. */
    fun xpOn(today: String): Int = if (lastPlayed == today) xpToday else 0

    /** The streak as it stands today: intact if played today or yesterday, else broken. */
    fun streakOn(today: String): Int = when (lastPlayed) {
        today, yesterdayOf(today) -> streakDays
        else -> 0
    }

    /** True when the streak is alive but today's play is still owed. */
    fun streakAtRisk(today: String): Boolean =
        streakDays > 0 && lastPlayed == yesterdayOf(today)

    fun dailyProgressOn(today: String): Float =
        (xpOn(today).toFloat() / DAILY_GOAL_XP).coerceIn(0f, 1f)

    /** Title shown next to the XP total. Purely cosmetic, deliberately cheap to reach. */
    val rank: String
        get() = RANKS.last { xp >= it.first }.second

    /** The next rank and how far away it is, or null at the top. */
    val nextRank: Pair<String, Int>?
        get() = RANKS.firstOrNull { xp < it.first }?.let { it.second to it.first - xp }

    /**
     * Tags the player keeps missing: seen at least [WEAK_MIN_SEEN] times with a
     * miss rate over [WEAK_MIN_RATE], worst first. This is what Pytor coaches on.
     */
    val weakTags: List<Pair<String, Float>>
        get() = tagSeen
            .filter { (_, seen) -> seen >= WEAK_MIN_SEEN }
            .map { (tag, seen) -> tag to (tagMissed[tag] ?: 0).toFloat() / seen }
            .filter { it.second > WEAK_MIN_RATE }
            .sortedByDescending { it.second }

    /**
     * The state after one graded answer. A correct answer promotes the question
     * one box, a wrong answer drops it straight to box 0 so it comes back this
     * session. Pure, so the unit test can pin the date and streak arithmetic.
     */
    fun applying(
        questionId: String,
        isCorrect: Boolean,
        xpGain: Int,
        today: String,
        tags: List<String> = emptyList(),
    ): Progress {
        val box = box(questionId)
        val next = if (isCorrect) minOf(box + 1, MAX_BOX) else 0
        val streak = when (lastPlayed) {
            today -> maxOf(streakDays, 1)
            yesterdayOf(today) -> streakDays + 1
            else -> 1
        }
        val gained = if (isCorrect) xpGain else 0
        val seen = tagSeen.toMutableMap()
        val missed = tagMissed.toMutableMap()
        for (tag in tags) {
            seen[tag] = (seen[tag] ?: 0) + 1
            if (!isCorrect) missed[tag] = (missed[tag] ?: 0) + 1
        }
        return copy(
            boxes = boxes + (questionId to next),
            xp = xp + gained,
            xpToday = if (lastPlayed == today) xpToday + gained else gained,
            streakDays = streak,
            lastPlayed = today,
            tagSeen = seen,
            tagMissed = missed,
            answered = answered + 1,
            correct = correct + if (isCorrect) 1 else 0,
        )
    }

    companion object {
        /** A question counts as mastered from box 3 upward. */
        const val MASTERED_BOX = 3
        const val MAX_BOX = 4
        const val DAILY_GOAL_XP = 50
        const val WEAK_MIN_SEEN = 3
        const val WEAK_MIN_RATE = 0.25f

        private val RANKS = listOf(
            0 to "Novice",
            250 to "Learner",
            750 to "Scripter",
            1750 to "Builder",
            3500 to "Engineer",
            7000 to "Architect",
            12000 to "Consultant",
        )

        fun levelKey(tier: Int, level: Int): String = "t$tier.l$level"

        fun yesterdayOf(today: String): String =
            runCatching { LocalDate.parse(today).minusDays(1).toString() }.getOrElse { "" }

        fun today(): String = LocalDate.now().toString()
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

    /** Applies one graded answer and persists the result. */
    fun record(
        question: String,
        correct: Boolean,
        xpGain: Int,
        today: String,
        tags: List<String> = emptyList(),
    ): Progress {
        val updated = load().applying(question, correct, xpGain, today, tags)
        save(updated)
        return updated
    }

    /** Marks a level finished. Idempotent, so a replay does not grow the list. */
    fun markCleared(tier: Int, level: Int): Progress {
        val updated = load().clearing(tier, level)
        save(updated)
        return updated
    }

    fun reset() {
        prefs.edit { remove(KEY) }
    }

    /**
     * Whether blocks show English ("n squared") instead of code ("n ** 2").
     * A beginner reads the English one and still learns which slot it belongs in.
     */
    var plainLabels: Boolean
        get() = prefs.getBoolean(PLAIN_LABELS, false)
        set(value) = prefs.edit { putBoolean(PLAIN_LABELS, value) }

    /** Whether the Pytor tab may reach the tutor service. Off means Codex only. */
    var pytorOnline: Boolean
        get() = prefs.getBoolean(PYTOR_ONLINE, true)
        set(value) = prefs.edit { putBoolean(PYTOR_ONLINE, value) }

    private companion object {
        const val KEY = "progress_v1"
        const val PLAIN_LABELS = "plain_labels"
        const val PYTOR_ONLINE = "pytor_online"
    }
}
