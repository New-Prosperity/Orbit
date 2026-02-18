package me.nebula.orbit.mechanic.powdersnow

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag

private val LEATHER_BOOTS = setOf(Material.LEATHER_BOOTS)

class PowderSnowModule : OrbitModule("powder-snow") {

    private val freezeTicksTag = Tag.Integer("mechanic:powder_snow:freeze_ticks")

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val block = instance.getBlock(player.position)

            if (block.name() != "minecraft:powder_snow") {
                val ticks = player.getTag(freezeTicksTag) ?: 0
                if (ticks > 0) {
                    player.setTag(freezeTicksTag, (ticks - 2).coerceAtLeast(0))
                }
                return@addListener
            }

            if (player.boots.material() in LEATHER_BOOTS) return@addListener

            val ticks = (player.getTag(freezeTicksTag) ?: 0) + 1
            player.setTag(freezeTicksTag, ticks)

            if (ticks >= 140) {
                player.damage(Damage(DamageType.FREEZE, null, null, null, 1f))
                player.setTag(freezeTicksTag, 120)
            }

            if (ticks >= 60) {
                player.addEffect(Potion(PotionEffect.SLOWNESS, 2, 40, Potion.ICON_FLAG))
            }
        }
    }
}
