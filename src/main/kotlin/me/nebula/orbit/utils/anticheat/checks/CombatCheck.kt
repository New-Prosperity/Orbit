package me.nebula.orbit.utils.anticheat.checks

import me.nebula.orbit.utils.anticheat.AntiCheat
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

private data class AttackTimestamp(val time: Long)

object CombatCheck {

    private const val REACH_THRESHOLD = 4.5
    private const val MAX_CPS = 20
    private const val CPS_WINDOW_MS = 1000L
    private const val WEIGHT = 2

    private val attackHistory = ConcurrentHashMap<UUID, ConcurrentLinkedDeque<AttackTimestamp>>()

    fun install(node: EventNode<Event>) {
        node.addListener(EntityAttackEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            if (player.gameMode == GameMode.CREATIVE) return@addListener

            val target = event.target
            val uuid = player.uuid

            val distance = player.position.distance(target.position)
            if (distance > REACH_THRESHOLD) {
                AntiCheat.flag(uuid, "reach", WEIGHT, AntiCheat.COMBAT_KICK_THRESHOLD)
            }

            val now = System.currentTimeMillis()
            val history = attackHistory.computeIfAbsent(uuid) { ConcurrentLinkedDeque() }
            history.addLast(AttackTimestamp(now))

            val cutoff = now - CPS_WINDOW_MS
            while (true) {
                val head = history.peekFirst() ?: break
                if (head.time < cutoff) history.pollFirst() else break
            }

            if (history.size > MAX_CPS) {
                AntiCheat.flag(uuid, "attackrate", WEIGHT, AntiCheat.COMBAT_KICK_THRESHOLD)
            }
        }
    }

    fun cleanup(uuid: UUID) {
        attackHistory.remove(uuid)
    }

    fun clearAll() {
        attackHistory.clear()
    }
}
