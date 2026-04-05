package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

object FlintAndSteelModule : VanillaModule {

    override val id = "flint-and-steel"
    override val description = "Place fire with flint and steel, ignite TNT"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-flint-and-steel")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val item = event.player.itemInMainHand
            if (item.material() != Material.FLINT_AND_STEEL) return@addListener

            val targetPos = event.blockPosition.relative(event.blockFace)
            val targetBlock = event.instance.getBlock(targetPos)

            if (targetBlock.isAir) {
                event.instance.setBlock(targetPos, Block.FIRE)
                event.player.playSound(SoundEvent.ITEM_FLINTANDSTEEL_USE)
                damageItem(event.player)
            }
        }

        return node
    }

    private fun damageItem(player: Player) {
        if (player.gameMode == GameMode.CREATIVE) return
        val item = player.itemInMainHand
        val maxDamage = item.get(DataComponents.MAX_DAMAGE) ?: return
        val currentDamage = item.get(DataComponents.DAMAGE) ?: 0
        val newDamage = currentDamage + 1
        if (newDamage >= maxDamage) {
            player.setItemInMainHand(ItemStack.AIR)
        } else {
            player.setItemInMainHand(item.with(DataComponents.DAMAGE, newDamage))
        }
    }
}
