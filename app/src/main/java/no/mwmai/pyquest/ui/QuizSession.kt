package no.mwmai.pyquest.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import no.mwmai.pyquest.data.EventLog
import no.mwmai.pyquest.data.Progress
import no.mwmai.pyquest.data.ProgressStore
import no.mwmai.pyquest.model.Question
import no.mwmai.pyquest.model.QuestionType
import kotlin.random.Random

/**
 * One sitting through a level, or through the misses of a previous sitting.
 *
 * A wrong answer does not just move on: the question drops to Leitner box 0 and
 * is re-inserted [REQUEUE_GAP] questions later, so you meet it again before the
 * session ends. The level is finished when the queue empties, which means a
 * question you keep missing keeps the level open.
 *
 * The first build counted progress as `total - remaining + 1`, which jumped
 * back to 1 after every requeued miss. Progress is now [solved] distinct
 * questions out of [total], which only ever goes up.
 */
class QuizSession(
    initial: List<Question>,
    private val store: ProgressStore,
    /** True when this sitting replays the misses of an earlier one. */
    val isReview: Boolean = false,
    /** Which level this is, so finishing it can be recorded. Null for ad-hoc sittings. */
    private val tier: Int? = null,
    private val level: Int? = null,
    private val log: EventLog? = null,
    private val today: () -> String = { Progress.today() },
    private val random: Random = Random.Default,
) {
    private var queue: List<Question> by mutableStateOf(initial)
    private var questionShownAt: Long = System.currentTimeMillis()

    var progress: Progress by mutableStateOf(store.load())
        private set

    /** Chosen indices into the question's choices, for the tap and row formats. */
    var selection: List<Int> by mutableStateOf(emptyList())
        private set

    /** Block id per slot, for the gap-fill and pipeline formats. Null means empty. */
    var slots: List<String?> by mutableStateOf(emptyList())
        private set

    /**
     * Display order for multiple-choice options, as indices into
     * [Question.options]. Shuffled per question so the answer is never learnt
     * by position, including on the requeued second try.
     */
    var optionOrder: List<Int> by mutableStateOf(emptyList())
        private set

    var checked: Boolean by mutableStateOf(false)
        private set

    var lastCorrect: Boolean by mutableStateOf(false)
        private set

    /** How many of the current question's hints the player has opened. */
    var hintsShown: Int by mutableStateOf(0)
        private set

    var hintsUsed: Int by mutableStateOf(0)
        private set

    var answered: Int by mutableStateOf(0)
        private set

    var correct: Int by mutableStateOf(0)
        private set

    /** Questions answered right on the first attempt, which is what accuracy means. */
    var firstTryCorrect: Int by mutableStateOf(0)
        private set

    /** Distinct questions answered correctly at least once. */
    var solved: Int by mutableStateOf(0)
        private set

    private val seen = mutableSetOf<String>()
    private val solvedIds = mutableSetOf<String>()
    private val missedIds = linkedMapOf<String, Question>()

    var xpEarned: Int by mutableStateOf(0)
        private set

    /**
     * The last piece of code the player assembled correctly, with the gaps filled
     * in. The level-cleared screen shows it back to them, because "you wrote this"
     * lands harder than a score.
     */
    var lastBuilt: String? by mutableStateOf(null)
        private set

    val startedAtMillis: Long = System.currentTimeMillis()

    val total: Int = initial.size

    val current: Question?
        get() = queue.firstOrNull()

    val finished: Boolean
        get() = queue.isEmpty()

    val remaining: Int
        get() = queue.size

    /** Every question missed at least once this sitting, in the order first missed. */
    val misses: List<Question>
        get() = missedIds.values.toList()

    init {
        log?.log(
            "level_start",
            "tier" to tier, "level" to level, "review" to isReview, "questions" to initial.size,
        )
        resetForCurrent()
    }

    private fun resetForCurrent() {
        val question = queue.firstOrNull()
        questionShownAt = System.currentTimeMillis()
        selection = emptyList()
        hintsShown = 0
        slots = if (question != null && question.slotCount > 0) {
            List(question.slotCount) { null }
        } else {
            emptyList()
        }
        optionOrder = if (question != null && question.type == QuestionType.MCQ) {
            question.options.indices.shuffled(random)
        } else {
            emptyList()
        }
    }

    /** The options as the player sees them, in [optionOrder]. */
    val displayedOptions: List<String>
        get() = current?.let { q -> optionOrder.map { q.options[it] } }.orEmpty()

    /** Index into [displayedOptions] of the picked option, or null. */
    val selectedDisplayIndex: Int?
        get() = selection.firstOrNull()?.let { optionOrder.indexOf(it) }?.takeIf { it >= 0 }

    /** Index into [displayedOptions] of the correct option, or -1. */
    val correctDisplayIndex: Int
        get() {
            val q = current ?: return -1
            val real = q.options.indexOf(q.answer.firstOrNull() ?: return -1)
            return optionOrder.indexOf(real)
        }

    /** Player tapped the option shown at [displayIndex]. */
    fun selectDisplayed(displayIndex: Int) {
        if (checked) return
        val real = optionOrder.getOrNull(displayIndex) ?: return
        select(real)
    }

    fun select(index: Int) {
        if (checked) return
        val question = current ?: return
        if (question.type == QuestionType.MCQ) selection = listOf(index)
    }

    fun setBlocks(placed: List<Int>) {
        if (checked) return
        selection = placed
    }

    fun placeInSlots(placed: List<String?>) {
        if (checked) return
        slots = placed
    }

    /** Opens the next hint. Returns false when there are none left. */
    fun revealHint(): Boolean {
        val question = current ?: return false
        if (hintsShown >= question.hints.size) return false
        hintsShown += 1
        hintsUsed += 1
        log?.log("hint", "id" to question.id, "n" to hintsShown)
        return true
    }

    val canSubmit: Boolean
        get() {
            val question = current ?: return false
            return when (question.type) {
                QuestionType.MCQ -> selection.size == 1
                QuestionType.BLOCKS, QuestionType.ORDER -> selection.isNotEmpty()
                QuestionType.FILL, QuestionType.PIPELINE -> slots.isNotEmpty() && slots.all { it != null }
            }
        }

    /** What the player built, as the plain strings the curriculum grades against. */
    fun given(question: Question): List<String> = when (question.type) {
        QuestionType.FILL, QuestionType.PIPELINE -> slots.map { it.orEmpty() }
        else -> selection.mapNotNull { question.choices.getOrNull(it) }
    }

    fun submit() {
        val question = current ?: return
        if (checked || !canSubmit) return
        lastCorrect = question.isCorrect(given(question))
        checked = true
        answered += 1
        if (lastCorrect) {
            correct += 1
            if (question.id !in seen) firstTryCorrect += 1
            if (solvedIds.add(question.id)) solved += 1
            xpEarned += question.xp
            rememberBuiltCode(question)
        } else {
            missedIds.putIfAbsent(question.id, question)
        }
        seen.add(question.id)
        progress = store.record(question.id, lastCorrect, question.xp, today(), question.tags)
        log?.log(
            "answer",
            "id" to question.id, "type" to question.type.name.lowercase(), "correct" to lastCorrect,
            "given" to given(question), "hints" to hintsShown,
            "ms" to (System.currentTimeMillis() - questionShownAt), "retry" to (question.id in missedIds),
        )
    }

    fun next() {
        val question = current ?: return
        val rest = queue.drop(1)
        queue = if (lastCorrect) {
            rest
        } else {
            // Put the miss back within reach, but not immediately: answering it
            // straight away tests short-term memory, not learning.
            val at = minOf(REQUEUE_GAP, rest.size)
            rest.toMutableList().also { it.add(at, question) }
        }
        checked = false
        lastCorrect = false
        resetForCurrent()
        if (queue.isEmpty()) finish()
    }

    private fun finish() {
        if (!isReview && tier != null && level != null) {
            progress = store.markCleared(tier, level)
        }
        log?.log(
            "level_end",
            "tier" to tier, "level" to level, "review" to isReview,
            "accuracy" to accuracy, "xp" to xpEarned, "hints" to hintsUsed,
            "misses" to misses.size, "secs" to ((System.currentTimeMillis() - startedAtMillis) / 1000),
        )
    }

    private fun rememberBuiltCode(question: Question) {
        if (question.type != QuestionType.FILL) return
        val template = question.template ?: return
        lastBuilt = Question.GAP.replace(template) { match ->
            val slot = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            slots.getOrNull(slot)?.let { question.block(it)?.code } ?: match.value
        }
    }

    /** Share of distinct questions answered right the first time they appeared. */
    val accuracy: Float
        get() = if (seen.isEmpty()) 0f else firstTryCorrect.toFloat() / seen.size

    val elapsedLabel: String
        get() {
            val seconds = ((System.currentTimeMillis() - startedAtMillis) / 1000).toInt()
            return "%d:%02d".format(seconds / 60, seconds % 60)
        }

    private companion object {
        const val REQUEUE_GAP = 3
    }
}
