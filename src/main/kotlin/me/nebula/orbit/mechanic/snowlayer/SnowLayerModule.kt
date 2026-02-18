package me.nebula.orbit.mechanic.snowlayer

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material

class SnowLayerModule : OrbitModule("snow-layer") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            val held = event.player.getItemInMainHand()
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            if (block.name() == "minecraft:snow" && held.material() == Material.SNOW) {
                val layers = block.getProperty("layers")?.toIntOrNull() ?: 1
                if (layers < 8) {
                    instance.setBlock(pos, block.withProperty("layers", (layers + 1).toString()))
                    consumeHeldItem(event)
                }
                return@addListener
            }

            if (held.material() == Material.DIAMOND_SHOVEL || held.material() == Material.IRON_SHOVEL ||
                held.material() == Material.GOLDEN_SHOVEL || held.material() == Material.STONE_SHOVEL ||
                held.material() == Material.WOODEN_SHOVEL || held.material() == Material.NETHERITE_SHOVEL
            ) {
                if (block.name() == "minecraft:snow") {
                    val layers = block.getProperty("layers")?.toIntOrNull() ?: 1
                    if (layers > 1) {
                        instance.setBlock(pos, block.withProperty("layers", (layers - 1).toString()))
                    } else {
                        instance.setBlock(pos, Block.AIR)
                    }
                } else if (block.name() == "minecraft:snow_block") {
                    instance.setBlock(pos, Block.AIR)
                }
            }
        }
    }

    private fun consumeHeldItem(event: PlayerBlockInteractEvent) {
        val held = event.player.getItemInMainHand()
        val slot = event.player.heldSlot.toInt()
        if (held.amount() > 1) {
            event.player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
        } else {
            event.player.inventory.setItemStack(slot, net.minestom.server.item.ItemStack.AIR)
        }
    }
}
