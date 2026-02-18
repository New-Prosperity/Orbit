package me.nebula.orbit.utils.anvilloader

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.translation.translateDefault
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

object AnvilWorldLoader {

    private val logger = logger("AnvilWorldLoader")
    private val loaded = ConcurrentHashMap<String, InstanceContainer>()

    fun validate(worldPath: Path) {
        require(Files.isDirectory(worldPath)) { "World path does not exist: ${worldPath.toAbsolutePath()}" }
        val regionDir = worldPath.resolve("region")
        require(Files.isDirectory(regionDir)) { "Missing region/ directory in ${worldPath.toAbsolutePath()}" }
        val regionFiles = regionDir.listDirectoryEntries("*.mca")
        require(regionFiles.isNotEmpty()) { "No .mca region files in ${regionDir.toAbsolutePath()}" }
        regionFiles.forEach { file ->
            logger.info { "  Region ${file.name}: ${file.fileSize()} bytes" }
        }
        logger.info { "Validated world at ${worldPath.toAbsolutePath()}: ${regionFiles.size} region file(s)" }
        logDataVersion(worldPath)
    }

    private fun logDataVersion(worldPath: Path) {
        val levelDat = worldPath.resolve("level.dat")
        if (Files.exists(levelDat)) {
            logger.info { "level.dat found (${levelDat.fileSize()} bytes)" }
        } else {
            logger.warn { "No level.dat in world — cannot verify MC version" }
        }
        val regionDir = worldPath.resolve("region")
        val firstRegion = regionDir.listDirectoryEntries("*.mca").firstOrNull() ?: return
        try {
            RandomAccessFile(firstRegion.toFile(), "r").use { raf ->
                val fileSize = raf.length()
                if (fileSize < 8192) {
                    logger.warn { "Region file ${firstRegion.name} is only $fileSize bytes (header-only, no chunk data)" }
                    return
                }
                var populatedChunks = 0
                for (i in 0 until 1024) {
                    raf.seek((i * 4).toLong())
                    val offset = (raf.read() shl 16) or (raf.read() shl 8) or raf.read()
                    val sectorCount = raf.read()
                    if (offset != 0 && sectorCount != 0) populatedChunks++
                }
                logger.info { "Region ${firstRegion.name}: $populatedChunks populated chunk(s) out of 1024 slots, file size ${fileSize / 1024}KB" }
            }
        } catch (e: Exception) {
            logger.warn { "Failed to read region header: ${e.message}" }
        }
    }

    fun load(name: String, worldPath: Path): InstanceContainer {
        require(!loaded.containsKey(name)) { "Anvil world '$name' already loaded" }
        validate(worldPath)
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.chunkLoader = AnvilLoader(worldPath)
        loaded[name] = instance
        return instance
    }

    fun load(name: String, worldPath: String): InstanceContainer =
        load(name, Path.of(worldPath))

    fun loadAndPreload(
        name: String,
        worldPath: Path,
        centerChunkX: Int = 0,
        centerChunkZ: Int = 0,
        radius: Int = 4,
    ): Pair<InstanceContainer, CompletableFuture<Void>> {
        val instance = load(name, worldPath)
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
        logger.info { "Scanning column ${checkPos.blockX()}, ${checkPos.blockZ()} from Y $dimMaxY to $dimMinY" }

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
            logger.info { "Found $nonAirCount non-air blocks. Highest: $firstBlock at Y $firstBlockY" }
            return true
        }
        logger.warn { "ZERO non-air blocks in entire column ${checkPos.blockX()}, ${checkPos.blockZ()} (Y $dimMaxY to $dimMinY)" }

        val loadedChunks = instance.chunks.size
        var totalNonAir = 0L
        for (c in instance.chunks) {
            for (x in 0..15) {
                for (z in 0..15) {
                    for (y in dimMinY..dimMaxY) {
                        val b = c.getBlock(c.chunkX * 16 + x, y, c.chunkZ * 16 + z)
                        if (b != Block.AIR && b != Block.VOID_AIR && b != Block.CAVE_AIR) {
                            totalNonAir++
                            if (totalNonAir == 1L) {
                                logger.info { "First non-air block in entire instance: ${b.name()} at chunk ${c.chunkX},${c.chunkZ} local $x,$y,$z" }
                            }
                        }
                    }
                }
            }
            if (totalNonAir > 0) break
        }
        logger.warn { "Full scan: $loadedChunks chunks loaded, $totalNonAir non-air blocks found in first chunk with data (or 0 if none)" }
        return false
    }

    fun get(name: String): InstanceContainer? = loaded[name]

    fun require(name: String): InstanceContainer =
        requireNotNull(loaded[name]) { "Anvil world '$name' not loaded" }

    fun unload(name: String) {
        val instance = loaded.remove(name) ?: return
        instance.players.forEach {
            it.kick(translateDefault("orbit.util.world.unloading"))
        }
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
    }

    fun all(): Map<String, InstanceContainer> = loaded.toMap()

    fun names(): Set<String> = loaded.keys.toSet()

    fun isLoaded(name: String): Boolean = loaded.containsKey(name)

    fun unloadAll() {
        loaded.keys.toList().forEach { unload(it) }
    }
}

fun loadAnvilWorld(name: String, path: Path): InstanceContainer =
    AnvilWorldLoader.load(name, path)

fun loadAnvilWorld(name: String, path: String): InstanceContainer =
    AnvilWorldLoader.load(name, path)
