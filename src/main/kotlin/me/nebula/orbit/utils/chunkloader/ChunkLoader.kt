package me.nebula.orbit.utils.chunkloader

import net.minestom.server.instance.Instance
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

object ChunkLoader {

    fun loadRadius(instance: Instance, centerX: Int, centerZ: Int, radius: Int): CompletableFuture<Int> {
        val futures = mutableListOf<CompletableFuture<*>>()
        val loaded = AtomicInteger(0)
        for (x in (centerX - radius)..(centerX + radius)) {
            for (z in (centerZ - radius)..(centerZ + radius)) {
                futures.add(instance.loadChunk(x, z).thenRun { loaded.incrementAndGet() })
            }
        }
        return CompletableFuture.allOf(*futures.toTypedArray()).thenApply { loaded.get() }
    }

    fun loadSquare(instance: Instance, minX: Int, minZ: Int, maxX: Int, maxZ: Int): CompletableFuture<Int> {
        val futures = mutableListOf<CompletableFuture<*>>()
        val loaded = AtomicInteger(0)
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                futures.add(instance.loadChunk(x, z).thenRun { loaded.incrementAndGet() })
            }
        }
        return CompletableFuture.allOf(*futures.toTypedArray()).thenApply { loaded.get() }
    }

    fun loadChunks(instance: Instance, chunks: List<Pair<Int, Int>>): CompletableFuture<Int> {
        val loaded = AtomicInteger(0)
        val futures = chunks.map { (x, z) ->
            instance.loadChunk(x, z).thenRun { loaded.incrementAndGet() }
        }
        return CompletableFuture.allOf(*futures.toTypedArray()).thenApply { loaded.get() }
    }

    fun preloadAroundSpawn(instance: Instance, spawnChunkX: Int = 0, spawnChunkZ: Int = 0, radius: Int = 4): CompletableFuture<Int> =
        loadRadius(instance, spawnChunkX, spawnChunkZ, radius)

    fun unloadAll(instance: Instance) {
        instance.chunks.forEach { chunk ->
            if (chunk.viewers.isEmpty()) {
                instance.unloadChunk(chunk)
            }
        }
    }

    fun unloadOutsideRadius(instance: Instance, centerX: Int, centerZ: Int, radius: Int) {
        instance.chunks.forEach { chunk ->
            val dx = chunk.chunkX - centerX
            val dz = chunk.chunkZ - centerZ
            if (dx * dx + dz * dz > radius * radius && chunk.viewers.isEmpty()) {
                instance.unloadChunk(chunk)
            }
        }
    }

    fun loadedChunkCount(instance: Instance): Int = instance.chunks.size
}

fun Instance.loadChunkRadius(centerX: Int, centerZ: Int, radius: Int): CompletableFuture<Int> =
    ChunkLoader.loadRadius(this, centerX, centerZ, radius)

fun Instance.preloadSpawnChunks(radius: Int = 4): CompletableFuture<Int> =
    ChunkLoader.preloadAroundSpawn(this, radius = radius)
