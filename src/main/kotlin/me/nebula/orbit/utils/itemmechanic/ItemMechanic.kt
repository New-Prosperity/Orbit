package me.nebula.orbit.utils.itemmechanic

import me.nebula.orbit.utils.itemresolver.ITEM_ID_TAG
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import java.util.concurrent.ConcurrentHashMap

interface ItemMechanic {
    fun onUse(player: Player) {}
    fun onAttack(attacker: Player, target: Player, event: EntityDamageEvent) {}
    fun onHurt(victim: Player, attacker: Player?, event: EntityDamageEvent) {}
    fun onBlockBreak(player: Player, event: PlayerBlockBreakEvent) {}
}

object ItemMechanicRegistry {

    private val mechanics = ConcurrentHashMap<String, ItemMechanic>()

    fun register(id: String, mechanic: ItemMechanic) {
        mechanics[id] = mechanic
    }

    fun unregister(id: String): ItemMechanic? = mechanics.remove(id)

    operator fun get(id: String): ItemMechanic? = mechanics[id]

    fun resolve(stack: ItemStack): ItemMechanic? {
        val id = stack.getTag(ITEM_ID_TAG) ?: return null
        return mechanics[id]
    }

    fun clear() = mechanics.clear()
}

object ItemMechanicListener {

    private var node: EventNode<*>? = null

    fun install() {
        check(node == null) { "ItemMechanicListener already installed" }
        val n = EventNode.all("item-mechanics")

        n.addListener(PlayerUseItemEvent::class.java) { event ->
            val mechanic = ItemMechanicRegistry.resolve(event.itemStack) ?: return@addListener
            mechanic.onUse(event.player)
        }

        n.addListener(EntityDamageEvent::class.java) { event ->
            val damage = event.damage as? EntityDamage ?: return@addListener
            val attacker = damage.source as? Player ?: return@addListener
            val target = event.entity as? Player ?: return@addListener

            val attackMechanic = ItemMechanicRegistry.resolve(attacker.itemInMainHand)
            attackMechanic?.onAttack(attacker, target, event)

            val armorSlots = listOf(
                target.helmet, target.chestplate, target.leggings, target.boots,
            )
            val hurtMechanics = armorSlots.mapNotNull { ItemMechanicRegistry.resolve(it) }.distinct()
            val heldMechanic = ItemMechanicRegistry.resolve(target.itemInMainHand)
            val allHurtMechanics = if (heldMechanic != null) hurtMechanics + heldMechanic else hurtMechanics
            allHurtMechanics.distinct().forEach { it.onHurt(target, attacker, event) }
        }

        n.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val mechanic = ItemMechanicRegistry.resolve(event.player.itemInMainHand) ?: return@addListener
            mechanic.onBlockBreak(event.player, event)
        }

        MinecraftServer.getGlobalEventHandler().addChild(n)
        node = n
    }

    fun uninstall() {
        node?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        node = null
        ItemMechanicRegistry.clear()
    }
}

class ItemMechanicBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var onUse: ((Player) -> Unit)? = null
    @PublishedApi internal var onAttack: ((Player, Player, EntityDamageEvent) -> Unit)? = null
    @PublishedApi internal var onHurt: ((Player, Player?, EntityDamageEvent) -> Unit)? = null
    @PublishedApi internal var onBlockBreak: ((Player, PlayerBlockBreakEvent) -> Unit)? = null

    fun onUse(action: (Player) -> Unit) { onUse = action }
    fun onAttack(action: (attacker: Player, target: Player, event: EntityDamageEvent) -> Unit) { onAttack = action }
    fun onHurt(action: (victim: Player, attacker: Player?, event: EntityDamageEvent) -> Unit) { onHurt = action }
    fun onBlockBreak(action: (Player, PlayerBlockBreakEvent) -> Unit) { onBlockBreak = action }

    @PublishedApi internal fun build(): ItemMechanic = object : ItemMechanic {
        override fun onUse(player: Player) { this@ItemMechanicBuilder.onUse?.invoke(player) }
        override fun onAttack(attacker: Player, target: Player, event: EntityDamageEvent) { this@ItemMechanicBuilder.onAttack?.invoke(attacker, target, event) }
        override fun onHurt(victim: Player, attacker: Player?, event: EntityDamageEvent) { this@ItemMechanicBuilder.onHurt?.invoke(victim, attacker, event) }
        override fun onBlockBreak(player: Player, event: PlayerBlockBreakEvent) { this@ItemMechanicBuilder.onBlockBreak?.invoke(player, event) }
    }
}

inline fun itemMechanic(id: String, block: ItemMechanicBuilder.() -> Unit): ItemMechanic {
    val mechanic = ItemMechanicBuilder().apply(block).build()
    ItemMechanicRegistry.register(id, mechanic)
    return mechanic
}
