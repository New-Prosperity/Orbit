package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerPickBlockEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

object BlockPickModule : VanillaModule {

    override val id = "block-pick"
    override val description = "Creative mode block picking (middle click)"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-block-pick")

        node.addListener(PlayerPickBlockEvent::class.java) { event ->
            if (event.player.gameMode != GameMode.CREATIVE) return@addListener
            val block = event.block
            val material = Material.fromKey(block.name()) ?: return@addListener
            event.player.setItemInMainHand(ItemStack.of(material))
        }

        return node
    }
}
