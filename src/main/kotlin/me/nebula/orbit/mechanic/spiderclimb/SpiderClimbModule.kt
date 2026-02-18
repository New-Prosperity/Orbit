package me.nebula.orbit.mechanic.spiderclimb

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private const val CLIMB_VELOCITY_Y = 0.2

class SpiderClimbModule : OrbitModule("spider-climb") {

    private var tickTask: Task? = null
    private val trackedSpiders: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(5))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedSpiders.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedSpiders.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.SPIDER) return@entityLoop
                trackedSpiders.add(entity)
            }
        }

        trackedSpiders.forEach { spider ->
            if (spider.isRemoved) return@forEach
            val instance = spider.instance ?: return@forEach

            if (isAdjacentToWall(spider, instance)) {
                spider.velocity = spider.velocity.withY(CLIMB_VELOCITY_Y)
            }
        }
    }

    private fun isAdjacentToWall(spider: Entity, instance: Instance): Boolean {
        val pos = spider.position
        val bx = pos.blockX()
        val by = pos.blockY()
        val bz = pos.blockZ()

        val offsets = arrayOf(
            intArrayOf(1, 0),
            intArrayOf(-1, 0),
            intArrayOf(0, 1),
            intArrayOf(0, -1),
        )

        for (offset in offsets) {
            val block = instance.getBlock(bx + offset[0], by, bz + offset[1])
            if (block.isSolid) return true
        }
        return false
    }
}
