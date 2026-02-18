package me.nebula.orbit.mechanic.painting

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material

class PaintingModule : OrbitModule("painting") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val held = event.player.getItemInMainHand()
            if (held.material() != Material.PAINTING) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val face = event.blockFace

            val targetPos = pos.add(
                face.toDirection().normalX(),
                face.toDirection().normalY(),
                face.toDirection().normalZ(),
            )

            if (instance.getBlock(targetPos) != Block.AIR) return@addListener

            val slot = event.player.heldSlot.toInt()
            if (held.amount() > 1) {
                event.player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
            } else {
                event.player.inventory.setItemStack(slot, net.minestom.server.item.ItemStack.AIR)
            }
        }
    }
}
