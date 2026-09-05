package no.mwmai.pyquest.pytor

import no.mwmai.pyquest.data.Progress
import no.mwmai.pyquest.model.Tier

/**
 * Pytor's one-liners on the track screen, chosen from the player's state. No
 * network, no model: this is the part of Pytor that has to work on a bus.
 *
 * Priority order matters. A streak about to lapse beats everything, because
 * it is the one thing that is time-critical; a weak tag beats the default
 * because it is the one thing the player cannot see for themselves.
 */
object PytorCoach {

    data class Line(val text: String, val tier: Int? = null, val level: Int? = null)

    fun greeting(progress: Progress, tiers: List<Tier>, today: String): Line {
        if (progress.answered == 0) {
            return Line("New here? Tier 1 is ten minutes and teaches you to read a call. Tap Level 1.", 1, 1)
        }
        val streak = progress.streakOn(today)
        if (progress.streakAtRisk(today)) {
            return Line("Your $streak-day streak ends at midnight. One level keeps it alive.")
        }
        val weak = progress.weakTags.firstOrNull()
        if (weak != null) {
            val pct = (weak.second * 100).toInt()
            val where = tiers.firstOrNull { tier -> tier.questions.any { weak.first in it.tags } }
            val hint = where?.let { " Replay tier ${it.tier}, or ask me about it." } ?: " Ask me about it."
            return Line("You miss $pct% of questions tagged ${weak.first}.$hint", where?.tier)
        }
        if (progress.xpOn(today) >= Progress.DAILY_GOAL_XP) {
            return Line("Daily goal done. Anything from here is extra credit, and I like extra credit.")
        }
        val next = nextLevel(progress, tiers)
        return if (next != null) {
            Line("Next up: tier ${next.first.tier}, ${next.first.title}, level ${next.second}.", next.first.tier, next.second)
        } else {
            Line("Every level cleared. Replay the ones under 100% mastery, or go build something and send me the bug reports.")
        }
    }

    /** The first level not yet cleared, walking the ladder in order. */
    fun nextLevel(progress: Progress, tiers: List<Tier>): Pair<Tier, Int>? {
        for (tier in tiers) {
            for (level in tier.levels) {
                if (!progress.isCleared(tier.tier, level)) return tier to level
            }
        }
        return null
    }

    /** What Pytor says on the level-cleared screen. */
    fun verdict(accuracy: Float, hintsUsed: Int, isReview: Boolean): String = when {
        isReview && accuracy >= 0.99f -> "Every miss fixed. That is how it is done."
        isReview -> "Better. The ones you missed again will be back next time."
        accuracy >= 0.99f && hintsUsed == 0 -> "Clean sweep, no hints. Move up."
        accuracy >= 0.99f -> "Clean sweep. The hints did their job; next time try without."
        accuracy >= 0.8f -> "Solid. Read the note on the ones you missed before moving on."
        accuracy >= 0.5f -> "You got there. Replay this level tomorrow and it will stick."
        else -> "Rough one. That is what the review button is for."
    }
}
