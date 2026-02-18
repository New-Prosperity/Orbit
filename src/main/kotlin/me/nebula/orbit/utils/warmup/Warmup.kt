package me.nebula.orbit.utils.warmup

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val miniMessage = MiniMessage.miniMessage()

enum class CancelTrigger {
    MOVE,
    DAMAGE,
    COMMAND,
    MANUAL,
}

data class WarmupConfig(
    val name: String,
    val duration: Duration,
    val cancelTriggers: Set<CancelTrigger>,
    val onTickHandler: ((Player, Duration) -> Unit)?,
    val onCompleteHandler: ((Player) -> Unit)?,
    val onCancelHandler: ((Player, CancelTrigger) -> Unit)?,
    val showProgressBar: Boolean,
    val progressBarFormat: String,
)

private data class ActiveWarmup(
    val config: WarmupConfig,
    val startTime: Long,
    val originX: Double,
    val originZ: Double,
    val task: Task,
)

object WarmupManager {

    private val active = ConcurrentHashMap<UUID, ActiveWarmup>()
    private var eventNode: EventNode<*>? = null

    fun start(player: Player, config: WarmupConfig) {
        cancel(player, CancelTrigger.MANUAL)
        if (eventNode == null) installEvents()

        val startTime = System.currentTimeMillis()
        val durationMs = config.duration.toMillis()

        val task = MinecraftServer.getSchedulerManager().buildTask {
            val elapsed = System.currentTimeMillis() - startTime
            val remainingMs = durationMs - elapsed
            val remaining = Duration.ofMillis(remainingMs.coerceAtLeast(0))

            if (remainingMs <= 0) {
                active.remove(player.uuid)
                config.onCompleteHandler?.invoke(player)
                return@buildTask
            }

            config.onTickHandler?.invoke(player, remaining)

            if (config.showProgressBar) {
                val progress = elapsed.toFloat() / durationMs
                val barLength = 20
                val filled = (progress * barLength).toInt().coerceIn(0, barLength)
                val empty = barLength - filled
                val seconds = (remainingMs / 1000.0)
                val bar = config.progressBarFormat
                    .replace("{bar}", "<green>${"|".repeat(filled)}<gray>${"|".repeat(empty)}")
                    .replace("{name}", config.name)
                    .replace("{time}", "%.1f".format(seconds))
                player.sendActionBar(miniMessage.deserialize(bar))
            }
        }.repeat(TaskSchedule.tick(2)).schedule()

        active[player.uuid] = ActiveWarmup(
            config = config,
            startTime = startTime,
            originX = player.position.x(),
            originZ = player.position.z(),
            task = task,
        )
    }

    fun cancel(player: Player, trigger: CancelTrigger = CancelTrigger.MANUAL) {
        val warmup = active.remove(player.uuid) ?: return
        warmup.task.cancel()
        player.sendActionBar(Component.empty())
        warmup.config.onCancelHandler?.invoke(player, trigger)
    }

    fun isWarming(player: Player): Boolean = active.containsKey(player.uuid)

    fun isWarming(uuid: UUID): Boolean = active.containsKey(uuid)

    fun remaining(player: Player): Duration {
        val warmup = active[player.uuid] ?: return Duration.ZERO
        val elapsed = System.currentTimeMillis() - warmup.startTime
        val remaining = warmup.config.duration.toMillis() - elapsed
        return if (remaining > 0) Duration.ofMillis(remaining) else Duration.ZERO
    }

    fun cancelAll() {
        active.forEach { (_, warmup) -> warmup.task.cancel() }
        active.clear()
    }

    fun uninstall() {
        cancelAll()
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
    }

    private fun installEvents() {
        val node = EventNode.all("warmup-manager")

        node.addListener(PlayerMoveEvent::class.java) { event ->
            val warmup = active[event.player.uuid] ?: return@addListener
            if (CancelTrigger.MOVE !in warmup.config.cancelTriggers) return@addListener
            val dx = event.newPosition.x() - warmup.originX
            val dz = event.newPosition.z() - warmup.originZ
            if (dx * dx + dz * dz > 0.25) {
                cancel(event.player, CancelTrigger.MOVE)
            }
        }

        node.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            val warmup = active[player.uuid] ?: return@addListener
            if (CancelTrigger.DAMAGE in warmup.config.cancelTriggers) {
                cancel(player, CancelTrigger.DAMAGE)
            }
        }

        node.addListener(PlayerChatEvent::class.java) { event ->
            if (!event.rawMessage.startsWith("/")) return@addListener
            val warmup = active[event.player.uuid] ?: return@addListener
            if (CancelTrigger.COMMAND in warmup.config.cancelTriggers) {
                cancel(event.player, CancelTrigger.COMMAND)
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }
}

class WarmupBuilder @PublishedApi internal constructor(
    private val player: Player,
    private val name: String,
    private val duration: Duration,
) {

    @PublishedApi internal val cancelTriggers = mutableSetOf<CancelTrigger>()
    @PublishedApi internal var onTickHandler: ((Player, Duration) -> Unit)? = null
    @PublishedApi internal var onCompleteHandler: ((Player) -> Unit)? = null
    @PublishedApi internal var onCancelHandler: ((Player, CancelTrigger) -> Unit)? = null
    @PublishedApi internal var showProgressBar: Boolean = true
    @PublishedApi internal var progressBarFormat: String = "<yellow>{name} <white>{bar} <gray>{time}s"

    fun cancelOnMove() { cancelTriggers.add(CancelTrigger.MOVE) }
    fun cancelOnDamage() { cancelTriggers.add(CancelTrigger.DAMAGE) }
    fun cancelOnCommand() { cancelTriggers.add(CancelTrigger.COMMAND) }
    fun onTick(handler: (Player, Duration) -> Unit) { onTickHandler = handler }
    fun onComplete(handler: (Player) -> Unit) { onCompleteHandler = handler }
    fun onCancel(handler: (Player, CancelTrigger) -> Unit) { onCancelHandler = handler }
    fun showProgressBar(value: Boolean = true) { showProgressBar = value }
    fun progressBarFormat(format: String) { progressBarFormat = format }

    @PublishedApi internal fun start() {
        WarmupManager.start(player, WarmupConfig(
            name = name,
            duration = duration,
            cancelTriggers = cancelTriggers.toSet(),
            onTickHandler = onTickHandler,
            onCompleteHandler = onCompleteHandler,
            onCancelHandler = onCancelHandler,
            showProgressBar = showProgressBar,
            progressBarFormat = progressBarFormat,
        ))
    }
}

inline fun warmup(player: Player, name: String, duration: Duration, block: WarmupBuilder.() -> Unit) {
    WarmupBuilder(player, name, duration).apply(block).start()
}
