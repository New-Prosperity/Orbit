package me.nebula.orbit.utils.modelengine.render

import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.math.QUAT_IDENTITY
import me.nebula.orbit.utils.modelengine.math.Quat
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val nextEntityId = AtomicInteger(-3_000_000)

private const val META_INTERPOLATION_DELAY = 8
private const val META_TRANSFORM_DURATION = 9
private const val META_TRANSLATION = 11
private const val META_SCALE = 12
private const val META_ROTATION_LEFT = 13
private const val META_ROTATION_RIGHT = 14
private const val META_BILLBOARD = 15
private const val META_BRIGHTNESS = 16
private const val META_VIEW_RANGE = 17
private const val META_GLOW_COLOR = 22
private const val META_DISPLAYED_ITEM = 23
private const val META_DISPLAY_TYPE = 24
private const val META_NO_GRAVITY = 5

class BoneEntity(
    val entityId: Int = nextEntityId.getAndDecrement(),
    val uuid: UUID = UUID.randomUUID(),
    val bone: ModelBone,
) {
    var lastPosition: Vec = Vec.ZERO
    var lastLeftRotation: Quat = QUAT_IDENTITY
    var lastRightRotation: Quat = QUAT_IDENTITY
    var lastScale: Vec = Vec(1.0, 1.0, 1.0)
    var lastItem: ItemStack? = null
    var lastVisible: Boolean = true
    var spawned: Boolean = false
}

