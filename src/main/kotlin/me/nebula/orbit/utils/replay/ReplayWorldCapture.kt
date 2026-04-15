package me.nebula.orbit.utils.replay

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.nebulaworld.LightData
import me.nebula.orbit.utils.nebulaworld.NebulaBlockEntity
import me.nebula.orbit.utils.nebulaworld.NebulaChunk
import me.nebula.orbit.utils.nebulaworld.NebulaSection
import me.nebula.orbit.utils.nebulaworld.NebulaWorld
import me.nebula.orbit.utils.nebulaworld.SECTION_BIOME_COUNT
import me.nebula.orbit.utils.nebulaworld.SECTION_BLOCK_COUNT
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.Section
import net.minestom.server.instance.block.Block

private val logger = logger("ReplayWorldCapture")

private const val MIN_SECTION = -4
private const val MAX_SECTION = 19
private const val SECTION_COUNT = MAX_SECTION - MIN_SECTION + 1

object ReplayWorldCapture {

    fun capture(instance: Instance): NebulaWorld {
        val loadedChunks = instance.chunks
        logger.info { "Capturing ${loadedChunks.size} chunks for replay world snapshot" }

        val nebulaChunks = HashMap<Long, NebulaChunk>()
        for (chunk in loadedChunks) {
            nebulaChunks[NebulaWorld.packChunkKey(chunk.chunkX, chunk.chunkZ)] = extractChunk(chunk)
        }

        return NebulaWorld(
            dataVersion = 0,
            minSection = MIN_SECTION,
            maxSection = MAX_SECTION,
            chunks = nebulaChunks,
        )
    }

    private fun extractChunk(chunk: Chunk): NebulaChunk {
        val sections = Array(SECTION_COUNT) { sectionIndex ->
            extractSection(chunk.getSection(MIN_SECTION + sectionIndex))
        }

        val blockEntities = mutableListOf<NebulaBlockEntity>()
        for (x in 0..15) {
            for (z in 0..15) {
                for (sectionIndex in 0 until SECTION_COUNT) {
                    val baseY = (MIN_SECTION + sectionIndex) * 16
                    for (localY in 0..15) {
                        val worldY = baseY + localY
                        val block = chunk.getBlock(chunk.chunkX * 16 + x, worldY, chunk.chunkZ * 16 + z)
                        if (block.hasNbt()) {
                            blockEntities += NebulaBlockEntity(x, worldY, z, null, block.nbt())
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
                    val stateStr = blockToString(block)
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

        return NebulaSection(
            isEmpty = false,
            blockPalette = blockPaletteEntries.toTypedArray(),
            blockData = blockData,
            biomePalette = biomePaletteEntries.toTypedArray(),
            biomeData = biomeData,
            blockLight = LightData.EMPTY,
            skyLight = LightData.EMPTY,
        )
    }

    private fun blockToString(block: Block): String {
        val props = block.properties()
        if (props.isEmpty()) return block.name()
        return "${block.name()}[${props.entries.joinToString(",") { "${it.key}=${it.value}" }}]"
    }
}
