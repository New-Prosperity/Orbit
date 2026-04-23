package me.nebula.orbit.mode.game.battleroyale.script

import me.nebula.orbit.mode.game.battleroyale.zone.ZoneShrinkController
import me.nebula.orbit.script.GameContext
import me.nebula.orbit.script.ScriptAction

data class ShrinkBorder(
    val diameter: Double,
    val durationSeconds: Double,
    val announceLeadSeconds: Double = 0.0,
) : ScriptAction {
    override fun execute(ctx: GameContext) {
        val mode = ctx.gameMode
        if (announceLeadSeconds > 0.0 && mode is ZoneShrinkController) {
            mode.planZoneShrink(diameter, durationSeconds, announceLeadSeconds)
            return
        }
        (mode as? BorderController)?.shrinkBorderTo(diameter, durationSeconds)
    }
}

data class SetBorderDamage(val damagePerSecond: Double) : ScriptAction {
    override fun execute(ctx: GameContext) {
        (ctx.gameMode as? BorderController)?.setBorderDamage(damagePerSecond)
    }
}

data object StartDeathmatch : ScriptAction {
    override fun execute(ctx: GameContext) {
        (ctx.gameMode as? DeathmatchController)?.startDeathmatch()
    }
}
