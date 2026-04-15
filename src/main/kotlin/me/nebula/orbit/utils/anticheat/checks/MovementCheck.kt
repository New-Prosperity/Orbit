package me.nebula.orbit.utils.anticheat.checks

import me.nebula.gravity.property.NetworkProperties
import me.nebula.gravity.property.PropertyStore
import me.nebula.orbit.utils.anticheat.AntiCheat
import me.nebula.orbit.utils.anticheat.AntiCheatCheck
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

private data class MovementState(
    val lastPosition: Pos,
    val lastMoveTime: Long,
    val teleportGraceTicks: Int = 0,
    val airTicks: Int = 0,
    val lastGroundY: Double = 0.0,
)

object MovementCheck : AntiCheatCheck {

    override val id: String = "movement"


    private const val FLY_Y_THRESHOLD = 0.5
    private const val SPEED_THRESHOLD = 0.65
    private const val NOFALL_DISTANCE = 4.0
    private const val TELEPORT_GRACE_TICKS = 20
    private const val WEIGHT = 1

    private val states = ConcurrentHashMap<UUID, MovementState>()

    override fun install(node: EventNode<in Event>) {
        node.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val uuid = player.uuid

            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return@addListener
            if (player.vehicle != null) return@addListener

            val newPos = event.newPosition
            val state = states[uuid]

            if (state == null) {
                states[uuid] = MovementState(
                    lastPosition = newPos,
                    lastMoveTime = System.currentTimeMillis(),
                    lastGroundY = newPos.y(),
                )
                return@addListener
            }

            if (state.teleportGraceTicks > 0) {
                states[uuid] = state.copy(
                    lastPosition = newPos,
                    lastMoveTime = System.currentTimeMillis(),
                    teleportGraceTicks = state.teleportGraceTicks - 1,
                    lastGroundY = if (player.isOnGround) newPos.y() else state.lastGroundY,
                )
                return@addListener
            }

            val old = state.lastPosition
            val dx: Double = newPos.x() - old.x()
            val dz: Double = newPos.z() - old.z()
            val dy: Double = newPos.y() - old.y()
            val horizontalDist: Double = sqrt(dx * dx + dz * dz)

            val isTeleport = horizontalDist > 10.0 || abs(dy) > 10.0
            if (isTeleport) {
                states[uuid] = state.copy(
                    lastPosition = newPos,
                    lastMoveTime = System.currentTimeMillis(),
                    teleportGraceTicks = TELEPORT_GRACE_TICKS,
                    airTicks = 0,
                    lastGroundY = newPos.y(),
                )
                return@addListener
            }

            val newAirTicks = if (player.isOnGround) 0 else state.airTicks + 1
            val newGroundY = if (player.isOnGround) newPos.y() else state.lastGroundY

            val lagMultiplier = (1.0 + player.latency.toDouble() / AntiCheat.lagCompensationMs).coerceAtMost(3.0)
            val speedThreshold = SPEED_THRESHOLD * lagMultiplier
            val flyThreshold = FLY_Y_THRESHOLD * lagMultiplier

            if (dy > flyThreshold && !player.isOnGround && newAirTicks > 3
                && PropertyStore[NetworkProperties.AC_CHECK_FLY_ENABLED]) {
                AntiCheat.flag(uuid, "fly", WEIGHT, AntiCheat.movementFlagThreshold, AntiCheat.movementKickThreshold)
            }

            if (horizontalDist > speedThreshold
                && PropertyStore[NetworkProperties.AC_CHECK_SPEED_ENABLED]) {
                AntiCheat.flag(uuid, "speed", WEIGHT, AntiCheat.movementFlagThreshold, AntiCheat.movementKickThreshold)
            }

            val fallDistance = state.lastGroundY - newPos.y()
            if (fallDistance > NOFALL_DISTANCE && player.isOnGround && !wasOnGround(state)
                && PropertyStore[NetworkProperties.AC_CHECK_NOFALL_ENABLED]) {
                AntiCheat.flag(uuid, "nofall", WEIGHT, AntiCheat.movementFlagThreshold, AntiCheat.movementKickThreshold)
            }

            states[uuid] = MovementState(
                lastPosition = newPos,
                lastMoveTime = System.currentTimeMillis(),
                airTicks = newAirTicks,
                lastGroundY = newGroundY,
            )
        }
    }

    fun notifyTeleport(uuid: UUID) {
        states.computeIfPresent(uuid) { _, state ->
            state.copy(teleportGraceTicks = TELEPORT_GRACE_TICKS)
        }
    }

    override fun cleanup(uuid: UUID) {
        states.remove(uuid)
    }

    override fun clearAll() {
        states.clear()
    }

    private fun wasOnGround(state: MovementState): Boolean =
        state.airTicks == 0
}
