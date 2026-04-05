package me.nebula.orbit.utils.nebulaworld

import me.nebula.ether.utils.logging.logger
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.ChunkLoader
import net.minestom.server.instance.Instance
import net.minestom.server.instance.Section
import net.minestom.server.instance.block.Block
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.biome.Biome
import java.util.concurrent.ConcurrentHashMap

private val logger = logger("NebulaChunkLoader")

private val blocksByStateString: Map<String, Block> by lazy {
    val map = HashMap<String, Block>()
    for (block in Block.values()) {
        map[block.name()] = block
        for (state in block.possibleStates()) {
            map[stateToString(state)] = state
        }
    }
    map
}

private fun stateToString(block: Block): String {
    val props = block.properties()
    if (props.isEmpty()) return block.name()
    val propStr = props.entries.joinToString(",") { "${it.key}=${it.value}" }
    return "${block.name()}[$propStr]"
}

private fun parseBlockState(state: String): Block {
    blocksByStateString[state]?.let { return it }
    val bracketIndex = state.indexOf('[')
    if (bracketIndex == -1) return Block.fromKey(state) ?: Block.AIR
    val name = state.substring(0, bracketIndex)
    val properties = state.substring(bracketIndex + 1, state.length - 1)
    var block = Block.fromKey(name) ?: return Block.AIR
    properties.split(',').forEach { prop ->
        val (key, value) = prop.split('=', limit = 2)
        block = block.withProperty(key, value)
    }
    return block
}

private val biomeIdCache = ConcurrentHashMap<String, Int>()

private fun resolveBiomeId(name: String): Int {
    return biomeIdCache.getOrPut(name) {
        val registry = MinecraftServer.getBiomeRegistry()
        val id = registry.getId(RegistryKey.unsafeOf<Biome>(name))
        if (id >= 0) id else registry.getId(Biome.PLAINS)
    }
}

class NebulaChunkLoader(
    private val world: NebulaWorld,
) : ChunkLoader {

    override fun loadChunk(instance: Instance, chunkX: Int, chunkZ: Int): Chunk? {
        val nebulaChunk = world.chunkAt(chunkX, chunkZ) ?: return null

        val chunk = instance.chunkSupplier.createChunk(instance, chunkX, chunkZ)

        for (sectionIndex in nebulaChunk.sections.indices) {
            val nebulaSection = nebulaChunk.sections[sectionIndex]
            if (nebulaSection.isEmpty) continue

            val section = chunk.getSection(world.minSection + sectionIndex)
            loadSection(section, nebulaSection)
        }

        for (be in nebulaChunk.blockEntities) {
            val worldX = chunkX * 16 + be.x
            val worldZ = chunkZ * 16 + be.z
            val block = chunk.getBlock(worldX, be.y, worldZ)
            if (be.nbt != null) {
                chunk.setBlock(worldX, be.y, worldZ, block.withNbt(be.nbt))
            }
        }

        return chunk
    }

    private fun loadSection(section: Section, nebula: NebulaSection) {
        val blockPalette = section.blockPalette()
        val resolvedBlocks = nebula.blockPalette.map { parseBlockState(it) }

        if (resolvedBlocks.size == 1) {
            blockPalette.fill(resolvedBlocks[0].stateId().toInt())
        } else {
            for (i in 0 until SECTION_BLOCK_COUNT) {
                val paletteIndex = if (i < nebula.blockData.size) nebula.blockData[i] else 0
                val block = resolvedBlocks.getOrElse(paletteIndex) { Block.AIR }
                val y = i / 256
                val z = (i % 256) / 16
                val x = i % 16
                blockPalette.set(x, y, z, block.stateId().toInt())
            }
        }

        val sectionBiomePalette = section.biomePalette()
        val resolvedBiomeIds = nebula.biomePalette.map { resolveBiomeId(it) }

        if (resolvedBiomeIds.size == 1) {
            sectionBiomePalette.fill(resolvedBiomeIds[0])
        } else {
            for (i in 0 until SECTION_BIOME_COUNT) {
                val paletteIndex = if (i < nebula.biomeData.size) nebula.biomeData[i] else 0
                val biomeId = resolvedBiomeIds.getOrElse(paletteIndex) {
                    MinecraftServer.getBiomeRegistry().getId(Biome.PLAINS)
                }
                val x = i % 4
                val z = (i / 4) % 4
                val y = i / 16
                sectionBiomePalette.set(x, y, z, biomeId)
            }
        }

        if (nebula.blockLight.content == LightContent.PRESENT && nebula.blockLight.data != null) {
            section.setBlockLight(nebula.blockLight.data)
        }
        if (nebula.skyLight.content == LightContent.PRESENT && nebula.skyLight.data != null) {
            section.setSkyLight(nebula.skyLight.data)
        }
    }

    override fun saveChunk(chunk: Chunk) {
    }

    override fun supportsParallelLoading(): Boolean = true

    override fun supportsParallelSaving(): Boolean = true
}
