package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticPlayerData
import me.nebula.gravity.cosmetic.CosmeticStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CosmeticDataCache {

    private const val TTL_MS = 5_000L

    private data class CachedEntry(val data: CosmeticPlayerData?, val timestamp: Long)

    private val cache = ConcurrentHashMap<UUID, CachedEntry>()

    fun get(uuid: UUID): CosmeticPlayerData? {
        val now = System.currentTimeMillis()
        val entry = cache[uuid]
        if (entry != null && now - entry.timestamp < TTL_MS) return entry.data
        val data = CosmeticStore.load(uuid)
        cache[uuid] = CachedEntry(data, now)
        return data
    }

    fun invalidate(uuid: UUID) {
        cache.remove(uuid)
    }

    fun clear() {
        cache.clear()
    }
}
