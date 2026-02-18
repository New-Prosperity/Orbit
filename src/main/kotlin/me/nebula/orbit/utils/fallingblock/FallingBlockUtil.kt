package me.nebula.orbit.utils.fallingblock

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule

fun spawnFallingBlock(
    instance: Instance,
    position: Point,
    block: Block,
    velocity: Vec = Vec.ZERO,
    onLand: (Instance, Point, Block) -> Unit = { inst, pos, b -> inst.setBlock(pos, b) },
): Entity {
    val entity = Entity(EntityType.FALLING_BLOCK)
    entity.setNoGravity(false)
    entity.velocity = velocity
    entity.setInstance(instance, Vec(position.x() + 0.5, position.y().toDouble(), position.z() + 0.5))

    entity.scheduler().buildTask {
        if (entity.isOnGround || entity.velocity.y().let { it > -0.01 && it < 0.01 }) {
            val landPos = entity.position
            entity.remove()
            onLand(instance, landPos, block)
        }
    }.repeat(TaskSchedule.tick(1)).schedule()

    entity.scheduler().buildTask {
        if (!entity.isRemoved) entity.remove()
    }.delay(TaskSchedule.tick(600)).schedule()

    return entity
}

fun launchBlock(
    instance: Instance,
    position: Point,
    block: Block,
    direction: Vec,
    speed: Double = 1.0,
    onLand: (Instance, Point, Block) -> Unit = { inst, pos, b -> inst.setBlock(pos, b) },
): Entity {
    val velocity = direction.normalize().mul(speed * 20.0)
    return spawnFallingBlock(instance, position, block, velocity, onLand)
}
