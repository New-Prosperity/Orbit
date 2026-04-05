package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.isCreativeOrSpectator
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.Instance

object VoidDamageModule : VanillaModule {

    override val id = "void-damage"
    override val description = "Damage when falling below the world"
    override val configParams = listOf(
        ConfigParam.DoubleParam("threshold", "Y level that triggers damage", -64.0, -512.0, 0.0),
        ConfigParam.DoubleParam("damage", "Damage per application", 4.0, 0.1, 100.0),
        ConfigParam.IntParam("tickRate", "Ticks between damage applications", 10, 1, 100),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val threshold = config.getDouble("threshold", -64.0)
        val damage = config.getDouble("damage", 4.0).toFloat()
        val tickRate = config.getInt("tickRate", 10)

        val node = EventNode.all("vanilla-void-damage")

        node.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            if (player.isCreativeOrSpectator) return@addListener
            if (player.position.y() < threshold && player.aliveTicks % tickRate == 0L) {
                player.damage(DamageType.OUT_OF_WORLD, damage)
            }
        }

        return node
    }
}
