package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player

class OpenContainer(private val pos: Point) : BotAction {
    override var isComplete = false
        private set

    override fun tick(player: Player) {
        val instance = player.instance ?: run { isComplete = true; return }
        val distSq = player.position.distanceSquared(blockCenter(pos))
        if (distSq > BLOCK_REACH_SQ) {
            BotMovement.moveToward(player, blockCenter(pos), false)
            return
        }
        BotMovement.lookAt(player, blockMidpoint(pos))
        val block = instance.getBlock(pos)
        fireInteractEvent(player, pos, block)
        isComplete = true
    }
}
