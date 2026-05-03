package me.nebula.orbit.utils.entitybuilder

import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance
import java.util.concurrent.ConcurrentHashMap

class PackBlackboard {

    private val data = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: MemoryKey<T>): T? = data[key.name] as? T

    fun <T : Any> set(key: MemoryKey<T>, value: T?) {
        if (value == null) data.remove(key.name) else data[key.name] = value
    }

    fun has(key: MemoryKey<*>): Boolean = data.containsKey(key.name)

    fun clear(key: MemoryKey<*>) { data.remove(key.name) }

    fun clearAll() { data.clear() }

    fun snapshot(): Map<String, Any> = data.toMap()

    fun size(): Int = data.size

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> claim(key: MemoryKey<T>, candidate: T): T =
        data.computeIfAbsent(key.name) { candidate } as T

    fun release(key: MemoryKey<*>, owner: Any) {
        data.remove(key.name, owner)
    }
}

object PackBlackboards {

    private data class PackKey(val instance: Instance, val type: EntityType)

    private val boards = ConcurrentHashMap<PackKey, PackBlackboard>()

    fun forEntity(entity: SmartEntity): PackBlackboard? {
        val instance = entity.instance ?: return null
        return boards.computeIfAbsent(PackKey(instance, entity.entityType)) { PackBlackboard() }
    }

    fun forPack(instance: Instance, type: EntityType): PackBlackboard =
        boards.computeIfAbsent(PackKey(instance, type)) { PackBlackboard() }

    fun cleanupInstance(instance: Instance) {
        boards.keys.removeAll { it.instance === instance }
    }

    fun cleanupType(instance: Instance, type: EntityType) {
        boards.remove(PackKey(instance, type))
    }

    internal fun activeKeys(): Set<Pair<Instance, EntityType>> =
        boards.keys.map { it.instance to it.type }.toSet()
}
