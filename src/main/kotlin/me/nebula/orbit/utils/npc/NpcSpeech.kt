package me.nebula.orbit.utils.npc

import me.nebula.orbit.Orbit
import me.nebula.orbit.localeCode
import net.kyori.adventure.text.Component
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

private val bubbleEntityIds = AtomicInteger(-6_000_000)

private const val META_NO_GRAVITY = 5
private const val META_DISPLAY_TRANSLATION = 11
private const val META_DISPLAY_SCALE = 12
private const val META_DISPLAY_BILLBOARD = 15
private const val META_DISPLAY_VIEW_RANGE = 16
private const val META_DISPLAY_TEXT = 23
private const val META_DISPLAY_LINE_WIDTH = 24
private const val META_DISPLAY_BACKGROUND = 25
private const val META_DISPLAY_OPACITY = 26
private const val META_DISPLAY_FLAGS = 27
private const val META_DISPLAY_INTERPOLATION_DELAY = 8
private const val META_DISPLAY_TRANSFORM_DURATION = 9

class SpeechLine(
    val key: String,
    val staticArgs: Map<String, String> = emptyMap(),
    val dynamicArgs: ((Player) -> Map<String, String>)? = null,
    val weight: Int = 1,
)

class SpeechBubbleConfig(
    val lines: List<SpeechLine>,
    val minIntervalTicks: Int = 100,
    val maxIntervalTicks: Int = 300,
    val displayTicks: Int = 80,
    val bubbleYOffset: Float = 2.5f,
    val bubbleScale: Float = 0.8f,
    val backgroundColor: Int = 0xC0000000.toInt(),
    val range: Double = 16.0,
)

class SpeechBubbleConfigBuilder @PublishedApi internal constructor() {
    @PublishedApi internal val lines = mutableListOf<SpeechLine>()
    @PublishedApi internal var minInterval: Int = 100
    @PublishedApi internal var maxInterval: Int = 300
    @PublishedApi internal var displayTicks: Int = 80
    @PublishedApi internal var yOffset: Float = 2.5f
    @PublishedApi internal var scale: Float = 0.8f
    @PublishedApi internal var background: Int = 0xC0000000.toInt()
    @PublishedApi internal var range: Double = 16.0

    fun line(key: String, weight: Int = 1) { lines += SpeechLine(key, weight = weight) }
    fun line(key: String, vararg args: Pair<String, String>, weight: Int = 1) { lines += SpeechLine(key, args.toMap(), weight = weight) }
    fun line(key: String, weight: Int = 1, args: (Player) -> Map<String, String>) { lines += SpeechLine(key, dynamicArgs = args, weight = weight) }
    fun interval(min: Int, max: Int) { minInterval = min; maxInterval = max }
    fun displayTime(ticks: Int) { displayTicks = ticks }
    fun yOffset(value: Float) { yOffset = value }
    fun scale(value: Float) { scale = value }
    fun background(argb: Int) { background = argb }
    fun range(blocks: Double) { range = blocks }

    @PublishedApi internal fun build(): SpeechBubbleConfig = SpeechBubbleConfig(
        lines.toList(), minInterval, maxInterval, displayTicks, yOffset, scale, background, range,
    )
}

class ActiveBubble(
    val entityId: Int,
    val entityUuid: UUID,
    val shownTo: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),
    var ticksRemaining: Int,
)

