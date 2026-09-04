package no.mwmai.pyquest.data

import android.content.res.AssetManager
import kotlinx.serialization.json.Json
import no.mwmai.pyquest.model.Tier

/**
 * Reads the curriculum out of the APK's assets. Content is data, never Kotlin,
 * so adding questions is a text edit plus a CI schema check.
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

    private companion object {
        const val DIR = "curriculum"
        val FILE_PATTERN = Regex("""tier_(\d+)\.json""")
    }
}
