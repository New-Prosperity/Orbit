package me.nebula.orbit.utils.graceperiod

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class GracePeriodConfig(
    val name: String,
    val duration: Duration,
    val cancelOnMove: Boolean,
    val cancelOnAttack: Boolean,
    val onEnd: ((Player) -> Unit)?,
    val onCancel: ((Player) -> Unit)?,
)

data class GraceEntry(val expiresAt: Long, val configName: String)

object GracePeriodManager {

    private val active = ConcurrentHashMap<UUID, GraceEntry>()
    private val configs = ConcurrentHashMap<String, GracePeriodConfig>()
    private var eventNode: EventNode<*>? = null
    private var cleanupTask: Task? = null

    fun register(config: GracePeriodConfig) {
        configs[config.name] = config
        if (eventNode == null) installEvents()
    }

    fun apply(player: Player, configName: String) {
        val config = requireNotNull(configs[configName]) { "Grace period config '$configName' not found" }
        active[player.uuid] = GraceEntry(
            expiresAt = System.currentTimeMillis() + config.duration.toMillis(),
            configName = configName,
        )
    }

    fun apply(player: Player) {
        val config = configs.values.firstOrNull() ?: return
        apply(player, config.name)
    }

    fun isProtected(player: Player): Boolean {
        val entry = active[player.uuid] ?: return false
        if (System.currentTimeMillis() >= entry.expiresAt) {
            expire(player.uuid)
            return false
        }
        return true
    }

    fun isProtected(uuid: UUID): Boolean {
        val entry = active[uuid] ?: return false
        if (System.currentTimeMillis() >= entry.expiresAt) {
            expire(uuid)
            return false
        }
        return true
    }

    fun cancel(player: Player) {
        val entry = active.remove(player.uuid) ?: return
        configs[entry.configName]?.onCancel?.invoke(player)
    }

    fun remaining(player: Player): Duration {
        val entry = active[player.uuid] ?: return Duration.ZERO
        val remaining = entry.expiresAt - System.currentTimeMillis()
        return if (remaining > 0) Duration.ofMillis(remaining) else Duration.ZERO
    }

    fun clearAll() {
        active.clear()
    }

    fun uninstall() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        cleanupTask?.cancel()
        cleanupTask = null
        active.clear()
    }

    private fun installEvents() {
        val node = EventNode.all("grace-period-manager")

        node.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            if (isProtected(player)) {
                event.isCancelled = true
            }
        }

        node.addListener(PlayerMoveEvent::class.java) { event ->
            val entry = active[event.player.uuid] ?: return@addListener
            val config = configs[entry.configName] ?: return@addListener
            if (config.cancelOnMove && hasActuallyMoved(event)) {
                val player = event.player
                active.remove(player.uuid)
                config.onCancel?.invoke(player)
            }
        }

        node.addListener(EntityAttackEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            val entry = active[player.uuid] ?: return@addListener
            val config = configs[entry.configName] ?: return@addListener
            if (config.cancelOnAttack) {
                active.remove(player.uuid)
                config.onCancel?.invoke(player)
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node

        cleanupTask = MinecraftServer.getSchedulerManager()
            .buildTask { cleanupExpired() }
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expired = active.entries.filter { it.value.expiresAt <= now }
        expired.forEach { (uuid, entry) ->
            active.remove(uuid)
            val config = configs[entry.configName] ?: return@forEach
            val player = MinecraftServer.getConnectionManager().onlinePlayers
                .firstOrNull { it.uuid == uuid }
            player?.let { config.onEnd?.invoke(it) }
        }
    }

    private fun expire(uuid: UUID) {
        val entry = active.remove(uuid) ?: return
        val config = configs[entry.configName] ?: return
        val player = MinecraftServer.getConnectionManager().onlinePlayers
            .firstOrNull { it.uuid == uuid }
        player?.let { config.onEnd?.invoke(it) }
    }

    private fun hasActuallyMoved(event: PlayerMoveEvent): Boolean {
        val old = event.player.position
        val new = event.newPosition
        return old.x() != new.x() || old.y() != new.y() || old.z() != new.z()
    }
}

class GracePeriodBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var duration: Duration = Duration.ofSeconds(5)
    @PublishedApi internal var cancelOnMove: Boolean = false
    @PublishedApi internal var cancelOnAttack: Boolean = false
    @PublishedApi internal var onEndHandler: ((Player) -> Unit)? = null
    @PublishedApi internal var onCancelHandler: ((Player) -> Unit)? = null

    fun duration(duration: Duration) { this.duration = duration }
    fun cancelOnMove(value: Boolean = true) { cancelOnMove = value }
    fun cancelOnAttack(value: Boolean = true) { cancelOnAttack = value }
    fun onEnd(handler: (Player) -> Unit) { onEndHandler = handler }
    fun onCancel(handler: (Player) -> Unit) { onCancelHandler = handler }

    @PublishedApi internal fun build(): GracePeriodConfig = GracePeriodConfig(
        name = name,
        duration = duration,
        cancelOnMove = cancelOnMove,
        cancelOnAttack = cancelOnAttack,
        onEnd = onEndHandler,
        onCancel = onCancelHandler,
    )
}

inline fun gracePeriod(name: String, block: GracePeriodBuilder.() -> Unit): GracePeriodConfig {
    val config = GracePeriodBuilder(name).apply(block).build()
    GracePeriodManager.register(config)
    return config
}

val Player.isInGracePeriod: Boolean get() = GracePeriodManager.isProtected(this)
