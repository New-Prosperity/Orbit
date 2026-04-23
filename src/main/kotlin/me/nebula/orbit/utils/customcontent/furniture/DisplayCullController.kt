package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DisplayCullController {

    private val logger = logger("DisplayCullController")

    @Volatile private var cullRadius: Double = 48.0
    @Volatile private var tickIntervalTicks: Int = 20

    private val renderedByFurniture = ConcurrentHashMap<UUID, Int>()
    private var tickTask: Task? = null

    fun configure(radius: Double = 48.0, tickInterval: Int = 20) {
        cullRadius = radius
        tickIntervalTicks = tickInterval
    }

    fun install() {
        if (tickTask != null) return
        tickTask = repeat(tickIntervalTicks) { reconcile() }
    }

    fun uninstall() {
        tickTask?.cancel()
        tickTask = null
        for ((_, entityId) in renderedByFurniture) {
            for (instance in MinecraftServer.getInstanceManager().instances) {
                FurnitureDisplaySpawner.despawn(instance, entityId)
            }
        }
        renderedByFurniture.clear()
    }

    fun onPlaced(furniture: FurnitureInstance, display: FurnitureDisplaySpawner.SpawnedDisplay) {
        renderedByFurniture[furniture.uuid] = display.entityId
        if (!isAnyPlayerNear(furniture)) {
            FurnitureDisplaySpawner.despawn(furniture.instance, display.entityId)
            renderedByFurniture.remove(furniture.uuid)
        }
    }

    fun onBroken(furnitureUuid: UUID) {
        renderedByFurniture.remove(furnitureUuid)
    }

    fun isRendered(furnitureUuid: UUID): Boolean = renderedByFurniture.containsKey(furnitureUuid)

    fun renderedCount(): Int = renderedByFurniture.size

    private fun reconcile() {
        val instances = MinecraftServer.getInstanceManager().instances
        val radiusSq = cullRadius * cullRadius
        for (instance in instances) {
            val allFurniture = PlacedFurnitureStore.all(instance)
            if (allFurniture.isEmpty()) continue
            val playerPositions = instance.players.map { it.position }
            if (playerPositions.isEmpty()) {
                for (furniture in allFurniture) despawnIfRendered(furniture)
                continue
            }
            for (furniture in allFurniture) {
                val shouldRender = playerPositions.any { pos ->
                    val dx = pos.x() - (furniture.anchorX + 0.5)
                    val dy = pos.y() - (furniture.anchorY + 0.5)
                    val dz = pos.z() - (furniture.anchorZ + 0.5)
                    dx * dx + dy * dy + dz * dz <= radiusSq
                }
                if (shouldRender && !renderedByFurniture.containsKey(furniture.uuid)) {
                    respawnDisplay(furniture)
                } else if (!shouldRender && renderedByFurniture.containsKey(furniture.uuid)) {
                    despawnIfRendered(furniture)
                }
            }
        }
    }

    private fun isAnyPlayerNear(furniture: FurnitureInstance): Boolean {
        val radiusSq = cullRadius * cullRadius
        return furniture.instance.players.any { p ->
            val dx = p.position.x() - (furniture.anchorX + 0.5)
            val dy = p.position.y() - (furniture.anchorY + 0.5)
            val dz = p.position.z() - (furniture.anchorZ + 0.5)
            dx * dx + dy * dy + dz * dz <= radiusSq
        }
    }

    private fun despawnIfRendered(furniture: FurnitureInstance) {
        val entityId = renderedByFurniture.remove(furniture.uuid) ?: return
        FurnitureDisplaySpawner.despawn(furniture.instance, entityId)
    }

    private fun respawnDisplay(furniture: FurnitureInstance) {
        val definition = FurnitureRegistry[furniture.definitionId] ?: return
        val display = try {
            FurnitureDisplaySpawner.spawn(
                definition,
                furniture.instance,
                furniture.anchorX, furniture.anchorY, furniture.anchorZ,
                yawDegrees = furniture.yawDegrees,
                pitchDegrees = furniture.pitchDegrees,
                rollDegrees = furniture.rollDegrees,
            )
        } catch (e: Exception) {
            logger.warn { "Failed to respawn culled display for ${furniture.definitionId}: ${e.message}" }
            return
        }
        renderedByFurniture[furniture.uuid] = display.entityId
        PlacedFurnitureStore.updateDisplayEntityId(furniture.instance, furniture.uuid, display.entityId)
    }
}
