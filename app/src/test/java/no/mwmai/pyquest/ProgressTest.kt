package no.mwmai.pyquest

import no.mwmai.pyquest.data.Progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The date arithmetic behind the daily ring and the streak.
 *
 * The first build showed yesterday's XP in today's ring until the first answer
 * landed, and kept showing a streak that had already lapsed. Both were reads of
 * stored fields with no date attached, so both are pinned here as functions of
 * the date passed in.
 */
class ProgressTest {

    private val day1 = "2026-09-04"
    private val day2 = "2026-09-05"
    private val day4 = "2026-09-07"

    @Test
    fun `daily xp is zero on a day nothing has been played`() {
        val p = Progress().applying("t1.l1.q1", isCorrect = true, xpGain = 10, today = day1)
        assertEquals(10, p.xpOn(day1))
        assertEquals(0, p.xpOn(day2))
        assertEquals(0f, p.dailyProgressOn(day2))
    }

    @Test
    fun `daily xp accumulates within a day and restarts on the next`() {
        var p = Progress()
        p = p.applying("a", true, 10, day1)
        p = p.applying("b", true, 15, day1)
        assertEquals(25, p.xpOn(day1))
        p = p.applying("c", true, 10, day2)
        assertEquals(10, p.xpOn(day2))
        assertEquals(35, p.xp)
    }

    @Test
    fun `a streak survives one day of grace and then breaks`() {
        var p = Progress().applying("a", true, 10, day1)
        assertEquals(1, p.streakOn(day1))
        // Not yet played today: the streak is alive but at risk.
        assertEquals(1, p.streakOn(day2))
        assertTrue(p.streakAtRisk(day2))
        p = p.applying("b", true, 10, day2)
        assertEquals(2, p.streakOn(day2))
        assertFalse(p.streakAtRisk(day2))
        // Two days skipped: the number is stale and must read as zero.
        assertEquals(0, p.streakOn(day4))
        p = p.applying("c", true, 10, day4)
        assertEquals(1, p.streakOn(day4))
    }

    @Test
    fun `a wrong answer drops the box to zero and a right one climbs by one`() {
        var p = Progress()
        repeat(3) { p = p.applying("q", true, 10, day1) }
        assertEquals(3, p.box("q"))
        assertEquals(1f, p.masteryOf(listOf("q")))
        p = p.applying("q", false, 10, day1)
        assertEquals(0, p.box("q"))
        repeat(9) { p = p.applying("q", true, 10, day1) }
        assertEquals(Progress.MAX_BOX, p.box("q"))
    }

    @Test
    fun `wrong answers never earn xp`() {
        val p = Progress().applying("q", false, 50, day1)
        assertEquals(0, p.xp)
        assertEquals(1, p.answered)
        assertEquals(0, p.correct)
    }

    @Test
    fun `weak tags need enough sightings and a real miss rate`() {
        var p = Progress()
        // "slicing" missed 2 of 3: weak. "print" seen twice: too few to judge.
        p = p.applying("a", false, 10, day1, listOf("slicing", "print"))
        p = p.applying("b", false, 10, day1, listOf("slicing", "print"))
        p = p.applying("c", true, 10, day1, listOf("slicing"))
        val weak = p.weakTags
        assertEquals(listOf("slicing"), weak.map { it.first })
        assertEquals(2f / 3f, weak.first().second, 0.001f)
    }

    @Test
    fun `rank climbs with xp and reports the next one`() {
        val p = Progress(xp = 300)
        assertEquals("Learner", p.rank)
        assertEquals("Scripter" to 450, p.nextRank)
    }

    @Test
    fun `solved counts one right answer and mastery needs three`() {
        var p = Progress().applying("a", true, 10, day1).applying("b", false, 10, day1)
        assertEquals(0.5f, p.solvedOf(listOf("a", "b")), 0.001f)
        assertEquals(0f, p.masteryOf(listOf("a", "b")), 0.001f)
        repeat(2) { p = p.applying("a", true, 10, day1) }
        assertEquals(0.5f, p.masteryOf(listOf("a", "b")), 0.001f)
    }

    @Test
    fun `clearing a level is recorded once and read back by tier and level`() {
        var p = Progress().clearing(3, 2)
        assertTrue(p.isCleared(3, 2))
        assertFalse(p.isCleared(3, 1))
        p = p.clearing(3, 2)
        assertEquals(listOf("t3.l2"), p.clearedLevels)
    }

    @Test
    fun `yesterday handles month boundaries and garbage`() {
        assertEquals("2026-08-31", Progress.yesterdayOf("2026-09-01"))
        assertEquals("", Progress.yesterdayOf("not a date"))
    }
}
