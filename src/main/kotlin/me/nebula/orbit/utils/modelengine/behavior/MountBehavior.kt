package me.nebula.orbit.utils.modelengine.behavior

import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val nextSeatId = AtomicInteger(-3_500_000)

class MountBehavior(
    override val bone: ModelBone,
    private val seatOffset: Vec = Vec.ZERO,
) : BoneBehavior {

    private val seatEntityId = nextSeatId.getAndDecrement()
    private val seatUuid = UUID.randomUUID()
    private val viewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @Volatile var passenger: Player? = null
        private set

    private var lastSeatPos: Vec = Vec.ZERO

    override fun onAdd(modeledEntity: ModeledEntity) {
        modeledEntity.viewers.forEach { uuid ->
            if (viewers.add(uuid)) findPlayer(uuid)?.let { showSeat(it, modeledEntity) }
        }
    }

    override fun tick(modeledEntity: ModeledEntity) {
        val newViewers = modeledEntity.viewers
        newViewers.forEach { uuid ->
            if (viewers.add(uuid)) findPlayer(uuid)?.let { showSeat(it, modeledEntity) }
        }
        viewers.removeAll { uuid ->
            if (uuid !in newViewers) {
                findPlayer(uuid)?.sendPacket(DestroyEntitiesPacket(listOf(seatEntityId)))
                true
            } else false
        }

        val transform = bone.globalTransform
        val worldPos = transform.toWorldPosition(modeledEntity.owner.position)
        val seatPos = worldPos.add(seatOffset)

        if (seatPos != lastSeatPos) {
            lastSeatPos = seatPos
            val teleport = EntityTeleportPacket(
                seatEntityId,
                Pos(seatPos.x(), seatPos.y(), seatPos.z()),
                Vec.ZERO, 0, false,
            )
            viewers.forEach { uuid -> findPlayer(uuid)?.sendPacket(teleport) }
        }
    }

    override fun onRemove(modeledEntity: ModeledEntity) {
        dismount()
        val packet = DestroyEntitiesPacket(listOf(seatEntityId))
        viewers.forEach { uuid -> findPlayer(uuid)?.sendPacket(packet) }
        viewers.clear()
    }

    override fun evictViewer(uuid: UUID) {
        viewers.remove(uuid)
    }

    fun mount(player: Player) {
        passenger = player
        val packet = SetPassengersPacket(seatEntityId, listOf(player.entityId))
        viewers.forEach { uuid -> findPlayer(uuid)?.sendPacket(packet) }
    }

    fun dismount(): Player? {
        val prev = passenger ?: return null
        passenger = null
        val packet = SetPassengersPacket(seatEntityId, emptyList())
        viewers.forEach { uuid -> findPlayer(uuid)?.sendPacket(packet) }
        return prev
    }

    private fun showSeat(player: Player, modeledEntity: ModeledEntity) {
        val transform = bone.globalTransform
        val worldPos = transform.toWorldPosition(modeledEntity.owner.position)
        val seatPos = worldPos.add(seatOffset)
        lastSeatPos = seatPos

        player.sendPacket(SpawnEntityPacket(
            seatEntityId, seatUuid, EntityType.INTERACTION,
            Pos(seatPos.x(), seatPos.y(), seatPos.z()), 0f, 0, Vec.ZERO,
        ))
        player.sendPacket(EntityMetaDataPacket(seatEntityId, mapOf(
            5 to Metadata.Boolean(true),
            8 to Metadata.Float(0.001f),
            9 to Metadata.Float(0.001f),
        )))

        passenger?.let { p ->
            player.sendPacket(SetPassengersPacket(seatEntityId, listOf(p.entityId)))
        }
    }

    private fun findPlayer(uuid: UUID): Player? =
        MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == uuid }
}
