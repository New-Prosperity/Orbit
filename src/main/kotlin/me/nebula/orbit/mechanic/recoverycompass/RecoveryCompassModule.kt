package me.nebula.orbit.mechanic.recoverycompass

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

private val CARDINALS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

private fun cardinalDirection(angle: Float): String {
    val normalized = ((angle % 360) + 360) % 360
    val index = ((normalized + 22.5f) / 45f).toInt() % 8
    return CARDINALS[index]
}

private val DEATH_POS_X_TAG: Tag<Double> = Tag.Double("death_pos_x")
private val DEATH_POS_Y_TAG: Tag<Double> = Tag.Double("death_pos_y")
private val DEATH_POS_Z_TAG: Tag<Double> = Tag.Double("death_pos_z")

class RecoveryCompassModule : OrbitModule("recovery-compass") {

    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            val player = event.player
            val pos = player.position
            player.setTag(DEATH_POS_X_TAG, pos.x())
            player.setTag(DEATH_POS_Y_TAG, pos.y())
            player.setTag(DEATH_POS_Z_TAG, pos.z())
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        super.onDisable()
    }

    private fun tick() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val held = player.getItemInMainHand()
            if (held.material() != Material.RECOVERY_COMPASS) return@forEach

            val deathX = player.getTag(DEATH_POS_X_TAG) ?: return@forEach
            val deathY = player.getTag(DEATH_POS_Y_TAG) ?: return@forEach
            val deathZ = player.getTag(DEATH_POS_Z_TAG) ?: return@forEach

            val deathPos = Pos(deathX, deathY, deathZ)
            val distance = player.position.distance(deathPos)
            val direction = deathPos.sub(player.position)

            val angle = Math.toDegrees(
                kotlin.math.atan2(direction.z(), direction.x())
            ).toFloat() - 90f

            val cardinal = cardinalDirection(angle)
            player.sendActionBar(
                player.translate("orbit.mechanic.recovery_compass.distance", "direction" to cardinal, "distance" to "${distance.toInt()}")
            )
        }
    }
}
