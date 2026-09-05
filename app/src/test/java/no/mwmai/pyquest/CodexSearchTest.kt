package no.mwmai.pyquest

import java.io.File
import kotlinx.serialization.json.Json
import no.mwmai.pyquest.data.CodexSearch
import no.mwmai.pyquest.model.Codex
import no.mwmai.pyquest.model.CodexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the offline ranking. The scorer is deliberately small, so the way to
 * keep it honest is a handful of queries a player would actually type against
 * the Codex that actually ships.
 */
class CodexSearchTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun shippedCodex(): List<CodexEntry> {
        val file = File("src/main/assets/codex/codex.json")
        assertTrue("codex not found at ${file.absolutePath}", file.isFile)
        return json.decodeFromString(Codex.serializer(), file.readText()).entries
    }

    @Test
    fun `tokens drop stopwords and keep code-ish terms`() {
        assertEquals(
            listOf("decorators", "python"),
            CodexSearch.tokens("What are decorators in Python?"),
        )
        assertEquals(listOf("c++", "f-string"), CodexSearch.tokens("c++ vs f-string"))
    }

    @Test
    fun `empty query returns nothing`() {
        assertTrue(CodexSearch.search(shippedCodex(), "the of and").isEmpty())
    }

    @Test
    fun `a title word outranks a passing mention`() {
        val entries = shippedCodex()
        val top = CodexSearch.search(entries, "decorators", limit = 1).single()
        assertTrue("got ${top.id}", top.title.contains("decorator", ignoreCase = true))
    }

    @Test
    fun `the questions a player would ask land on the right note`() {
        val entries = shippedCodex()
        val expectations = mapOf(
            "what is a list comprehension" to "python",
            "how does RAG chunking work" to "ai",
            "what does p95 latency mean" to "engineering",
            "difference between is and ==" to "python",
            "prompt injection" to "ai",
            "why use pytest fixtures" to "engineering",
        )
        for ((query, domain) in expectations) {
            val hits = CodexSearch.search(entries, query, limit = 3)
            assertTrue("no hit for '$query'", hits.isNotEmpty())
            assertTrue(
                "'$query' returned ${hits.map { it.id }}, none in domain $domain",
                hits.any { it.domain == domain },
            )
        }
    }

    @Test
    fun `every shipped entry is reachable by its own title`() {
        val entries = shippedCodex()
        val unreachable = entries.filter { entry ->
            CodexSearch.search(entries, entry.title, limit = 3).none { it.id == entry.id }
        }
        assertTrue("entries not found by their own title: ${unreachable.map { it.id }}", unreachable.isEmpty())
    }
}
