package me.nebula.orbit.utils.podium

import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class PodiumEntry(
    val player: Player,
    val position: Pos,
    val place: Int,
)

class PodiumDisplay @PublishedApi internal constructor(
    private val instance: Instance,
    private val entries: List<PodiumEntry>,
    private val displayDuration: Duration,
) {

    private val placedBlocks = mutableListOf<Pos>()
    private val spawnedEntities = mutableListOf<Entity>()
    private var cleanupTask: Task? = null

    fun show() {
        entries.forEach { entry ->
            val (block, height) = when (entry.place) {
                1 -> Block.GOLD_BLOCK to 3
                2 -> Block.IRON_BLOCK to 2
                3 -> Block.COPPER_BLOCK to 1
                else -> return@forEach
            }

            for (y in 0 until height) {
                val blockPos = Pos(entry.position.x(), entry.position.y() + y, entry.position.z())
                instance.setBlock(blockPos, block)
                placedBlocks.add(blockPos)
            }

            val teleportPos = Pos(
                entry.position.x() + 0.5,
                entry.position.y() + height,
                entry.position.z() + 0.5,
                entry.position.yaw(),
                entry.position.pitch(),
            )
            entry.player.teleport(teleportPos)

            if (entry.place == 1) {
                launchFirework(teleportPos)
            }
        }

        entries.forEach { entry ->
            entry.player.playSound(
                Sound.sound(SoundEvent.UI_TOAST_CHALLENGE_COMPLETE.key(), Sound.Source.PLAYER, 1f, 1f)
            )
        }

        cleanupTask = MinecraftServer.getSchedulerManager()
            .buildTask { cleanup() }
            .delay(TaskSchedule.millis(displayDuration.inWholeMilliseconds))
            .schedule()
    }

    fun cleanup() {
        cleanupTask?.cancel()
        cleanupTask = null
        placedBlocks.forEach { instance.setBlock(it, Block.AIR) }
        placedBlocks.clear()
        spawnedEntities.forEach { if (!it.isRemoved) it.remove() }
        spawnedEntities.clear()
    }

    private fun launchFirework(position: Pos) {
        val entity = Entity(EntityType.FIREWORK_ROCKET)
        entity.velocity = Vec(0.0, 20.0, 0.0)
        entity.setInstance(instance, position)
        spawnedEntities.add(entity)

        MinecraftServer.getSchedulerManager()
            .buildTask { if (!entity.isRemoved) entity.remove() }
            .delay(TaskSchedule.tick(30))
            .schedule()
    }
}

class PodiumBuilder @PublishedApi internal constructor(private val instance: Instance) {

    @PublishedApi internal val entries = mutableListOf<PodiumEntry>()
    @PublishedApi internal var duration: Duration = 10.seconds

    fun first(player: Player, position: Pos) { entries.add(PodiumEntry(player, position, 1)) }
    fun second(player: Player, position: Pos) { entries.add(PodiumEntry(player, position, 2)) }
    fun third(player: Player, position: Pos) { entries.add(PodiumEntry(player, position, 3)) }
    fun displayDuration(duration: Duration) { this.duration = duration }

    @PublishedApi internal fun build(): PodiumDisplay = PodiumDisplay(instance, entries.toList(), duration)
}

inline fun podium(instance: Instance, block: PodiumBuilder.() -> Unit): PodiumDisplay =
    PodiumBuilder(instance).apply(block).build()
