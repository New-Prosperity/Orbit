package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import kotlin.math.abs

private val SLIME_VELOCITY_Y_TAG = Tag.Double("vanilla:slime_velocity_y")

object SlimeBlockModule : VanillaModule {

    override val id = "slime-block"
    override val description = "Bounce on slime blocks, negate fall damage"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-slime-block")

        node.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            if (player.gameMode == GameMode.SPECTATOR) return@addListener
            if (player.isSneaking) return@addListener

            val prevVelY = player.getTag(SLIME_VELOCITY_Y_TAG) ?: 0.0
            player.setTag(SLIME_VELOCITY_Y_TAG, player.velocity.y())

            if (!player.isOnGround) return@addListener
            if (prevVelY >= -0.5) return@addListener

            val inst = player.instance ?: return@addListener
            val below = inst.getBlock(player.position.blockX(), player.position.blockY() - 1, player.position.blockZ())
            if (!below.compare(Block.SLIME_BLOCK)) return@addListener

            val bounceVel = abs(prevVelY) * 0.8
            player.velocity = Vec(player.velocity.x(), bounceVel, player.velocity.z())
        }

        return node
    }
}
