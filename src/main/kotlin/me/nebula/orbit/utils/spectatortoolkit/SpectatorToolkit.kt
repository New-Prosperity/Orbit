package me.nebula.orbit.utils.spectatortoolkit

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.hotbar.Hotbar
import me.nebula.orbit.utils.hotbar.hotbar
import me.nebula.orbit.utils.hud.hideHud
import me.nebula.orbit.utils.hud.setHudCondition
import me.nebula.orbit.utils.hud.showHud
import me.nebula.orbit.utils.hud.updateHud
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.item.Material
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import me.nebula.gravity.translation.Keys

class SpectatorToolkit @PublishedApi internal constructor(
    private val nextTarget: (Player) -> Player?,
    private val previousTarget: (Player) -> Player?,
    private val alivePlayers: () -> List<Player>,
    private val speedSteps: List<Float>,
    private val onLeave: ((Player) -> Unit)?,
    private val hudEnabled: Boolean,
    private val freeCameraEnabled: Boolean,
    private val hideOtherSpectators: Boolean,
    private val aliveCountProvider: (() -> Int)?,
    private val gameTimerProvider: (() -> String)?,
    private val targetStatsProvider: ((Player) -> SpectatorTargetStats?)?,
    private val maxHealthSegments: Int,
    private val maxArmorSegments: Int,
    private val tickIntervalTicks: Int,
) {

    private val activeSpectators: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private var hotbar: Hotbar? = null
    private var hudTask: Task? = null
    private var eventNode: EventNode<Event>? = null

    @Volatile private var lastKillText: String = ""
    @Volatile private var lastKillTimestamp: Long = 0L

    fun install() {
        if (hudEnabled) SpectatorHud.ensureRegistered(maxHealthSegments, maxArmorSegments)

        hotbar = buildHotbar().also { it.install() }

        val node = EventNode.all("spectator-toolkit")
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            if (activeSpectators.remove(event.player.uuid) && hideOtherSpectators) {
                rebuildHidingRule()
            }
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node

        if (hudEnabled) {
            hudTask = repeat(tickIntervalTicks) { tickHud() }
        }
    }

    fun uninstall() {
        hudTask?.cancel()
        hudTask = null
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        hotbar?.uninstall()
        hotbar = null

        for (uuid in activeSpectators) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: continue
            player.updateViewableRule()
        }
        activeSpectators.clear()
    }

    fun apply(player: Player) {
        player.setTag(SpectatorTags.ACTIVE, true)
        hotbar?.apply(player)

        if (hudEnabled) {
            player.showHud(SpectatorHud.LAYOUT_ID)
            player.setHudCondition(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_FREECAM_INDICATOR) {
                it.getTag(SpectatorTags.FREECAM) == true
            }
            val targetVisible: (Player) -> Boolean = {
                it.getTag(SpectatorTags.CURRENT_TARGET) != null && it.getTag(SpectatorTags.FREECAM) != true
            }
            player.setHudCondition(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_TARGET_NAME, targetVisible)
            player.setHudCondition(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_TARGET_HEALTH, targetVisible)
            player.setHudCondition(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_TARGET_ARMOR, targetVisible)
            player.setHudCondition(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_TARGET_KILLS, targetVisible)
            player.setHudCondition(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_KILLFEED) {
                System.currentTimeMillis() - lastKillTimestamp < KILL_FEED_TTL_MS
            }
        }

        if (hideOtherSpectators && activeSpectators.add(player.uuid)) {
            rebuildHidingRule()
        }
    }

    fun remove(player: Player) {
        player.removeTag(SpectatorTags.ACTIVE)
        player.removeTag(SpectatorTags.SPEED_INDEX)
        player.removeTag(SpectatorTags.CURRENT_TARGET)
        player.removeTag(SpectatorTags.FREECAM)
        player.removeTag(SpectatorTags.FREECAM_LAST_TARGET)
        hotbar?.remove(player)
        player.flyingSpeed = DEFAULT_FLY_SPEED

        if (hudEnabled) player.hideHud(SpectatorHud.LAYOUT_ID)

        if (hideOtherSpectators && activeSpectators.remove(player.uuid)) {
            player.updateViewableRule()
            rebuildHidingRule()
        }
    }

    fun notifyTargetChanged(player: Player, target: Player) {
        player.setTag(SpectatorTags.CURRENT_TARGET, target.uuid)
        if (player.getTag(SpectatorTags.FREECAM) == true) {
            player.setTag(SpectatorTags.FREECAM_LAST_TARGET, target.uuid)
        }
    }

    fun recordKill(killerName: String?, victimName: String) {
        val safeKiller = killerName?.take(MAX_NAME_LEN) ?: "-"
        val safeVictim = victimName.take(MAX_NAME_LEN)
        lastKillText = "$safeKiller>$safeVictim"
        lastKillTimestamp = System.currentTimeMillis()
    }

    private fun buildHotbar(): Hotbar = hotbar("spectator-toolkit") {
        clearOtherSlots = true

        slot(0, itemStack(Material.COMPASS) {
            name("<green><bold>➤ <reset><gray>Next Player")
            clean()
        }) { player ->
            if (player.getTag(SpectatorTags.FREECAM) == true) exitFreeCamera(player)
            val target = nextTarget(player)
            if (target != null) {
                notifyTargetChanged(player, target)
                player.sendActionBar(player.translate(Keys.Orbit.Spectator.Watching, "name" to target.username))
            }
        }

        slot(1, itemStack(Material.CLOCK) {
            name("<red><bold>◄ <reset><gray>Previous Player")
            clean()
        }) { player ->
            if (player.getTag(SpectatorTags.FREECAM) == true) exitFreeCamera(player)
            val target = previousTarget(player)
            if (target != null) {
                notifyTargetChanged(player, target)
                player.sendActionBar(player.translate(Keys.Orbit.Spectator.Watching, "name" to target.username))
            }
        }

        if (freeCameraEnabled) {
            slot(3, itemStack(Material.ENDER_EYE) {
                name("<light_purple><bold>✦ <reset><gray>Free Camera")
                clean()
            }) { player ->
                toggleFreeCamera(player)
            }
        }

        slot(4, itemStack(Material.PLAYER_HEAD) {
            name("<yellow><bold>☰ <reset><gray>Player List")
            clean()
        }) { player ->
            SpectatorPlayerSelector.open(
                spectator = player,
                candidates = alivePlayers(),
                statsLookup = targetStatsProvider,
            ) { spec, target ->
                if (spec.getTag(SpectatorTags.FREECAM) == true) exitFreeCamera(spec)
                spec.spectate(target)
                notifyTargetChanged(spec, target)
                spec.sendActionBar(spec.translate(Keys.Orbit.Spectator.Watching, "name" to target.username))
            }
        }

        slot(7, itemStack(Material.FEATHER) {
            name("<aqua><bold>» <reset><gray>Speed Toggle")
            clean()
        }) { player ->
            cycleSpeed(player)
        }

        if (onLeave != null) {
            slot(8, itemStack(Material.BARRIER) {
                name("<red><bold>✕ <reset><gray>Leave")
                clean()
            }) { player ->
                onLeave.invoke(player)
            }
        }
    }

    private fun cycleSpeed(player: Player) {
        if (speedSteps.isEmpty()) return
        val index = ((player.getTag(SpectatorTags.SPEED_INDEX) ?: 0) + 1) % speedSteps.size
        player.setTag(SpectatorTags.SPEED_INDEX, index)
        val speed = speedSteps[index]
        player.flyingSpeed = DEFAULT_FLY_SPEED * speed
        player.sendActionBar(player.translate(Keys.Orbit.Spectator.Speed, "speed" to "${speed}x"))
    }

    private fun toggleFreeCamera(player: Player) {
        if (player.getTag(SpectatorTags.FREECAM) == true) {
            exitFreeCamera(player)
        } else {
            enterFreeCamera(player)
        }
    }

    private fun enterFreeCamera(player: Player) {
        val current = player.getTag(SpectatorTags.CURRENT_TARGET)
        if (current != null) player.setTag(SpectatorTags.FREECAM_LAST_TARGET, current)

        val targetPos = current
            ?.let { MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it) }
            ?.position
            ?: player.position

        player.stopSpectating()
        player.teleport(targetPos)
        player.setTag(SpectatorTags.FREECAM, true)
        player.sendActionBar(player.translate(Keys.Orbit.Spectator.Freecam.Enabled))
    }

    private fun exitFreeCamera(player: Player) {
        player.removeTag(SpectatorTags.FREECAM)
        val last = player.getTag(SpectatorTags.FREECAM_LAST_TARGET)
        val target = last?.let { MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it) }
        if (target != null && target.isOnline) {
            player.spectate(target)
            player.setTag(SpectatorTags.CURRENT_TARGET, target.uuid)
        }
        player.sendActionBar(player.translate(Keys.Orbit.Spectator.Freecam.Disabled))
    }

    private fun tickHud() {
        val connectionManager = MinecraftServer.getConnectionManager()
        for (player in connectionManager.onlinePlayers) {
            if (player.getTag(SpectatorTags.ACTIVE) != true) continue
            updatePlayerHud(player)
        }
    }

    private fun updatePlayerHud(player: Player) {
        aliveCountProvider?.let { player.updateHud(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_ALIVE, it().toString()) }
        gameTimerProvider?.let { player.updateHud(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_TIMER, it()) }

        if (System.currentTimeMillis() - lastKillTimestamp < KILL_FEED_TTL_MS) {
            player.updateHud(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_KILLFEED, lastKillText)
        }

        val targetUuid = player.getTag(SpectatorTags.CURRENT_TARGET)
        val target = targetUuid?.let { MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it) }
        if (target != null && target.isOnline) {
            player.updateHud(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_TARGET_NAME, target.username.take(MAX_NAME_LEN))

            val maxHealth = target.getAttributeValue(Attribute.MAX_HEALTH).toFloat().coerceAtLeast(1f)
            val healthSegments = ((target.health / maxHealth) * maxHealthSegments).roundToInt().coerceIn(0, maxHealthSegments)
            player.updateHud(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_TARGET_HEALTH, healthSegments)

            val armor = target.getAttributeValue(Attribute.ARMOR).toFloat().coerceAtLeast(0f)
            val armorSegments = ((armor / 20f) * maxArmorSegments).roundToInt().coerceIn(0, maxArmorSegments)
            player.updateHud(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_TARGET_ARMOR, armorSegments)

            val stats = targetStatsProvider?.invoke(target)
            if (stats != null) {
                player.updateHud(SpectatorHud.LAYOUT_ID, SpectatorHud.ELEMENT_TARGET_KILLS, stats.kills.toString())
            }
        }
    }

    private fun rebuildHidingRule() {
        val snapshot = activeSpectators.toSet()
        val connectionManager = MinecraftServer.getConnectionManager()
        for (uuid in snapshot) {
            val player = connectionManager.getOnlinePlayerByUuid(uuid) ?: continue
            player.updateViewableRule { viewer -> viewer.uuid !in snapshot }
        }
    }

    private companion object {
        const val DEFAULT_FLY_SPEED = 0.05f
        const val KILL_FEED_TTL_MS = 4000L
        const val MAX_NAME_LEN = 12
    }
}

class SpectatorToolkitBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var nextTarget: (Player) -> Player? = { null }
    @PublishedApi internal var previousTarget: (Player) -> Player? = { null }
    @PublishedApi internal var alivePlayers: () -> List<Player> = { emptyList() }
    @PublishedApi internal var speedSteps: List<Float> = listOf(1f, 2f, 4f)
    @PublishedApi internal var onLeave: ((Player) -> Unit)? = null
    @PublishedApi internal var hudEnabled: Boolean = false
    @PublishedApi internal var freeCameraEnabled: Boolean = false
    @PublishedApi internal var hideOtherSpectators: Boolean = false
    @PublishedApi internal var aliveCountProvider: (() -> Int)? = null
    @PublishedApi internal var gameTimerProvider: (() -> String)? = null
    @PublishedApi internal var targetStatsProvider: ((Player) -> SpectatorTargetStats?)? = null
    @PublishedApi internal var maxHealthSegments: Int = 10
    @PublishedApi internal var maxArmorSegments: Int = 10
    @PublishedApi internal var tickIntervalTicks: Int = 10

    fun onNext(handler: (Player) -> Player?) { nextTarget = handler }
    fun onPrevious(handler: (Player) -> Player?) { previousTarget = handler }
    fun alivePlayers(provider: () -> List<Player>) { alivePlayers = provider }
    fun speedSteps(vararg steps: Float) { speedSteps = steps.toList() }
    fun onLeave(handler: (Player) -> Unit) { onLeave = handler }

    fun hud() { hudEnabled = true }
    fun freeCamera() { freeCameraEnabled = true }
    fun hideOtherSpectators() { hideOtherSpectators = true }
    fun aliveCount(provider: () -> Int) { aliveCountProvider = provider }
    fun gameTimer(provider: () -> String) { gameTimerProvider = provider }
    fun targetStats(provider: (Player) -> SpectatorTargetStats?) { targetStatsProvider = provider }
    fun maxHealthSegments(value: Int) { maxHealthSegments = value }
    fun maxArmorSegments(value: Int) { maxArmorSegments = value }
    fun tickInterval(ticks: Int) { tickIntervalTicks = ticks }

    @PublishedApi internal fun build(): SpectatorToolkit = SpectatorToolkit(
        nextTarget, previousTarget, alivePlayers, speedSteps, onLeave,
        hudEnabled, freeCameraEnabled, hideOtherSpectators,
        aliveCountProvider, gameTimerProvider, targetStatsProvider,
        maxHealthSegments, maxArmorSegments, tickIntervalTicks,
    )
}

inline fun spectatorToolkit(block: SpectatorToolkitBuilder.() -> Unit): SpectatorToolkit =
    SpectatorToolkitBuilder().apply(block).build()
