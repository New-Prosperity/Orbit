package me.nebula.orbit.mechanic.axestripping

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent

private val STRIP_MAP: Map<Block, Block> = mapOf(
    Block.OAK_LOG to Block.STRIPPED_OAK_LOG,
    Block.SPRUCE_LOG to Block.STRIPPED_SPRUCE_LOG,
    Block.BIRCH_LOG to Block.STRIPPED_BIRCH_LOG,
    Block.JUNGLE_LOG to Block.STRIPPED_JUNGLE_LOG,
    Block.ACACIA_LOG to Block.STRIPPED_ACACIA_LOG,
    Block.DARK_OAK_LOG to Block.STRIPPED_DARK_OAK_LOG,
    Block.MANGROVE_LOG to Block.STRIPPED_MANGROVE_LOG,
    Block.CHERRY_LOG to Block.STRIPPED_CHERRY_LOG,
    Block.CRIMSON_STEM to Block.STRIPPED_CRIMSON_STEM,
    Block.WARPED_STEM to Block.STRIPPED_WARPED_STEM,
    Block.OAK_WOOD to Block.STRIPPED_OAK_WOOD,
    Block.SPRUCE_WOOD to Block.STRIPPED_SPRUCE_WOOD,
    Block.BIRCH_WOOD to Block.STRIPPED_BIRCH_WOOD,
    Block.JUNGLE_WOOD to Block.STRIPPED_JUNGLE_WOOD,
    Block.ACACIA_WOOD to Block.STRIPPED_ACACIA_WOOD,
    Block.DARK_OAK_WOOD to Block.STRIPPED_DARK_OAK_WOOD,
    Block.MANGROVE_WOOD to Block.STRIPPED_MANGROVE_WOOD,
    Block.CHERRY_WOOD to Block.STRIPPED_CHERRY_WOOD,
    Block.CRIMSON_HYPHAE to Block.STRIPPED_CRIMSON_HYPHAE,
    Block.WARPED_HYPHAE to Block.STRIPPED_WARPED_HYPHAE,
)

class AxeStrippingModule : OrbitModule("axe-stripping") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val held = event.player.getItemInMainHand()
            if (!held.material().name().endsWith("_axe")) return@addListener

            val block = event.block
            val stripped = STRIP_MAP.entries.firstOrNull { (source, _) ->
                block.name() == source.name()
            }?.value ?: return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val properties = block.properties()
            var result = stripped
            for ((key, value) in properties) {
                runCatching { result = result.withProperty(key, value) }
            }

            instance.setBlock(pos, result)

            instance.playSound(
                Sound.sound(SoundEvent.ITEM_AXE_STRIP.key(), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }
}
