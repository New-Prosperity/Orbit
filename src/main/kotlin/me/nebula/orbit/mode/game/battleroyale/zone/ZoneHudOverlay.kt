package me.nebula.orbit.mode.game.battleroyale.zone

import me.nebula.ether.utils.duration.DurationFormatter
import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.orbit.event.GameEvent
import me.nebula.orbit.event.GameEventBus
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.bossbar.AnimatedBossBarManager
import me.nebula.orbit.utils.scheduler.repeat
import net.kyori.adventure.bossbar.BossBar
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task

class ZoneHudOverlay(
    private val gameMode: GameMode,
    private val zoneStateProvider: () -> ZoneState,
) {

    private var subscription: GameEventBus.Subscription? = null
    private var refreshTask: Task? = null

    fun install() {
        if (subscription != null) return
        subscription = gameMode.events.subscribe<GameEvent.ZoneTransition> { event ->
            val instance = gameMode.gameInstanceOrNull() ?: return@subscribe
            val next = event.to
            for (player in instance.players) applyState(player, next)
        }
        refreshTask = repeat(20) {
            val instance = gameMode.gameInstanceOrNull() ?: return@repeat
            val state = zoneStateProvider() as? ZoneState.Shrinking ?: return@repeat
            for (player in instance.players) refreshShrinking(player, state)
        }
    }

    fun uninstall() {
        subscription?.cancel()
        subscription = null
        refreshTask?.cancel()
        refreshTask = null
        val instance = gameMode.gameInstanceOrNull() ?: return
        for (player in instance.players) player.removeZoneBossBar()
    }

    private fun applyState(player: Player, state: ZoneState) {
        when (state) {
            is ZoneState.Waiting, is ZoneState.Ended -> player.removeZoneBossBar()
            is ZoneState.Announcing -> showAnnouncing(player, state)
            is ZoneState.Shrinking -> showShrinking(player, state)
            is ZoneState.Static -> showStatic(player, state)
            is ZoneState.Deathmatch -> showDeathmatch(player, state)
        }
    }

    private fun showAnnouncing(player: Player, state: ZoneState.Announcing) {
        val bar = AnimatedBossBarManager.create(
            player = player,
            id = ZONE_BAR_ID,
            title = "next zone",
            progress = 1f,
            color = BossBar.Color.YELLOW,
        )
        val secondsUntilShrink = ((state.shrinkStartsAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
        bar.updateTitle(player.translate(
            KEY_ANNOUNCING,
            "phase" to state.phaseIndex.toString(),
            "diameter" to state.nextDiameter.toInt().toString(),
            "time" to DurationFormatter.formatCompact(secondsUntilShrink * 1000L),
        ))
    }

    private fun showShrinking(player: Player, state: ZoneState.Shrinking) {
        val bar = AnimatedBossBarManager.create(
            player = player,
            id = ZONE_BAR_ID,
            title = "zone shrinking",
            progress = 1f - state.progress(System.currentTimeMillis()).toFloat(),
            color = BossBar.Color.RED,
        )
        refreshShrinking(player, state)
        val durationTicks = (state.durationSeconds * 20.0).toInt().coerceAtLeast(1)
        val elapsedTicks = ((System.currentTimeMillis() - state.startedAtMs) / 50L).toInt().coerceAtLeast(0)
        val remainingTicks = (durationTicks - elapsedTicks).coerceAtLeast(1)
        bar.setProgress(target = 0f, durationTicks = remainingTicks)
    }

    private fun refreshShrinking(player: Player, state: ZoneState.Shrinking) {
        val remaining = state.remainingSeconds(System.currentTimeMillis()).toLong()
        val title = player.translate(
            KEY_SHRINKING,
            "phase" to state.phaseIndex.toString(),
            "diameter" to state.toDiameter.toInt().toString(),
            "time" to DurationFormatter.formatCompact(remaining * 1000L),
        )
        AnimatedBossBarManager.get(player, ZONE_BAR_ID)?.updateTitle(title)
    }

    private fun showStatic(player: Player, state: ZoneState.Static) {
        val bar = AnimatedBossBarManager.create(
            player = player,
            id = ZONE_BAR_ID,
            title = "zone stable",
            progress = 1f,
            color = BossBar.Color.GREEN,
        )
        bar.updateTitle(player.translate(
            KEY_STATIC,
            "phase" to state.phaseIndex.toString(),
            "diameter" to state.diameter.toInt().toString(),
        ))
    }

    private fun showDeathmatch(player: Player, state: ZoneState.Deathmatch) {
        val bar = AnimatedBossBarManager.create(
            player = player,
            id = ZONE_BAR_ID,
            title = "deathmatch",
            progress = 1f,
            color = BossBar.Color.PURPLE,
        )
        bar.updateTitle(player.translate(
            KEY_DEATHMATCH,
            "diameter" to state.diameter.toInt().toString(),
        ))
    }

    private fun Player.removeZoneBossBar() {
        AnimatedBossBarManager.remove(this, ZONE_BAR_ID)
    }

    companion object {
        private const val ZONE_BAR_ID = "br_zone"
        private val KEY_SHRINKING = "orbit.game.br.zone.shrinking".asTranslationKey()
        private val KEY_STATIC = "orbit.game.br.zone.static".asTranslationKey()
        private val KEY_DEATHMATCH = "orbit.game.br.zone.deathmatch".asTranslationKey()
        private val KEY_ANNOUNCING = "orbit.game.br.zone.announcing".asTranslationKey()
    }
}
