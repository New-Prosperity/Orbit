package me.nebula.orbit.utils.mapgen

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.schematic.Schematic
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import kotlin.math.max
import kotlin.random.Random

enum class StructureCategory { SURFACE, UNDERGROUND, EMBEDDED }

enum class EntranceType { SHAFT, STAIRCASE, NONE }

data class SchematicStructureDef(
    val id: String,
    val schematicPath: String,
    val weight: Double = 1.0,
    val anchorY: Int = 0,
    val burialDepth: Int = 2,
    val maxSlopeVariance: Int = 5,
    val foundationBlock: String = "minecraft:cobblestone",
    val rotatable: Boolean = true,
    val biomes: Set<String>? = null,
    val excludedBiomes: Set<String> = emptySet(),
    val category: StructureCategory = StructureCategory.SURFACE,
    val minY: Int = 10,
    val maxY: Int = 40,
    val depthFromSurface: Int = 0,
    val generateEntrance: Boolean = false,
    val entranceType: EntranceType = EntranceType.SHAFT,
    val shellBlock: String? = null,
    val lootTableId: String? = null,
)

data class SchematicPopulationConfig(
    val enabled: Boolean = true,
    val spacing: Int = 4,
    val chance: Double = 0.3,
    val definitions: List<SchematicStructureDef> = emptyList(),
    val placeholderBlock: String = "minecraft:dragon_egg",
    val replacementBlock: String = "minecraft:chest",
)

data class SchematicPlacement(
    val origin: Pos,
    val definitionId: String,
    val rotation: Int,
    val chestPositions: List<Pos> = emptyList(),
)

