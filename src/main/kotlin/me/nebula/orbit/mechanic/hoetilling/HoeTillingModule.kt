package me.nebula.orbit.mechanic.hoetilling

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent

private val HOE_MATERIALS = setOf(
    "minecraft:wooden_hoe",
    "minecraft:stone_hoe",
    "minecraft:iron_hoe",
    "minecraft:golden_hoe",
    "minecraft:diamond_hoe",
    "minecraft:netherite_hoe",
)

private val TILLABLE_BLOCKS = setOf(
    "minecraft:dirt",
    "minecraft:grass_block",
    "minecraft:dirt_path",
    "minecraft:coarse_dirt",
    "minecraft:rooted_dirt",
)

class HoeTillingModule : OrbitModule("hoe-tilling") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val held = event.player.getItemInMainHand()
            if (held.material().name() !in HOE_MATERIALS) return@addListener
            if (event.block.name() !in TILLABLE_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            instance.setBlock(pos, Block.FARMLAND)

            instance.playSound(
                Sound.sound(SoundEvent.ITEM_HOE_TILL.key(), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }
}
