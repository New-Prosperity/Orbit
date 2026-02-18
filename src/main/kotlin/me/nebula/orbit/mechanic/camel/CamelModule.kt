package me.nebula.orbit.mechanic.camel

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.tag.Tag
import kotlin.math.cos
import kotlin.math.sin

private val LAST_DASH_TAG: Tag<Long> = Tag.Long("camel_last_dash")
private const val DASH_COOLDOWN_MS = 5_000L
private const val DASH_STRENGTH = 15.0

class CamelModule : OrbitModule("camel") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            val target = event.target
            if (target.entityType != EntityType.CAMEL) return@addListener

            val player = event.player
            val passengers = target.passengers
            if (passengers.size >= 2) return@addListener
            if (player in passengers) return@addListener

            target.addPassenger(player)
        }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            if (!player.isSprinting) return@addListener

            val vehicle = player.vehicle ?: return@addListener
            if (vehicle.entityType != EntityType.CAMEL) return@addListener

            val now = System.currentTimeMillis()
            val lastDash = vehicle.getTag(LAST_DASH_TAG) ?: 0L
            if (now - lastDash < DASH_COOLDOWN_MS) return@addListener

            vehicle.setTag(LAST_DASH_TAG, now)

            val yaw = Math.toRadians(player.position.yaw().toDouble())
            val dirX = -sin(yaw)
            val dirZ = cos(yaw)
            vehicle.velocity = Vec(dirX * DASH_STRENGTH, 3.0, dirZ * DASH_STRENGTH)
        }
    }
}
