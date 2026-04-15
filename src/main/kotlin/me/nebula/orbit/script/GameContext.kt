package me.nebula.orbit.script

import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.PlayerTracker
import me.nebula.orbit.rules.GameRules
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import kotlin.time.Duration

interface GameContext {
    val rules: GameRules
    val tracker: PlayerTracker
    val instance: Instance
    val gameMode: GameMode

    fun broadcast(translationKey: String, sound: String? = null)
    fun broadcastPlayers(action: (Player) -> Unit)
}

interface GameTickContext : GameContext {
    val gameTime: Duration
    val tickCount: Long
}
