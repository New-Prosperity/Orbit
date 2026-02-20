package me.nebula.orbit.utils.modelengine.vfx

import me.nebula.orbit.utils.modelengine.math.Quat
import me.nebula.orbit.utils.modelengine.math.QUAT_IDENTITY
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

private val nextVfxId = AtomicInteger(-3_800_000)

private const val META_NO_GRAVITY = 5
private const val META_INTERPOLATION_DELAY = 8
private const val META_TRANSFORM_DURATION = 9
private const val META_TRANSLATION = 11
private const val META_SCALE = 12
private const val META_ROTATION_LEFT = 13
private const val META_BILLBOARD = 15
private const val META_DISPLAYED_ITEM = 23
private const val META_DISPLAY_TYPE = 24

class VFX(
    val item: ItemStack,
    var position: Vec,
    var rotation: Quat = QUAT_IDENTITY,
    var scale: Vec = Vec(1.0, 1.0, 1.0),
    var billboard: AbstractDisplayMeta.BillboardConstraints = AbstractDisplayMeta.BillboardConstraints.FIXED,
    val interpolationDuration: Int = 1,
    var lifetime: Int = -1,
) {
    val entityId: Int = nextVfxId.getAndDecrement()
    val uuid: UUID = UUID.randomUUID()
    val viewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    var age: Int = 0
        internal set

    var removed: Boolean = false
        internal set

    fun show(player: Player) {
        if (!viewers.add(player.uuid)) return
        player.sendPacket(SpawnEntityPacket(
            entityId, uuid, EntityType.ITEM_DISPLAY,
            Pos.ZERO, 0f, 0, Vec.ZERO,
        ))
        player.sendPacket(EntityMetaDataPacket(entityId, buildFullMeta()))
    }

    fun hide(player: Player) {
        if (!viewers.remove(player.uuid)) return
        player.sendPacket(DestroyEntitiesPacket(listOf(entityId)))
    }

    fun update() {
        val meta = buildFullMeta()
        val packet = EntityMetaDataPacket(entityId, meta)
        forEachViewer { it.sendPacket(packet) }
    }

    fun evictViewer(uuid: UUID) {
        viewers.remove(uuid)
    }

    fun remove() {
        removed = true
        val packet = DestroyEntitiesPacket(listOf(entityId))
        forEachViewer { it.sendPacket(packet) }
        viewers.clear()
    }

    private fun buildFullMeta(): Map<Int, Metadata.Entry<*>> = buildMap {
        put(META_NO_GRAVITY, Metadata.Boolean(true))
        put(META_INTERPOLATION_DELAY, Metadata.VarInt(0))
        put(META_TRANSFORM_DURATION, Metadata.VarInt(interpolationDuration))
        put(META_TRANSLATION, Metadata.Vector3(position))
        put(META_SCALE, Metadata.Vector3(scale))
        put(META_ROTATION_LEFT, Metadata.Quaternion(rotation.toFloatArray()))
        put(META_BILLBOARD, Metadata.Byte(billboard.ordinal.toByte()))
        put(META_DISPLAYED_ITEM, Metadata.ItemStack(item))
        put(META_DISPLAY_TYPE, Metadata.Byte(0))
    }

    private inline fun forEachViewer(action: (Player) -> Unit) {
        viewers.forEach { uuid ->
            MinecraftServer.getConnectionManager()
                .onlinePlayers.firstOrNull { it.uuid == uuid }?.let(action)
        }
    }
}

class VFXBuilder @PublishedApi internal constructor(private val item: ItemStack) {

    @PublishedApi internal var position: Vec = Vec.ZERO
    @PublishedApi internal var rotation: Quat = QUAT_IDENTITY
    @PublishedApi internal var scale: Vec = Vec(1.0, 1.0, 1.0)
    @PublishedApi internal var billboard: AbstractDisplayMeta.BillboardConstraints = AbstractDisplayMeta.BillboardConstraints.FIXED
    @PublishedApi internal var interpolationDuration: Int = 1
    @PublishedApi internal var lifetime: Int = -1

    fun position(pos: Vec) { position = pos }
    fun rotation(rot: Quat) { rotation = rot }
    fun scale(s: Vec) { scale = s }
    fun scale(s: Double) { scale = Vec(s, s, s) }
    fun billboard(b: AbstractDisplayMeta.BillboardConstraints) { billboard = b }
    fun interpolation(ticks: Int) { interpolationDuration = ticks }
    fun lifetime(ticks: Int) { lifetime = ticks }

    @PublishedApi internal fun build(): VFX = VFX(item, position, rotation, scale, billboard, interpolationDuration, lifetime)
}

inline fun vfx(item: ItemStack, block: VFXBuilder.() -> Unit = {}): VFX =
    VFXBuilder(item).apply(block).build()
