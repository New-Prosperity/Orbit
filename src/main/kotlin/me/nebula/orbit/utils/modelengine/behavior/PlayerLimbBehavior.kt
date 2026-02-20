package me.nebula.orbit.utils.modelengine.behavior

import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.network.packet.server.play.*
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val nextLimbId = AtomicInteger(-3_700_000)

enum class LimbType { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

class PlayerLimbBehavior(
    override val bone: ModelBone,
    val limbType: LimbType,
    private val skin: PlayerSkin? = null,
) : BoneBehavior {

    private val limbEntityId = nextLimbId.getAndDecrement()
    private val limbUuid = UUID.randomUUID()
    private val viewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private var lastLimbPos: Pos = Pos.ZERO

    override fun onAdd(modeledEntity: ModeledEntity) {
        modeledEntity.viewers.forEach { uuid ->
            if (viewers.add(uuid)) findPlayer(uuid)?.let { showLimb(it, modeledEntity) }
        }
    }

    override fun tick(modeledEntity: ModeledEntity) {
        val newViewers = modeledEntity.viewers
        newViewers.forEach { uuid ->
            if (viewers.add(uuid)) findPlayer(uuid)?.let { showLimb(it, modeledEntity) }
        }
        viewers.removeAll { uuid ->
            if (uuid !in newViewers) {
                findPlayer(uuid)?.let { player ->
                    player.sendPacket(DestroyEntitiesPacket(listOf(limbEntityId)))
                    player.sendPacket(PlayerInfoRemovePacket(listOf(limbUuid)))
                }
                true
            } else false
        }

        val transform = bone.globalTransform
        val worldPos = transform.toWorldPosition(modeledEntity.owner.position)
        val (_, yaw, _) = me.nebula.orbit.utils.modelengine.math.quatToEuler(
            transform.toWorldRotation(modeledEntity.owner.position.yaw())
        )

        val limbPos = Pos(worldPos.x(), worldPos.y(), worldPos.z(), yaw, 0f)
        if (limbPos != lastLimbPos) {
            lastLimbPos = limbPos
            val packet = EntityTeleportPacket(limbEntityId, limbPos, Vec.ZERO, 0, false)
            viewers.forEach { uuid -> findPlayer(uuid)?.sendPacket(packet) }
        }
    }

    override fun onRemove(modeledEntity: ModeledEntity) {
        val destroyPacket = DestroyEntitiesPacket(listOf(limbEntityId))
        val infoRemove = PlayerInfoRemovePacket(listOf(limbUuid))
        viewers.forEach { uuid ->
            findPlayer(uuid)?.let { player ->
                player.sendPacket(destroyPacket)
                player.sendPacket(infoRemove)
            }
        }
        viewers.clear()
    }

    override fun evictViewer(uuid: UUID) {
        viewers.remove(uuid)
    }

    private fun showLimb(player: Player, modeledEntity: ModeledEntity) {
        val property = skin?.let {
            PlayerInfoUpdatePacket.Property("textures", it.textures(), it.signature())
        }

        player.sendPacket(PlayerInfoUpdatePacket(
            EnumSet.of(
                PlayerInfoUpdatePacket.Action.ADD_PLAYER,
                PlayerInfoUpdatePacket.Action.UPDATE_LISTED,
            ),
            listOf(PlayerInfoUpdatePacket.Entry(
                limbUuid, "limb_${limbType.name.lowercase()}",
                if (property != null) listOf(property) else emptyList(),
                false, 0, GameMode.CREATIVE, null, null, 0, true,
            )),
        ))

        val transform = bone.globalTransform
        val worldPos = transform.toWorldPosition(modeledEntity.owner.position)

        player.sendPacket(SpawnEntityPacket(
            limbEntityId, limbUuid, EntityType.PLAYER,
            Pos(worldPos.x(), worldPos.y(), worldPos.z()), 0f, 0, Vec.ZERO,
        ))

        player.sendPacket(EntityMetaDataPacket(limbEntityId, mapOf(
            5 to Metadata.Boolean(true),
            17 to Metadata.Byte(0x7F),
        )))

        MinecraftServer.getSchedulerManager().buildTask {
            if (viewers.contains(player.uuid)) {
                player.sendPacket(PlayerInfoRemovePacket(listOf(limbUuid)))
            }
        }.delay(net.minestom.server.timer.TaskSchedule.tick(40)).schedule()
    }

    private fun findPlayer(uuid: UUID): Player? =
        MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == uuid }
}
