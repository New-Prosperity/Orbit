package me.nebula.orbit.mode.game.battleroyale.script

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.orbit.mode.game.battleroyale.Season
import me.nebula.orbit.script.ScriptAction
import me.nebula.orbit.script.ScriptStep
import me.nebula.orbit.script.ScriptTrigger
import kotlin.time.Duration.Companion.seconds

private val BORDER_SHRINK_KEY = "orbit.game.br.border_shrinking".asTranslationKey()

fun buildBorderSteps(season: Season, speedMultiplier: Double = 1.0): List<ScriptStep> {
    if (season.borderPhases.isNotEmpty()) {
        return season.borderPhases.map { phase ->
            val announceLead = (phase.announceLeadSeconds * speedMultiplier).toInt()
            val triggerAt = ((phase.startAfterSeconds - phase.announceLeadSeconds) * speedMultiplier).toInt().coerceAtLeast(0)
            val shrinkDuration = (phase.shrinkDurationSeconds * speedMultiplier).toInt()
            ScriptStep(
                id = "br_border_phase_${phase.startAfterSeconds}",
                trigger = ScriptTrigger.AtTime(triggerAt.seconds),
                actions = listOf(
                    ScriptAction.Announce(BORDER_SHRINK_KEY),
                    SetBorderDamage(phase.damagePerSecond.toDouble()),
                    ShrinkBorder(phase.targetDiameter, shrinkDuration.toDouble(), announceLead.toDouble()),
                ),
            )
        }
    }

    val border = season.border
    val shrink = ShrinkBorder(border.finalDiameter, border.shrinkDurationSeconds.toDouble())
    return if (border.shrinkStartSeconds > 0) {
        listOf(
            ScriptStep(
                id = "br_border_legacy",
                trigger = ScriptTrigger.AtTime(border.shrinkStartSeconds.seconds),
                actions = listOf(ScriptAction.Announce(BORDER_SHRINK_KEY), shrink),
            ),
        )
    } else {
        listOf(
            ScriptStep(
                id = "br_border_legacy_immediate",
                trigger = ScriptTrigger.AtTime(0.seconds),
                actions = listOf(shrink),
            ),
        )
    }
}
