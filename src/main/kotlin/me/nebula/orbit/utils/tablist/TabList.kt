package me.nebula.orbit.utils.tablist

import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val miniMessage = MiniMessage.miniMessage()

class TabListBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var headerText: String = ""
    @PublishedApi internal var footerText: String = ""

    fun header(text: String) { headerText = text }
    fun footer(text: String) { footerText = text }
}

fun Player.tabList(block: TabListBuilder.() -> Unit) {
    val builder = TabListBuilder().apply(block)
    sendPlayerListHeaderAndFooter(
        miniMessage.deserialize(builder.headerText),
        miniMessage.deserialize(builder.footerText),
    )
}

fun Player.setTabList(header: String, footer: String) {
    sendPlayerListHeaderAndFooter(
        miniMessage.deserialize(header),
        miniMessage.deserialize(footer),
    )
}

class LiveTabList @PublishedApi internal constructor(
    private val headerProvider: (Player) -> String,
    private val footerProvider: (Player) -> String,
    refreshInterval: Duration,
) {

    private val viewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val eventNode = EventNode.all("live-tablist-${System.nanoTime()}")
    private val refreshTask: Task

    init {
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (event.isFirstSpawn) show(event.player)
        }
        eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            viewers.remove(event.player.uuid)
        }
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        refreshTask = MinecraftServer.getSchedulerManager()
            .buildTask(::refreshAll)
            .repeat(TaskSchedule.millis(refreshInterval.inWholeMilliseconds))
            .schedule()
    }

    fun show(player: Player) {
        viewers.add(player.uuid)
        player.sendPlayerListHeaderAndFooter(
            miniMessage.deserialize(headerProvider(player)),
            miniMessage.deserialize(footerProvider(player)),
        )
    }

    fun refreshAll() {
        for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
            if (viewers.contains(player.uuid)) {
                player.sendPlayerListHeaderAndFooter(
                    miniMessage.deserialize(headerProvider(player)),
                    miniMessage.deserialize(footerProvider(player)),
                )
            }
        }
    }

    fun uninstall() {
        refreshTask.cancel()
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
        viewers.clear()
    }
}

class LiveTabListBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var headerProvider: (Player) -> String = { "" }
    @PublishedApi internal var footerProvider: (Player) -> String = { "" }
    @PublishedApi internal var refreshInterval: Duration = 5.seconds

    fun header(text: String) { headerProvider = { text } }
    fun header(provider: (Player) -> String) { headerProvider = provider }

    fun footer(text: String) { footerProvider = { text } }
    fun footer(provider: (Player) -> String) { footerProvider = provider }

    fun refreshEvery(duration: Duration) { refreshInterval = duration }
}

inline fun liveTabList(block: LiveTabListBuilder.() -> Unit): LiveTabList {
    val builder = LiveTabListBuilder().apply(block)
    return LiveTabList(builder.headerProvider, builder.footerProvider, builder.refreshInterval)
}
