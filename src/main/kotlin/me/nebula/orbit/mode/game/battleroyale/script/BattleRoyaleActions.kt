package me.nebula.orbit.mode.game.battleroyale.script

import me.nebula.orbit.script.GameContext
import me.nebula.orbit.script.ScriptAction

data class ShrinkBorder(val diameter: Double, val durationSeconds: Double) : ScriptAction {
    override fun execute(ctx: GameContext) {
        (ctx.gameMode as? BorderController)?.shrinkBorderTo(diameter, durationSeconds)
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