class BoneRenderer(
    private val interpolationDuration: Int = 1,
) {
    private val _viewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val viewers: Set<UUID> get() = _viewers

    private val boneEntities = ConcurrentHashMap<String, BoneEntity>()

    fun registerBone(bone: ModelBone) {
        boneEntities.computeIfAbsent(bone.blueprint.name) { BoneEntity(bone = bone) }
    }

    fun unregisterBone(boneName: String) {
        val entity = boneEntities.remove(boneName) ?: return
        if (entity.spawned) {
            val destroyPacket = DestroyEntitiesPacket(listOf(entity.entityId))
            forEachViewer { it.sendPacket(destroyPacket) }
        }
    }

    fun show(player: Player, modelPosition: Pos) {
        if (!_viewers.add(player.uuid)) return
        boneEntities.values.forEach { boneEntity ->
            if (boneEntity.bone.visible && boneEntity.bone.modelItem != null) {
                spawnBoneFor(player, boneEntity, modelPosition)
            }
        }
    }

    fun hide(player: Player) {
        if (!_viewers.remove(player.uuid)) return
        val ids = boneEntities.values.filter { it.spawned }.map { it.entityId }
        if (ids.isNotEmpty()) {
            player.sendPacket(DestroyEntitiesPacket(ids))
        }
    }

    fun update(modelPosition: Pos) {
        boneEntities.values.forEach { boneEntity ->
            val bone = boneEntity.bone
            val shouldBeVisible = bone.visible && bone.modelItem != null

            if (shouldBeVisible && !boneEntity.spawned) {
                forEachViewer { spawnBoneFor(it, boneEntity, modelPosition) }
                return@forEach
            }

            if (!shouldBeVisible && boneEntity.spawned) {
                val destroyPacket = DestroyEntitiesPacket(listOf(boneEntity.entityId))
                forEachViewer { it.sendPacket(destroyPacket) }
                boneEntity.spawned = false
                return@forEach
            }

            if (!boneEntity.spawned) return@forEach

            val transform = bone.globalTransform
            val worldPos = transform.toWorldPosition(modelPosition)
            val worldLeftRot = transform.toWorldRotation(modelPosition.yaw())

            val meta = buildDirtyMetadata(boneEntity, worldPos, worldLeftRot, transform.rightRotation, transform.scale, bone.modelItem)
            if (meta.isNotEmpty()) {
                val packet = EntityMetaDataPacket(boneEntity.entityId, meta)
                forEachViewer { it.sendPacket(packet) }
            }
        }
    }

    fun destroy() {
        val ids = boneEntities.values.filter { it.spawned }.map { it.entityId }
        if (ids.isNotEmpty()) {
            val destroyPacket = DestroyEntitiesPacket(ids)
            forEachViewer { it.sendPacket(destroyPacket) }
        }
        _viewers.clear()
        boneEntities.clear()
    }

    fun evictViewer(uuid: UUID) {
        _viewers.remove(uuid)
    }

    fun boneEntity(boneName: String): BoneEntity? = boneEntities[boneName]

    private fun spawnBoneFor(player: Player, boneEntity: BoneEntity, modelPosition: Pos) {
        val transform = boneEntity.bone.globalTransform
        val worldPos = transform.toWorldPosition(modelPosition)

        player.sendPacket(SpawnEntityPacket(
            boneEntity.entityId, boneEntity.uuid, EntityType.ITEM_DISPLAY,
            Pos.ZERO, 0f, 0, Vec.ZERO,
        ))

        val worldLeftRot = transform.toWorldRotation(modelPosition.yaw())
        val meta = buildFullMetadata(boneEntity, worldPos, worldLeftRot, transform.rightRotation, transform.scale, boneEntity.bone.modelItem)
        player.sendPacket(EntityMetaDataPacket(boneEntity.entityId, meta))

        boneEntity.lastPosition = worldPos
        boneEntity.lastLeftRotation = worldLeftRot
        boneEntity.lastRightRotation = transform.rightRotation
        boneEntity.lastScale = transform.scale
        boneEntity.lastItem = boneEntity.bone.modelItem
        boneEntity.lastVisible = boneEntity.bone.visible
        boneEntity.spawned = true
    }

    private fun buildFullMetadata(
        entity: BoneEntity,
        position: Vec,
        leftRotation: Quat,
        rightRotation: Quat,
        scale: Vec,
        item: ItemStack?,
    ): Map<Int, Metadata.Entry<*>> = buildMap {
        put(META_NO_GRAVITY, Metadata.Boolean(true))
        put(META_INTERPOLATION_DELAY, Metadata.VarInt(0))
        put(META_TRANSFORM_DURATION, Metadata.VarInt(interpolationDuration))
        put(META_TRANSLATION, Metadata.Vector3(position))
        put(META_SCALE, Metadata.Vector3(scale))
        put(META_ROTATION_LEFT, Metadata.Quaternion(leftRotation.toFloatArray()))
        put(META_ROTATION_RIGHT, Metadata.Quaternion(rightRotation.toFloatArray()))
        put(META_BILLBOARD, Metadata.Byte(AbstractDisplayMeta.BillboardConstraints.FIXED.ordinal.toByte()))
        put(META_VIEW_RANGE, Metadata.Float(1.0f))
        if (item != null) {
            put(META_DISPLAYED_ITEM, Metadata.ItemStack(item))
            put(META_DISPLAY_TYPE, Metadata.Byte(0))
        }
    }

    private fun buildDirtyMetadata(
        entity: BoneEntity,
        position: Vec,
        leftRotation: Quat,
        rightRotation: Quat,
        scale: Vec,
        item: ItemStack?,
    ): Map<Int, Metadata.Entry<*>> = buildMap {
        var hasChange = false

        if (position != entity.lastPosition) {
            put(META_TRANSLATION, Metadata.Vector3(position))
            entity.lastPosition = position
            hasChange = true
        }
        if (leftRotation != entity.lastLeftRotation) {
            put(META_ROTATION_LEFT, Metadata.Quaternion(leftRotation.toFloatArray()))
            entity.lastLeftRotation = leftRotation
            hasChange = true
        }
        if (rightRotation != entity.lastRightRotation) {
            put(META_ROTATION_RIGHT, Metadata.Quaternion(rightRotation.toFloatArray()))
            entity.lastRightRotation = rightRotation
            hasChange = true
        }
        if (scale != entity.lastScale) {
            put(META_SCALE, Metadata.Vector3(scale))
            entity.lastScale = scale
            hasChange = true
        }
        if (item != entity.lastItem) {
            if (item != null) {
                put(META_DISPLAYED_ITEM, Metadata.ItemStack(item))
            }
            entity.lastItem = item
            hasChange = true
        }

        if (hasChange) {
            put(META_INTERPOLATION_DELAY, Metadata.VarInt(0))
            put(META_TRANSFORM_DURATION, Metadata.VarInt(interpolationDuration))
        }
    }

    private inline fun forEachViewer(action: (Player) -> Unit) {
        _viewers.forEach { uuid ->
            MinecraftServer.getConnectionManager()
                .onlinePlayers.firstOrNull { it.uuid == uuid }?.let(action)
        }
    }
}
