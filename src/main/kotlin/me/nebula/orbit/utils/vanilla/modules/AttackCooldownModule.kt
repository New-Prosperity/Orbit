package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag

private val LAST_ATTACK_TICK_TAG = Tag.Long("vanilla:last_attack_tick")

object AttackCooldownModule : VanillaModule {

    override val id = "attack-cooldown"
    override val description = "1.9+ attack cooldown — damage scales with cooldown progress"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-attack-cooldown")

        node.addListener(EntityDamageEvent::class.java) { event ->
            val attacker = event.damage.attacker as? Player ?: return@addListener

            val attackSpeed = attacker.getAttributeValue(Attribute.ATTACK_SPEED)
            val cooldownTicks = (20.0 / attackSpeed).toLong().coerceAtLeast(1)
            val currentTick = attacker.aliveTicks
            val lastTick = attacker.getTag(LAST_ATTACK_TICK_TAG) ?: 0L
            val elapsed = currentTick - lastTick

            val progress = (elapsed.toDouble() / cooldownTicks).coerceIn(0.0, 1.0)
            val scaledProgress = 0.2 + progress * progress * 0.8

            if (scaledProgress < 1.0) {
                event.damage.setAmount((event.damage.amount * scaledProgress).toFloat())
            }

            attacker.setTag(LAST_ATTACK_TICK_TAG, currentTick)
        }

        return node
    }
}
