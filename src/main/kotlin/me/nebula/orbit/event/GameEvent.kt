package me.nebula.orbit.event

import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.rules.RuleKey
import me.nebula.orbit.utils.matchresult.MatchResult
import me.nebula.orbit.variant.GameVariant
import net.minestom.server.entity.Player
import kotlin.time.Duration

sealed interface GameEvent {
    data class PhaseChanged(val from: GamePhase, val to: GamePhase) : GameEvent
    data class PlayerJoined(val player: Player) : GameEvent
    data class PlayerLeft(val player: Player) : GameEvent
    data class PlayerEliminated(val player: Player, val killer: Player?) : GameEvent
    data class PlayerRespawned(val player: Player) : GameEvent
    data class PlayerDamaged(val victim: Player, val attacker: Player?, val amount: Float) : GameEvent
    data class KillStreak(val player: Player, val streak: Int) : GameEvent
    data class CountdownTick(val remaining: Duration) : GameEvent
    data class GameStarted(val alivePlayers: List<Player>) : GameEvent
    data class GameEnded(val result: MatchResult) : GameEvent
    data class VariantActivated(val variant: GameVariant) : GameEvent
    data class RuleChanged<T : Any>(val key: RuleKey<T>, val old: T, val new: T) : GameEvent
}
