package me.nebula.orbit.utils.mapgen.planet

import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.customcontent.furniture.FurniturePersistence
import me.nebula.orbit.utils.nebulaworld.NebulaWorld
import me.nebula.orbit.utils.nebulaworld.NebulaWorldReader
import net.minestom.server.instance.block.Block

private val logger = logger("StructureLibrary")

data class StructureMetadata(
    val id: String,
    val nebulaPath: String,
    val socketY: Int = 0,
    val transitionRadius: Int = 6,
    val protectBox: List<Int>? = null,
    val underfillBlock: String = "minecraft:stone",
    val weight: Double = 1.0,
    val rotatable: Boolean = true,
    val biomes: Set<String>? = null,
    val excludedBiomes: Set<String> = emptySet(),
)

class LoadedStructure(
    val metadata: StructureMetadata,
    val width: Int,
    val height: Int,
    val length: Int,
    private val blocks: Array<Block>,
    val furnitureManifest: FurniturePersistence.Manifest?,
) {
    val protectBoxLocal: AABB by lazy {
        val pb = metadata.protectBox
        if (pb != null && pb.size == 6) AABB(pb[0], pb[1], pb[2], pb[3], pb[4], pb[5])
        else AABB(0, 0, 0, width - 1, height - 1, length - 1)
    }

    fun blockAt(x: Int, y: Int, z: Int): Block {
        if (x !in 0 until width || y !in 0 until height || z !in 0 until length) return Block.AIR
        return blocks[y * width * length + z * width + x]
    }

    companion object {
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
            val propsStr = state.substring(bracketIndex + 1, state.length - 1)
            var block = Block.fromKey(name) ?: return Block.AIR
            propsStr.split(',').forEach { prop ->
                val parts = prop.split('=', limit = 2)
                if (parts.size == 2) block = block.withProperty(parts[0], parts[1])
            }
            return block
        }

        fun fromNebulaWorld(metadata: StructureMetadata, world: NebulaWorld): LoadedStructure {
            val resolved = Array(world.globalBlockPalette.size) { idx ->
                parseBlockState(world.globalBlockPalette[idx])
            }

            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var minZ = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            var maxZ = Int.MIN_VALUE

            class SectionBlocks(val sectionIndex: Int, val data: Array<Block>)
            class ChunkBlocks(val cx: Int, val cz: Int, val perSection: Array<SectionBlocks?>)

            val collected = ArrayList<ChunkBlocks>()

            for (key in world.chunkKeys()) {
                val cx = (key shr 32).toInt()
                val cz = key.toInt()
                val chunk = world.chunkAt(cx, cz) ?: continue
                val sections = arrayOfNulls<SectionBlocks>(chunk.sections.size)
                for (s in chunk.sections.indices) {
                    val sec = chunk.sections[s]
                    if (sec.isEmpty) continue
                    val sectionData = Array(4096) { Block.AIR }
                    val refs = IntArray(sec.blockPaletteRefs.size) { i -> sec.blockPaletteRefs[i] }
                    if (refs.size == 1) {
                        val b = resolved[refs[0]]
                        if (b == Block.AIR) continue
                        for (i in 0 until 4096) sectionData[i] = b
                    } else {
                        for (i in 0 until 4096) {
                            val refIdx = sec.blockData[i]
                            sectionData[i] = resolved[refs[refIdx]]
                        }
                    }
                    val secY = world.minSection + s
                    var any = false
                    for (i in 0 until 4096) {
                        val b = sectionData[i]
                        if (b == Block.AIR) continue
                        any = true
                        val ly = i / 256
                        val lz = (i % 256) / 16
                        val lx = i % 16
                        val wx = cx * 16 + lx
                        val wy = secY * 16 + ly
                        val wz = cz * 16 + lz
                        if (wx < minX) minX = wx
                        if (wy < minY) minY = wy
                        if (wz < minZ) minZ = wz
                        if (wx > maxX) maxX = wx
                        if (wy > maxY) maxY = wy
                        if (wz > maxZ) maxZ = wz
                    }
                    if (any) sections[s] = SectionBlocks(s, sectionData)
                }
                collected += ChunkBlocks(cx, cz, sections)
            }

            val rawManifest = decodeFurniture(world)
            if (minX == Int.MAX_VALUE) {
                return LoadedStructure(metadata, 1, 1, 1, arrayOf(Block.AIR), rawManifest)
            }

            val width = maxX - minX + 1
            val height = maxY - minY + 1
            val length = maxZ - minZ + 1
            val flat = Array(width * height * length) { Block.AIR }

            for (chunk in collected) {
                for (sb in chunk.perSection) {
                    if (sb == null) continue
                    val secY = world.minSection + sb.sectionIndex
                    for (i in 0 until 4096) {
                        val b = sb.data[i]
                        if (b == Block.AIR) continue
                        val ly = i / 256
                        val lz = (i % 256) / 16
                        val lx = i % 16
                        val wx = chunk.cx * 16 + lx - minX
                        val wy = secY * 16 + ly - minY
                        val wz = chunk.cz * 16 + lz - minZ
                        flat[wy * width * length + wz * width + wx] = b
                    }
                }
            }

            val localManifest = rawManifest?.let { m ->
                m.copy(pieces = m.pieces.map { piece ->
                    piece.copy(
                        anchorX = piece.anchorX - minX,
                        anchorY = piece.anchorY - minY,
                        anchorZ = piece.anchorZ - minZ,
                    )
                })
            }

            return LoadedStructure(metadata, width, height, length, flat, localManifest)
        }

        private fun decodeFurniture(world: NebulaWorld): FurniturePersistence.Manifest? =
            if (world.userData.isNotEmpty()) FurniturePersistence.decode(world.userData) else null
    }
}

object StructureLibrary {

    private val gson = GsonProvider.default
    private val loaded = HashMap<String, LoadedStructure>()

    fun all(): Collection<LoadedStructure> = loaded.values

    operator fun get(id: String): LoadedStructure? = loaded[id]

    fun clear() = loaded.clear()

    fun loadFromResources(resources: ResourceManager, dir: String): Int {
        val metaPaths = runCatching { resources.list(dir, "json", recursive = true) }
            .getOrDefault(emptyList())

        var count = 0
        for (metaPath in metaPaths) {
            val bytes = resources.readBytes(metaPath)
            val meta = runCatching { gson.fromJson(bytes.toString(Charsets.UTF_8), StructureMetadata::class.java) }
                .getOrElse { e ->
                    logger.warn { "Failed to parse structure metadata $metaPath: ${e.message}" }
                    continue
                }

            val nebulaResolved = resolvePath(dir, meta.nebulaPath)
            if (!resources.exists(nebulaResolved)) {
                logger.warn { "Structure file missing for '${meta.id}': $nebulaResolved" }
                continue
            }

            val world = NebulaWorldReader.read(resources.readBytes(nebulaResolved))
            val structure = LoadedStructure.fromNebulaWorld(meta, world)
            loaded[meta.id] = structure
            count++
            logger.info { "Loaded structure '${meta.id}' (${structure.width}x${structure.height}x${structure.length}${if (structure.furnitureManifest != null) ", ${structure.furnitureManifest.pieces.size} furniture" else ""})" }
        }
        return count
    }

    private fun resolvePath(dir: String, path: String): String {
        if (path.startsWith("/") || path.contains(":")) return path.removePrefix("/")
        return "$dir/$path"
    }
}
