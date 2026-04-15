package me.nebula.orbit.utils.anticheat.checks

import me.nebula.gravity.property.NetworkProperties
import me.nebula.gravity.property.PropertyStore
import me.nebula.orbit.utils.anticheat.AntiCheat
import me.nebula.orbit.utils.anticheat.AntiCheatCheck
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object KillDistanceCheck : AntiCheatCheck {

    override val id: String = "kill_distance"

    private const val MELEE_MAX_DISTANCE = 6.0
    private const val BOW_MAX_DISTANCE = 150.0
    private const val WEIGHT = 5

    private val recentViolations = ConcurrentHashMap<UUID, Int>()

    override fun install(node: EventNode<in Event>) {
        node.addListener(EntityAttackEvent::class.java) { event ->
            if (!PropertyStore[NetworkProperties.AC_CHECK_KILL_DISTANCE_ENABLED]) return@addListener
            val attacker = event.entity as? Player ?: return@addListener
            if (attacker.gameMode == GameMode.CREATIVE) return@addListener

            val distance = attacker.position.distance(event.target.position)
            val weapon = attacker.itemInMainHand.material().key().value().lowercase()
            val isRanged = "bow" in weapon || "crossbow" in weapon || "trident" in weapon
            val maxAllowed = if (isRanged) BOW_MAX_DISTANCE else MELEE_MAX_DISTANCE

            if (distance > maxAllowed) {
                val count = recentViolations.compute(attacker.uuid) { _, v -> (v ?: 0) + 1 } ?: 0
                if (count >= 3) {
                    AntiCheat.flag(
                        attacker.uuid, "kill_distance", WEIGHT,
                        AntiCheat.combatFlagThreshold, AntiCheat.combatKickThreshold,
                    )
                    recentViolations.remove(attacker.uuid)
                }
            }
        }
    }

    override fun cleanup(uuid: UUID) {
        recentViolations.remove(uuid)
    }

    override fun clearAll() {
        recentViolations.clear()
    }
}
