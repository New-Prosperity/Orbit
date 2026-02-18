package me.nebula.orbit.mechanic.mace

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

private val FALL_START_Y_TAG = Tag.Double("mechanic:mace:fall_start_y")
private val MACE_ON_GROUND_TAG = Tag.Boolean("mechanic:mace:on_ground").defaultValue(true)

private const val BASE_DAMAGE = 6f
private const val FALL_DAMAGE_MULTIPLIER = 4f
private const val FALL_THRESHOLD = 1.5

class MaceModule : OrbitModule("mace") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val onGround = event.isOnGround
            val currentY = event.newPosition.y()

            if (!onGround) {
                if (player.getTag(MACE_ON_GROUND_TAG)) {
                    player.setTag(FALL_START_Y_TAG, currentY)
                    player.setTag(MACE_ON_GROUND_TAG, false)
                } else {
                    val tracked = player.getTag(FALL_START_Y_TAG)
                    if (tracked == null || currentY > tracked) {
                        player.setTag(FALL_START_Y_TAG, currentY)
                    }
                }
            } else {
                player.setTag(MACE_ON_GROUND_TAG, true)
                player.removeTag(FALL_START_Y_TAG)
            }
        }

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val attacker = event.entity as? Player ?: return@addListener
            val target = event.target as? LivingEntity ?: return@addListener

            val item = attacker.getItemInMainHand()
            if (item.material() != Material.MACE) return@addListener

            val fallStartY = attacker.getTag(FALL_START_Y_TAG) ?: return@addListener
            val fallDistance = fallStartY - attacker.position.y()
            if (fallDistance <= 0) return@addListener

            val bonusDamage = (fallDistance - FALL_THRESHOLD).coerceAtLeast(0.0).toFloat() * FALL_DAMAGE_MULTIPLIER
            val totalDamage = BASE_DAMAGE + bonusDamage

            target.damage(EntityDamage(attacker, totalDamage))

            attacker.removeTag(FALL_START_Y_TAG)
            attacker.setTag(MACE_ON_GROUND_TAG, true)

            attacker.velocity = Vec(attacker.velocity.x(), 8.0, attacker.velocity.z())
        }
    }
}
