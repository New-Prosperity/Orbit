package me.nebula.orbit.utils.hologram

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val miniMessage = MiniMessage.miniMessage()
private val nextEntityId = AtomicInteger(-1_000_000)

private const val META_NO_GRAVITY = 5
private const val META_SCALE = 12
private const val META_BILLBOARD = 15
private const val META_TEXT = 23
private const val META_BACKGROUND_COLOR = 25
private const val META_TEXT_OPACITY = 26
private const val META_TEXT_FLAGS = 27

class HologramBuilder {
    val lines: MutableList<Component> = mutableListOf()
    var lineSpacing: Double = 0.3
    var billboard: AbstractDisplayMeta.BillboardConstraints = AbstractDisplayMeta.BillboardConstraints.VERTICAL
    var backgroundColor: Int = 0x40000000
    var seeThrough: Boolean = false
    var scale: Float = 1.0f

    fun line(text: Component) {
        lines += text
    }

    fun line(text: String) {
        lines += miniMessage.deserialize(text)
    }
}

class Hologram internal constructor(
    private val entities: MutableList<Entity>,
    private val instance: Instance,
    private var position: Pos,
    private val builder: HologramBuilder
) {

    val lines: List<Component> get() = entities.map { (it.entityMeta as TextDisplayMeta).text }

    fun updateLine(index: Int, text: Component) {
        require(index in entities.indices) { "Line index $index out of bounds" }
        (entities[index].entityMeta as TextDisplayMeta).text = text
    }

    fun addLine(text: Component) {
        val entity = createTextDisplayEntity(text, builder)
        val offset = entities.size * builder.lineSpacing
        entity.setInstance(instance, position.add(0.0, offset, 0.0))
        entities += entity
    }

    fun removeLine(index: Int) {
        require(index in entities.indices) { "Line index $index out of bounds" }
        entities.removeAt(index).remove()
        repositionEntities()
    }

    fun teleport(pos: Pos) {
        position = pos
        repositionEntities()
    }

    fun remove() {
        entities.forEach { it.remove() }
        entities.clear()
    }

    private fun repositionEntities() {
        entities.forEachIndexed { i, entity ->
            entity.teleport(position.add(0.0, i * builder.lineSpacing, 0.0))
        }
    }
}

class PlayerHologram internal constructor(
    private val position: Pos,
    private val builder: HologramBuilder
) {

    private data class VirtualLine(val entityId: Int, val uuid: UUID, var text: Component)

    private val virtualLines: MutableList<VirtualLine> = builder.lines.mapIndexed { i, text ->
        VirtualLine(nextEntityId.getAndDecrement(), UUID.randomUUID(), text)
    }.toMutableList()

    private val _viewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val viewers: Set<UUID> get() = _viewers.toSet()

    fun show(player: Player) {
        if (!_viewers.add(player.uuid)) return
        virtualLines.forEachIndexed { i, line ->
            val pos = position.add(0.0, i * builder.lineSpacing, 0.0)
            player.sendPacket(
                SpawnEntityPacket(
                    line.entityId, line.uuid, EntityType.TEXT_DISPLAY,
                    pos, pos.yaw(), 0, Vec.ZERO
                )
            )
            player.sendPacket(buildMetadataPacket(line.entityId, line.text))
        }
    }

    fun hide(player: Player) {
        if (!_viewers.remove(player.uuid)) return
        player.sendPacket(DestroyEntitiesPacket(virtualLines.map { it.entityId }))
    }

    fun updateLine(index: Int, text: Component) {
        require(index in virtualLines.indices) { "Line index $index out of bounds" }
        virtualLines[index].text = text
        val line = virtualLines[index]
        val packet = EntityMetaDataPacket(line.entityId, mapOf(META_TEXT to Metadata.Component(text)))
        forEachViewer { it.sendPacket(packet) }
    }

    fun remove() {
        val packet = DestroyEntitiesPacket(virtualLines.map { it.entityId })
        forEachViewer { it.sendPacket(packet) }
        _viewers.clear()
    }

    private fun buildMetadataPacket(entityId: Int, text: Component): EntityMetaDataPacket {
        val flags: Byte = if (builder.seeThrough) 0x02 else 0x00
        val entries = mapOf(
            META_NO_GRAVITY to Metadata.Boolean(true),
            META_SCALE to Metadata.Vector3(Vec(builder.scale.toDouble(), builder.scale.toDouble(), builder.scale.toDouble())),
            META_BILLBOARD to Metadata.Byte(builder.billboard.ordinal.toByte()),
            META_TEXT to Metadata.Component(text),
            META_BACKGROUND_COLOR to Metadata.VarInt(builder.backgroundColor),
            META_TEXT_OPACITY to Metadata.Byte((-1).toByte()),
            META_TEXT_FLAGS to Metadata.Byte(flags)
        )
        return EntityMetaDataPacket(entityId, entries)
    }

    private inline fun forEachViewer(action: (Player) -> Unit) {
        _viewers.forEach { uuid ->
            net.minestom.server.MinecraftServer.getConnectionManager()
                .onlinePlayers.firstOrNull { it.uuid == uuid }?.let(action)
        }
    }
}

private fun createTextDisplayEntity(text: Component, builder: HologramBuilder): Entity {
    val entity = Entity(EntityType.TEXT_DISPLAY)
    val meta = entity.entityMeta as TextDisplayMeta
    meta.setNotifyAboutChanges(false)
    meta.setHasNoGravity(true)
    meta.text = text
    meta.setBillboardRenderConstraints(builder.billboard)
    meta.setBackgroundColor(builder.backgroundColor)
    meta.setTextOpacity((-1).toByte())
    meta.isSeeThrough = builder.seeThrough
    meta.setScale(Vec(builder.scale.toDouble(), builder.scale.toDouble(), builder.scale.toDouble()))
    meta.setNotifyAboutChanges(true)
    return entity
}

fun Instance.hologram(pos: Pos, block: HologramBuilder.() -> Unit): Hologram {
    val builder = HologramBuilder().apply(block)
    val entities = builder.lines.mapIndexed { i, text ->
        val entity = createTextDisplayEntity(text, builder)
        entity.setInstance(this, pos.add(0.0, i * builder.lineSpacing, 0.0))
        entity
    }.toMutableList()
    return Hologram(entities, this, pos, builder)
}

fun Player.hologram(pos: Pos, block: HologramBuilder.() -> Unit): PlayerHologram {
    val builder = HologramBuilder().apply(block)
    val hologram = PlayerHologram(pos, builder)
    hologram.show(this)
    return hologram
}

fun Player.showHologram(hologram: PlayerHologram) = hologram.show(this)
fun Player.hideHologram(hologram: PlayerHologram) = hologram.hide(this)
