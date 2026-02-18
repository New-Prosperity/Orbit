package me.nebula.orbit.mechanic.shield

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.item.PlayerBeginItemUseEvent
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

private val BLOCKING_TAG = Tag.Boolean("mechanic:shield:blocking").defaultValue(false)
private val BLOCK_START_TAG = Tag.Long("mechanic:shield:block_start").defaultValue(0L)

class ShieldModule : OrbitModule("shield") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBeginItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.SHIELD) return@addListener
            event.player.setTag(BLOCKING_TAG, true)
            event.player.setTag(BLOCK_START_TAG, System.currentTimeMillis())
        }

        eventNode.addListener(PlayerCancelItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.SHIELD) return@addListener
            event.player.setTag(BLOCKING_TAG, false)
        }

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            if (!player.getTag(BLOCKING_TAG)) return@addListener

            val blockStart = player.getTag(BLOCK_START_TAG)
            if (System.currentTimeMillis() - blockStart < 200) return@addListener

            val damage = event.damage
            if (damage is EntityDamage) {
                val attacker = damage.source ?: return@addListener
                val attackDir = attacker.position.sub(player.position)
                val yaw = Math.toRadians(player.position.yaw().toDouble())
                val lookX = -kotlin.math.sin(yaw)
                val lookZ = kotlin.math.cos(yaw)
                val dot = attackDir.x() * lookX + attackDir.z() * lookZ

                if (dot > 0) {
                    event.isCancelled = true

                    if (attacker is Player) {
                        attacker.velocity = attacker.velocity.add(
                            net.minestom.server.coordinate.Vec(
                                (attacker.position.x() - player.position.x()) * 8.0,
                                2.0,
                                (attacker.position.z() - player.position.z()) * 8.0,
                            ),
                        )
                    }
                }
            }
        }
    }
}
