package me.nebula.orbit.utils.nameplate

import me.nebula.gravity.leveling.LevelFormula
import me.nebula.orbit.levelData
import me.nebula.orbit.localeCode
import me.nebula.orbit.rankBadge
import me.nebula.orbit.rankColor
import me.nebula.orbit.rankDisplayName
import me.nebula.orbit.rankName
import me.nebula.orbit.rankPrefix
import me.nebula.orbit.rankSuffix
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.hud.font.HudSpriteRegistry
import me.nebula.orbit.utils.tablist.NegativeSpaceFont
import me.nebula.orbit.utils.vanish.VanishManager
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.scheduler.repeat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.SetPassengersPacket
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import net.minestom.server.network.packet.server.play.TeamsPacket
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val META_NO_GRAVITY = 5
private const val META_DISPLAY_TRANSLATION = 11
private const val META_DISPLAY_SCALE = 12
private const val META_DISPLAY_BILLBOARD = 15
private const val META_DISPLAY_VIEW_RANGE = 17
private const val META_DISPLAY_TEXT = 23
private const val META_DISPLAY_LINE_WIDTH = 24
private const val META_DISPLAY_BACKGROUND = 25
private const val META_DISPLAY_OPACITY = 26
private const val META_DISPLAY_FLAGS = 27

private const val TEAM_NAME = "nn"

fun interface NameplateLine {
    fun render(target: Player, viewer: Player): String?
}

class NameplateLayout(
    val id: String,
    val translationKey: String?,
    val lines: List<NameplateLine>,
    val yOffset: Float = 0.3f,
    val scale: Float = 1.0f,
    val backgroundColor: Int = 0x40000000,
    val seeThrough: Boolean = false,
    val shadow: Boolean = true,
    val lineWidth: Int = 200,
    val viewRange: Float = 1.0f,
)

class NameplateLayoutBuilder @PublishedApi internal constructor(
    @PublishedApi internal val id: String,
) {
    @PublishedApi internal val lines = mutableListOf<NameplateLine>()
    @PublishedApi internal var translationKey: String? = null
    @PublishedApi internal var yOffset: Float = 0.3f
    @PublishedApi internal var scale: Float = 1.0f
    @PublishedApi internal var backgroundColor: Int = 0x40000000
    @PublishedApi internal var seeThrough: Boolean = false
    @PublishedApi internal var shadow: Boolean = true
    @PublishedApi internal var lineWidth: Int = 200
    @PublishedApi internal var viewRange: Float = 1.0f

    fun translationKey(key: String) { translationKey = key }

    fun staticLine(text: String) {
        lines += NameplateLine { _, _ -> text }
    }

    fun line(provider: (Player) -> String) {
        lines += NameplateLine { target, _ -> provider(target) }
    }

    fun translatedLine(key: String, vararg staticArgs: Pair<String, String>) {
        lines += NameplateLine { _, viewer ->
            val locale = viewer.localeCode
            var template = me.nebula.orbit.Orbit.translations.get(key, locale) ?: return@NameplateLine null
            for ((k, v) in staticArgs) template = template.replace("{$k}", v)
            template
        }
    }

    fun translatedLine(key: String, argsProvider: (Player) -> Array<Pair<String, String>>) {
        lines += NameplateLine { target, viewer ->
            val locale = viewer.localeCode
            var template = me.nebula.orbit.Orbit.translations.get(key, locale) ?: return@NameplateLine null
            for ((k, v) in argsProvider(target)) template = template.replace("{$k}", v)
            template
        }
    }

    fun conditionalLine(condition: (Player) -> Boolean, provider: (Player) -> String) {
        lines += NameplateLine { target, _ -> if (condition(target)) provider(target) else null }
    }

    fun perViewerLine(provider: (Player, Player) -> String?) {
        lines += NameplateLine { target, viewer -> provider(target, viewer) }
    }

    fun blankLine() {
        lines += NameplateLine { _, _ -> " " }
    }

    fun yOffset(value: Float) { yOffset = value }
    fun scale(value: Float) { scale = value }
    fun backgroundColor(argb: Int) { backgroundColor = argb }
    fun transparentBackground() { backgroundColor = 0 }
    fun seeThrough(value: Boolean) { seeThrough = value }
    fun shadow(value: Boolean) { shadow = value }
    fun lineWidth(value: Int) { lineWidth = value }
    fun viewRange(value: Float) { viewRange = value }

    @PublishedApi internal fun build(): NameplateLayout =
        NameplateLayout(id, translationKey, lines.toList(), yOffset, scale, backgroundColor, seeThrough, shadow, lineWidth, viewRange)
}

