package me.nebula.orbit.progression

import me.nebula.ether.utils.logging.logger
import net.minestom.server.entity.Player
import java.util.concurrent.CopyOnWriteArrayList

sealed interface ProgressionEvent {
    val player: Player

    data class BlockMined(override val player: Player) : ProgressionEvent
    data class BlockPlaced(override val player: Player) : ProgressionEvent
    data class GameStarted(override val player: Player, val gameMode: String) : ProgressionEvent
    data class GameEnded(override val player: Player, val placement: Int, val won: Boolean) : ProgressionEvent
    data class SurvivalTick(override val player: Player) : ProgressionEvent
    data class DistanceWalked(override val player: Player, val blocks: Int) : ProgressionEvent
    data class DamageDealt(override val player: Player, val amount: Int) : ProgressionEvent
    data class Kill(override val player: Player) : ProgressionEvent
    data class Assist(override val player: Player) : ProgressionEvent
    data class KillStreak(override val player: Player) : ProgressionEvent
    data class TopPlacement(override val player: Player) : ProgressionEvent
}

object ProgressionEventBus {

    private val log = logger("ProgressionEventBus")
    private val subscribers = CopyOnWriteArrayList<(ProgressionEvent) -> Unit>()

    fun subscribe(subscriber: (ProgressionEvent) -> Unit) {
        subscribers += subscriber
    }

    fun unsubscribe(subscriber: (ProgressionEvent) -> Unit) {
        subscribers -= subscriber
    }

    fun publish(event: ProgressionEvent) {
        for (subscriber in subscribers) {
            runCatching { subscriber(event) }.onFailure {
                log.warn(it) { "Progression subscriber failed for event $event" }
            }
        }
    }
}
