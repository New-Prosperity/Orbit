package me.nebula.orbit.mechanic.bamboomosaic

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent

private val BAMBOO_STRIP_MAP: Map<String, Block> = mapOf(
    "minecraft:bamboo_block" to Block.STRIPPED_BAMBOO_BLOCK,
)

class BambooMosaicModule : OrbitModule("bamboo-mosaic") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val held = event.player.getItemInMainHand()
            if (!held.material().name().endsWith("_axe")) return@addListener

            val block = event.block
            val stripped = BAMBOO_STRIP_MAP[block.name()] ?: return@addListener

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
