package me.nebula.orbit.mechanic.stairs

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockPlaceEvent

private val STAIR_BLOCKS = buildSet {
    val materials = listOf(
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo",
        "crimson", "warped", "stone", "cobblestone", "mossy_cobblestone",
        "stone_brick", "mossy_stone_brick", "granite", "polished_granite",
        "diorite", "polished_diorite", "andesite", "polished_andesite",
        "cobbled_deepslate", "polished_deepslate", "deepslate_brick", "deepslate_tile",
        "brick", "mud_brick", "nether_brick", "red_nether_brick", "quartz",
        "smooth_quartz", "purpur", "prismarine", "prismarine_brick", "dark_prismarine",
        "sandstone", "smooth_sandstone", "red_sandstone", "smooth_red_sandstone",
        "blackstone", "polished_blackstone", "polished_blackstone_brick",
        "end_stone_brick", "oxidized_cut_copper", "weathered_cut_copper",
        "exposed_cut_copper", "cut_copper", "waxed_oxidized_cut_copper",
        "waxed_weathered_cut_copper", "waxed_exposed_cut_copper", "waxed_cut_copper",
    )
    for (m in materials) add("minecraft:${m}_stairs")
}

class StairsModule : OrbitModule("stairs") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in STAIR_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val facing = event.block.getProperty("facing") ?: return@addListener

            val neighborPos = when (facing) {
                "north" -> instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ() - 1)
                "south" -> instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ() + 1)
                "east" -> instance.getBlock(pos.blockX() + 1, pos.blockY(), pos.blockZ())
                "west" -> instance.getBlock(pos.blockX() - 1, pos.blockY(), pos.blockZ())
                else -> return@addListener
            }

            if (neighborPos.name() in STAIR_BLOCKS) {
                val neighborFacing = neighborPos.getProperty("facing")
                if (neighborFacing != null && neighborFacing != facing) {
                    val shape = determineShape(facing, neighborFacing)
                    if (shape != null) {
                        instance.setBlock(pos, event.block.withProperty("shape", shape))
                    }
                }
            }
        }
    }

    private fun determineShape(myFacing: String, neighborFacing: String): String? {
        return when {
            myFacing == "north" && neighborFacing == "east" -> "outer_right"
            myFacing == "north" && neighborFacing == "west" -> "outer_left"
            myFacing == "south" && neighborFacing == "east" -> "outer_left"
            myFacing == "south" && neighborFacing == "west" -> "outer_right"
            myFacing == "east" && neighborFacing == "north" -> "outer_left"
            myFacing == "east" && neighborFacing == "south" -> "outer_right"
            myFacing == "west" && neighborFacing == "north" -> "outer_right"
            myFacing == "west" && neighborFacing == "south" -> "outer_left"
            else -> null
        }
    }
}