fun nameplateLayout(id: String, block: NameplateLayoutBuilder.() -> Unit): NameplateLayout =
    NameplateLayoutBuilder(id).apply(block).build()

class ActiveNameplate(
    val target: Player,
    val layout: NameplateLayout,
    val entityId: Int,
    val entityUuid: UUID,
) {
    val viewers = ConcurrentHashMap.newKeySet<UUID>()
    val lastRendered = ConcurrentHashMap<UUID, Component>()
    var needsResync = ConcurrentHashMap.newKeySet<UUID>()
}

object NameplateManager {

    private val entityIdCounter = AtomicInteger(-5_000_000)
    private val activePlates = ConcurrentHashMap<UUID, ActiveNameplate>()
    private val teamMembers = ConcurrentHashMap.newKeySet<String>()
    private var activeLayout: NameplateLayout? = null
    private var refreshTask: Task? = null
    private var refreshTicks: Int = 10
    private var eventNode: EventNode<Event>? = null

    fun setLayout(layout: NameplateLayout, refreshIntervalTicks: Int = 10) {
        activeLayout = layout
        refreshTicks = refreshIntervalTicks
    }

    fun apply(player: Player) {
        val layout = activeLayout ?: return
        remove(player)
        val eid = entityIdCounter.getAndDecrement()
        val plate = ActiveNameplate(player, layout, eid, UUID.randomUUID())
        activePlates[player.uuid] = plate
        hideVanillaName(player)
    }

    fun remove(player: Player) {
        val plate = activePlates.remove(player.uuid) ?: return
        val destroy = DestroyEntitiesPacket(listOf(plate.entityId))
        for (viewerUuid in plate.viewers) {
            findPlayer(viewerUuid)?.sendPacket(destroy)
        }
        restoreVanillaName(player)
    }

    fun refresh(target: Player) {
        val plate = activePlates[target.uuid] ?: return
        for (viewerUuid in plate.viewers) {
            val viewer = findPlayer(viewerUuid) ?: continue
            updateForViewer(plate, viewer)
        }
    }

    fun refreshAll() {
        val toRemove = mutableListOf<UUID>()
        for ((uuid, plate) in activePlates) {
            val target = findPlayer(uuid)
            if (target == null) {
                toRemove += uuid
                continue
            }
            val targetInstance = target.instance ?: continue
            val staleViewers = mutableListOf<UUID>()
            for (viewerUuid in plate.viewers) {
                val viewer = findPlayer(viewerUuid)
                if (viewer == null || viewer.instance != targetInstance) {
                    staleViewers += viewerUuid
                    continue
                }
                updateForViewer(plate, viewer)
                if (plate.needsResync.remove(viewerUuid)) {
                    viewer.sendPacket(SetPassengersPacket(target.entityId, listOf(plate.entityId)))
                }
            }
            for (stale in staleViewers) {
                plate.viewers.remove(stale)
                plate.lastRendered.remove(stale)
            }
        }
        for (uuid in toRemove) {
            activePlates.remove(uuid)
        }
    }

    fun showTo(target: Player, viewer: Player) {
        if (!VanishManager.canSee(viewer, target)) return
        val plate = activePlates[target.uuid] ?: return
        if (!plate.viewers.add(viewer.uuid)) return
        spawnForViewer(plate, viewer)
        updateForViewer(plate, viewer)
    }

