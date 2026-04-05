package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.EntityStatusPacket

object TotemModule : VanillaModule {

    override val id = "totem-of-undying"
    override val description = "Totem of Undying prevents lethal damage, restores health, plays animation"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-totem")

        node.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            val damage = event.damage.amount

            if (player.health - damage > 0) return@addListener

            val hand = findTotemHand(player) ?: return@addListener

            event.isCancelled = true

            if (hand == PlayerHand.MAIN) player.setItemInMainHand(ItemStack.AIR)
            else player.setItemInOffHand(ItemStack.AIR)

            val maxHp = player.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
            player.health = 1f.coerceAtMost(maxHp)
            player.setFireTicks(0)

            val statusPacket = EntityStatusPacket(player.entityId, 35)
            player.sendPacketToViewersAndSelf(statusPacket)
        }

        return node
    }

    private fun findTotemHand(player: Player): PlayerHand? {
        if (player.itemInMainHand.material() == Material.TOTEM_OF_UNDYING) return PlayerHand.MAIN
        if (player.itemInOffHand.material() == Material.TOTEM_OF_UNDYING) return PlayerHand.OFF
        return null
    }
}
