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
