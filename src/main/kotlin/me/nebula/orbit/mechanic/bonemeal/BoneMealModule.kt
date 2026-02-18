package me.nebula.orbit.mechanic.bonemeal

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

private val CROP_BLOCKS = mapOf(
    "minecraft:wheat" to 7,
    "minecraft:carrots" to 7,
    "minecraft:potatoes" to 7,
    "minecraft:beetroots" to 3,
    "minecraft:torchflower_crop" to 2,
)

private val SAPLING_BLOCKS = setOf(
    "minecraft:oak_sapling", "minecraft:spruce_sapling", "minecraft:birch_sapling",
    "minecraft:jungle_sapling", "minecraft:acacia_sapling", "minecraft:dark_oak_sapling",
    "minecraft:cherry_sapling",
)

class BoneMealModule : OrbitModule("bonemeal") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val held = event.player.getItemInMainHand()
            if (held.material() != Material.BONE_MEAL) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val block = event.block
            val blockName = block.name()

            var consumed = false

            val maxAge = CROP_BLOCKS[blockName]
            if (maxAge != null) {
                val currentAge = block.getProperty("age")?.toIntOrNull() ?: 0
                if (currentAge < maxAge) {
                    val newAge = (currentAge + kotlin.random.Random.nextInt(2, 6)).coerceAtMost(maxAge)
                    instance.setBlock(pos, block.withProperty("age", newAge.toString()))
                    consumed = true
                }
            }

            if (blockName in SAPLING_BLOCKS) {
                consumed = true
            }

            if (blockName == "minecraft:grass_block") {
                val above = pos.add(0, 1, 0)
                if (instance.getBlock(above) == Block.AIR) {
                    val flowers = listOf(Block.SHORT_GRASS, Block.DANDELION, Block.POPPY)
                    for (dx in -2..2) {
                        for (dz in -2..2) {
                            val targetPos = pos.add(dx, 1, dz)
                            if (instance.getBlock(targetPos) == Block.AIR &&
                                instance.getBlock(targetPos.add(0, -1, 0)).name() == "minecraft:grass_block" &&
                                kotlin.random.Random.nextFloat() < 0.3f
                            ) {
                                instance.setBlock(targetPos, flowers.random())
                            }
                        }
                    }
                    consumed = true
                }
            }

            if (consumed) {
                val slot = event.player.heldSlot.toInt()
                if (held.amount() > 1) {
                    event.player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
                } else {
                    event.player.inventory.setItemStack(slot, ItemStack.AIR)
                }
            }
        }
    }
}
