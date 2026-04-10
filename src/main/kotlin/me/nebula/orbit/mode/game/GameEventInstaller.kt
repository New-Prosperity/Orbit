package me.nebula.orbit.mode.game

import me.nebula.orbit.progression.ProgressionEvent
import me.nebula.orbit.progression.ProgressionEventBus
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerMoveEvent

class GameEventInstaller(private val gameMode: GameMode) {

    @Volatile private var freezeEventNode: EventNode<*>? = null
    @Volatile private var gameMechanicsNode: EventNode<*>? = null

    fun installFreezeNode() {
        if (!gameMode.settings.timing.freezeDuringCountdown) return
        val node = EventNode.all("gamemode-countdown-freeze")
        node.addListener(PlayerMoveEvent::class.java) { event ->
            if (gameMode.phase != GamePhase.STARTING) return@addListener
            if (!gameMode.tracker.isAlive(event.player.uuid)) return@addListener
            val old = event.player.position
            event.newPosition = Pos(old.x(), old.y(), old.z(), event.newPosition.yaw(), event.newPosition.pitch())
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        freezeEventNode = node
    }

    fun installGameMechanicsNode() {
        val node = EventNode.all("gamemode-mechanics")
        var hasListeners = false

        val needsFriendlyFire = gameMode.isTeamMode && !gameMode.isFriendlyFireEnabled
        val needsDamageHook = true
        val needsDamageTracking = gameMode.settings.timing.combatLogSeconds > 0
        val needsActivityTracking = gameMode.settings.timing.afkEliminationSeconds > 0

        if (needsFriendlyFire || needsDamageHook || needsDamageTracking) {
            hasListeners = true
            gameMode.damageRouterInternal.install(node)
        }

        if (needsActivityTracking) {
            hasListeners = true
            node.addListener(PlayerMoveEvent::class.java) { event ->
                if (gameMode.phase == GamePhase.PLAYING && gameMode.tracker.isAlive(event.player.uuid)) {
                    gameMode.tracker.markActivity(event.player.uuid)
                }
            }
        }

        if (gameMode.settings.timing.isolateSpectatorChat && gameMode.chatPipelineInternal == null) {
            hasListeners = true
            node.addListener(PlayerChatEvent::class.java) { event ->
                if (gameMode.phase != GamePhase.PLAYING) return@addListener
                val sender = event.player
                val senderAlive = gameMode.tracker.isAlive(sender.uuid)

                event.recipients.removeIf { recipient ->
                    if (!gameMode.tracker.contains(recipient.uuid)) return@removeIf false
                    val recipientAlive = gameMode.tracker.isAlive(recipient.uuid)
                    if (!senderAlive && recipientAlive) return@removeIf true
                    if (senderAlive && !recipientAlive) return@removeIf true
                    false
                }
            }
        }

        hasListeners = true
        node.addListener(PlayerChatEvent::class.java) { event ->
            if (gameMode.phase == GamePhase.PLAYING) {
                gameMode.semanticRecorderInternal.recordChat(event.player, event.rawMessage)
            }
        }

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (gameMode.phase == GamePhase.PLAYING && gameMode.tracker.isAlive(event.player.uuid)) {
                ProgressionEventBus.publish(ProgressionEvent.BlockMined(event.player))
            }
        }

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (gameMode.phase == GamePhase.PLAYING && gameMode.tracker.isAlive(event.player.uuid)) {
                ProgressionEventBus.publish(ProgressionEvent.BlockPlaced(event.player))
            }
        }

        if (hasListeners) {
            MinecraftServer.getGlobalEventHandler().addChild(node)
            gameMechanicsNode = node
        }
    }

    val isFreezeNodeActive: Boolean get() = freezeEventNode != null
    val isMechanicsNodeActive: Boolean get() = gameMechanicsNode != null

    fun cleanupFreezeNode() {
        freezeEventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        freezeEventNode = null
    }

    fun cleanupGameMechanicsNode() {
        gameMechanicsNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        gameMechanicsNode = null
    }
}
