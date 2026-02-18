package me.nebula.orbit.mechanic.striderbehavior

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

private val SHIVERING_TAG = Tag.Boolean("mechanic:strider:shivering").defaultValue(false)

private const val SCAN_INTERVAL_TICKS = 10
private const val LAVA_WALK_SPEED = 8.0
private const val STEER_SPEED = 12.0
private const val SHIVER_SPEED_MULTIPLIER = 0.4

class StriderBehaviorModule : OrbitModule("strider-behavior") {

    private var tickTask: Task? = null
    private val trackedStriders: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        trackedStriders.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedStriders.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.STRIDER) return@entityLoop
                trackedStriders.add(entity)
            }
        }

        trackedStriders.forEach { strider ->
            if (strider.isRemoved) return@forEach
            val instance = strider.instance ?: return@forEach

            val onLava = isOnLava(strider, instance)
            strider.setTag(SHIVERING_TAG, !onLava)

            if (onLava) {
                strider.setNoGravity(true)
                handleLavaWalk(strider, instance)
            } else {
                strider.setNoGravity(false)
            }

            handlePassengerSteering(strider, onLava)
        }
    }

    private fun isOnLava(strider: Entity, instance: Instance): Boolean {
        val below = instance.getBlock(
            strider.position.blockX(),
            strider.position.blockY() - 1,
            strider.position.blockZ(),
        )
        return below.name() == "minecraft:lava"
    }

    private fun handleLavaWalk(strider: Entity, instance: Instance) {
        if (strider.passengers.isNotEmpty()) return

        val currentVel = strider.velocity
        strider.velocity = Vec(currentVel.x(), 0.0, currentVel.z())
    }

    private fun handlePassengerSteering(strider: Entity, onLava: Boolean) {
        val rider = strider.passengers.firstOrNull { it is Player } as? Player ?: return

        val holdingFungusStick = rider.getItemInMainHand().material() == Material.WARPED_FUNGUS_ON_A_STICK ||
            rider.getItemInOffHand().material() == Material.WARPED_FUNGUS_ON_A_STICK

        if (!holdingFungusStick) return

        val yaw = Math.toRadians(rider.position.yaw().toDouble())
        val dirX = -sin(yaw)
        val dirZ = cos(yaw)

        val speed = if (onLava) STEER_SPEED else STEER_SPEED * SHIVER_SPEED_MULTIPLIER
        strider.velocity = Vec(dirX * speed, strider.velocity.y(), dirZ * speed)
    }
}
