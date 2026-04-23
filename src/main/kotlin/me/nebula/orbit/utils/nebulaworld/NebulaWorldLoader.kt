package me.nebula.orbit.utils.nebulaworld

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.translation.translateDefault
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.readBytes
import me.nebula.gravity.translation.Keys

object NebulaWorldLoader {

    private val logger = logger("NebulaWorldLoader")
    private val loaded = ConcurrentHashMap<String, InstanceContainer>()
    private val worldsByName = ConcurrentHashMap<String, NebulaWorld>()
    private val postLoadHooks = CopyOnWriteArrayList<(InstanceContainer, NebulaWorld) -> Unit>()
    private val preUnloadHooks = CopyOnWriteArrayList<(InstanceContainer, NebulaWorld) -> Unit>()

    fun registerPostLoadHook(hook: (InstanceContainer, NebulaWorld) -> Unit) {
        postLoadHooks += hook
    }

    fun registerPreUnloadHook(hook: (InstanceContainer, NebulaWorld) -> Unit) {
        preUnloadHooks += hook
    }

    fun unregisterPostLoadHook(hook: (InstanceContainer, NebulaWorld) -> Unit) {
        postLoadHooks -= hook
    }

    fun unregisterPreUnloadHook(hook: (InstanceContainer, NebulaWorld) -> Unit) {
        preUnloadHooks -= hook
    }

    fun load(name: String, path: Path): InstanceContainer {
        require(!loaded.containsKey(name)) { "World '$name' already loaded" }

        val bytes = path.readBytes()
        val world = NebulaWorldReader.read(bytes)

        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.chunkLoader = NebulaChunkLoader(world)
        loaded[name] = instance
        worldsByName[name] = world

        logger.info { "Loaded world '$name' from ${path.fileName} (${world.chunks.size} chunks, ${bytes.size / 1024}KB${if (world.userData.isNotEmpty()) ", ${world.userData.size}B userData" else ""})" }
        for (hook in postLoadHooks) {
            runCatching { hook(instance, world) }.onFailure {
                logger.warn { "Post-load hook failed for '$name': ${it.message}" }
            }
        }
        return instance
    }

    fun worldFor(name: String): NebulaWorld? = worldsByName[name]

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
        val chunk = instance.getChunkAt(checkPos)
        if (chunk == null) {
            logger.warn { "Chunk at ${checkPos.blockX() shr 4}, ${checkPos.blockZ() shr 4} is null" }
            return false
        }

        val dimMinY = -64
        val dimMaxY = 319

        var nonAirCount = 0
        var firstBlock: String? = null
        var firstBlockY = 0
        for (y in dimMaxY downTo dimMinY) {
            val block = chunk.getBlock(checkPos.blockX(), y, checkPos.blockZ())
            if (block != Block.AIR && block != Block.VOID_AIR && block != Block.CAVE_AIR) {
                nonAirCount++
                if (firstBlock == null) {
                    firstBlock = block.name()
                    firstBlockY = y
                }
            }
        }
        if (firstBlock != null) {
            logger.info { "Verified column ${checkPos.blockX()},${checkPos.blockZ()}: $nonAirCount non-air blocks, highest $firstBlock at Y $firstBlockY" }
            return true
        }
        logger.warn { "No non-air blocks in column ${checkPos.blockX()}, ${checkPos.blockZ()} (Y $dimMaxY to $dimMinY)" }
        return false
    }

    fun get(name: String): InstanceContainer? = loaded[name]

    fun require(name: String): InstanceContainer =
        requireNotNull(loaded[name]) { "World '$name' not loaded" }

    fun isLoaded(name: String): Boolean = loaded.containsKey(name)

    fun names(): Set<String> = loaded.keys.toSet()

    fun all(): Map<String, InstanceContainer> = loaded.toMap()

    fun unload(name: String) {
        val instance = loaded.remove(name) ?: return
        val world = worldsByName.remove(name)
        if (world != null) {
            for (hook in preUnloadHooks) {
                runCatching { hook(instance, world) }.onFailure {
                    logger.warn { "Pre-unload hook failed for '$name': ${it.message}" }
                }
            }
        }
        instance.players.forEach {
            it.kick(translateDefault(Keys.Orbit.Util.World.Unloading))
        }
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
    }

    fun unloadAll() {
        loaded.keys.toList().forEach { unload(it) }
    }
}
