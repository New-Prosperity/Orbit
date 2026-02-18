package me.nebula.orbit.mechanic.data

import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

data class DropEntry(val itemStack: ItemStack, val chance: Float = 1f)

data class BlockDrop(val drops: List<DropEntry>)

object BlockDropData {

    private val specialDrops = buildMap<String, BlockDrop> {
        drop("minecraft:stone", Material.COBBLESTONE)
        drop("minecraft:cobblestone", Material.COBBLESTONE)
        drop("minecraft:deepslate", Material.COBBLED_DEEPSLATE)
        drop("minecraft:coal_ore", Material.COAL)
        drop("minecraft:deepslate_coal_ore", Material.COAL)
        drop("minecraft:iron_ore", Material.RAW_IRON)
        drop("minecraft:deepslate_iron_ore", Material.RAW_IRON)
        drop("minecraft:copper_ore", Material.RAW_COPPER)
        drop("minecraft:deepslate_copper_ore", Material.RAW_COPPER)
        drop("minecraft:gold_ore", Material.RAW_GOLD)
        drop("minecraft:deepslate_gold_ore", Material.RAW_GOLD)
        drop("minecraft:diamond_ore", Material.DIAMOND)
        drop("minecraft:deepslate_diamond_ore", Material.DIAMOND)
        drop("minecraft:lapis_ore", Material.LAPIS_LAZULI, 6)
        drop("minecraft:deepslate_lapis_ore", Material.LAPIS_LAZULI, 6)
        drop("minecraft:redstone_ore", Material.REDSTONE, 4)
        drop("minecraft:deepslate_redstone_ore", Material.REDSTONE, 4)
        drop("minecraft:emerald_ore", Material.EMERALD)
        drop("minecraft:deepslate_emerald_ore", Material.EMERALD)
        drop("minecraft:nether_quartz_ore", Material.QUARTZ)
        drop("minecraft:nether_gold_ore", Material.GOLD_NUGGET, 4)
        drop("minecraft:glowstone", Material.GLOWSTONE_DUST, 3)

        put("minecraft:gravel", BlockDrop(listOf(
            DropEntry(ItemStack.of(Material.FLINT), 0.1f),
            DropEntry(ItemStack.of(Material.GRAVEL), 0.9f),
        )))

        drop("minecraft:grass_block", Material.DIRT)
        drop("minecraft:mycelium", Material.DIRT)

        noDrops("minecraft:glass")
        noDrops("minecraft:glass_pane")
        noDrops("minecraft:ice")
        noDrops("minecraft:infested_stone")
        noDrops("minecraft:infested_cobblestone")
        noDrops("minecraft:infested_stone_bricks")
    }

    operator fun get(block: Block): BlockDrop? =
        specialDrops[block.key().asString()] ?: defaultDrop(block)

    private fun defaultDrop(block: Block): BlockDrop? {
        val material = block.registry()?.material() ?: return null
        return BlockDrop(listOf(DropEntry(ItemStack.of(material))))
    }

    private fun MutableMap<String, BlockDrop>.drop(key: String, material: Material, count: Int = 1) {
        put(key, BlockDrop(listOf(DropEntry(ItemStack.of(material, count)))))
    }

    private fun MutableMap<String, BlockDrop>.noDrops(key: String) {
        put(key, BlockDrop(emptyList()))
    }
}