    fun hideTo(target: Player, viewer: Player) {
        val plate = activePlates[target.uuid] ?: return
        if (!plate.viewers.remove(viewer.uuid)) return
        plate.lastRendered.remove(viewer.uuid)
        plate.needsResync.remove(viewer.uuid)
        viewer.sendPacket(DestroyEntitiesPacket(listOf(plate.entityId)))
    }

    fun markResync(playerUuid: UUID) {
        val plate = activePlates[playerUuid] ?: return
        plate.needsResync.addAll(plate.viewers)
    }

    fun install(handler: EventNode<Event>) {
        val node = EventNode.all("nameplate-manager")
        this.eventNode = node

        node.addListener(PlayerSpawnEvent::class.java) { event ->
            val joiner = event.player

            if (activeLayout != null) {
                apply(joiner)
            }

            sendTeamToPlayer(joiner)

            val joinerInstance = joiner.instance ?: return@addListener

            for ((uuid, _) in activePlates) {
                if (uuid == joiner.uuid) continue
                val target = findPlayer(uuid) ?: continue
                if (target.instance != joinerInstance) continue
                if (!VanishManager.canSee(joiner, target)) continue
                showTo(target, joiner)
            }

            val joinerPlate = activePlates[joiner.uuid]
            if (joinerPlate != null) {
                for (other in joinerInstance.players) {
                    if (other.uuid == joiner.uuid) continue
                    if (!VanishManager.canSee(other, joiner)) continue
                    showTo(joiner, other)
                }
            }
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            remove(event.player)
            for ((_, plate) in activePlates) {
                plate.viewers.remove(event.player.uuid)
                plate.lastRendered.remove(event.player.uuid)
                plate.needsResync.remove(event.player.uuid)
            }
        }

        handler.addChild(node)
        refreshTask = repeat(refreshTicks) { refreshAll() }
    }

    fun uninstall() {
        refreshTask?.cancel()
        refreshTask = null
        eventNode?.let { node ->
            node.parent?.removeChild(node)
        }
        eventNode = null
        for ((_, plate) in activePlates) {
            val destroy = DestroyEntitiesPacket(listOf(plate.entityId))
            for (viewerUuid in plate.viewers) {
                findPlayer(viewerUuid)?.sendPacket(destroy)
            }
        }
        activePlates.clear()
        teamMembers.clear()
    }

    private fun hideVanillaName(player: Player) {
        player.isCustomNameVisible = false
        if (teamMembers.add(player.username)) {
            val addMember = TeamsPacket(
                TEAM_NAME,
                TeamsPacket.AddEntitiesToTeamAction(listOf(player.username)),
            )
            for (online in MinecraftServer.getConnectionManager().onlinePlayers) {
                online.sendPacket(addMember)
            }
        }
    }

    private fun restoreVanillaName(player: Player) {
        player.isCustomNameVisible = true
        if (teamMembers.remove(player.username)) {
            val removeMember = TeamsPacket(
                TEAM_NAME,
                TeamsPacket.RemoveEntitiesToTeamAction(listOf(player.username)),
            )
            for (online in MinecraftServer.getConnectionManager().onlinePlayers) {
                online.sendPacket(removeMember)
            }
        }
    }

    private fun sendTeamToPlayer(player: Player) {
        val members = teamMembers.toList()
        if (members.isEmpty()) return
        player.sendPacket(TeamsPacket(
            TEAM_NAME,
            TeamsPacket.CreateTeamAction(
                Component.empty(),
                0x00,
                TeamsPacket.NameTagVisibility.NEVER,
                TeamsPacket.CollisionRule.ALWAYS,
                NamedTextColor.WHITE,
                Component.empty(),
                Component.empty(),
                members,
            ),
        ))
    }

