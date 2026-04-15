package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

private val HOE_MATERIALS = setOf(
    Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
    Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE,
)

private val TILLABLE_BLOCKS = setOf("minecraft:dirt", "minecraft:grass_block", "minecraft:dirt_path")

object FarmlandModule : VanillaModule {

    override val id = "farmland"
    override val description = "Hoe dirt/grass into farmland, right-click dirt path creation"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-farmland")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val tool = event.player.itemInMainHand.material()
            if (tool !in HOE_MATERIALS) return@addListener

            val blockName = event.block.name()
            if (blockName !in TILLABLE_BLOCKS) return@addListener

            val above = event.instance.getBlock(
                event.blockPosition.blockX(),
                event.blockPosition.blockY() + 1,
                event.blockPosition.blockZ(),
            )
            if (!above.isAir) return@addListener

            val result = if (blockName == "minecraft:dirt_path") Block.FARMLAND else Block.FARMLAND
            event.instance.setBlock(event.blockPosition, result)
            event.player.playSound(SoundEvent.ITEM_HOE_TILL)
        }

        return node
    }
}
