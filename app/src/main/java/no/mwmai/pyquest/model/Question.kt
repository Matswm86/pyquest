package no.mwmai.pyquest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One question, loaded verbatim from `assets/curriculum/tier_NN.json`.
 *
 * Grading never runs Python. An answer is correct when the submitted list of
 * strings equals [answer], or equals any entry in [accept]. That covers the
 * cases where two orderings are genuinely both right, such as two independent
 * assignments that can appear in either order.
 */
@Serializable
data class Question(
    val id: String,
    val tier: Int,
    val level: Int,
    val type: QuestionType,
    val prompt: String,
    /** Optional code snippet shown above the answer area, in monospace. */
    val code: String? = null,
    /** Tap targets for [QuestionType.MCQ]. */
    val options: List<String> = emptyList(),
    /** Draggable blocks for [QuestionType.BLOCKS] and [QuestionType.ORDER]. */
    val tray: List<String> = emptyList(),
    val answer: List<String> = emptyList(),
    val accept: List<List<String>> = emptyList(),
    val explain: String,
    val xp: Int = 10,
    val tags: List<String> = emptyList(),
    /** ISO date the content was last checked. Tier 9 and 10 age fastest. */
    val reviewed: String? = null,
) {
    fun isCorrect(given: List<String>): Boolean =
        given == answer || accept.any { it == given }

    /** Blocks the player may pick from, tray order preserved as authored. */
    val choices: List<String>
        get() = if (type == QuestionType.MCQ) options else tray
}

@Serializable
enum class QuestionType {
    /** Four options, tap one. */
    @SerialName("mcq")
    MCQ,

    /** Drag blocks from the tray into an ordered answer row. */
    @SerialName("blocks")
    BLOCKS,

    /** Same interaction as BLOCKS, but every tray block must be used. */
    @SerialName("order")
    ORDER,
}

@Serializable
data class Tier(
    val tier: Int,
    val title: String,
    val subtitle: String = "",
    val questions: List<Question>,
) {
    fun level(level: Int): List<Question> = questions.filter { it.level == level }

    val levels: List<Int>
        get() = questions.map { it.level }.distinct().sorted()
}
