package me.nebula.orbit.utils.modelengine.behavior

import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.network.packet.server.play.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val nextTagId = AtomicInteger(-3_600_000)

private const val META_NO_GRAVITY = 5
private const val META_SCALE = 12
private const val META_BILLBOARD = 15
private const val META_TEXT = 23
private const val META_BACKGROUND = 25
private const val META_OPACITY = 26
private const val META_FLAGS = 27

class NameTagBehavior(
    override val bone: ModelBone,
    text: Component,
    private val yOffset: Double = 0.3,
    private val backgroundColor: Int = 0,
    private val seeThrough: Boolean = false,
    private val scale: Float = 1f,
) : BoneBehavior {

    private val tagEntityId = nextTagId.getAndDecrement()
    private val tagUuid = UUID.randomUUID()
    private val viewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private var lastTagPos: Vec = Vec.ZERO

    var text: Component = text
        set(value) {
            field = value
            val packet = EntityMetaDataPacket(tagEntityId, mapOf(META_TEXT to Metadata.Component(value)))
            viewers.forEach { uuid -> findPlayer(uuid)?.sendPacket(packet) }
        }

    override fun onAdd(modeledEntity: ModeledEntity) {
        modeledEntity.viewers.forEach { uuid ->
            if (viewers.add(uuid)) findPlayer(uuid)?.let { showTag(it, modeledEntity) }
        }
    }

    override fun tick(modeledEntity: ModeledEntity) {
        val newViewers = modeledEntity.viewers
        newViewers.forEach { uuid ->
            if (viewers.add(uuid)) findPlayer(uuid)?.let { showTag(it, modeledEntity) }
        }
        viewers.removeAll { uuid ->
            if (uuid !in newViewers) {
                findPlayer(uuid)?.sendPacket(DestroyEntitiesPacket(listOf(tagEntityId)))
                true
            } else false
        }

        val transform = bone.globalTransform
        val worldPos = transform.toWorldPosition(modeledEntity.owner.position)
        val tagPos = worldPos.add(0.0, yOffset, 0.0)

        if (tagPos != lastTagPos) {
            lastTagPos = tagPos
            val teleport = EntityTeleportPacket(
                tagEntityId,
                Pos(tagPos.x(), tagPos.y(), tagPos.z()),
                Vec.ZERO, 0, false,
            )
            viewers.forEach { uuid -> findPlayer(uuid)?.sendPacket(teleport) }
        }
    }

    override fun onRemove(modeledEntity: ModeledEntity) {
        val packet = DestroyEntitiesPacket(listOf(tagEntityId))
        viewers.forEach { uuid -> findPlayer(uuid)?.sendPacket(packet) }
        viewers.clear()
    }

    override fun evictViewer(uuid: UUID) {
        viewers.remove(uuid)
    }

    private fun showTag(player: Player, modeledEntity: ModeledEntity) {
        val transform = bone.globalTransform
        val worldPos = transform.toWorldPosition(modeledEntity.owner.position)
        val tagPos = worldPos.add(0.0, yOffset, 0.0)
        lastTagPos = tagPos

        player.sendPacket(SpawnEntityPacket(
            tagEntityId, tagUuid, EntityType.TEXT_DISPLAY,
            Pos(tagPos.x(), tagPos.y(), tagPos.z()), 0f, 0, Vec.ZERO,
        ))

        val flags: Byte = if (seeThrough) 0x02 else 0x00
        player.sendPacket(EntityMetaDataPacket(tagEntityId, mapOf(
            META_NO_GRAVITY to Metadata.Boolean(true),
            META_SCALE to Metadata.Vector3(Vec(scale.toDouble(), scale.toDouble(), scale.toDouble())),
            META_BILLBOARD to Metadata.Byte(AbstractDisplayMeta.BillboardConstraints.CENTER.ordinal.toByte()),
            META_TEXT to Metadata.Component(text),
            META_BACKGROUND to Metadata.VarInt(backgroundColor),
            META_OPACITY to Metadata.Byte((-1).toByte()),
            META_FLAGS to Metadata.Byte(flags),
        )))
    }

    private fun findPlayer(uuid: UUID): Player? =
        MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == uuid }
}
