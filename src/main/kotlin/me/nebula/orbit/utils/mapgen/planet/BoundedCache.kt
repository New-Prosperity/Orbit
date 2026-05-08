package me.nebula.orbit.utils.mapgen.planet

import java.util.concurrent.ConcurrentHashMap

class BoundedCache<K : Any, V : Any>(
    private val maxSize: Int,
    private val onEvict: (K, V) -> Unit = { _, _ -> },
) {

    private val map = ConcurrentHashMap<K, V>()
    private val insertions = java.util.concurrent.ConcurrentLinkedDeque<K>()

    fun computeIfAbsent(key: K, compute: (K) -> V): V {
        map[key]?.let { return it }
        val computed = map.computeIfAbsent(key) { k ->
            insertions.add(k)
            compute(k)
        }
        if (map.size > maxSize) trim()
        return computed
    }

    operator fun get(key: K): V? = map[key]

    fun put(key: K, value: V) {
        val prev = map.put(key, value)
        if (prev == null) insertions.add(key)
        if (map.size > maxSize) trim()
    }

    fun markFresh(key: K, value: V): Boolean {
        val prev = map.putIfAbsent(key, value)
        if (prev == null) {
            insertions.add(key)
            if (map.size > maxSize) trim()
            return true
        }
        return false
    }

    fun size(): Int = map.size

    fun clear() {
        map.clear()
        insertions.clear()
    }

    private fun trim() {
        val target = (maxSize * 0.85).toInt().coerceAtLeast(1)
        while (map.size > target) {
            val oldest = insertions.pollFirst() ?: break
            val evicted = map.remove(oldest) ?: continue
            try {
                onEvict(oldest, evicted)
            } catch (_: Throwable) {
                // eviction listener failures must not break the cache
            }
        }
    }
}

object SeedMix {
    private val MIX64_K1: Long = 0xBF58476D1CE4E5B7uL.toLong()
    private val MIX64_K2: Long = 0x94D049BB133111EBuL.toLong()

    fun mix64(z: Long): Long {
        var x = z
        x = (x xor (x ushr 30)) * MIX64_K1
        x = (x xor (x ushr 27)) * MIX64_K2
        x = x xor (x ushr 31)
        return x
    }

    fun chunkSeed(worldSeed: Long, chunkX: Int, chunkZ: Int, salt: Long = 0L): Long {
        val packed = (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
        return mix64(mix64(worldSeed xor packed) xor salt)
    }
}
