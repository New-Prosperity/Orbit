package me.nebula.orbit.mechanic.shovelpathing

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

private val SHOVEL_MATERIALS = setOf(
    Material.WOODEN_SHOVEL,
    Material.STONE_SHOVEL,
    Material.IRON_SHOVEL,
    Material.GOLDEN_SHOVEL,
    Material.DIAMOND_SHOVEL,
    Material.NETHERITE_SHOVEL,
)

private val PATHABLE_BLOCKS = setOf(
    Block.GRASS_BLOCK,
    Block.DIRT,
    Block.COARSE_DIRT,
    Block.MYCELIUM,
    Block.PODZOL,
    Block.ROOTED_DIRT,
)

class ShovelPathingModule : OrbitModule("shovel-pathing") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val player = event.player
            val held = player.getItemInMainHand()
            if (held.material() !in SHOVEL_MATERIALS) return@addListener

            val block = event.block
            if (PATHABLE_BLOCKS.none { block.compare(it) }) return@addListener

            val instance = player.instance ?: return@addListener
            instance.setBlock(event.blockPosition, Block.DIRT_PATH)

            player.playSound(
                Sound.sound(SoundEvent.ITEM_SHOVEL_FLATTEN.key(), Sound.Source.BLOCK, 1f, 1f),
                event.blockPosition.x() + 0.5,
                event.blockPosition.y() + 0.5,
                event.blockPosition.z() + 0.5,
            )
        }
    }
}
