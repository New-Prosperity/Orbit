package me.nebula.orbit.mechanic.azalea

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material

private val AZALEA_BLOCKS = setOf(
    "minecraft:azalea",
    "minecraft:flowering_azalea",
)

class AzaleaModule : OrbitModule("azalea") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (block.name() !in AZALEA_BLOCKS) return@addListener

            val held = event.player.getItemInMainHand()
            if (held.material() != Material.BONE_MEAL) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val isFlowering = block.name() == "minecraft:flowering_azalea"
            val leafBlock = if (isFlowering) {
                Block.fromKey("minecraft:flowering_azalea_leaves") ?: Block.OAK_LEAVES
            } else {
                Block.fromKey("minecraft:azalea_leaves") ?: Block.OAK_LEAVES
            }

            instance.setBlock(pos, Block.OAK_LOG)
            for (dy in 1..3) {
                instance.setBlock(pos.add(0, dy, 0), Block.OAK_LOG)
            }

            val topY = pos.blockY() + 4
            for (dx in -2..2) {
                for (dz in -2..2) {
                    for (dy in -1..1) {
                        if (dx * dx + dz * dz + dy * dy > 5) continue
                        val lx = pos.blockX() + dx
                        val ly = topY + dy
                        val lz = pos.blockZ() + dz
                        val existing = instance.getBlock(lx, ly, lz)
                        if (existing.isAir) {
                            instance.setBlock(lx, ly, lz, leafBlock)
                        }
                    }
                }
            }

            val newCount = held.amount() - 1
            event.player.setItemInMainHand(
                if (newCount <= 0) net.minestom.server.item.ItemStack.AIR else held.withAmount(newCount)
            )
        }
    }
}
