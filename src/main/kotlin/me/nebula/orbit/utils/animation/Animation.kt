package me.nebula.orbit.utils.animation

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockChangePacket
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

data class BlockFrame(val positions: Map<Point, Block>)

class BlockAnimation(
    val instance: Instance,
    val frames: List<BlockFrame>,
    val intervalTicks: Int = 5,
    val loop: Boolean = false,
    val restoreOnComplete: Boolean = true,
    val packetOnly: Boolean = false,
) {
    private var task: Task? = null
    private var currentFrame = 0
    private val animatedPositions = mutableSetOf<Point>()
    private var originalBlocks = mutableMapOf<Point, Block>()

    fun start() {
        val allPositions = frames.flatMap { it.positions.keys }.distinct()
        animatedPositions.addAll(allPositions)

        if (restoreOnComplete && !packetOnly) {
            allPositions.forEach { pos ->
                originalBlocks.putIfAbsent(pos, instance.getBlock(pos))
            }
        }

        task = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(intervalTicks))
            .schedule()
    }

    fun stop() {
        task?.cancel()
        task = null
        if (restoreOnComplete) restore()
    }

    private fun tick() {
        if (currentFrame >= frames.size) {
            if (loop) {
                currentFrame = 0
            } else {
                stop()
                return
            }
        }

        val frame = frames[currentFrame]
        if (packetOnly) {
            frame.positions.forEach { (pos, block) ->
                instance.sendGroupedPacket(BlockChangePacket(pos, block))
            }
        } else {
            frame.positions.forEach { (pos, block) -> instance.setBlock(pos, block) }
        }
        currentFrame++
    }

    private fun restore() {
        if (packetOnly) {
            animatedPositions.forEach { pos ->
                instance.sendGroupedPacket(BlockChangePacket(pos, instance.getBlock(pos)))
            }
        } else {
            originalBlocks.forEach { (pos, block) -> instance.setBlock(pos, block) }
        }
        originalBlocks.clear()
        animatedPositions.clear()
    }
}

class EntityAnimation(
    val entity: Entity,
    val keyframes: List<Pos>,
    val intervalTicks: Int = 1,
    val loop: Boolean = false,
    val interpolate: Boolean = true,
) {
    private var task: Task? = null
    private var currentIndex = 0
    private var progress = 0f

    fun start() {
        task = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(intervalTicks))
            .schedule()
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun tick() {
        if (keyframes.size < 2) return

        val fromIndex = currentIndex % keyframes.size
        val toIndex = (currentIndex + 1) % keyframes.size

        if (!interpolate) {
            entity.teleport(keyframes[fromIndex])
            currentIndex++
            if (currentIndex >= keyframes.size && !loop) stop()
            return
        }

        val from = keyframes[fromIndex]
        val to = keyframes[toIndex]
        progress += 0.1f

        if (progress >= 1f) {
            entity.teleport(to)
            progress = 0f
            currentIndex++
            if (currentIndex >= keyframes.size - 1 && !loop) stop()
            return
        }

        val interpolated = Pos(
            lerp(from.x(), to.x(), progress.toDouble()),
            lerp(from.y(), to.y(), progress.toDouble()),
            lerp(from.z(), to.z(), progress.toDouble()),
            lerpAngle(from.yaw(), to.yaw(), progress),
            lerpAngle(from.pitch(), to.pitch(), progress),
        )
        entity.teleport(interpolated)
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = b - a
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return a + diff * t
    }
}

class BlockAnimationBuilder(val instance: Instance) {
    private val frames = mutableListOf<BlockFrame>()
    var intervalTicks: Int = 5
    var loop: Boolean = false
    var restoreOnComplete: Boolean = true
    var packetOnly: Boolean = false

    fun frame(block: MutableMap<Point, Block>.() -> Unit) {
        frames.add(BlockFrame(buildMap(block)))
    }

    fun build(): BlockAnimation = BlockAnimation(instance, frames.toList(), intervalTicks, loop, restoreOnComplete, packetOnly)
}

inline fun blockAnimation(instance: Instance, block: BlockAnimationBuilder.() -> Unit): BlockAnimation =
    BlockAnimationBuilder(instance).apply(block).build()

class EntityAnimationBuilder(val entity: Entity) {
    private val keyframes = mutableListOf<Pos>()
    var intervalTicks: Int = 1
    var loop: Boolean = false
    var interpolate: Boolean = true

    fun keyframe(pos: Pos) { keyframes.add(pos) }
    fun keyframe(x: Double, y: Double, z: Double) { keyframes.add(Pos(x, y, z)) }

    fun build(): EntityAnimation = EntityAnimation(entity, keyframes.toList(), intervalTicks, loop, interpolate)
}

inline fun entityAnimation(entity: Entity, block: EntityAnimationBuilder.() -> Unit): EntityAnimation =
    EntityAnimationBuilder(entity).apply(block).build()
