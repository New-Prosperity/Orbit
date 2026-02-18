package me.nebula.orbit.mechanic.targetblock

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent
import net.minestom.server.timer.TaskSchedule

class TargetBlockModule : OrbitModule("target-block") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(ProjectileCollideWithBlockEvent::class.java) { event ->
            val instance = event.entity.instance ?: return@addListener
            val pos = event.collisionPosition
            val blockPos = net.minestom.server.coordinate.Vec(
                pos.blockX().toDouble(), pos.blockY().toDouble(), pos.blockZ().toDouble()
            )
            val block = instance.getBlock(blockPos)

            if (block.name() != "minecraft:target") return@addListener

            val centerX = blockPos.x() + 0.5
            val centerY = blockPos.y() + 0.5
            val centerZ = blockPos.z() + 0.5

            val dx = pos.x() - centerX
            val dy = pos.y() - centerY
            val dz = pos.z() - centerZ
            val distance = Math.sqrt(dx * dx + dy * dy + dz * dz)

            val power = (15 - (distance * 15 / 0.7).toInt()).coerceIn(1, 15)

            instance.setBlock(blockPos, block.withProperty("power", power.toString()))

            MinecraftServer.getSchedulerManager().buildTask {
                val current = instance.getBlock(blockPos)
                if (current.name() == "minecraft:target") {
                    instance.setBlock(blockPos, current.withProperty("power", "0"))
                }
            }.delay(TaskSchedule.tick(8)).schedule()
        }
    }
}
