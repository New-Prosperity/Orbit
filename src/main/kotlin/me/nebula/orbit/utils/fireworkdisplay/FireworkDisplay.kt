package me.nebula.orbit.utils.fireworkdisplay

import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.color.Color
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

enum class FireworkShape {
    BALL,
    BALL_LARGE,
    STAR,
    BURST,
    CREEPER,
}

class FireworkConfig @PublishedApi internal constructor(
    val position: Pos,
    val colors: List<Color>,
    val fadeColors: List<Color>,
    val shape: FireworkShape,
    val trail: Boolean,
    val flicker: Boolean,
    val flightTicks: Int,
    val velocity: Vec,
)

class FireworkLaunchBuilder @PublishedApi internal constructor(private val position: Pos) {

    @PublishedApi internal val colors = mutableListOf<Color>()
    @PublishedApi internal val fadeColors = mutableListOf<Color>()
    @PublishedApi internal var shape: FireworkShape = FireworkShape.BALL
    @PublishedApi internal var trail: Boolean = false
    @PublishedApi internal var flicker: Boolean = false
    @PublishedApi internal var flightTicks: Int = 30
    @PublishedApi internal var velocity: Vec = Vec(0.0, 20.0, 0.0)

    fun color(color: NamedTextColor) { colors.add(Color(color.value())) }
    fun color(r: Int, g: Int, b: Int) { colors.add(Color(r, g, b)) }
    fun color(color: Color) { colors.add(color) }
    fun fadeColor(color: NamedTextColor) { fadeColors.add(Color(color.value())) }
    fun fadeColor(r: Int, g: Int, b: Int) { fadeColors.add(Color(r, g, b)) }
    fun shape(shape: FireworkShape) { this.shape = shape }
    fun trail() { trail = true }
    fun flicker() { flicker = true }
    fun flightTicks(ticks: Int) { flightTicks = ticks }
    fun velocity(x: Double, y: Double, z: Double) { velocity = Vec(x, y, z) }

    @PublishedApi internal fun build(): FireworkConfig = FireworkConfig(
        position,
        colors.ifEmpty { listOf(Color(255, 255, 255)) },
        fadeColors.toList(),
        shape,
        trail,
        flicker,
        flightTicks,
        velocity,
    )
}

data class ScheduledLaunch(val tickOffset: Int, val firework: FireworkConfig)

class ShowMomentBuilder @PublishedApi internal constructor(private val tickOffset: Int) {

    @PublishedApi internal val launches = mutableListOf<FireworkConfig>()

    inline fun launch(position: Pos, block: FireworkLaunchBuilder.() -> Unit = {}) {
        launches.add(FireworkLaunchBuilder(position).apply(block).build())
    }

    @PublishedApi internal fun build(): List<ScheduledLaunch> =
        launches.map { ScheduledLaunch(tickOffset, it) }
}

class FireworkShowRunner @PublishedApi internal constructor(
    private val instance: Instance,
    private val launches: List<ScheduledLaunch>,
) {
    private val tasks = mutableListOf<Task>()
    private val entities = mutableListOf<Entity>()

    fun start() {
        val grouped = launches.groupBy { it.tickOffset }
        grouped.forEach { (tickOffset, group) ->
            val task = MinecraftServer.getSchedulerManager()
                .buildTask {
                    group.forEach { scheduled -> spawnFirework(scheduled.firework) }
                }
                .delay(TaskSchedule.tick(tickOffset.coerceAtLeast(1)))
                .schedule()
            tasks.add(task)
        }
    }

    fun cancel() {
        tasks.forEach { it.cancel() }
        tasks.clear()
        entities.forEach { if (!it.isRemoved) it.remove() }
        entities.clear()
    }

    private fun spawnFirework(config: FireworkConfig) {
        val entity = Entity(EntityType.FIREWORK_ROCKET)
        entity.velocity = config.velocity
        entity.setInstance(instance, config.position)
        entities.add(entity)

        MinecraftServer.getSchedulerManager()
            .buildTask {
                if (!entity.isRemoved) entity.remove()
                entities.remove(entity)
            }
            .delay(TaskSchedule.tick(config.flightTicks))
            .schedule()
    }
}

class FireworkShowBuilder @PublishedApi internal constructor(private val instance: Instance) {

    @PublishedApi internal val allLaunches = mutableListOf<ScheduledLaunch>()

    inline fun at(tickOffset: Int, block: ShowMomentBuilder.() -> Unit) {
        allLaunches.addAll(ShowMomentBuilder(tickOffset).apply(block).build())
    }

    @PublishedApi internal fun build(): FireworkShowRunner = FireworkShowRunner(instance, allLaunches.toList())
}

inline fun fireworkShow(instance: Instance, block: FireworkShowBuilder.() -> Unit): FireworkShowRunner =
    FireworkShowBuilder(instance).apply(block).build()

fun Instance.launchFirework(position: Pos, velocity: Vec = Vec(0.0, 20.0, 0.0), flightTicks: Int = 30): Entity {
    val entity = Entity(EntityType.FIREWORK_ROCKET)
    entity.velocity = velocity
    entity.setInstance(this, position)
    MinecraftServer.getSchedulerManager()
        .buildTask { if (!entity.isRemoved) entity.remove() }
        .delay(TaskSchedule.tick(flightTicks))
        .schedule()
    return entity
}
