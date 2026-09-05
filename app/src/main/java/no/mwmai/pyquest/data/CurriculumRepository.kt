package no.mwmai.pyquest.data

import android.content.res.AssetManager
import kotlinx.serialization.json.Json
import no.mwmai.pyquest.model.Codex
import no.mwmai.pyquest.model.CodexEntry
import no.mwmai.pyquest.model.Tier

/**
 * Reads the curriculum and the Codex out of the APK's assets. Content is data,
 * never Kotlin, so adding a question or a Codex note is a text edit plus a CI
 * schema check.
 *
 * Eight tier files and a hundred Codex entries parse in well under a second,
 * but that is still long enough to blank the first frame, so [MainActivity]
 * calls [allTiers] and [codex] off the main thread and shows Pytor until they
 * arrive.
 */
class CurriculumRepository(private val assets: AssetManager) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    private val cache = mutableMapOf<Int, Tier>()

    fun tierNumbers(): List<Int> =
        (assets.list(DIR) ?: emptyArray())
            .mapNotNull { FILE_PATTERN.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            .sorted()

    fun tier(number: Int): Tier = cache.getOrPut(number) {
        val path = "$DIR/tier_%02d.json".format(number)
        val text = assets.open(path).bufferedReader().use { it.readText() }
        json.decodeFromString(Tier.serializer(), text)
    }

    fun allTiers(): List<Tier> = tierNumbers().map { tier(it) }

    /** Every Codex entry, or an empty list when the asset is missing rather than a crash. */
    fun codex(): List<CodexEntry> = runCatching {
        val text = assets.open(CODEX).bufferedReader().use { it.readText() }
        json.decodeFromString(Codex.serializer(), text).entries
    }.getOrDefault(emptyList())

    private companion object {
        const val DIR = "curriculum"
        const val CODEX = "codex/codex.json"
        val FILE_PATTERN = Regex("""tier_(\d+)\.json""")
    }
}
