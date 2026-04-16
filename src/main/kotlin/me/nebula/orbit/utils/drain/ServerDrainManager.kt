package me.nebula.orbit.utils.drain

import me.nebula.ether.utils.duration.DurationFormatter
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.scheduling.ScheduledTask
import me.nebula.ether.utils.scheduling.TaskScheduler
import me.nebula.gravity.server.LiveServerRegistry
import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.translation.translate
import net.kyori.adventure.bossbar.BossBar
import net.minestom.server.MinecraftServer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

object ServerDrainManager {

    private val logger = logger("ServerDrainManager")

    private val POLL_INTERVAL = 5.seconds
    private const val DRAIN_TIMEOUT_MS = 5 * 60_000L
    private const val NOTIFY_INTERVAL_MS = 30_000L

    @Volatile private var pollerTask: ScheduledTask? = null
    private val draining = AtomicBoolean(false)
    @Volatile private var drainStartedAt = 0L
    @Volatile private var lastNotifyAt = 0L
    @Volatile private var bossBar: BossBar? = null

    val isDraining: Boolean get() = draining.get()

    fun install() {
        check(pollerTask == null) { "ServerDrainManager already installed" }
        pollerTask = TaskScheduler.scheduleAtFixedRate("drain-poller", POLL_INTERVAL, ::tick)
        runCatching {
            (Orbit.mode as? GameMode)?.onPhaseChange { _, to -> onPhaseChanged(to) }
        }
        logger.info { "ServerDrainManager installed" }
    }

    private fun onPhaseChanged(to: GamePhase) {
        if (!draining.get()) return
        if (to == GamePhase.WAITING || to == GamePhase.STARTING || to == GamePhase.ENDING) {
            drainAllNow("phase->$to")
        }
    }

    fun uninstall() {
        pollerTask?.cancel()
        pollerTask = null
        clearBossBar()
        draining.set(false)
        drainStartedAt = 0L
        lastNotifyAt = 0L
        logger.info { "ServerDrainManager uninstalled" }
    }

    fun handleIncomingPlayer(uuid: java.util.UUID) {
        if (!draining.get()) return
        PlayerTransfer.transferToHubByUuid(uuid)
    }

    private fun tick() {
        try {
            val name = Orbit.serverName
            val live = LiveServerRegistry.get(name) ?: return
            if (live.drain) onDrainActive() else onDrainCleared()
        } catch (e: Throwable) {
            logger.warn(e) { "Drain tick failed: ${e.message}" }
        }
    }

    private fun onDrainActive() {
        if (draining.compareAndSet(false, true)) {
            drainStartedAt = System.currentTimeMillis()
            lastNotifyAt = 0L
            logger.info { "Drain mode enabled for ${Orbit.serverName}" }
        }

        val phase = currentPhase()
        when {
            phase == null -> drainAllNow("hub mode")
            phase == GamePhase.WAITING || phase == GamePhase.STARTING || phase == GamePhase.ENDING ->
                drainAllNow("phase=$phase")
            phase == GamePhase.PLAYING -> {
                refreshNotification(phase)
                if (System.currentTimeMillis() - drainStartedAt > DRAIN_TIMEOUT_MS) {
                    logger.warn { "Drain timeout (${DRAIN_TIMEOUT_MS / 1000}s) reached during PLAYING — force-transferring" }
                    drainAllNow("timeout")
                }
            }
        }
    }

    private fun onDrainCleared() {
        if (draining.compareAndSet(true, false)) {
            drainStartedAt = 0L
            lastNotifyAt = 0L
            clearBossBar()
            logger.info { "Drain mode cleared for ${Orbit.serverName}" }
        }
    }

    private fun drainAllNow(reason: String) {
        val players = MinecraftServer.getConnectionManager().onlinePlayers
        if (players.isEmpty()) return
        logger.info { "Transferring ${players.size} player(s) to hub ($reason)" }
        PlayerTransfer.transferAllToHub()
    }

    private fun refreshNotification(phase: GamePhase) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < NOTIFY_INTERVAL_MS) return
        lastNotifyAt = now

        val remainingMs = (DRAIN_TIMEOUT_MS - (now - drainStartedAt)).coerceAtLeast(0L)
        val remainingText = DurationFormatter.formatCompact(remainingMs)

        val players = MinecraftServer.getConnectionManager().onlinePlayers
        if (players.isEmpty()) return
        for (player in players) {
            player.sendMessage(player.translate("orbit.drain.notification", "remaining" to remainingText))
        }

        val bar = bossBar ?: BossBar.bossBar(
            Orbit.deserialize("orbit.drain.bossbar", Orbit.translations.defaultLocale),
            1.0f,
            BossBar.Color.YELLOW,
            BossBar.Overlay.PROGRESS,
        ).also { bossBar = it }
        val progress = (remainingMs.toFloat() / DRAIN_TIMEOUT_MS).coerceIn(0f, 1f)
        bar.progress(progress)
        for (player in players) player.showBossBar(bar)
        logger.debug { "Drain notification refreshed (phase=$phase, remaining=$remainingText)" }
    }

    private fun clearBossBar() {
        val bar = bossBar ?: return
        for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
            runCatching { player.hideBossBar(bar) } // noqa: dangling runCatching
        }
        bossBar = null
    }

    private fun currentPhase(): GamePhase? {
        if (!Orbit.isModeInitialized) return null
        return (Orbit.mode as? GameMode)?.phase
    }
}
