package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack

object ToolDurabilityModule : VanillaModule {

    override val id = "tool-durability"
    override val description = "Tools and weapons lose durability on use, break when depleted"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-tool-durability")

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.player.gameMode == GameMode.CREATIVE) return@addListener
            damageTool(event.player, 1)
        }

        node.addListener(EntityDamageEvent::class.java) { event ->
            val attacker = event.damage.attacker as? Player ?: return@addListener
            if (attacker.gameMode == GameMode.CREATIVE) return@addListener
            damageTool(attacker, 1)
        }

        return node
    }

    private fun damageTool(player: Player, amount: Int) {
        val item = player.itemInMainHand
        if (item.isAir) return

        val maxDamage = item.get(DataComponents.MAX_DAMAGE) ?: return
        if (item.has(DataComponents.UNBREAKABLE)) return

        val currentDamage = item.get(DataComponents.DAMAGE) ?: 0
        val newDamage = currentDamage + amount

        if (newDamage >= maxDamage) {
            player.setItemInMainHand(ItemStack.AIR)
        } else {
            player.setItemInMainHand(item.with(DataComponents.DAMAGE, newDamage))
        }
    }
}