class NpcSpeechManager(
    val npc: Npc,
    val config: SpeechBubbleConfig,
) {
    @Volatile var nextSpeechIn: Int = rollInterval()
    @Volatile var activeBubble: ActiveBubble? = null

    fun tick() {
        val bubble = activeBubble
        if (bubble != null) {
            bubble.ticksRemaining--
            if (bubble.ticksRemaining <= 0) {
                dismissBubble(bubble)
                activeBubble = null
                nextSpeechIn = rollInterval()
            }
            return
        }

        nextSpeechIn--
        if (nextSpeechIn <= 0) {
            speak()
        }
    }

    fun speak() {
        val line = pickLine() ?: return
        val eid = bubbleEntityIds.getAndDecrement()
        val euuid = UUID.randomUUID()
        val bubble = ActiveBubble(eid, euuid, ticksRemaining = config.displayTicks)
        activeBubble = bubble

        val viewers = npc.viewers
        for (viewerUuid in viewers) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(viewerUuid) ?: continue
            if (npc.position.distance(player.position) > config.range) continue
            showBubbleTo(player, bubble, line)
        }
    }

    fun showBubbleTo(player: Player, bubble: ActiveBubble, line: SpeechLine) {
        val text = resolveText(player, line)
        val bubbleText = miniMessage.deserialize("<white>\u2709 $text")
        val pos = npc.position.add(0.0, config.bubbleYOffset.toDouble(), 0.0)

        player.sendPacket(SpawnEntityPacket(
            bubble.entityId, bubble.entityUuid, EntityType.TEXT_DISPLAY,
            pos, 0f, 0, Vec.ZERO,
        ))
        player.sendPacket(EntityMetaDataPacket(bubble.entityId, mapOf(
            META_NO_GRAVITY to Metadata.Boolean(true),
            META_DISPLAY_INTERPOLATION_DELAY to Metadata.VarInt(0),
            META_DISPLAY_TRANSFORM_DURATION to Metadata.VarInt(5),
            META_DISPLAY_TRANSLATION to Metadata.Vector3(Vec(0.0, 0.0, 0.0)),
            META_DISPLAY_SCALE to Metadata.Vector3(Vec(config.bubbleScale.toDouble(), config.bubbleScale.toDouble(), config.bubbleScale.toDouble())),
            META_DISPLAY_BILLBOARD to Metadata.Byte(AbstractDisplayMeta.BillboardConstraints.CENTER.ordinal.toByte()),
            META_DISPLAY_VIEW_RANGE to Metadata.Float(0.5f),
            META_DISPLAY_TEXT to Metadata.Component(bubbleText),
            META_DISPLAY_LINE_WIDTH to Metadata.VarInt(200),
            META_DISPLAY_BACKGROUND to Metadata.VarInt(config.backgroundColor),
            META_DISPLAY_OPACITY to Metadata.Byte((-1).toByte()),
            META_DISPLAY_FLAGS to Metadata.Byte(0x01),
        )))
        bubble.shownTo.add(player.uuid)
    }

    fun dismissBubble(bubble: ActiveBubble) {
        val packet = DestroyEntitiesPacket(listOf(bubble.entityId))
        for (uuid in bubble.shownTo) {
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.sendPacket(packet)
        }
        bubble.shownTo.clear()
    }

    fun onViewerAdd(player: Player) {
        val bubble = activeBubble ?: return
        if (bubble.ticksRemaining <= 0) return
        if (npc.position.distance(player.position) > config.range) return
        val line = pickLine() ?: return
        showBubbleTo(player, bubble, line)
    }

    fun onViewerRemove(player: Player) {
        val bubble = activeBubble ?: return
        if (bubble.shownTo.remove(player.uuid)) {
            player.sendPacket(DestroyEntitiesPacket(listOf(bubble.entityId)))
        }
    }

    fun destroy() {
        activeBubble?.let { dismissBubble(it) }
        activeBubble = null
    }

    private fun resolveText(player: Player, line: SpeechLine): String {
        val locale = player.localeCode
        var text = Orbit.translations.get(line.key, locale) ?: line.key
        for ((k, v) in line.staticArgs) text = text.replace("{$k}", v)
        line.dynamicArgs?.invoke(player)?.forEach { (k, v) -> text = text.replace("{$k}", v) }
        return text
    }

    private fun pickLine(): SpeechLine? {
        if (config.lines.isEmpty()) return null
        val totalWeight = config.lines.sumOf { it.weight }
        var roll = Random.nextInt(totalWeight)
        for (line in config.lines) {
            roll -= line.weight
            if (roll < 0) return line
        }
        return config.lines.last()
    }

    private fun rollInterval(): Int =
        Random.nextInt(config.minIntervalTicks, config.maxIntervalTicks + 1)
}

object NpcSpeechTicker {

    private val managers = ConcurrentHashMap<Int, NpcSpeechManager>()
    private var task: Task? = null

    fun register(npc: Npc, manager: NpcSpeechManager) {
        managers[npc.entityId] = manager
        if (task == null) {
            task = repeat(5) { managers.values.forEach { it.tick() } }
        }
    }

    fun unregister(npc: Npc) {
        managers.remove(npc.entityId)?.destroy()
    }

    fun get(npc: Npc): NpcSpeechManager? = managers[npc.entityId]
}
