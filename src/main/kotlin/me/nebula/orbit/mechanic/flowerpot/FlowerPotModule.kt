package me.nebula.orbit.mechanic.flowerpot

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

private val PLANTABLE_TO_POTTED = mapOf(
    Material.OAK_SAPLING to "minecraft:potted_oak_sapling",
    Material.SPRUCE_SAPLING to "minecraft:potted_spruce_sapling",
    Material.BIRCH_SAPLING to "minecraft:potted_birch_sapling",
    Material.JUNGLE_SAPLING to "minecraft:potted_jungle_sapling",
    Material.ACACIA_SAPLING to "minecraft:potted_acacia_sapling",
    Material.DARK_OAK_SAPLING to "minecraft:potted_dark_oak_sapling",
    Material.CHERRY_SAPLING to "minecraft:potted_cherry_sapling",
    Material.DANDELION to "minecraft:potted_dandelion",
    Material.POPPY to "minecraft:potted_poppy",
    Material.BLUE_ORCHID to "minecraft:potted_blue_orchid",
    Material.ALLIUM to "minecraft:potted_allium",
    Material.AZURE_BLUET to "minecraft:potted_azure_bluet",
    Material.RED_TULIP to "minecraft:potted_red_tulip",
    Material.ORANGE_TULIP to "minecraft:potted_orange_tulip",
    Material.WHITE_TULIP to "minecraft:potted_white_tulip",
    Material.PINK_TULIP to "minecraft:potted_pink_tulip",
    Material.OXEYE_DAISY to "minecraft:potted_oxeye_daisy",
    Material.CORNFLOWER to "minecraft:potted_cornflower",
    Material.LILY_OF_THE_VALLEY to "minecraft:potted_lily_of_the_valley",
    Material.RED_MUSHROOM to "minecraft:potted_red_mushroom",
    Material.BROWN_MUSHROOM to "minecraft:potted_brown_mushroom",
    Material.FERN to "minecraft:potted_fern",
    Material.DEAD_BUSH to "minecraft:potted_dead_bush",
    Material.CACTUS to "minecraft:potted_cactus",
    Material.BAMBOO to "minecraft:potted_bamboo",
    Material.CRIMSON_FUNGUS to "minecraft:potted_crimson_fungus",
    Material.WARPED_FUNGUS to "minecraft:potted_warped_fungus",
    Material.CRIMSON_ROOTS to "minecraft:potted_crimson_roots",
    Material.WARPED_ROOTS to "minecraft:potted_warped_roots",
    Material.AZALEA to "minecraft:potted_azalea_bush",
    Material.FLOWERING_AZALEA to "minecraft:potted_flowering_azalea_bush",
)

class FlowerPotModule : OrbitModule("flower-pot") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            if (block.name() == "minecraft:flower_pot") {
                val held = event.player.getItemInMainHand()
                val pottedName = PLANTABLE_TO_POTTED[held.material()] ?: return@addListener
                val potted = Block.fromKey(pottedName) ?: return@addListener

                instance.setBlock(pos, potted)

                val slot = event.player.heldSlot.toInt()
                if (held.amount() > 1) {
                    event.player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
                } else {
                    event.player.inventory.setItemStack(slot, ItemStack.AIR)
                }
                return@addListener
            }

            if (block.name().startsWith("minecraft:potted_")) {
                instance.setBlock(pos, Block.FLOWER_POT)
            }
        }
    }
}
