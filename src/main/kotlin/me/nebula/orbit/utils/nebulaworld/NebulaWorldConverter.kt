package me.nebula.orbit.utils.nebulaworld

import me.nebula.ether.utils.logging.logger
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Section
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private val logger = logger("NebulaWorldConverter")

object NebulaWorldConverter {

    private const val MIN_SECTION = -4
    private const val MAX_SECTION = 19

    fun convert(anvilPath: Path, outputPath: Path, radiusChunks: Int = -1) {
        val world = buildNebulaWorld(anvilPath, radiusChunks)
        NebulaWorldWriter.write(world, outputPath)
        logger.info { "Converted ${world.chunks.size} chunks → ${outputPath.fileName} (${Files.size(outputPath) / 1024}KB)" }
    }

    fun convertToBytes(anvilPath: Path, radiusChunks: Int = -1): ByteArray {
        val world = buildNebulaWorld(anvilPath, radiusChunks)
        return NebulaWorldWriter.write(world)
    }

    private fun buildNebulaWorld(anvilPath: Path, radiusChunks: Int): NebulaWorld {
        val regionDir = resolveRegionDir(anvilPath)
        require(Files.isDirectory(regionDir)) { "No region directory at $regionDir" }

        val anvilRoot = anvilPath.resolve("dimensions/minecraft/overworld")

        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.chunkLoader = AnvilLoader(anvilRoot)

        try {
            val chunkCoords = if (radiusChunks > 0) {
                buildRadiusCoords(radiusChunks)
            } else {
                discoverChunksFromRegions(regionDir)
            }

            logger.info { "Loading ${chunkCoords.size} chunks from Anvil at $anvilPath..." }
            val futures = chunkCoords.map { (x, z) -> instance.loadChunk(x, z) }
            CompletableFuture.allOf(*futures.toTypedArray()).join()

            val loadedChunks = instance.chunks
            logger.info { "Loaded ${loadedChunks.size} chunks, extracting sections..." }

            val nebulaChunks = HashMap<Long, NebulaChunk>()
            for (chunk in loadedChunks) {
                val nebulaChunk = extractChunk(chunk)
                nebulaChunks[NebulaWorld.packChunkKey(chunk.chunkX, chunk.chunkZ)] = nebulaChunk
            }

            return NebulaWorld(
                dataVersion = 0,
                minSection = MIN_SECTION,
                maxSection = MAX_SECTION,
                chunks = nebulaChunks,
            )
        } finally {
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
        }
    }

    private fun extractChunk(chunk: Chunk): NebulaChunk {
        val sectionCount = MAX_SECTION - MIN_SECTION + 1
        val sections = Array(sectionCount) { sectionIndex ->
            val section = chunk.getSection(MIN_SECTION + sectionIndex)
            extractSection(section)
        }

        val blockEntities = mutableListOf<NebulaBlockEntity>()
        for (x in 0..15) {
            for (z in 0..15) {
                for (sectionIndex in 0 until sectionCount) {
                    val baseY = (MIN_SECTION + sectionIndex) * 16
                    for (localY in 0..15) {
                        val worldY = baseY + localY
                        val block = chunk.getBlock(chunk.chunkX * 16 + x, worldY, chunk.chunkZ * 16 + z)
                        if (block.hasNbt()) {
                            blockEntities += NebulaBlockEntity(
                                x = x,
                                y = worldY,
                                z = z,
                                id = block.handler()?.let { "minecraft:${block.name().removePrefix("minecraft:")}" },
                                nbt = block.nbt(),
                            )
                        }
                    }
                }
            }
        }

        return NebulaChunk(chunk.chunkX, chunk.chunkZ, sections, blockEntities)
    }

    private fun extractSection(section: Section): NebulaSection {
        val blockPalette = section.blockPalette()
        val biomePalette = section.biomePalette()

        val blockPaletteEntries = mutableListOf<String>()
        val blockStateMap = HashMap<String, Int>()
        val blockData = IntArray(SECTION_BLOCK_COUNT)
        var allAir = true

        for (y in 0..15) {
            for (z in 0..15) {
                for (x in 0..15) {
                    val stateId = blockPalette.get(x, y, z)
                    val block = Block.fromStateId(stateId) ?: Block.AIR
                    if (block != Block.AIR) allAir = false

                    val stateStr = stateToString(block)
                    val paletteIndex = blockStateMap.getOrPut(stateStr) {
                        blockPaletteEntries += stateStr
                        blockPaletteEntries.size - 1
                    }
                    blockData[y * 256 + z * 16 + x] = paletteIndex
                }
            }
        }

        if (allAir) return NebulaSection.EMPTY

        val biomePaletteEntries = mutableListOf<String>()
        val biomeStateMap = HashMap<Int, Int>()
        val biomeData = IntArray(SECTION_BIOME_COUNT)
        val biomeRegistry = MinecraftServer.getBiomeRegistry()

        for (y in 0..3) {
            for (z in 0..3) {
                for (x in 0..3) {
                    val biomeId = biomePalette.get(x, y, z)
                    val paletteIndex = biomeStateMap.getOrPut(biomeId) {
                        val key = biomeRegistry.getKey(biomeId)
                        biomePaletteEntries += key?.name() ?: "minecraft:plains"
                        biomePaletteEntries.size - 1
                    }
                    biomeData[x + z * 4 + y * 16] = paletteIndex
                }
            }
        }

        val blockLightObj = section.blockLight()
        val skyLightObj = section.skyLight()

        val blockLightArray = blockLightObj.array()
        val skyLightArray = skyLightObj.array()

        val blockLightData = if (blockLightArray.isNotEmpty() && blockLightArray.any { it != 0.toByte() }) {
            LightData(LightContent.PRESENT, blockLightArray.copyOf())
        } else {
            LightData.EMPTY
        }

        val skyLightData = if (skyLightArray.isNotEmpty() && skyLightArray.any { it != 0.toByte() }) {
            LightData(LightContent.PRESENT, skyLightArray.copyOf())
        } else {
            LightData.EMPTY
        }

        return NebulaSection(
            isEmpty = false,
            blockPalette = blockPaletteEntries.toTypedArray(),
            blockData = blockData,
            biomePalette = biomePaletteEntries.toTypedArray(),
            biomeData = biomeData,
            blockLight = blockLightData,
            skyLight = skyLightData,
        )
    }

    private fun stateToString(block: Block): String {
        val props = block.properties()
        if (props.isEmpty()) return block.name()
        val propStr = props.entries.joinToString(",") { "${it.key}=${it.value}" }
        return "${block.name()}[$propStr]"
    }

    private fun resolveRegionDir(worldPath: Path): Path =
        worldPath.resolve("dimensions/minecraft/overworld/region")

    private fun buildRadiusCoords(radius: Int): List<Pair<Int, Int>> {
        val coords = mutableListOf<Pair<Int, Int>>()
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                coords += x to z
            }
        }
        return coords
    }

    private fun discoverChunksFromRegions(regionDir: Path): List<Pair<Int, Int>> {
        val coords = mutableListOf<Pair<Int, Int>>()
        val regionFiles = regionDir.listDirectoryEntries("*.mca")
        for (file in regionFiles) {
            val name = file.name
            val parts = name.removePrefix("r.").removeSuffix(".mca").split(".")
            if (parts.size != 2) continue
            val rx = parts[0].toIntOrNull() ?: continue
            val rz = parts[1].toIntOrNull() ?: continue
            for (cx in 0..31) {
                for (cz in 0..31) {
                    coords += (rx * 32 + cx) to (rz * 32 + cz)
                }
            }
        }
        return coords
    }
}
