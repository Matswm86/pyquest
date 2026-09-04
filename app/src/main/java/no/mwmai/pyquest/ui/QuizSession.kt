package no.mwmai.pyquest.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import no.mwmai.pyquest.data.Progress
import no.mwmai.pyquest.data.ProgressStore
import no.mwmai.pyquest.model.Question
import no.mwmai.pyquest.model.QuestionType
import java.time.LocalDate

/**
 * One sitting through a level.
 *
 * A wrong answer does not just move on: the question drops to Leitner box 0 and
 * is re-inserted [REQUEUE_GAP] questions later, so you meet it again before the
 * session ends. The level is finished when the queue empties, which means a
 * question you keep missing keeps the level open.
 */
class QuizSession(
    initial: List<Question>,
    private val store: ProgressStore,
    private val today: () -> String = { LocalDate.now().toString() },
) {
    private var queue: List<Question> by mutableStateOf(initial)

    var progress: Progress by mutableStateOf(store.load())
        private set

    /** Chosen indices into the question's choices, for the tap and row formats. */
    var selection: List<Int> by mutableStateOf(emptyList())
        private set

    /** Block id per slot, for the gap-fill and pipeline formats. Null means empty. */
    var slots: List<String?> by mutableStateOf(emptyList())
        private set

    var checked: Boolean by mutableStateOf(false)
        private set

    var lastCorrect: Boolean by mutableStateOf(false)
        private set

    var answered: Int by mutableStateOf(0)
        private set

    var correct: Int by mutableStateOf(0)
        private set

    /** Questions answered right on the first attempt, which is what accuracy means. */
    var firstTryCorrect: Int by mutableStateOf(0)
        private set

    private val seen = mutableSetOf<String>()

    var xpEarned: Int by mutableStateOf(0)
        private set

    /**
     * The last piece of code the player assembled correctly, with the gaps filled
     * in. The tier-cleared screen shows it back to them, because "you wrote this"
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

    init {
        resetForCurrent()
    }

    private fun resetForCurrent() {
        val question = queue.firstOrNull()
        selection = emptyList()
        slots = if (question != null && question.slotCount > 0) {
            List(question.slotCount) { null }
        } else {
            emptyList()
        }
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

    fun setSlots(placed: List<String?>) {
        if (checked) return
        slots = placed
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
            xpEarned += question.xp
        }
        seen.add(question.id)
        if (lastCorrect) rememberBuiltCode(question)
        progress = store.record(question.id, lastCorrect, question.xp, today())
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
