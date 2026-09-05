package no.mwmai.pyquest.data

import no.mwmai.pyquest.model.CodexEntry

/**
 * Offline search over the Codex: a small weighted keyword scorer.
 *
 * It is not BM25 and does not need to be. The corpus is a hundred-odd entries
 * with hand-written tags, so a title hit is worth far more than a body hit and
 * a four-letter prefix match covers plurals and verb endings well enough that
 * "decorators" finds "decorator" and "chunking" finds "chunk". Pure Kotlin so
 * the unit test can pin the ranking without an emulator.
 */
object CodexSearch {

    private val STOP = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "what", "whats",
        "how", "do", "does", "did", "in", "of", "to", "and", "or", "i", "my", "me",
        "it", "its", "for", "on", "with", "why", "when", "can", "could", "should",
        "would", "this", "that", "these", "those", "vs", "versus", "use", "using",
        "tell", "about", "explain", "please", "pytor", "you", "your", "there", "some",
        "any", "not", "no", "yes", "than", "then", "so", "if", "at", "by", "from",
        "into", "as", "we", "our", "they", "them", "up", "out", "just", "mean",
        "means", "difference", "between", "work", "works", "one", "get", "make",
    )

    /**
     * Keeps the characters that make code-ish queries meaningful: `f-string`,
     * `c++`, `==`, `**`, `//`. Splitting on hyphens would turn "f-string" into
     * a lone "string" and lose the tag it was meant to hit.
     */
    private val SPLIT = Regex("""[^a-z0-9_+#.=<>!*/%-]+""")

    fun tokens(text: String): List<String> =
        text.lowercase()
            .split(SPLIT)
            .map { it.trim('.', '_', '-') }
            .filter { it.length >= 2 && it !in STOP }

    /** Positive when the entry says something about the query, zero otherwise. */
    fun score(entry: CodexEntry, query: List<String>): Int {
        if (query.isEmpty()) return 0
        val title = tokens(entry.title)
        val tags = entry.tags.map { it.lowercase() }
        val tagTokens = tags.flatMap(::tokens)
        val body = tokens(entry.summary + " " + entry.body)
        var total = 0
        for (term in query) {
            var hit = 0
            if (term in title) hit += 8
            if (term in tags || term in tagTokens) hit += 5
            if (hit == 0 && term.length >= 4) {
                if (title.any { it.startsWith(term) || term.startsWith(it) && it.length >= 4 }) hit += 3
                if (tagTokens.any { it.startsWith(term) || term.startsWith(it) && it.length >= 4 }) hit += 2
            }
            val inBody = body.count { it == term || (term.length >= 5 && it.startsWith(term)) }
            hit += minOf(inBody, 3)
            total += hit
        }
        // A query whose every term hit the same entry is what the player meant.
        val covered = query.count { term ->
            term in title || term in tagTokens || body.any { it == term } ||
                (term.length >= 4 && (title + tagTokens).any { it.startsWith(term) })
        }
        if (covered == query.size && query.size > 1) total += 6
        return total
    }

    fun search(entries: List<CodexEntry>, query: String, limit: Int = 5): List<CodexEntry> {
        val terms = tokens(query).distinct()
        if (terms.isEmpty()) return emptyList()
        return entries
            .map { it to score(it, terms) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<CodexEntry, Int>> { it.second }.thenBy { it.first.title })
            .take(limit)
            .map { it.first }
    }
}
