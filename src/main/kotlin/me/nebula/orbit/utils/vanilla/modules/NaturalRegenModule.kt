package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.isCreativeOrSpectator
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.Instance

object NaturalRegenModule : VanillaModule {

    override val id = "natural-regen"
    override val description = "Regenerate health: fast regen (food=20, saturation>0, costs 1.5 sat/HP), slow regen (food>=18, 6.0 exhaustion/HP)"
    override val configParams = listOf(
        ConfigParam.IntParam("fastRegenTicks", "Ticks between fast regen (food=20, saturation>0)", 10, 1, 100),
        ConfigParam.IntParam("slowRegenTicks", "Ticks between slow regen (food>=18)", 80, 1, 200),
        ConfigParam.DoubleParam("fastRegenSatCost", "Saturation cost per fast regen heal", 1.5, 0.0, 20.0),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val fastRate = config.getInt("fastRegenTicks", 10)
        val slowRate = config.getInt("slowRegenTicks", 80)
        val fastSatCost = config.getDouble("fastRegenSatCost", 1.5).toFloat()

        val node = EventNode.all("vanilla-natural-regen")

        node.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            if (player.isCreativeOrSpectator) return@addListener
            val maxHp = player.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
            if (player.health >= maxHp) return@addListener

            val food = player.food
            val tick = player.aliveTicks

            if (food >= 20 && player.foodSaturation > 0 && tick % fastRate == 0L) {
                player.health = (player.health + 1f).coerceAtMost(maxHp)
                player.foodSaturation = (player.foodSaturation - fastSatCost).coerceAtLeast(0f)
            } else if (food >= 18 && tick % slowRate == 0L) {
                player.health = (player.health + 1f).coerceAtMost(maxHp)
                if (player.foodSaturation > 0) {
                    player.foodSaturation = (player.foodSaturation - 1f).coerceAtLeast(0f)
                } else if (player.food > 0) {
                    player.food = player.food - 1
                }
            }
        }

        return node
    }
}
