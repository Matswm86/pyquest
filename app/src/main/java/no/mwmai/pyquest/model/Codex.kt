package no.mwmai.pyquest.model

import kotlinx.serialization.Serializable

/**
 * One entry in Pytor's Codex, the expert reference that ships inside the APK.
 *
 * The Codex is what makes Pytor useful on a bus with no signal: every entry is
 * a dense, correct note on one topic across the three things Pytor is an
 * authority on, Python itself, software engineering, and AI and LLM engineering.
 * Entries are data in `assets/codex/codex.json`, never Kotlin, and CI validates
 * them the same way it validates the curriculum.
 */
@Serializable
data class CodexEntry(
    val id: String,
    /** One of [CodexDomain.PYTHON], [CodexDomain.ENGINEERING], [CodexDomain.AI]. */
    val domain: String,
    val title: String,
    val tags: List<String> = emptyList(),
    /** One sentence, shown in lists and used as the chat answer's opener. */
    val summary: String = "",
    /** The note itself. Plain paragraphs separated by blank lines. */
    val body: String,
    /** Optional snippet, rendered in monospace under the body. */
    val code: String? = null,
    /** Ids of entries worth reading next. */
    val related: List<String> = emptyList(),
)

@Serializable
data class Codex(val entries: List<CodexEntry>)

object CodexDomain {
    const val PYTHON = "python"
    const val ENGINEERING = "engineering"
    const val AI = "ai"

    val ALL = listOf(PYTHON, ENGINEERING, AI)

    fun label(domain: String): String = when (domain) {
        PYTHON -> "Python"
        ENGINEERING -> "Software engineering"
        AI -> "AI and LLMs"
        else -> domain
    }
}
