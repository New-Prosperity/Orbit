package me.nebula.orbit.utils.teleport

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PendingTeleport(
    val player: UUID,
    val target: Pos,
    val targetInstance: Instance?,
    val warmupTicks: Int,
    val onComplete: (() -> Unit)?,
    val onCancel: (() -> Unit)?,
)

object TeleportManager {

    private val pending = ConcurrentHashMap<UUID, TeleportState>()

    private class TeleportState(
        val teleport: PendingTeleport,
        val originPos: Pos,
        val task: Task,
        var ticksRemaining: Int,
    )

    fun teleport(player: Player, target: Pos, instance: Instance? = null) {
        if (instance != null && instance != player.instance) {
            player.setInstance(instance, target)
        } else {
            player.teleport(target)
        }
    }

    fun teleportWithWarmup(
        player: Player,
        target: Pos,
        targetInstance: Instance? = null,
        warmupTicks: Int = 60,
        moveThreshold: Double = 0.5,
        onTick: ((Int) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null,
    ) {
        cancel(player.uuid)

        val origin = player.position
        val thresholdSq = moveThreshold * moveThreshold
        var ticks = warmupTicks

        val task = MinecraftServer.getSchedulerManager()
            .buildTask {
                val p = MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == player.uuid }
                if (p == null) {
                    cancel(player.uuid)
                    return@buildTask
                }

                val dx = p.position.x() - origin.x()
                val dz = p.position.z() - origin.z()
                if (dx * dx + dz * dz > thresholdSq) {
                    onCancel?.invoke()
                    cancel(player.uuid)
                    return@buildTask
                }

                ticks--
                onTick?.invoke(ticks)

                if (ticks <= 0) {
                    teleport(p, target, targetInstance)
                    onComplete?.invoke()
                    pending.remove(player.uuid)
                }
            }
            .repeat(TaskSchedule.tick(1))
            .schedule()

        pending[player.uuid] = TeleportState(
            PendingTeleport(player.uuid, target, targetInstance, warmupTicks, onComplete, onCancel),
            origin,
            task,
            warmupTicks,
        )
    }

    fun cancel(uuid: UUID): Boolean {
        val state = pending.remove(uuid) ?: return false
        state.task.cancel()
        return true
    }

    fun isPending(uuid: UUID): Boolean = pending.containsKey(uuid)

    fun cancelAll() {
        pending.values.forEach { it.task.cancel() }
        pending.clear()
    }
}

fun Player.safeTeleport(target: Pos, instance: Instance? = null) =
    TeleportManager.teleport(this, target, instance)

fun Player.teleportWithWarmup(
    target: Pos,
    warmupTicks: Int = 60,
    targetInstance: Instance? = null,
    onTick: ((Int) -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) = TeleportManager.teleportWithWarmup(
    this, target, targetInstance, warmupTicks, onTick = onTick, onComplete = onComplete, onCancel = onCancel,
)
