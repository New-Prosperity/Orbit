package me.nebula.orbit.mechanic.copperoxidation

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

private val OXIDATION_CHAIN = mapOf(
    "minecraft:copper_block" to "minecraft:exposed_copper",
    "minecraft:exposed_copper" to "minecraft:weathered_copper",
    "minecraft:weathered_copper" to "minecraft:oxidized_copper",

    "minecraft:cut_copper" to "minecraft:exposed_cut_copper",
    "minecraft:exposed_cut_copper" to "minecraft:weathered_cut_copper",
    "minecraft:weathered_cut_copper" to "minecraft:oxidized_cut_copper",

    "minecraft:cut_copper_stairs" to "minecraft:exposed_cut_copper_stairs",
    "minecraft:exposed_cut_copper_stairs" to "minecraft:weathered_cut_copper_stairs",
    "minecraft:weathered_cut_copper_stairs" to "minecraft:oxidized_cut_copper_stairs",

    "minecraft:cut_copper_slab" to "minecraft:exposed_cut_copper_slab",
    "minecraft:exposed_cut_copper_slab" to "minecraft:weathered_cut_copper_slab",
    "minecraft:weathered_cut_copper_slab" to "minecraft:oxidized_cut_copper_slab",
)

private val DEOXIDATION_CHAIN = OXIDATION_CHAIN.entries.associate { (k, v) -> v to k }

private val WAX_MAP = buildMap {
    listOf(
        "minecraft:copper_block" to "minecraft:waxed_copper_block",
        "minecraft:exposed_copper" to "minecraft:waxed_exposed_copper",
        "minecraft:weathered_copper" to "minecraft:waxed_weathered_copper",
        "minecraft:oxidized_copper" to "minecraft:waxed_oxidized_copper",
        "minecraft:cut_copper" to "minecraft:waxed_cut_copper",
        "minecraft:exposed_cut_copper" to "minecraft:waxed_exposed_cut_copper",
        "minecraft:weathered_cut_copper" to "minecraft:waxed_weathered_cut_copper",
        "minecraft:oxidized_cut_copper" to "minecraft:waxed_oxidized_cut_copper",
    ).forEach { (unwaxed, waxed) -> put(unwaxed, waxed) }
}

private val UNWAX_MAP = WAX_MAP.entries.associate { (k, v) -> v to k }

class CopperOxidationModule : OrbitModule("copper-oxidation") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            val blockName = block.name()
            val held = event.player.getItemInMainHand()
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            if (held.material() == Material.HONEYCOMB) {
                val waxed = WAX_MAP[blockName] ?: return@addListener
                val resolved = Block.fromKey(waxed) ?: return@addListener
                instance.setBlock(pos, resolved)
                instance.playSound(
                    Sound.sound(SoundEvent.ITEM_HONEYCOMB_WAX_ON.key(), Sound.Source.BLOCK, 1f, 1f),
                    pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
                )
                return@addListener
            }

            if (held.material().name().endsWith("_axe")) {
                val unwaxed = UNWAX_MAP[blockName]
                if (unwaxed != null) {
                    val resolved = Block.fromKey(unwaxed) ?: return@addListener
                    instance.setBlock(pos, resolved)
                    instance.playSound(
                        Sound.sound(SoundEvent.ITEM_AXE_SCRAPE.key(), Sound.Source.BLOCK, 1f, 1f),
                        pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
                    )
                    return@addListener
                }

                val deoxidized = DEOXIDATION_CHAIN[blockName]
                if (deoxidized != null) {
                    val resolved = Block.fromKey(deoxidized) ?: return@addListener
                    instance.setBlock(pos, resolved)
                    instance.playSound(
                        Sound.sound(SoundEvent.ITEM_AXE_SCRAPE.key(), Sound.Source.BLOCK, 1f, 1f),
                        pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
                    )
                }
            }
        }
    }
}
