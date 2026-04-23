package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.orbit.utils.scheduler.delay
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.other.ArmorStandMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerStartSneakingEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SeatController {

    private data class ActiveSeat(
        val furnitureUuid: UUID,
        val sitter: UUID,
        val anchorEntity: Entity,
    )

    private val seatsByFurniture = ConcurrentHashMap<UUID, ActiveSeat>()
    private val seatsByPlayer = ConcurrentHashMap<UUID, UUID>()
    private var eventNode: EventNode<*>? = null

    fun install() {
        if (eventNode != null) return
        val node = EventNode.all("furniture-seat")
        node.addListener(PlayerStartSneakingEvent::class.java) { event ->
            dismount(event.player)
        }
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            dismount(event.player)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun uninstall() {
        val node = eventNode ?: return
        MinecraftServer.getGlobalEventHandler().removeChild(node)
        eventNode = null
        seatsByFurniture.values.forEach { it.anchorEntity.remove() }
        seatsByFurniture.clear()
        seatsByPlayer.clear()
    }

    fun onClick(player: Player, furniture: FurnitureInstance, seat: FurnitureInteraction.Seat) {
        if (seatsByPlayer.containsKey(player.uuid)) return
        val existing = seatsByFurniture[furniture.uuid]
        if (existing != null) return

        val instance = furniture.instance
        val anchor = Entity(EntityType.ARMOR_STAND)
        val meta = anchor.entityMeta as ArmorStandMeta
        meta.setNotifyAboutChanges(false)
        meta.setMarker(true)
        meta.setInvisible(true)
        meta.setNotifyAboutChanges(true)
        val pos = Pos(
            furniture.anchorX + 0.5,
            furniture.anchorY + seat.offsetY,
            furniture.anchorZ + 0.5,
            furniture.yawDegrees + seat.yawOffsetDegrees,
            0f,
        )
        anchor.setInstance(instance, pos).thenRun {
            anchor.addPassenger(player)
        }

        val active = ActiveSeat(furniture.uuid, player.uuid, anchor)
        seatsByFurniture[furniture.uuid] = active
        seatsByPlayer[player.uuid] = furniture.uuid
    }

    fun dismount(player: Player) {
        val furnitureUuid = seatsByPlayer.remove(player.uuid) ?: return
        val active = seatsByFurniture.remove(furnitureUuid) ?: return
        active.anchorEntity.passengers.forEach { active.anchorEntity.removePassenger(it) }
        delay(1) { active.anchorEntity.remove() }
    }

    fun onFurnitureBroken(furnitureUuid: UUID) {
        val active = seatsByFurniture.remove(furnitureUuid) ?: return
        seatsByPlayer.remove(active.sitter)
        active.anchorEntity.passengers.forEach { active.anchorEntity.removePassenger(it) }
        active.anchorEntity.remove()
    }

    fun isSittingOn(furnitureUuid: UUID): Boolean = seatsByFurniture.containsKey(furnitureUuid)

    fun sitterOf(furnitureUuid: UUID): UUID? = seatsByFurniture[furnitureUuid]?.sitter
}
