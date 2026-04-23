package me.nebula.orbit.mode.game.battleroyale.downed

import me.nebula.gravity.translation.Keys
import me.nebula.orbit.displayUsername
import me.nebula.orbit.mode.game.PlayerTracker
import me.nebula.orbit.mode.game.battleroyale.BattleRoyaleTeamConfig
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.bossbar.AnimatedBossBarManager
import net.kyori.adventure.bossbar.BossBar
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerEntityInteractEvent
import java.util.UUID

class DownedReviveListener(
    private val controller: DownedPlayerController,
    private val tracker: PlayerTracker,
    private val config: BattleRoyaleTeamConfig,
) {

    private var eventNode: EventNode<*>? = null

    fun install() {
        val node = EventNode.all("br-downed-revive")
        node.addListener(PlayerEntityInteractEvent::class.java) { event ->
            val target = event.target as? Player ?: return@addListener
            val reviver = event.player
            tryStartRevive(reviver, target)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun uninstall() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        clearAllBars()
    }

    fun tickRevives() {
        for (uuid in controller.activeUuids()) {
            val record = controller.record(uuid) ?: continue
            val reviverUuid = record.reviverUuid ?: continue
            val downed = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            val reviver = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(reviverUuid)
            if (downed == null || reviver == null) {
                controller.cancelRevive(reviverUuid, uuid)
                hideBossBar(reviverUuid)
                continue
            }
            val distance = reviver.position.distance(downed.position)
            if (distance > config.reviveInteractRangeBlocks) {
                controller.cancelRevive(reviverUuid, uuid)
                hideBossBar(reviverUuid)
                continue
            }
            updateBossBar(reviver, downed, controller.reviveProgress(uuid).toFloat())
        }
    }

    fun onRevived(reviverUuid: UUID) {
        hideBossBar(reviverUuid)
    }

    private fun tryStartRevive(reviver: Player, target: Player) {
        if (!config.reviveEnabled) return
        if (!controller.isDowned(target.uuid)) return
        if (controller.isDowned(reviver.uuid)) return
        if (!tracker.areTeammates(reviver.uuid, target.uuid)) return
        if (reviver.position.distance(target.position) > config.reviveInteractRangeBlocks) return
        if (!controller.startRevive(reviver.uuid, target.uuid)) return
        updateBossBar(reviver, target, controller.reviveProgress(target.uuid).toFloat())
    }

    private fun updateBossBar(reviver: Player, target: Player, progress: Float) {
        val title = reviver.translate(
            Keys.Orbit.Game.Br.Downed.ReviveBossBar,
            "player" to target.displayUsername,
        )
        val instance = AnimatedBossBarManager.get(reviver, BAR_ID)
            ?: AnimatedBossBarManager.create(
                reviver, BAR_ID, "", progress, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS,
            )
        instance.updateTitle(title)
        instance.setProgressInstant(progress)
    }

    private fun hideBossBar(reviverUuid: UUID) {
        val reviver = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(reviverUuid) ?: return
        AnimatedBossBarManager.remove(reviver, BAR_ID)
    }

    private fun clearAllBars() {
        for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
            AnimatedBossBarManager.remove(player, BAR_ID)
        }
    }

    companion object {
        private const val BAR_ID = "br-revive"
    }
}
