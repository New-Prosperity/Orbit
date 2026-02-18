package me.nebula.orbit.utils.blockbreakanimation

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.BlockBreakAnimationPacket
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val nextEntityId = AtomicInteger(Int.MAX_VALUE - 1000)

object BlockBreakAnimationManager {

    private val activeAnimations = ConcurrentHashMap<Long, Task>()

    fun startAnimation(instance: Instance, position: Point, durationTicks: Int): Long {
        require(durationTicks > 0) { "Duration must be positive" }
        val entityId = nextEntityId.decrementAndGet()
        val animationId = position.hashCode().toLong() shl 32 or entityId.toLong()
        val ticksPerStage = durationTicks / 10
        var currentStage = 0

        val task = MinecraftServer.getSchedulerManager()
            .buildTask {
                if (currentStage > 9) {
                    stopAnimation(animationId, instance, position, entityId)
                    return@buildTask
                }
                val packet = BlockBreakAnimationPacket(entityId, position, currentStage.toByte())
                instance.sendGroupedPacket(packet)
                currentStage++
            }
            .repeat(TaskSchedule.tick(ticksPerStage.coerceAtLeast(1)))
            .schedule()

        activeAnimations[animationId] = task
        return animationId
    }

    fun cancelAnimation(animationId: Long, instance: Instance, position: Point) {
        val task = activeAnimations.remove(animationId) ?: return
        task.cancel()
        val entityId = (animationId and 0xFFFFFFFFL).toInt()
        val packet = BlockBreakAnimationPacket(entityId, position, (-1).toByte())
        instance.sendGroupedPacket(packet)
    }

    fun cancelAll() {
        activeAnimations.values.forEach { it.cancel() }
        activeAnimations.clear()
    }

    private fun stopAnimation(animationId: Long, instance: Instance, position: Point, entityId: Int) {
        val task = activeAnimations.remove(animationId) ?: return
        task.cancel()
        val packet = BlockBreakAnimationPacket(entityId, position, (-1).toByte())
        instance.sendGroupedPacket(packet)
    }
}

fun Instance.showBreakProgress(position: Point, entityId: Int, stage: Byte) {
    val packet = BlockBreakAnimationPacket(entityId, position, stage)
    sendGroupedPacket(packet)
}

fun Instance.animateBlockBreak(position: Point, durationTicks: Int): Long =
    BlockBreakAnimationManager.startAnimation(this, position, durationTicks)