    private fun spawnForViewer(plate: ActiveNameplate, viewer: Player) {
        val pos = plate.target.position
        viewer.sendPacket(SpawnEntityPacket(
            plate.entityId, plate.entityUuid, EntityType.TEXT_DISPLAY,
            pos, pos.yaw(), 0, Vec.ZERO,
        ))

        val layout = plate.layout
        var flags: Byte = 0
        if (layout.shadow) flags = (flags.toInt() or 0x01).toByte()
        if (layout.seeThrough) flags = (flags.toInt() or 0x02).toByte()

        viewer.sendPacket(EntityMetaDataPacket(plate.entityId, mapOf(
            META_NO_GRAVITY to Metadata.Boolean(true),
            META_DISPLAY_TRANSLATION to Metadata.Vector3(Vec(0.0, layout.yOffset.toDouble(), 0.0)),
            META_DISPLAY_SCALE to Metadata.Vector3(Vec(layout.scale.toDouble(), layout.scale.toDouble(), layout.scale.toDouble())),
            META_DISPLAY_BILLBOARD to Metadata.Byte(AbstractDisplayMeta.BillboardConstraints.CENTER.ordinal.toByte()),
            META_DISPLAY_VIEW_RANGE to Metadata.Float(layout.viewRange),
            META_DISPLAY_LINE_WIDTH to Metadata.VarInt(layout.lineWidth),
            META_DISPLAY_BACKGROUND to Metadata.VarInt(layout.backgroundColor),
            META_DISPLAY_OPACITY to Metadata.Byte((-1).toByte()),
            META_DISPLAY_FLAGS to Metadata.Byte(flags),
        )))

        viewer.sendPacket(SetPassengersPacket(plate.target.entityId, listOf(plate.entityId)))
    }

    private fun updateForViewer(plate: ActiveNameplate, viewer: Player) {
        val rendered = renderText(plate, viewer)
        val previous = plate.lastRendered[viewer.uuid]
        if (rendered == previous) return
        plate.lastRendered[viewer.uuid] = rendered
        viewer.sendPacket(EntityMetaDataPacket(plate.entityId, mapOf(
            META_DISPLAY_TEXT to Metadata.Component(rendered),
        )))
    }

    private fun renderText(plate: ActiveNameplate, viewer: Player): Component {
        val layout = plate.layout
        if (layout.translationKey != null) {
            val args = buildTranslationArgs(plate.target).map { (k, v) -> k to resolveInlineTags(v) }
            return viewer.translate(layout.translationKey, *args.toTypedArray())
        }
        val parts = mutableListOf<Component>()
        for (line in layout.lines) {
            val text = line.render(plate.target, viewer) ?: continue
            parts += miniMessage.deserialize(resolveInlineTags(text))
        }
        return Component.join(JoinConfiguration.separator(Component.newline()), parts)
    }

    private fun buildTranslationArgs(target: Player): Array<Pair<String, String>> {
        val levelData = target.levelData
        val progress = LevelFormula.progressPercent(levelData.xp)
        val progressPct = "%.0f".format(progress * 100)
        return arrayOf(
            "player" to target.username,
            "display_name" to (target.displayName?.let { miniMessage.serialize(it) } ?: target.username),
            "rank" to target.rankName,
            "rank_display" to target.rankDisplayName,
            "rank_prefix" to target.rankPrefix,
            "rank_suffix" to target.rankSuffix,
            "rank_badge" to target.rankBadge,
            "rank_color" to target.rankColor,
            "level" to levelData.level.toString(),
            "prestige" to levelData.prestige.toString(),
            "xp_progress" to progressPct,
        )
    }

    private fun findPlayer(uuid: UUID): Player? =
        MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
}

fun Player.applyNameplate() = NameplateManager.apply(this)
fun Player.removeNameplate() = NameplateManager.remove(this)
fun Player.refreshNameplate() = NameplateManager.refresh(this)

private val INLINE_PATTERN = Regex("\\{(sprite|shift):([a-zA-Z0-9_-]+)}")

fun resolveInlineTags(text: String): String = INLINE_PATTERN.replace(text) { match ->
    when (match.groupValues[1]) {
        "sprite" -> {
            val sprite = HudSpriteRegistry.getOrNull(match.groupValues[2]) ?: return@replace match.value
            val chars = sprite.columns.joinToString("") { it.char.toString() }
            "<font:minecraft:hud>$chars</font>"
        }
        "shift" -> {
            val px = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            NegativeSpaceFont.shift(px)
        }
        else -> match.value
    }
}

