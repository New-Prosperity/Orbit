package me.nebula.orbit.utils.anticheat.checks

import me.nebula.gravity.config.ConfigStore
import me.nebula.gravity.config.NetworkConfig
import me.nebula.orbit.utils.anticheat.AntiCheat
import me.nebula.orbit.utils.anticheat.AntiCheatCheck
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

private data class AttackTimestamp(val time: Long)

object CombatCheck : AntiCheatCheck {

    override val id: String = "combat"

    private const val REACH_THRESHOLD = 4.5
    private const val MAX_CPS = 20
    private const val CPS_WINDOW_MS = 1000L
    private const val WEIGHT = 2

    private val attackHistory = ConcurrentHashMap<UUID, ConcurrentLinkedDeque<AttackTimestamp>>()

    override fun install(node: EventNode<in Event>) {
        node.addListener(EntityAttackEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            if (player.gameMode == GameMode.CREATIVE) return@addListener

            val target = event.target
            val uuid = player.uuid

            val distance = player.position.distance(target.position)
            if (distance > REACH_THRESHOLD && ConfigStore.get(NetworkConfig.AC_CHECK_REACH_ENABLED)) {
                AntiCheat.flag(uuid, "reach", WEIGHT, AntiCheat.combatFlagThreshold, AntiCheat.combatKickThreshold)
            }

            val now = System.currentTimeMillis()
            val history = attackHistory.computeIfAbsent(uuid) { ConcurrentLinkedDeque() }
            history.addLast(AttackTimestamp(now))

            val cutoff = now - CPS_WINDOW_MS
            while (true) {
                val head = history.peekFirst() ?: break
                if (head.time < cutoff) history.pollFirst() else break
            }

            if (history.size > MAX_CPS && ConfigStore.get(NetworkConfig.AC_CHECK_ATTACKRATE_ENABLED)) {
                AntiCheat.flag(uuid, "attackrate", WEIGHT, AntiCheat.combatFlagThreshold, AntiCheat.combatKickThreshold)
            }
        }
    }

    override fun cleanup(uuid: UUID) {
        attackHistory.remove(uuid)
    }

    override fun clearAll() {
        attackHistory.clear()
    }
}
