package me.nebula.orbit.mechanic.ladder

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerMoveEvent

private val CLIMBABLE_BLOCKS = setOf(
    "minecraft:ladder", "minecraft:vine",
    "minecraft:twisting_vines", "minecraft:twisting_vines_plant",
    "minecraft:weeping_vines", "minecraft:weeping_vines_plant",
    "minecraft:cave_vines", "minecraft:cave_vines_plant",
)

class LadderModule : OrbitModule("ladder") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val block = instance.getBlock(player.position)

            if (block.name() !in CLIMBABLE_BLOCKS) return@addListener

            val velocity = player.velocity
            if (player.isSneaking) {
                player.velocity = Vec(velocity.x(), 0.0.coerceAtLeast(velocity.y()), velocity.z())
            } else if (velocity.y() < -0.15) {
                player.velocity = Vec(velocity.x(), -0.15, velocity.z())
            }
        }
    }
}
