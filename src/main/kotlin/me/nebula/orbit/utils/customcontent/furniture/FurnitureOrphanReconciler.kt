package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.logging.logger
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.util.UUID

object FurnitureOrphanReconciler {

    private val logger = logger("FurnitureOrphanReconciler")

    data class ScanResult(
        val storedWithoutBarrier: List<UUID>,
        val barrierWithoutStore: List<Triple<Int, Int, Int>>,
    ) {
        val hasIssues: Boolean get() = storedWithoutBarrier.isNotEmpty() || barrierWithoutStore.isNotEmpty()
    }

    fun scan(instance: Instance): ScanResult {
        val storedMissing = mutableListOf<UUID>()
        for (furniture in PlacedFurnitureStore.all(instance)) {
            if (furniture.cellKeys.isEmpty()) continue
            val anyMissing = furniture.cellKeys.any { key ->
                val (x, y, z) = FurnitureInstance.unpackKey(key)
                val block = instance.getBlock(x, y, z)
                !FurnitureCollisionStates.isFurnitureCollisionBlock(block)
            }
            if (anyMissing) storedMissing += furniture.uuid
        }

        val knownCellKeys = PlacedFurnitureStore.all(instance)
            .flatMap { it.cellKeys }
            .toSet()

        val barrierOrphans = mutableListOf<Triple<Int, Int, Int>>()
        for (chunk in instance.chunks) {
            val baseX = chunk.chunkX shl 4
            val baseZ = chunk.chunkZ shl 4
            for (lx in 0 until 16) {
                for (lz in 0 until 16) {
                    for (y in instance.cachedDimensionType.minY() until instance.cachedDimensionType.minY() + instance.cachedDimensionType.height()) {
                        val worldX = baseX + lx
                        val worldZ = baseZ + lz
                        val block = instance.getBlock(worldX, y, worldZ)
                        if (!block.compare(Block.BARRIER)) continue
                        val key = FurnitureInstance.packKey(worldX, y, worldZ)
                        if (key !in knownCellKeys) barrierOrphans += Triple(worldX, y, worldZ)
                    }
                }
            }
        }
        return ScanResult(storedMissing, barrierOrphans)
    }

    fun repairStoredWithoutBarrier(instance: Instance, uuids: List<UUID>): Int {
        var removed = 0
        for (uuid in uuids) {
            val furniture = PlacedFurnitureStore.byUuid(instance, uuid) ?: continue
            FurnitureDisplaySpawner.despawn(instance, furniture.displayEntityId)
            for (entityId in furniture.interactionEntityIds) {
                InteractionEntitySpawner.despawn(instance, entityId)
            }
            SeatController.onFurnitureBroken(uuid)
            DisplayCullController.onBroken(uuid)
            FurnitureLightingController.onBroken(instance, furniture)
            FurnitureInstanceState.remove(uuid)
            PlacedFurnitureStore.remove(instance, uuid)
            removed++
            logger.info { "Removed orphan furniture entry '${furniture.definitionId}' (uuid=$uuid)" }
        }
        return removed
    }

    fun repairBarrierWithoutStore(instance: Instance, positions: List<Triple<Int, Int, Int>>): Int {
        var airified = 0
        for ((x, y, z) in positions) {
            instance.setBlock(x, y, z, Block.AIR)
            airified++
            logger.info { "AIR-ed out orphan barrier at ($x, $y, $z)" }
        }
        return airified
    }

    fun scanAndRepair(instance: Instance): ScanResult {
        val result = scan(instance)
        if (result.hasIssues) {
            logger.warn { "Found ${result.storedWithoutBarrier.size} stored-without-barrier + ${result.barrierWithoutStore.size} barrier-without-store orphans; repairing" }
            repairStoredWithoutBarrier(instance, result.storedWithoutBarrier)
            repairBarrierWithoutStore(instance, result.barrierWithoutStore)
        }
        return result
    }
}
