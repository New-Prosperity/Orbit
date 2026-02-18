package me.nebula.orbit.mechanic.witherrose

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag

class WitherRoseModule : OrbitModule("wither-rose") {

    private val lastDamageTag = Tag.Long("mechanic:wither_rose:last_damage")

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val block = instance.getBlock(player.position)

            if (block.name() != "minecraft:wither_rose") return@addListener

            val now = System.currentTimeMillis()
            val lastDamage = player.getTag(lastDamageTag) ?: 0L
            if (now - lastDamage < 500L) return@addListener

            player.addEffect(Potion(PotionEffect.WITHER, 0, 40))
            player.setTag(lastDamageTag, now)
        }
    }
}
