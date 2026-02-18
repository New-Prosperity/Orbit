package me.nebula.orbit.mechanic.turtlescute

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val BABY_TAG = Tag.Boolean("mechanic:turtle_scute:is_baby").defaultValue(true)
private val AGE_TICKS_TAG = Tag.Integer("mechanic:turtle_scute:age_ticks").defaultValue(0)

private const val GROWTH_THRESHOLD_TICKS = 24000
private const val SCAN_INTERVAL_TICKS = 40

class TurtleScuteModule : OrbitModule("turtle-scute") {

    private var tickTask: Task? = null
    private val trackedTurtles: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(SCAN_INTERVAL_TICKS))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedTurtles.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedTurtles.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.TURTLE) return@entityLoop
                trackedTurtles.add(entity)
            }
        }

        trackedTurtles.toList().forEach { turtle ->
            if (turtle.isRemoved) return@forEach
            if (!turtle.getTag(BABY_TAG)) return@forEach

            val age = turtle.getTag(AGE_TICKS_TAG) + SCAN_INTERVAL_TICKS
            turtle.setTag(AGE_TICKS_TAG, age)

            if (age >= GROWTH_THRESHOLD_TICKS) {
                turtle.setTag(BABY_TAG, false)
                dropScute(turtle)
            }
        }
    }

    private fun dropScute(turtle: Entity) {
        val instance = turtle.instance ?: return
        val pos = turtle.position

        val scute = ItemEntity(ItemStack.of(Material.TURTLE_SCUTE))
        scute.setPickupDelay(Duration.ofMillis(500))
        scute.setInstance(instance, Pos(pos.x(), pos.y() + 0.5, pos.z()))

        scute.scheduler().buildTask { scute.remove() }
            .delay(TaskSchedule.minutes(5))
            .schedule()
    }
}
