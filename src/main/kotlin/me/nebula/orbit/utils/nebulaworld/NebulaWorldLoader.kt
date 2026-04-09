package me.nebula.orbit.utils.nebulaworld

import me.nebula.ether.utils.logging.logger
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.readBytes

object NebulaWorldLoader {

    private val logger = logger("NebulaWorldLoader")
    private val loaded = ConcurrentHashMap<String, InstanceContainer>()

    fun load(name: String, path: Path): InstanceContainer {
        require(!loaded.containsKey(name)) { "World '$name' already loaded" }

        val bytes = path.readBytes()
        val world = NebulaWorldReader.read(bytes)

        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.chunkLoader = NebulaChunkLoader(world)
        loaded[name] = instance

        logger.info { "Loaded world '$name' from ${path.fileName} (${world.chunks.size} chunks, ${bytes.size / 1024}KB)" }
        return instance
    }

    fun loadAndPreload(
        name: String,
        path: Path,
        centerChunkX: Int = 0,
        centerChunkZ: Int = 0,
        radius: Int = 4,
    ): Pair<InstanceContainer, CompletableFuture<Void>> {
        val instance = load(name, path)
        val futures = mutableListOf<CompletableFuture<*>>()
        for (x in (centerChunkX - radius)..(centerChunkX + radius)) {
            for (z in (centerChunkZ - radius)..(centerChunkZ + radius)) {
                futures.add(instance.loadChunk(x, z))
            }
        }
        val totalChunks = futures.size
        val combined = CompletableFuture.allOf(*futures.toTypedArray())
        combined.thenRun {
            logger.info { "Preloaded $totalChunks chunks for world '$name' (center=$centerChunkX,$centerChunkZ radius=$radius)" }
        }
        return instance to combined
    }

    fun verifyLoaded(instance: InstanceContainer, checkPos: Pos): Boolean {
        val chunk = instance.getChunkAt(checkPos) ?: return false
        for (y in 319 downTo -64) {
            val block = chunk.getBlock(checkPos.blockX(), y, checkPos.blockZ())
            if (block != Block.AIR && block != Block.VOID_AIR && block != Block.CAVE_AIR) {
                logger.info { "Verified blocks at ${checkPos.blockX()},${checkPos.blockZ()}: ${block.name()} at Y $y" }
                return true
            }
        }
        logger.warn { "No non-air blocks at ${checkPos.blockX()},${checkPos.blockZ()}" }
        return false
    }

    fun get(name: String): InstanceContainer? = loaded[name]

    fun unload(name: String) {
        val instance = loaded.remove(name) ?: return
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
    }

    fun isLoaded(name: String): Boolean = loaded.containsKey(name)

    fun all(): Map<String, InstanceContainer> = loaded.toMap()
}
