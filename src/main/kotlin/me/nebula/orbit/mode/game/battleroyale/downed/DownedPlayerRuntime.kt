package me.nebula.orbit.mode.game.battleroyale.downed

import net.minestom.server.entity.EntityPose
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PlayerAttrSnapshot(
    val movementSpeed: Double,
    val jumpStrength: Double,
    val attackDamage: Double,
    val maxHealth: Double,
)

class DownedPlayerRuntime {

    private val snapshots = ConcurrentHashMap<UUID, PlayerAttrSnapshot>()

    fun applyDownedState(player: Player) {
        val uuid = player.uuid
        if (snapshots.containsKey(uuid)) return
        snapshots[uuid] = PlayerAttrSnapshot(
            movementSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue,
            jumpStrength = player.getAttribute(Attribute.JUMP_STRENGTH).baseValue,
            attackDamage = player.getAttribute(Attribute.ATTACK_DAMAGE).baseValue,
            maxHealth = player.getAttribute(Attribute.MAX_HEALTH).baseValue,
        )
        player.pose = EntityPose.SWIMMING
        player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = DOWNED_MOVEMENT_SPEED
        player.getAttribute(Attribute.JUMP_STRENGTH).baseValue = 0.0
        player.getAttribute(Attribute.ATTACK_DAMAGE).baseValue = 0.0
        player.health = DOWNED_VISIBLE_HEALTH
        player.addEffect(Potion(PotionEffect.SLOWNESS, 3, Int.MAX_VALUE))
        player.addEffect(Potion(PotionEffect.MINING_FATIGUE, 3, Int.MAX_VALUE))
        player.addEffect(Potion(PotionEffect.WEAKNESS, 3, Int.MAX_VALUE))
    }

    fun clearDownedState(player: Player, restoreHealth: Boolean) {
        val uuid = player.uuid
        val snap = snapshots.remove(uuid) ?: return
        player.pose = EntityPose.STANDING
        player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = snap.movementSpeed
        player.getAttribute(Attribute.JUMP_STRENGTH).baseValue = snap.jumpStrength
        player.getAttribute(Attribute.ATTACK_DAMAGE).baseValue = snap.attackDamage
        player.getAttribute(Attribute.MAX_HEALTH).baseValue = snap.maxHealth
        player.removeEffect(PotionEffect.SLOWNESS)
        player.removeEffect(PotionEffect.MINING_FATIGUE)
        player.removeEffect(PotionEffect.WEAKNESS)
        if (restoreHealth) {
            player.health = snap.maxHealth.toFloat()
        }
    }

    fun isTracked(uuid: UUID): Boolean = snapshots.containsKey(uuid)

    fun clearAll() { snapshots.clear() }

    companion object {
        const val DOWNED_MOVEMENT_SPEED: Double = 0.03
        const val DOWNED_VISIBLE_HEALTH: Float = 1.0f
    }
}
