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

    /** Indices into the current question's choices, in the order the player built them. */
    var selection: List<Int> by mutableStateOf(emptyList())
        private set

    var checked: Boolean by mutableStateOf(false)
        private set

    var lastCorrect: Boolean by mutableStateOf(false)
        private set

    var answered: Int by mutableStateOf(0)
        private set

    var correct: Int by mutableStateOf(0)
        private set

    val total: Int = initial.size

    val current: Question?
        get() = queue.firstOrNull()

    val finished: Boolean
        get() = queue.isEmpty()

    val remaining: Int
        get() = queue.size

    fun select(index: Int) {
        if (checked) return
        val question = current ?: return
        selection = if (question.type == QuestionType.MCQ) listOf(index) else selection
    }

    fun setBlocks(placed: List<Int>) {
        if (checked) return
        selection = placed
    }

    val canSubmit: Boolean
        get() {
            val question = current ?: return false
            return when (question.type) {
                QuestionType.MCQ -> selection.size == 1
                QuestionType.BLOCKS, QuestionType.ORDER -> selection.isNotEmpty()
            }
        }

    fun submit() {
        val question = current ?: return
        if (checked || !canSubmit) return
        val given = selection.map { question.choices[it] }
        lastCorrect = question.isCorrect(given)
        checked = true
        answered += 1
        if (lastCorrect) correct += 1
        progress = store.record(question.id, lastCorrect, question.xp, today())
    }

    fun next() {
        val question = current ?: return
        val rest = queue.drop(1)
        queue = if (lastCorrect) {
            rest
        } else {
            // Put the miss back within reach, but not immediately: answering it
            // right away tests short-term memory, not learning.
            val at = minOf(REQUEUE_GAP, rest.size)
            rest.toMutableList().also { it.add(at, question) }
        }
        selection = emptyList()
        checked = false
        lastCorrect = false
    }

    private companion object {
        const val REQUEUE_GAP = 3
    }
}
