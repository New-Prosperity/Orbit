package me.nebula.orbit.mechanic.slab

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.utils.customcontent.block.BlockStateAllocator
import net.minestom.server.event.player.PlayerBlockPlaceEvent

private val SLAB_BLOCKS = buildSet {
    val materials = listOf(
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo",
        "crimson", "warped", "stone", "smooth_stone", "sandstone", "cut_sandstone",
        "red_sandstone", "cut_red_sandstone", "cobblestone", "mossy_cobblestone",
        "stone_brick", "mossy_stone_brick", "granite", "polished_granite",
        "diorite", "polished_diorite", "andesite", "polished_andesite",
        "cobbled_deepslate", "polished_deepslate", "deepslate_brick", "deepslate_tile",
        "brick", "mud_brick", "nether_brick", "red_nether_brick", "quartz",
        "smooth_quartz", "purpur", "prismarine", "prismarine_brick", "dark_prismarine",
        "blackstone", "polished_blackstone", "polished_blackstone_brick",
        "end_stone_brick", "oxidized_cut_copper", "weathered_cut_copper",
        "exposed_cut_copper", "cut_copper", "waxed_oxidized_cut_copper",
        "waxed_weathered_cut_copper", "waxed_exposed_cut_copper", "waxed_cut_copper",
    )
    for (m in materials) add("minecraft:${m}_slab")
}

class SlabModule : OrbitModule("slab") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in SLAB_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val existing = instance.getBlock(pos)

            if (BlockStateAllocator.isAllocated(existing)) return@addListener

            if (existing.name() == event.block.name()) {
                val existingType = existing.getProperty("type") ?: "bottom"
                val placingType = event.block.getProperty("type") ?: "bottom"

                if ((existingType == "bottom" && placingType == "top") ||
                    (existingType == "top" && placingType == "bottom")
                ) {
                    event.isCancelled = true
                    instance.setBlock(pos, existing.withProperty("type", "double"))
                }
            }
        }
    }
}
