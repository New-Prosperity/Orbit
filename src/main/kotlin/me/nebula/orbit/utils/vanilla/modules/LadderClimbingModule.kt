package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.Instance

private val CLIMBABLE_NAMES = setOf(
    "minecraft:ladder", "minecraft:vine",
    "minecraft:twisting_vines", "minecraft:twisting_vines_plant",
    "minecraft:weeping_vines", "minecraft:weeping_vines_plant",
    "minecraft:cave_vines", "minecraft:cave_vines_plant",
    "minecraft:scaffolding",
)

object LadderClimbingModule : VanillaModule {

    override val id = "ladder-climbing"
    override val description = "Players can climb ladders, vines, and scaffolding"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-ladder-climbing")

        node.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            if (player.gameMode == GameMode.SPECTATOR) return@addListener

            val inst = player.instance ?: return@addListener
            val block = inst.getBlock(player.position.blockX(), player.position.blockY(), player.position.blockZ())

            if (block.name() in CLIMBABLE_NAMES) {
                val vel = player.velocity
                val clampedY = vel.y().coerceAtLeast(-0.15)
                if (player.isSneaking && vel.y() < 0) {
                    player.velocity = Vec(vel.x() * 0.7, 0.0, vel.z() * 0.7)
                } else {
                    player.velocity = Vec(vel.x() * 0.7, clampedY, vel.z() * 0.7)
                }
            }
        }

        return node
    }
}
