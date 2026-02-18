package me.nebula.orbit.utils.worldreset

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import java.util.concurrent.CompletableFuture

object WorldReset {

    fun resetChunks(instance: InstanceContainer, minChunkX: Int, minChunkZ: Int, maxChunkX: Int, maxChunkZ: Int): CompletableFuture<Int> {
        val chunks = instance.chunks.filter { chunk ->
            chunk.chunkX in minChunkX..maxChunkX && chunk.chunkZ in minChunkZ..maxChunkZ
        }
        chunks.forEach { instance.unloadChunk(it) }
        val futures = mutableListOf<CompletableFuture<*>>()
        for (x in minChunkX..maxChunkX) {
            for (z in minChunkZ..maxChunkZ) {
                futures.add(instance.loadChunk(x, z))
            }
        }
        return CompletableFuture.allOf(*futures.toTypedArray()).thenApply { chunks.size }
    }

    fun resetRadius(instance: InstanceContainer, centerX: Int, centerZ: Int, radius: Int): CompletableFuture<Int> =
        resetChunks(instance, centerX - radius, centerZ - radius, centerX + radius, centerZ + radius)

    fun clearArea(instance: InstanceContainer, min: Pos, max: Pos) {
        val minX = minOf(min.blockX(), max.blockX())
        val minY = minOf(min.blockY(), max.blockY())
        val minZ = minOf(min.blockZ(), max.blockZ())
        val maxX = maxOf(min.blockX(), max.blockX())
        val maxY = maxOf(min.blockY(), max.blockY())
        val maxZ = maxOf(min.blockZ(), max.blockZ())
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    instance.setBlock(x, y, z, Block.AIR)
                }
            }
        }
    }

    fun fillArea(instance: InstanceContainer, min: Pos, max: Pos, block: Block) {
        val minX = minOf(min.blockX(), max.blockX())
        val minY = minOf(min.blockY(), max.blockY())
        val minZ = minOf(min.blockZ(), max.blockZ())
        val maxX = maxOf(min.blockX(), max.blockX())
        val maxY = maxOf(min.blockY(), max.blockY())
        val maxZ = maxOf(min.blockZ(), max.blockZ())
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    instance.setBlock(x, y, z, block)
                }
            }
        }
    }

    fun recreateInstance(
        instance: InstanceContainer,
        teleportTo: InstanceContainer? = null,
        teleportPos: Pos = Pos(0.0, 64.0, 0.0),
    ): InstanceContainer {
        val generator = instance.generator()
        val chunkLoader = instance.chunkLoader

        if (teleportTo != null) {
            instance.players.forEach { it.setInstance(teleportTo, teleportPos) }
        }

        MinecraftServer.getInstanceManager().unregisterInstance(instance)

        val newInstance = MinecraftServer.getInstanceManager().createInstanceContainer()
        generator?.let { newInstance.setGenerator(it) }
        chunkLoader?.let { newInstance.chunkLoader = it }
        return newInstance
    }

    fun removeEntities(instance: InstanceContainer, keepPlayers: Boolean = true) {
        instance.entities.forEach { entity ->
            if (keepPlayers && entity is net.minestom.server.entity.Player) return@forEach
            entity.remove()
        }
    }
}

fun InstanceContainer.resetChunks(minChunkX: Int, minChunkZ: Int, maxChunkX: Int, maxChunkZ: Int): CompletableFuture<Int> =
    WorldReset.resetChunks(this, minChunkX, minChunkZ, maxChunkX, maxChunkZ)

fun InstanceContainer.clearEntities(keepPlayers: Boolean = true) =
    WorldReset.removeEntities(this, keepPlayers)
