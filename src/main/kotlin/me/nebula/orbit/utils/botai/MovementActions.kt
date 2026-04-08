package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player

class WalkTo(private val target: Point) : BotAction {
    override var isComplete = false
        private set

    override fun tick(player: Player) {
        val distSq = player.position.distanceSquared(target)
        if (distSq <= REACH_DISTANCE_SQ) {
            isComplete = true
            player.velocity = Vec(0.0, player.velocity.y(), 0.0)
            return
        }
        val jitter = BotAI.skillLevels[player.uuid]?.movementJitter ?: 0f
        BotMovement.moveToward(player, target, false, jitter)
    }

    override fun cancel(player: Player) {
        player.velocity = Vec(0.0, player.velocity.y(), 0.0)
    }
}

class SprintTo(private val target: Point) : BotAction {
    override var isComplete = false
        private set

    override fun tick(player: Player) {
        val distSq = player.position.distanceSquared(target)
        if (distSq <= REACH_DISTANCE_SQ) {
            isComplete = true
            player.isSprinting = false
            player.velocity = Vec(0.0, player.velocity.y(), 0.0)
            return
        }
        val jitter = BotAI.skillLevels[player.uuid]?.movementJitter ?: 0f
        BotMovement.moveToward(player, target, true, jitter)
    }

    override fun cancel(player: Player) {
        player.isSprinting = false
        player.velocity = Vec(0.0, player.velocity.y(), 0.0)
    }
}

class LookAt(private val target: Point) : BotAction {
    override var isComplete = false
        private set

    override fun tick(player: Player) {
        BotMovement.lookAt(player, target)
        isComplete = true
    }
}

class Wait(private val ticks: Int) : BotAction {
    override var isComplete = false
        private set
    private var elapsed = 0

    override fun tick(player: Player) {
        elapsed++
        if (elapsed >= ticks) isComplete = true
    }
}
