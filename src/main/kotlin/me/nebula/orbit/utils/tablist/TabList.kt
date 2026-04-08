package me.nebula.orbit.utils.tablist

import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import java.time.Duration as JavaDuration
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

class TabSection(
    val provider: (Player) -> String,
    val visibleWhen: ((Player) -> Boolean)?,
)

class LiveTabList @PublishedApi internal constructor(
    private val headerSections: List<TabSection>,
    private val footerSections: List<TabSection>,
    refreshInterval: Duration,
) {

    private val viewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val lastSent: ConcurrentHashMap<UUID, Int> = ConcurrentHashMap()
    private val eventNode = EventNode.all("live-tablist-${System.nanoTime()}")
    private val refreshTask: Task

    init {
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (event.isFirstSpawn) show(event.player)
        }
        eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            viewers.remove(event.player.uuid)
            lastSent.remove(event.player.uuid)
        }
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        refreshTask = repeat(JavaDuration.ofMillis(refreshInterval.inWholeMilliseconds)) { refreshAll() }
    }

    fun show(player: Player) {
        viewers.add(player.uuid)
        send(player)
    }

    fun refreshAll() {
        for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
            if (viewers.contains(player.uuid)) send(player)
        }
    }

    fun uninstall() {
        refreshTask.cancel()
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
        viewers.clear()
        lastSent.clear()
    }

    private fun send(player: Player) {
        val headerBuilder = StringBuilder()
        for (section in headerSections) {
            if (section.visibleWhen?.invoke(player) == false) continue
            if (headerBuilder.isNotEmpty()) headerBuilder.append('\n')
            headerBuilder.append(section.provider(player))
        }
        val footerBuilder = StringBuilder()
        for (section in footerSections) {
            if (section.visibleWhen?.invoke(player) == false) continue
            if (footerBuilder.isNotEmpty()) footerBuilder.append('\n')
            footerBuilder.append(section.provider(player))
        }

        val header = headerBuilder.toString()
        val footer = footerBuilder.toString()
        val digest = 31 * header.hashCode() + footer.hashCode()

        if (lastSent[player.uuid] == digest) return
        lastSent[player.uuid] = digest

        player.sendPlayerListHeaderAndFooter(
            miniMessage.deserialize(header),
            miniMessage.deserialize(footer),
        )
    }
}

class LiveTabListBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val headerSections = mutableListOf<TabSection>()
    @PublishedApi internal val footerSections = mutableListOf<TabSection>()
    @PublishedApi internal var refreshInterval: Duration = 5.seconds

    fun header(text: String) {
        headerSections += TabSection({ text }, null)
    }

    fun header(visibleWhen: ((Player) -> Boolean)? = null, provider: (Player) -> String) {
        headerSections += TabSection(provider, visibleWhen)
    }

    fun footer(text: String) {
        footerSections += TabSection({ text }, null)
    }

    fun footer(visibleWhen: ((Player) -> Boolean)? = null, provider: (Player) -> String) {
        footerSections += TabSection(provider, visibleWhen)
    }

    fun refreshEvery(duration: Duration) { refreshInterval = duration }
}

inline fun liveTabList(block: LiveTabListBuilder.() -> Unit): LiveTabList {
    val builder = LiveTabListBuilder().apply(block)
    return LiveTabList(builder.headerSections.toList(), builder.footerSections.toList(), builder.refreshInterval)
}
