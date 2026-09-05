package no.mwmai.pyquest.pytor

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The online half of Pytor: the tutor service that already runs behind the
 * PyLearn site, called in its expert `quest` mode.
 *
 * Plain HttpURLConnection, no HTTP library, because this is one POST with a
 * JSON body. The model key lives on the server, never in the APK. Every failure
 * is returned as a [PytorReply.Failure] with a reason the UI can show, and the
 * caller falls back to the offline Codex, so a dead network is never a dead
 * screen.
 */
object PytorClient {

    private const val BASE_URL = "https://pytor.mwmai.no/api/tutor"
    private const val USER_AGENT = "pyquest-android/0.2.0"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 45_000

    /** What the bridge advertises: `modes` must contain `quest` for expert answers. */
    data class Health(val ok: Boolean, val backend: String, val modes: List<String>)

    sealed class PytorReply {
        data class Answer(val text: String, val backend: String) : PytorReply()
        data class Failure(val reason: String, val rateLimited: Boolean = false) : PytorReply()
    }

    data class Turn(val role: String, val content: String)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun health(): Health? = withContext(Dispatchers.IO) {
        runCatching {
            val body = request("GET", "$BASE_URL/health", null)
            val obj = json.parseToJsonElement(body).jsonObject
            Health(
                ok = obj["status"]?.jsonPrimitive?.contentOrNull == "ok",
                backend = obj["backend"]?.jsonPrimitive?.contentOrNull ?: "?",
                modes = obj["modes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            )
        }.getOrNull()
    }

    /**
     * Asks Pytor. [context] is what the game knows (tier, level, the current
     * question); [history] is the last few turns so follow-ups make sense.
     */
    suspend fun chat(question: String, context: String, history: List<Turn>): PytorReply =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("question", question)
                put("mode", "quest")
                put("context", context)
                put(
                    "history",
                    buildJsonArray {
                        history.takeLast(6).forEach { turn ->
                            add(
                                buildJsonObject {
                                    put("role", turn.role)
                                    put("content", turn.content)
                                },
                            )
                        }
                    },
                )
            }
            try {
                val body = request("POST", "$BASE_URL/chat", payload.toString())
                val obj = json.parseToJsonElement(body).jsonObject
                val text = obj["response"]?.jsonPrimitive?.contentOrNull
                if (text.isNullOrBlank()) {
                    PytorReply.Failure(obj["error"]?.jsonPrimitive?.contentOrNull ?: "empty reply")
                } else {
                    PytorReply.Answer(text.trim(), obj["backend"]?.jsonPrimitive?.contentOrNull ?: "?")
                }
            } catch (e: RateLimited) {
                PytorReply.Failure("Pytor is busy, try again in ${e.retryAfter} s", rateLimited = true)
            } catch (e: HttpFailure) {
                PytorReply.Failure("tutor service answered ${e.code}")
            } catch (e: IOException) {
                PytorReply.Failure(e.message ?: "no connection")
            }
        }

    private class RateLimited(val retryAfter: Int) : IOException("rate limited")
    private class HttpFailure(val code: Int) : IOException("http $code")

    private fun request(method: String, url: String, body: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code == 429) {
                throw RateLimited(conn.getHeaderField("Retry-After")?.toIntOrNull() ?: 30)
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 && text.isBlank()) throw HttpFailure(code)
            return text
        } finally {
            conn.disconnect()
        }
    }
}
