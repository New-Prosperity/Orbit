package me.nebula.orbit.mode.game

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.host.ConsumeTicketProcessor
import me.nebula.gravity.host.HostTicketStore
import me.nebula.gravity.rank.RankManager
import me.nebula.orbit.Orbit
import me.nebula.orbit.progression.PartyBonusCalculator
import me.nebula.orbit.progression.BattlePassRegistry
import me.nebula.orbit.utils.graceperiod.GracePeriodManager
import me.nebula.orbit.utils.graceperiod.gracePeriod
import me.nebula.orbit.utils.minigametimer.minigameTimer
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class GameInitializer(private val gameMode: GameMode) {

    private val logger = logger("GameInitializer")

    fun tearDownLobbyAndCountdown() {
        gameMode.lobbyLifecycleInternal.tearDownLobby()
        gameMode.startingCountdownInternal?.stop()
        gameMode.startingCountdownInternal = null
        gameMode.gameEventInstallerInternal.cleanupFreezeNode()
    }

    fun initializeGameSession() {
        gameMode.gameStartTime = System.currentTimeMillis()
        gameMode.initialPlayerCount = gameMode.tracker.aliveCount
        gameMode.activeSeasonPasses = BattlePassRegistry.activePasses()
        gameMode.gamePartySnapshotInternal = PartyBonusCalculator.buildPartySnapshot(gameMode.tracker.all)
        gameMode.partyBonusesInternal = gameMode.tracker.all.associateWith {
            PartyBonusCalculator.calculateBonus(it, gameMode.gamePartySnapshotInternal)
        }
    }

    fun consumeHostTicketIfNeeded() {
        val owner = Orbit.hostOwner ?: return
        if (RankManager.hasPermission(owner, "*")) return
        val consumed = HostTicketStore.executeOnKey(owner, ConsumeTicketProcessor())
        if (!consumed) logger.warn { "Failed to consume host ticket for $owner (balance may be zero)" }
    }

    fun applyTeamAssignments() {
        val teamConfig = gameMode.settings.teams ?: return
        if (teamConfig.teamCount <= 0) return
        val assignments = gameMode.assignTeamsInternal(gameMode.tracker.alive.toList())
        for ((uuid, team) in assignments) gameMode.tracker.assignTeam(uuid, team)
        val grouped = assignments.entries.groupBy({ it.value }, { it.key })
        gameMode.onTeamsAssigned(grouped)
    }

    fun collectAlivePlayers(): List<Player> {
        val alivePlayers = mutableListOf<Player>()
        gameMode.tracker.forEachAlive { uuid ->
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.let(alivePlayers::add)
        }
        return alivePlayers
    }

    fun transferAlivePlayersToGameInstance(alivePlayers: List<Player>) {
        if (!gameMode.isDualInstance) return
        val transfers = alivePlayers.map { it.setInstance(gameMode.gameInstance, gameMode.spawnPoint) }
        CompletableFuture.allOf(*transfers.toTypedArray()).join()
    }

    fun applyGracePeriodIfConfigured(alivePlayers: List<Player>) {
        if (gameMode.settings.timing.gracePeriodSeconds <= 0) return
        val config = gracePeriod("game-grace") {
            duration(gameMode.settings.timing.gracePeriodSeconds.seconds)
        }
        alivePlayers.forEach { GracePeriodManager.apply(it, config.name) }
    }

    fun startGameTimerIfConfigured(alivePlayers: List<Player>) {
        val resolvedDuration = gameMode.resolveGameDuration()
        if (resolvedDuration <= 0) return
        gameMode.gameTimerInternal = minigameTimer("game-timer") {
            duration(resolvedDuration.seconds)
            onEnd {
                gameMode.gameTimerInternal = null
                gameMode.handleTimerExpiredInternal()
            }
        }.also {
            it.addAllViewers(alivePlayers)
            it.start()
        }
    }
}