class SchematicPopulator(
    private val seed: Long,
    private val terrain: TerrainGenerator,
    private val config: SchematicPopulationConfig,
) {

    private val logger = logger("SchematicPopulator")
    private val schematics = mutableMapOf<String, Schematic>()
    private val occupiedCells = mutableSetOf<Long>()
    private val _placements = mutableListOf<SchematicPlacement>()
    val placements: List<SchematicPlacement> get() = _placements.toList()

    private val placeholderBlock = resolveBlock(config.placeholderBlock)
    private val replacementBlock = resolveBlock(config.replacementBlock)

    init {
        config.definitions.forEach { def ->
            val stream = SchematicPopulator::class.java.getResourceAsStream("/schematics/${def.schematicPath}")
            if (stream != null) {
                schematics[def.id] = Schematic.load(stream)
                logger.info { "Loaded schematic '${def.id}' from ${def.schematicPath} (${schematics[def.id]!!.width}x${schematics[def.id]!!.height}x${schematics[def.id]!!.length})" }
            } else {
                logger.warn { "Schematic not found for '${def.id}': /schematics/${def.schematicPath}" }
            }
        }
    }

    fun populate(instance: InstanceContainer, radiusChunks: Int) {
        if (!config.enabled || schematics.isEmpty()) return

        val random = Random(seed + 900)
        val available = config.definitions.filter { schematics.containsKey(it.id) }
        if (available.isEmpty()) return

        for (cx in -radiusChunks..radiusChunks step config.spacing) {
            for (cz in -radiusChunks..radiusChunks step config.spacing) {
                if (random.nextDouble() > config.chance) continue
                if (isOccupied(cx, cz)) continue
                if (instance.getChunk(cx, cz) == null) continue

                val wx = cx * 16 + random.nextInt(16)
                val wz = cz * 16 + random.nextInt(16)
                val biome = terrain.biomes.biomeAt(wx, wz)
                val surfaceY = terrain.surfaceHeight(wx, wz)

                val candidates = available.filter { def ->
                    (def.biomes == null || biome.id in def.biomes) &&
                        biome.id !in def.excludedBiomes
                }
                if (candidates.isEmpty()) continue

                val def = weightedRandom(candidates, random) ?: continue
                val schematic = schematics[def.id] ?: continue
                val rotation = if (def.rotatable) random.nextInt(4) else 0

                val chests = when (def.category) {
                    StructureCategory.SURFACE -> placeSurface(instance, schematic, def, wx, wz, surfaceY, rotation)
                    StructureCategory.UNDERGROUND -> placeUnderground(instance, schematic, def, wx, wz, surfaceY, rotation, random)
                    StructureCategory.EMBEDDED -> placeEmbedded(instance, schematic, def, wx, wz, surfaceY, rotation)
                }

                if (chests != null) {
                    val (footW, footL) = rotatedSize(schematic.width, schematic.length, rotation)
                    markOccupied(cx, cz, footW, footL)
                    _placements.add(SchematicPlacement(
                        Pos(wx.toDouble(), surfaceY.toDouble(), wz.toDouble()),
                        def.id,
                        rotation,
                        chests,
                    ))
                }
            }
        }
    }

    private fun placeSurface(
        instance: InstanceContainer,
        schematic: Schematic,
        def: SchematicStructureDef,
        wx: Int, wz: Int,
        surfaceY: Int,
        rotation: Int,
    ): List<Pos>? {
        val (footW, footL) = rotatedSize(schematic.width, schematic.length, rotation)

        val heights = sampleHeights(wx, wz, footW, footL)
        if (heights.isEmpty()) return null

        val minH = heights.min()
        val maxH = heights.max()
        if (maxH - minH > def.maxSlopeVariance) return null
        if (minH < terrain.seaLevel) return null
        if (terrain.isRiverAt(wx + footW / 2, wz + footL / 2)) return null

        heights.sort()
        val medianY = heights[heights.size / 2]
        val placementY = medianY - def.burialDepth - def.anchorY
        val foundation = resolveBlock(def.foundationBlock)

        val chests = pasteRotated(instance, schematic, wx, placementY, wz, rotation)

        fillFoundation(instance, schematic, def, wx, wz, placementY, rotation, footW, footL, foundation)
        carveInterior(instance, schematic, def, wx, wz, placementY, rotation, footW, footL)

        return chests
    }

    private fun placeUnderground(
        instance: InstanceContainer,
        schematic: Schematic,
        def: SchematicStructureDef,
        wx: Int, wz: Int,
        surfaceY: Int,
        rotation: Int,
        random: Random,
    ): List<Pos>? {
        val (footW, footL) = rotatedSize(schematic.width, schematic.length, rotation)

        val placementY = if (def.depthFromSurface > 0) {
            surfaceY - def.depthFromSurface - schematic.height
        } else {
            random.nextInt(def.minY.coerceAtLeast(2), def.maxY.coerceAtMost(surfaceY - schematic.height) + 1)
        }

        if (placementY < 2) return null
        if (placementY + schematic.height >= surfaceY - 2) return null

        val shell = def.shellBlock?.let { resolveBlock(it) }
        carveCavity(instance, wx, wz, placementY, footW, footL, schematic.height, shell)

        val chests = pasteRotated(instance, schematic, wx, placementY, wz, rotation)

        if (def.generateEntrance) {
            val entranceX = wx + footW / 2
            val entranceZ = wz + footL / 2
            val topY = surfaceY
            val bottomY = placementY + schematic.height - 1

            when (def.entranceType) {
                EntranceType.SHAFT -> generateShaftEntrance(instance, entranceX, entranceZ, topY, bottomY)
                EntranceType.STAIRCASE -> generateStaircaseEntrance(instance, entranceX, entranceZ, topY, bottomY)
                EntranceType.NONE -> {}
            }
        }

        return chests
    }

    private fun placeEmbedded(
        instance: InstanceContainer,
        schematic: Schematic,
        def: SchematicStructureDef,
        wx: Int, wz: Int,
        surfaceY: Int,
        rotation: Int,
    ): List<Pos>? {
        val (footW, footL) = rotatedSize(schematic.width, schematic.length, rotation)

        val heights = sampleHeights(wx, wz, footW, footL)
        if (heights.isEmpty()) return null

        val minH = heights.min()
        if (minH < terrain.seaLevel - 2) return null
        if (terrain.isRiverAt(wx + footW / 2, wz + footL / 2)) return null

        val placementY = minH - def.burialDepth - def.anchorY
        val foundation = resolveBlock(def.foundationBlock)

        val chests = pasteRotated(instance, schematic, wx, placementY, wz, rotation)

        fillFoundation(instance, schematic, def, wx, wz, placementY, rotation, footW, footL, foundation)
        carveInterior(instance, schematic, def, wx, wz, placementY, rotation, footW, footL)

        return chests
    }

    private fun pasteRotated(
        instance: InstanceContainer,
        schematic: Schematic,
        wx: Int, wy: Int, wz: Int,
        rotation: Int,
    ): List<Pos> {
        val chests = mutableListOf<Pos>()
        for (x in 0 until schematic.width) {
            for (y in 0 until schematic.height) {
                for (z in 0 until schematic.length) {
                    val block = schematic.getBlock(x, y, z)
                    if (block.isAir) continue

                    val (rx, rz) = rotateCoord(x, z, rotation, schematic.width, schematic.length)
                    val worldX = wx + rx
                    val worldY = wy + y
                    val worldZ = wz + rz

                    if (block.compare(placeholderBlock)) {
                        instance.setBlock(worldX, worldY, worldZ, replacementBlock)
                        chests.add(Pos(worldX.toDouble(), worldY.toDouble(), worldZ.toDouble()))
                    } else {
                        instance.setBlock(worldX, worldY, worldZ, rotateBlock(block, rotation))
                    }
                }
            }
        }
        return chests
    }

    private fun fillFoundation(
        instance: InstanceContainer,
        schematic: Schematic,
        def: SchematicStructureDef,
        wx: Int, wz: Int,
        placementY: Int,
        rotation: Int,
        footW: Int, footL: Int,
        foundation: Block,
    ) {
        for (rx in 0 until footW) {
            for (rz in 0 until footL) {
                val worldX = wx + rx
                val worldZ = wz + rz
                val terrainY = terrain.surfaceHeight(worldX, worldZ)

                val (origX, origZ) = inverseRotateCoord(rx, rz, rotation, schematic.width, schematic.length)
                var lowestSchematicY = -1
                for (sy in 0 until schematic.height) {
                    if (origX in 0 until schematic.width && origZ in 0 until schematic.length) {
                        if (!schematic.getBlock(origX, sy, origZ).isAir) {
                            lowestSchematicY = placementY + sy
                            break
                        }
                    }
                }

                if (lowestSchematicY > 0 && lowestSchematicY > terrainY + 1) {
                    for (fillY in (terrainY + 1) until lowestSchematicY) {
                        instance.setBlock(worldX, fillY, worldZ, foundation)
                    }
                }
            }
        }
    }

    private fun carveInterior(
        instance: InstanceContainer,
        schematic: Schematic,
        def: SchematicStructureDef,
        wx: Int, wz: Int,
        placementY: Int,
        rotation: Int,
        footW: Int, footL: Int,
    ) {
        for (rx in 0 until footW) {
            for (rz in 0 until footL) {
                val worldX = wx + rx
                val worldZ = wz + rz
                val terrainY = terrain.surfaceHeight(worldX, worldZ)
                val floorY = placementY + def.anchorY

                if (terrainY <= floorY) continue

                val (origX, origZ) = inverseRotateCoord(rx, rz, rotation, schematic.width, schematic.length)
                if (origX !in 0 until schematic.width || origZ !in 0 until schematic.length) continue

                for (carveY in (floorY + 1)..terrainY) {
                    val sy = carveY - placementY
                    if (sy in 0 until schematic.height) {
                        if (schematic.getBlock(origX, sy, origZ).isAir) {
                            val existing = instance.getBlock(worldX, carveY, worldZ)
                            if (!existing.isAir) {
                                instance.setBlock(worldX, carveY, worldZ, Block.AIR)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun carveCavity(
        instance: InstanceContainer,
        wx: Int, wz: Int,
        placementY: Int,
        footW: Int, footL: Int,
        height: Int,
        shell: Block?,
    ) {
        for (dx in -1..footW) {
            for (dz in -1..footL) {
                for (dy in -1..height) {
                    val worldX = wx + dx
                    val worldY = placementY + dy
                    val worldZ = wz + dz

                    val isShell = dx == -1 || dx == footW || dz == -1 || dz == footL ||
                        dy == -1 || dy == height

                    if (isShell) {
                        if (shell != null) {
                            val existing = instance.getBlock(worldX, worldY, worldZ)
                            if (!existing.isAir && !existing.compare(Block.WATER)) {
                                instance.setBlock(worldX, worldY, worldZ, shell)
                            }
                        }
                    } else {
                        instance.setBlock(worldX, worldY, worldZ, Block.CAVE_AIR)
                    }
                }
            }
        }
    }

    private fun generateShaftEntrance(
        instance: InstanceContainer,
        x: Int, z: Int,
        topY: Int, bottomY: Int,
    ) {
        for (y in bottomY..topY) {
            instance.setBlock(x, y, z, Block.LADDER.withProperty("facing", "south"))
            instance.setBlock(x, y, z + 1, Block.AIR)
        }

        for (y in bottomY until topY) {
            instance.setBlock(x - 1, y, z, Block.OAK_PLANKS)
            instance.setBlock(x + 1, y, z, Block.OAK_PLANKS)
            instance.setBlock(x - 1, y, z + 1, Block.OAK_PLANKS)
            instance.setBlock(x + 1, y, z + 1, Block.OAK_PLANKS)
        }

        for (y in bottomY until topY step 4) {
            for (dx in -1..1) {
                instance.setBlock(x + dx, y, z - 1, Block.OAK_PLANKS)
                instance.setBlock(x + dx, y, z + 2, Block.OAK_PLANKS)
            }
        }

        instance.setBlock(x - 1, topY + 1, z, Block.OAK_FENCE)
        instance.setBlock(x + 1, topY + 1, z, Block.OAK_FENCE)
        instance.setBlock(x - 1, topY + 1, z + 1, Block.OAK_FENCE)
        instance.setBlock(x + 1, topY + 1, z + 1, Block.OAK_FENCE)
        instance.setBlock(x, topY + 1, z - 1, Block.OAK_FENCE)
        instance.setBlock(x, topY + 1, z + 1 + 1, Block.OAK_FENCE)
    }

    private fun generateStaircaseEntrance(
        instance: InstanceContainer,
        x: Int, z: Int,
        topY: Int, bottomY: Int,
    ) {
        val facings = listOf("south", "west", "north", "east")
        val offsets = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)
        var step = 0

        for (y in topY downTo bottomY) {
            val dir = step % 4
            val (ox, oz) = offsets[dir]
            val sx = x + ox
            val sz = z + oz

            instance.setBlock(sx, y, sz, Block.STONE_BRICK_STAIRS.withProperty("facing", facings[dir]))
            instance.setBlock(sx, y + 1, sz, Block.AIR)
            instance.setBlock(sx, y + 2, sz, Block.AIR)
            if (y + 3 <= topY + 2) instance.setBlock(sx, y + 3, sz, Block.AIR)

            step++
        }

        for (y in bottomY..topY) {
            instance.setBlock(x, y, z, Block.STONE_BRICKS)
        }
    }

    private fun sampleHeights(wx: Int, wz: Int, footW: Int, footL: Int): IntArray {
        val points = mutableListOf<Int>()
        val stepX = max(1, footW / 3)
        val stepZ = max(1, footL / 3)
        var sx = 0
        while (sx < footW) {
            var sz = 0
            while (sz < footL) {
                points.add(terrain.surfaceHeight(wx + sx, wz + sz))
                sz += stepZ
            }
            points.add(terrain.surfaceHeight(wx + sx, wz + footL - 1))
            sx += stepX
        }
        sx = footW - 1
        var sz = 0
        while (sz < footL) {
            points.add(terrain.surfaceHeight(wx + sx, wz + sz))
            sz += stepZ
        }
        points.add(terrain.surfaceHeight(wx + footW / 2, wz + footL / 2))
        return points.toIntArray()
    }

    private fun rotateCoord(x: Int, z: Int, rotations: Int, width: Int, length: Int): Pair<Int, Int> =
        when (rotations % 4) {
            1 -> (length - 1 - z) to x
            2 -> (width - 1 - x) to (length - 1 - z)
            3 -> z to (width - 1 - x)
            else -> x to z
        }

    private fun inverseRotateCoord(rx: Int, rz: Int, rotations: Int, width: Int, length: Int): Pair<Int, Int> =
        when (rotations % 4) {
            1 -> rz to (length - 1 - rx)
            2 -> (width - 1 - rx) to (length - 1 - rz)
            3 -> (width - 1 - rz) to rx
            else -> rx to rz
        }

    private fun rotatedSize(width: Int, length: Int, rotations: Int): Pair<Int, Int> =
        if (rotations % 2 == 0) width to length else length to width

    private fun rotateBlock(block: Block, rotations: Int): Block {
        if (rotations == 0) return block
        val facing = block.getProperty("facing")
        if (facing != null && facing in HORIZONTAL_FACINGS) {
            return block.withProperty("facing", rotateFacing(facing, rotations))
        }
        val axis = block.getProperty("axis")
        if (axis != null && rotations % 2 != 0) {
            return when (axis) {
                "x" -> block.withProperty("axis", "z")
                "z" -> block.withProperty("axis", "x")
                else -> block
            }
        }
        return block
    }

    private fun rotateFacing(facing: String, rotations: Int): String {
        val idx = HORIZONTAL_FACINGS.indexOf(facing)
        if (idx == -1) return facing
        return HORIZONTAL_FACINGS[(idx + rotations) % 4]
    }

    private fun resolveBlock(name: String): Block =
        Block.fromKey(if (":" in name) name else "minecraft:${name.lowercase()}")
            ?: Block.COBBLESTONE

    private fun packCell(cx: Int, cz: Int): Long =
        (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    private fun isOccupied(cx: Int, cz: Int): Boolean = occupiedCells.contains(packCell(cx, cz))

    private fun markOccupied(cx: Int, cz: Int, footW: Int, footL: Int) {
        val chunkSpanX = (footW / 16) + 1
        val chunkSpanZ = (footL / 16) + 1
        for (dx in 0..chunkSpanX) {
            for (dz in 0..chunkSpanZ) {
                occupiedCells.add(packCell(cx + dx, cz + dz))
            }
        }
    }

    private fun weightedRandom(defs: List<SchematicStructureDef>, random: Random): SchematicStructureDef? {
        val totalWeight = defs.sumOf { it.weight }
        if (totalWeight <= 0) return null
        var roll = random.nextDouble() * totalWeight
        for (def in defs) {
            roll -= def.weight
            if (roll <= 0) return def
        }
        return defs.last()
    }

    companion object {
        private val HORIZONTAL_FACINGS = listOf("north", "east", "south", "west")
    }
}
