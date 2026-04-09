package me.nebula.orbit.mode.game

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.hotbar.Hotbar
import me.nebula.orbit.utils.lobby.Lobby
import me.nebula.orbit.utils.lobby.lobby
import me.nebula.orbit.utils.scheduler.repeat
import me.nebula.orbit.utils.vanish.VanishManager
import net.minestom.server.entity.GameMode as MinestomGameMode
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import java.util.concurrent.CompletableFuture

class LobbyLifecycleManager(private val mode: GameMode) {

    @Volatile internal var currentLobby: Lobby? = null
        private set

    @Volatile internal var currentHotbar: Hotbar? = null
        private set

    @Volatile private var waitingActionBarTask: Task? = null

    fun installLobbyAndHotbar() {
        val settings = mode.settings
        currentLobby = lobby {
            instance = mode.lobbyInstance
            spawnPoint = mode.lobbySpawnPoint
            gameMode = MinestomGameMode.valueOf(settings.lobby.gameMode)
            protectBlocks = settings.lobby.protectBlocks
            disableDamage = settings.lobby.disableDamage
            disableHunger = settings.lobby.disableHunger
            lockInventory = settings.lobby.lockInventory
            voidTeleportY = settings.lobby.voidTeleportY
        }.also { it.install() }

        currentHotbar = mode.buildLobbyHotbarInternal()
        currentHotbar?.install()
    }

    fun teleportPlayersToLobby() {
        val settings = mode.settings
        if (mode.isDualInstance) {
            val gameInst = mode.gameInstanceOrNull()
            if (gameInst != null) {
                val transfers = gameInst.players.toList().map { player ->
                    player.setInstance(mode.lobbyInstance, mode.lobbySpawnPoint)
                }
                CompletableFuture.allOf(*transfers.toTypedArray()).join()
            }
        }

        for (player in mode.lobbyInstance.players) {
            if (VanishManager.isVanished(player)) {
                player.gameMode = MinestomGameMode.SPECTATOR
                continue
            }
            mode.tracker.join(player.uuid)
            player.gameMode = MinestomGameMode.valueOf(settings.lobby.gameMode)
            if (!mode.isDualInstance) player.teleport(mode.lobbySpawnPoint)
            currentHotbar?.apply(player)
        }
    }

    fun startWaitingActionBarLoop() {
        waitingActionBarTask = repeat(40) {
            if (mode.phase != GamePhase.WAITING) return@repeat
            val current = mode.tracker.aliveCount
            val needed = mode.settings.timing.minPlayers
            for (player in mode.lobbyInstance.players) {
                player.sendActionBar(player.translate("orbit.game.waiting",
                    "current" to current.toString(),
                    "needed" to needed.toString()))
            }
        }
    }

    fun cancelWaitingActionBarLoop() {
        waitingActionBarTask?.cancel()
        waitingActionBarTask = null
    }

    fun applyHotbarTo(player: Player) {
        currentHotbar?.apply(player)
    }

    fun removeHotbarFrom(player: Player) {
        currentHotbar?.remove(player)
    }

    fun tearDownLobby() {
        currentLobby?.uninstall()
        currentLobby = null
        currentHotbar?.uninstall()
        currentHotbar = null
    }

    fun hasLobby(): Boolean = currentLobby != null
}
