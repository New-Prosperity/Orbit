package me.nebula.orbit.utils.customcontent.furniture

import net.minestom.server.instance.Instance
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlacedFurnitureStore {

    private data class InstanceIndex(
        val byUuid: ConcurrentHashMap<UUID, FurnitureInstance> = ConcurrentHashMap(),
        val byCellKey: ConcurrentHashMap<Long, UUID> = ConcurrentHashMap(),
        val byChunkKey: ConcurrentHashMap<Long, MutableSet<UUID>> = ConcurrentHashMap(),
        val byInteractionEntityId: ConcurrentHashMap<Int, UUID> = ConcurrentHashMap(),
    )

    private val indices = ConcurrentHashMap<Int, InstanceIndex>()
    private val indexLock = Any()

    private fun indexFor(instance: Instance): InstanceIndex =
        indices.computeIfAbsent(System.identityHashCode(instance)) { InstanceIndex() }

    fun add(furniture: FurnitureInstance): AddResult {
        val index = indexFor(furniture.instance)
        synchronized(indexLock) { // noqa: synchronized on local var — coordinates atomic multi-map updates
            val conflict = furniture.cellKeys.firstOrNull { index.byCellKey.containsKey(it) }
            if (conflict != null) return AddResult.Conflict(conflict)
            if (index.byUuid.containsKey(furniture.uuid)) {
                return AddResult.DuplicateUuid
            }
            for (cell in furniture.cellKeys) {
                index.byCellKey[cell] = furniture.uuid
                val (x, _, z) = FurnitureInstance.unpackKey(cell)
                val chunkKey = chunkKeyOf(x shr 4, z shr 4)
                index.byChunkKey.computeIfAbsent(chunkKey) { ConcurrentHashMap.newKeySet() }.add(furniture.uuid)
            }
            for (entityId in furniture.interactionEntityIds) {
                index.byInteractionEntityId[entityId] = furniture.uuid
            }
            index.byUuid[furniture.uuid] = furniture
        }
        return AddResult.Success
    }

    fun remove(instance: Instance, uuid: UUID): FurnitureInstance? {
        val index = indices[System.identityHashCode(instance)] ?: return null
        synchronized(indexLock) { // noqa: synchronized on local var — coordinates atomic multi-map updates
            val furniture = index.byUuid.remove(uuid) ?: return null
            for (cell in furniture.cellKeys) {
                index.byCellKey.remove(cell)
                val (x, _, z) = FurnitureInstance.unpackKey(cell)
                val chunkKey = chunkKeyOf(x shr 4, z shr 4)
                val set = index.byChunkKey[chunkKey]
                if (set != null) {
                    set.remove(uuid)
                    if (set.isEmpty()) index.byChunkKey.remove(chunkKey)
                }
            }
            for (entityId in furniture.interactionEntityIds) {
                index.byInteractionEntityId.remove(entityId)
            }
            return furniture
        }
    }

    fun byInteractionEntity(instance: Instance, entityId: Int): FurnitureInstance? {
        val index = indices[System.identityHashCode(instance)] ?: return null
        val uuid = index.byInteractionEntityId[entityId] ?: return null
        return index.byUuid[uuid]
    }

    fun updateDisplayEntityId(instance: Instance, uuid: UUID, entityId: Int) {
        val index = indices[System.identityHashCode(instance)] ?: return
        synchronized(indexLock) { // noqa: synchronized on local var — coordinates atomic multi-map updates
            val existing = index.byUuid[uuid] ?: return
            index.byUuid[uuid] = existing.copy(displayEntityId = entityId)
        }
    }

    fun updateTransform(instance: Instance, uuid: UUID, updated: FurnitureInstance) {
        val index = indices[System.identityHashCode(instance)] ?: return
        synchronized(indexLock) { // noqa: synchronized on local var — coordinates atomic multi-map updates
            val existing = index.byUuid[uuid] ?: return
            for (cell in existing.cellKeys) {
                index.byCellKey.remove(cell)
                val (x, _, z) = FurnitureInstance.unpackKey(cell)
                val chunkKey = chunkKeyOf(x shr 4, z shr 4)
                val set = index.byChunkKey[chunkKey]
                if (set != null) {
                    set.remove(uuid)
                    if (set.isEmpty()) index.byChunkKey.remove(chunkKey)
                }
            }
            for (cell in updated.cellKeys) {
                index.byCellKey[cell] = uuid
                val (x, _, z) = FurnitureInstance.unpackKey(cell)
                val chunkKey = chunkKeyOf(x shr 4, z shr 4)
                index.byChunkKey.computeIfAbsent(chunkKey) { ConcurrentHashMap.newKeySet() }.add(uuid)
            }
            index.byUuid[uuid] = updated
        }
    }

    fun byUuid(instance: Instance, uuid: UUID): FurnitureInstance? =
        indices[System.identityHashCode(instance)]?.byUuid?.get(uuid)

    fun atCell(instance: Instance, x: Int, y: Int, z: Int): FurnitureInstance? {
        val index = indices[System.identityHashCode(instance)] ?: return null
        val uuid = index.byCellKey[FurnitureInstance.packKey(x, y, z)] ?: return null
        return index.byUuid[uuid]
    }

    fun inChunk(instance: Instance, chunkX: Int, chunkZ: Int): Collection<FurnitureInstance> {
        val index = indices[System.identityHashCode(instance)] ?: return emptyList()
        val uuids = index.byChunkKey[chunkKeyOf(chunkX, chunkZ)] ?: return emptyList()
        return uuids.mapNotNull { index.byUuid[it] }
    }

    fun all(instance: Instance): Collection<FurnitureInstance> =
        indices[System.identityHashCode(instance)]?.byUuid?.values ?: emptyList()

    fun count(instance: Instance): Int =
        indices[System.identityHashCode(instance)]?.byUuid?.size ?: 0

    fun clear(instance: Instance) {
        indices.remove(System.identityHashCode(instance))
    }

    fun clearAll() {
        indices.clear()
    }

    private fun chunkKeyOf(chunkX: Int, chunkZ: Int): Long =
        (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)

    sealed interface AddResult {
        data object Success : AddResult
        data class Conflict(val cellKey: Long) : AddResult
        data object DuplicateUuid : AddResult
    }
}
