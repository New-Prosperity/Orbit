package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.isCreativeOrSpectator
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.instance.Instance

object CriticalHitsModule : VanillaModule {

    override val id = "critical-hits"
    override val description = "1.5x damage when attacking while falling and not on ground"
    override val configParams = listOf(
        ConfigParam.DoubleParam("multiplier", "Critical hit damage multiplier", 1.5, 1.0, 5.0),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val multiplier = config.getDouble("multiplier", 1.5)

        val node = EventNode.all("vanilla-critical-hits")

        node.addListener(EntityDamageEvent::class.java) { event ->
            val attacker = event.damage.attacker as? Player ?: return@addListener
            if (attacker.isCreativeOrSpectator) return@addListener
            if (attacker.isOnGround) return@addListener
            if (attacker.isFlying) return@addListener
            if (attacker.isSprinting) return@addListener
            if (attacker.velocity.y() >= 0) return@addListener

            event.damage.setAmount((event.damage.amount * multiplier).toFloat())
        }

        return node
    }
}
