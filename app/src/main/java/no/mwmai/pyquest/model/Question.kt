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
 *
 * Every question also carries Pytor's material: [hints] are progressive and
 * never give the answer away, [deep] is the expert note shown after the check,
 * the part of the explanation that a working engineer would want to know.
 */
@Serializable
data class Question(
    val id: String,
    val tier: Int,
    val level: Int,
    val type: QuestionType,
    val prompt: String,
    /** Optional read-only snippet shown above the answer area, in monospace. */
    val code: String? = null,
    /** Tap targets for [QuestionType.MCQ]. */
    val options: List<String> = emptyList(),
    /** Plain draggable blocks for [QuestionType.BLOCKS] and [QuestionType.ORDER]. */
    val tray: List<String> = emptyList(),
    /** Typed blocks for [QuestionType.FILL] and [QuestionType.PIPELINE]. */
    val blocks: List<Block> = emptyList(),
    /**
     * Code with numbered gaps for [QuestionType.FILL], written as `{0}`, `{1}`
     * and so on. The gaps are filled in the order they appear, and [answer]
     * lists the block ids in that same order.
     */
    val template: String? = null,
    /** The client scenario for [QuestionType.PIPELINE]. */
    val brief: Brief? = null,
    val answer: List<String> = emptyList(),
    val accept: List<List<String>> = emptyList(),
    val explain: String,
    /** Pytor's progressive hints, mildest first. Never contain the answer. */
    val hints: List<String> = emptyList(),
    /** Pytor's expert note after the check: the mechanism, the idiom, the trap. */
    val deep: String? = null,
    val xp: Int = 10,
    val tags: List<String> = emptyList(),
    /** ISO date the content was last checked. Tiers 7 and 8 age fastest. */
    val reviewed: String? = null,
) {
    fun isCorrect(given: List<String>): Boolean =
        given == answer || accept.any { it == given }

    /**
     * The pool a player picks from, addressed by index. Multiple-choice uses the
     * option text, plain blocks use the block text, and the two typed formats
     * use block ids so that two components sharing a label stay distinct.
     */
    val choices: List<String>
        get() = when (type) {
            QuestionType.MCQ -> options
            QuestionType.BLOCKS, QuestionType.ORDER -> tray
            QuestionType.FILL, QuestionType.PIPELINE -> blocks.map { it.id }
        }

    /** How many slots the player has to fill before the answer can be checked. */
    val slotCount: Int
        get() = when (type) {
            QuestionType.FILL -> GAP.findAll(template.orEmpty()).count()
            QuestionType.PIPELINE -> brief?.stages ?: answer.size
            else -> 0
        }

    fun block(id: String): Block? = blocks.firstOrNull { it.id == id }

    companion object {
        /**
         * Matches a `{0}` style gap marker inside [template].
         *
         * The closing brace has to be escaped. OpenJDK's regex engine accepts a
         * bare `}` and Android's ICU-backed one rejects it, so the unescaped
         * version compiled on a desktop JVM, passed the unit tests, and then
         * threw PatternSyntaxException inside this class's static initializer on
         * the first phone that ran it.
         */
        val GAP = Regex("""\{(\d+)\}""")
    }
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

    /** Drop typed blocks into gaps inside a piece of real code. */
    @SerialName("fill")
    FILL,

    /** Wire a client's inference pipeline against a latency and cost budget. */
    @SerialName("pipeline")
    PIPELINE,
}

/**
 * A block the player can place. [code] is what shows on the block, [plain] is the
 * same thing in English for players who have the plain-label setting on.
 * [ms] and [cost] are only meaningful for pipeline components.
 */
@Serializable
data class Block(
    val id: String,
    val code: String,
    val plain: String = "",
    val kind: BlockKind = BlockKind.EXPR,
    val ms: Int = 0,
    val cost: Double = 0.0,
) {
    fun label(plainLabels: Boolean): String =
        if (plainLabels && plain.isNotBlank()) plain else code
}

@Serializable
enum class BlockKind {
    /** A name that already holds a value. Blue. */
    @SerialName("var")
    VAR,

    /** Something that computes a value. Amber. */
    @SerialName("expr")
    EXPR,

    /** A call. Violet. */
    @SerialName("call")
    CALL,

    /** A pipeline stage. Lime. */
    @SerialName("stage")
    STAGE,
}

/**
 * A capstone client brief. The budget numbers are the teaching device: the player
 * watches estimated latency and cost move as components go in, and a wiring that
 * breaches either ceiling is visibly wrong before it is ever checked.
 */
@Serializable
data class Brief(
    val client: String,
    /** Two letters for the client avatar, for example "NL". */
    val initials: String,
    val quote: String,
    val constraints: List<String> = emptyList(),
    val stages: Int,
    val maxMs: Int,
    val maxCost: Double,
    val currency: String = "EUR",
)

@Serializable
data class Tier(
    val tier: Int,
    val title: String,
    val subtitle: String = "",
    /** Short lesson names shown when the tier card is expanded. */
    val lessons: List<String> = emptyList(),
    val capstone: Boolean = false,
    /** One line from Pytor when the player opens this tier. */
    val pytor: String = "",
    val questions: List<Question>,
) {
    fun level(level: Int): List<Question> = questions.filter { it.level == level }

    val levels: List<Int>
        get() = questions.map { it.level }.distinct().sorted()
}
