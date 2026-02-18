package me.nebula.orbit.mechanic.magmablock

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

class MagmaBlockModule : OrbitModule("magma-block") {

    private val lastDamageTag = Tag.Long("mechanic:magma:last_damage")

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener

            if (!player.isOnGround) return@addListener

            val belowPos = Vec(player.position.x(), player.position.y() - 0.1, player.position.z())
            val block = instance.getBlock(belowPos)
            if (block.name() != "minecraft:magma_block") return@addListener

            if (player.isSneaking) return@addListener

            val boots = player.boots
            if (!boots.isAir && boots.material() == Material.LEATHER_BOOTS) return@addListener

            val now = System.currentTimeMillis()
            val lastDamage = player.getTag(lastDamageTag) ?: 0L
            if (now - lastDamage < 500L) return@addListener

            player.damage(Damage(DamageType.HOT_FLOOR, null, null, null, 1f))
            player.setTag(lastDamageTag, now)
        }
    }
}
