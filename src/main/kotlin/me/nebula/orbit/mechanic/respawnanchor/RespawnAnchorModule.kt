package me.nebula.orbit.mechanic.respawnanchor

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material

class RespawnAnchorModule : OrbitModule("respawn-anchor") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (block.name() != "minecraft:respawn_anchor") return@addListener

            val charges = block.getProperty("charges")?.toIntOrNull() ?: 0
            val heldItem = event.player.getItemInMainHand()

            if (heldItem.material() == Material.GLOWSTONE && charges < 4) {
                val instance = event.player.instance ?: return@addListener
                val pos = event.blockPosition
                instance.setBlock(pos, block.withProperty("charges", (charges + 1).toString()))
                if (heldItem.amount() > 1) {
                    event.player.setItemInMainHand(heldItem.withAmount(heldItem.amount() - 1))
                } else {
                    event.player.setItemInMainHand(net.minestom.server.item.ItemStack.AIR)
                }
            } else if (charges > 0) {
                val pos = event.blockPosition
                event.player.respawnPoint = Pos(
                    pos.x() + 0.5,
                    pos.y() + 1.0,
                    pos.z() + 0.5,
                )
            }
        }
    }
}
