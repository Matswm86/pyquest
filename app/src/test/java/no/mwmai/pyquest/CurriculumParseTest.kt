package no.mwmai.pyquest

import java.io.File
import kotlinx.serialization.json.Json
import no.mwmai.pyquest.model.QuestionType
import no.mwmai.pyquest.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses the curriculum that actually ships, through the model that actually runs.
 *
 * The Python validator checks the shape of the JSON; this checks that Kotlin can
 * decode it. Those are different failures: a field the validator never looks at,
 * or an enum value it accepts and the serializer does not, is a crash on someone's
 * phone at launch. Catching it here costs a couple of seconds in CI.
 */
class CurriculumParseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    private fun curriculumFiles(): List<File> {
        val dir = File("src/main/assets/curriculum")
        assertTrue("curriculum directory not found at ${dir.absolutePath}", dir.isDirectory)
        return dir.listFiles { file -> file.extension == "json" }!!.sortedBy { it.name }
    }

    @Test
    fun `every tier file decodes`() {
        val files = curriculumFiles()
        assertTrue("no tier files found", files.isNotEmpty())
        files.forEach { file ->
            val tier = json.decodeFromString(Tier.serializer(), file.readText())
            assertTrue("${file.name} has no questions", tier.questions.isNotEmpty())
            assertTrue("${file.name} has a blank title", tier.title.isNotBlank())
        }
    }

    @Test
    fun `every question carries Pytor's hints and deep note`() {
        curriculumFiles().forEach { file ->
            val tier = json.decodeFromString(Tier.serializer(), file.readText())
            tier.questions.forEach { question ->
                assertTrue("${question.id} has no hints", question.hints.isNotEmpty())
                assertTrue("${question.id} has no deep note", !question.deep.isNullOrBlank())
            }
        }
    }

    @Test
    fun `typed questions resolve every block they name`() {
        curriculumFiles().forEach { file ->
            val tier = json.decodeFromString(Tier.serializer(), file.readText())
            tier.questions
                .filter { it.type == QuestionType.FILL || it.type == QuestionType.PIPELINE }
                .forEach { question ->
                    question.answer.forEach { id ->
                        assertTrue(
                            "${question.id} names block '$id' that is not in its blocks list",
                            question.block(id) != null,
                        )
                    }
                    assertEquals(
                        "${question.id} has ${question.slotCount} slots but ${question.answer.size} answers",
                        question.slotCount,
                        question.answer.size,
                    )
                }
        }
    }

    @Test
    fun `a pipeline answer stays inside its own brief`() {
        curriculumFiles().forEach { file ->
            val tier = json.decodeFromString(Tier.serializer(), file.readText())
            tier.questions.filter { it.type == QuestionType.PIPELINE }.forEach { question ->
                val brief = question.brief!!
                val chosen = question.answer.mapNotNull(question::block)
                assertTrue(
                    "${question.id} costs ${chosen.sumOf { it.ms }} ms against a ${brief.maxMs} ms brief",
                    chosen.sumOf { it.ms } <= brief.maxMs,
                )
                assertTrue(
                    "${question.id} costs ${chosen.sumOf { it.cost }} against a ${brief.maxCost} brief",
                    chosen.sumOf { it.cost } <= brief.maxCost,
                )
            }
        }
    }
}

/**
 * Guards against regex patterns that a desktop JVM accepts and Android does not.
 *
 * Android compiles regexes through ICU, which is stricter than OpenJDK. An
 * unescaped closing brace cost a launch crash that every JVM test in this file
 * passed straight through, because OpenJDK compiles that pattern happily. The
 * source itself is checked here instead.
 */
class RegexPortabilityTest {

    @Test
    fun `no Kotlin source leaves a closing brace unescaped in a regex`() {
        val regexLiteral = Regex("Regex\\(([^)]*)\\)")
        val bareClosingBrace = Regex("(?<!\\\\)\\}")

        val offenders = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                regexLiteral.findAll(file.readText()).map { file.name to it.groupValues[1] }
            }
            .filter { (_, pattern) -> bareClosingBrace.containsMatchIn(pattern) }
            .toList()

        assertTrue(
            "Android's ICU regex rejects a bare closing brace, escape it: $offenders",
            offenders.isEmpty(),
        )
    }
}
