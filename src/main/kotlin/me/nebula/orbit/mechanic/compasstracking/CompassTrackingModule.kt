package me.nebula.orbit.mechanic.compasstracking

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

private val LODESTONE_X = Tag.Double("mechanic:compass:lodestone_x")
private val LODESTONE_Y = Tag.Double("mechanic:compass:lodestone_y")
private val LODESTONE_Z = Tag.Double("mechanic:compass:lodestone_z")
private val LODESTONE_TRACKED = Tag.Boolean("mechanic:compass:tracked").defaultValue(false)

class CompassTrackingModule : OrbitModule("compass-tracking") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:lodestone") return@addListener

            val player = event.player
            val itemInHand = player.getItemInHand(event.hand)

            if (itemInHand.material() != Material.COMPASS) return@addListener

            val pos = event.blockPosition
            val targetX = pos.x() + 0.5
            val targetY = pos.y() + 0.5
            val targetZ = pos.z() + 0.5

            val lodestoneCompass = itemInHand
                .withTag(LODESTONE_X, targetX)
                .withTag(LODESTONE_Y, targetY)
                .withTag(LODESTONE_Z, targetZ)
                .withTag(LODESTONE_TRACKED, true)

            player.setItemInHand(event.hand, lodestoneCompass)
        }
    }
}
