package me.nebula.orbit.utils.joinleavemessage

import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent

private val miniMessage = MiniMessage.miniMessage()

class JoinLeaveHandle @PublishedApi internal constructor(
    private val eventNode: EventNode<*>,
) {
    fun uninstall() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
    }
}

class JoinLeaveBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var joinFormat: String? = null
    @PublishedApi internal var leaveFormat: String? = null
    @PublishedApi internal var joinHandler: ((Player) -> Unit)? = null
    @PublishedApi internal var leaveHandler: ((Player) -> Unit)? = null

    fun joinFormat(format: String) { joinFormat = format }
    fun leaveFormat(format: String) { leaveFormat = format }
    fun onJoin(handler: (Player) -> Unit) { joinHandler = handler }
    fun onLeave(handler: (Player) -> Unit) { leaveHandler = handler }

    @PublishedApi internal fun build(): JoinLeaveHandle {
        val node = EventNode.all("join-leave-messages-${System.nanoTime()}")

        val jf = joinFormat
        val lf = leaveFormat
        val jh = joinHandler
        val lh = leaveHandler

        node.addListener(PlayerSpawnEvent::class.java) { event ->
            if (!event.isFirstSpawn) return@addListener
            jh?.invoke(event.player)
            if (jf != null) {
                val resolved = jf.replace("{player}", event.player.username)
                val component = miniMessage.deserialize(resolved)
                MinecraftServer.getConnectionManager().onlinePlayers.forEach { it.sendMessage(component) }
            }
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            lh?.invoke(event.player)
            if (lf != null) {
                val resolved = lf.replace("{player}", event.player.username)
                val component = miniMessage.deserialize(resolved)
                MinecraftServer.getConnectionManager().onlinePlayers.forEach { it.sendMessage(component) }
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        return JoinLeaveHandle(node)
    }
}

inline fun joinLeaveMessages(block: JoinLeaveBuilder.() -> Unit): JoinLeaveHandle =
    JoinLeaveBuilder().apply(block).build()
