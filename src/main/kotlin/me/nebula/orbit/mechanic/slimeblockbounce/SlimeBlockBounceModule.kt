package me.nebula.orbit.mechanic.slimeblockbounce

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerMoveEvent

class SlimeBlockBounceModule : OrbitModule("slime-block-bounce") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener

            if (!event.isOnGround) return@addListener

            val belowPos = Vec(player.position.x(), player.position.y() - 0.1, player.position.z())
            val belowBlock = instance.getBlock(belowPos)

            if (belowBlock.name() != "minecraft:slime_block") return@addListener
            if (player.isSneaking) return@addListener

            val vy = player.velocity.y()
            if (vy >= 0.0) return@addListener

            player.velocity = Vec(
                player.velocity.x(),
                -vy * 0.8,
                player.velocity.z(),
            )
        }

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            if (event.damage.type != DamageType.FALL) return@addListener

            val entity = event.entity
            val instance = entity.instance ?: return@addListener

            val belowPos = Vec(entity.position.x(), entity.position.y() - 0.1, entity.position.z())
            val belowBlock = instance.getBlock(belowPos)

            if (belowBlock.name() == "minecraft:slime_block") {
                event.isCancelled = true
            }
        }
    }
}
