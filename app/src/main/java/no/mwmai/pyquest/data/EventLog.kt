package no.mwmai.pyquest.data

import android.content.Context
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A local, append-only log of what happened in the game: every answer, hint,
 * level, chat exchange and Codex read, one JSON line each, in the app's private
 * files directory. Nothing leaves the phone unless the player shares it from
 * the You tab, which is how a play session gets reviewed afterwards.
 *
 * Writes go through a single background thread so a slow flash write never
 * stalls a tap. Reads are rare (the You tab) and happen on demand.
 */
class EventLog(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pyquest-log").apply { isDaemon = true }
    }

    fun log(type: String, vararg fields: Pair<String, Any?>) {
        val line = buildJsonObject {
            put("ts", Instant.now().toString())
            put("type", type)
            for ((key, value) in fields) {
                when (value) {
                    null -> {}
                    is Boolean -> put(key, value)
                    is Int -> put(key, value)
                    is Long -> put(key, value)
                    is Float -> put(key, value)
                    is Double -> put(key, value)
                    is List<*> -> put(key, JsonPrimitive(value.joinToString(" | ") { it.toString() }))
                    else -> put(key, value.toString().take(MAX_FIELD_CHARS))
                }
            }
        }.toString()
        writer.execute {
            runCatching {
                file.appendText(line + "\n")
                if (file.length() > MAX_BYTES) trim()
            }
        }
    }

    /** The last [count] lines, oldest first. */
    fun tail(count: Int): List<String> = runCatching {
        if (!file.exists()) emptyList() else file.readLines().takeLast(count)
    }.getOrDefault(emptyList())

    /** Everything, capped so a share intent stays inside what Android accepts. */
    fun readAll(): String = runCatching {
        if (!file.exists()) "" else file.readText().takeLast(SHARE_CAP_CHARS)
    }.getOrDefault("")

    fun sizeBytes(): Long = if (file.exists()) file.length() else 0L

    fun clear() {
        writer.execute { runCatching { file.delete() } }
    }

    /** Keeps the newest half when the file outgrows its cap. */
    private fun trim() {
        val lines = file.readLines()
        file.writeText(lines.takeLast(lines.size / 2).joinToString("\n", postfix = "\n"))
    }

    private companion object {
        const val FILE_NAME = "pyquest_log.jsonl"
        const val MAX_BYTES = 2L * 1024 * 1024
        const val MAX_FIELD_CHARS = 2000
        const val SHARE_CAP_CHARS = 400_000
    }
}
