package me.nebula.orbit.mechanic.spyglass

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.Player
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag

private val SPYGLASS_ACTIVE_TAG = Tag.Boolean("mechanic:spyglass:active").defaultValue(false)

class SpyglassModule : OrbitModule("spyglass") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.itemStack.material() != Material.SPYGLASS) return@addListener

            val player = event.player
            player.setTag(SPYGLASS_ACTIVE_TAG, true)
            player.addEffect(Potion(PotionEffect.SLOWNESS, 5, 32767, Potion.ICON_FLAG))
        }

        eventNode.addListener(PlayerCancelItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.SPYGLASS) return@addListener
            stopZoom(event.player)
        }
    }

    private fun stopZoom(player: Player) {
        if (!player.getTag(SPYGLASS_ACTIVE_TAG)) return
        player.setTag(SPYGLASS_ACTIVE_TAG, false)
        player.removeEffect(PotionEffect.SLOWNESS)
    }
}
