package me.nebula.orbit.mechanic.noteblockinstrument

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

private val INSTRUMENT_MAP = mapOf(
    "minecraft:oak_planks" to "bass",
    "minecraft:spruce_planks" to "bass",
    "minecraft:birch_planks" to "bass",
    "minecraft:jungle_planks" to "bass",
    "minecraft:acacia_planks" to "bass",
    "minecraft:dark_oak_planks" to "bass",
    "minecraft:mangrove_planks" to "bass",
    "minecraft:cherry_planks" to "bass",
    "minecraft:bamboo_planks" to "bass",
    "minecraft:crimson_planks" to "bass",
    "minecraft:warped_planks" to "bass",
    "minecraft:sand" to "snare",
    "minecraft:red_sand" to "snare",
    "minecraft:gravel" to "snare",
    "minecraft:glass" to "hat",
    "minecraft:stone" to "basedrum",
    "minecraft:cobblestone" to "basedrum",
    "minecraft:deepslate" to "basedrum",
    "minecraft:blackstone" to "basedrum",
    "minecraft:netherrack" to "basedrum",
    "minecraft:obsidian" to "basedrum",
    "minecraft:gold_block" to "bell",
    "minecraft:clay" to "flute",
    "minecraft:packed_ice" to "chime",
    "minecraft:bone_block" to "xylophone",
    "minecraft:iron_block" to "iron_xylophone",
    "minecraft:soul_sand" to "cow_bell",
    "minecraft:pumpkin" to "didgeridoo",
    "minecraft:emerald_block" to "bit",
    "minecraft:hay_block" to "banjo",
    "minecraft:glowstone" to "pling",
)

class NoteBlockInstrumentModule : OrbitModule("noteblock-instrument") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:note_block") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val belowBlock = instance.getBlock(pos.add(0, -1, 0))

            val instrument = resolveInstrument(belowBlock)
            event.block = event.block.withProperty("instrument", instrument)
        }
    }

    companion object {

        fun resolveInstrument(belowBlock: Block): String =
            INSTRUMENT_MAP[belowBlock.name()] ?: resolveByCategory(belowBlock)

        private fun resolveByCategory(block: Block): String {
            val name = block.name()
            return when {
                name.endsWith("_planks") || name.endsWith("_log") || name.endsWith("_wood") -> "bass"
                name.endsWith("_sand") -> "snare"
                name.contains("glass") -> "hat"
                block.isSolid -> "basedrum"
                else -> "harp"
            }
        }
    }
}
