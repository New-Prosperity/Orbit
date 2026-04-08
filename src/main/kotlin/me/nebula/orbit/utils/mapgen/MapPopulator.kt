package me.nebula.orbit.utils.mapgen

import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import kotlin.random.Random

data class PopulationConfig(
    val treesEnabled: Boolean = true,
    val vegetationEnabled: Boolean = true,
    val bouldersEnabled: Boolean = true,
    val boulderChance: Double = 0.02,
    val pondsEnabled: Boolean = true,
    val pondChance: Double = 0.008,
    val mushroomsEnabled: Boolean = true,
    val underwaterVegetationEnabled: Boolean = true,
    val sugarCaneEnabled: Boolean = true,
    val cactusEnabled: Boolean = true,
    val lilyPadsEnabled: Boolean = true,
    val tallPlantsEnabled: Boolean = true,
    val fallenLogsEnabled: Boolean = true,
)

class MapPopulator(
    private val seed: Long,
    private val terrain: TerrainGenerator,
    private val config: PopulationConfig = PopulationConfig(),
) {

    private val treePosSet = mutableSetOf<Long>()

    fun populate(instance: InstanceContainer, radiusChunks: Int) {
        if (config.bouldersEnabled) populateBoulders(instance, radiusChunks)
        if (config.pondsEnabled) populatePonds(instance, radiusChunks)
        if (config.treesEnabled) populateTrees(instance, radiusChunks)
        if (config.vegetationEnabled) populateVegetation(instance, radiusChunks)
        if (config.tallPlantsEnabled) populateTallPlants(instance, radiusChunks)
        if (config.fallenLogsEnabled) populateFallenLogs(instance, radiusChunks)
        if (config.underwaterVegetationEnabled) populateUnderwaterVegetation(instance, radiusChunks)
        if (config.sugarCaneEnabled) populateSugarCane(instance, radiusChunks)
        if (config.cactusEnabled) populateCactus(instance, radiusChunks)
        if (config.lilyPadsEnabled) populateLilyPads(instance, radiusChunks)
    }

    private fun populateTrees(instance: InstanceContainer, radiusChunks: Int) {
        val treeRandom = Random(seed + 300)
        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                if (instance.getChunk(cx, cz) == null) continue
                for (attempt in 0 until 10) {
                    val lx = treeRandom.nextInt(16)
                    val lz = treeRandom.nextInt(16)
                    val wx = cx * 16 + lx
                    val wz = cz * 16 + lz
                    val biome = terrain.biomes.biomeAt(wx, wz)
                    if (treeRandom.nextDouble() > biome.treeDensity * 8) continue
                    val height = terrain.surfaceHeight(wx, wz)
                    if (height < terrain.seaLevel) continue
                    if (terrain.isRiverAt(wx, wz)) continue
                    if (!instance.getBlock(wx, height + 1, wz).isAir) continue

                    val tooClose = SPACING_OFFSETS.any { (dx, dz) ->
                        treePosSet.contains(packPos(wx + dx, wz + dz))
                    }
                    if (tooClose) continue

                    val treeType = biome.treeTypes.getOrElse(treeRandom.nextInt(biome.treeTypes.size)) { TreeType.OAK }
                    placeTree(instance, wx, height + 1, wz, treeType, treeRandom)
                    treePosSet.add(packPos(wx, wz))
                }
            }
        }
    }

    private fun placeTree(instance: InstanceContainer, x: Int, baseY: Int, z: Int, type: TreeType, random: Random) {
        when (type) {
            TreeType.OAK -> placeOakTree(instance, x, baseY, z, random.nextInt(4, 7))
            TreeType.BIRCH -> placeBirchTree(instance, x, baseY, z, random.nextInt(5, 7))
            TreeType.SPRUCE -> placeSpruceTree(instance, x, baseY, z, random.nextInt(6, 10))
            TreeType.DARK_OAK -> placeDarkOakTree(instance, x, baseY, z, random.nextInt(5, 8))
            TreeType.ACACIA -> placeAcaciaTree(instance, x, baseY, z, random.nextInt(5, 8), random)
            TreeType.JUNGLE -> placeJungleTree(instance, x, baseY, z, random.nextInt(8, 16))
        }
    }

    private fun placeOakTree(instance: InstanceContainer, x: Int, baseY: Int, z: Int, trunkHeight: Int) {
        for (y in 0 until trunkHeight) instance.setBlock(x, baseY + y, z, Block.OAK_LOG)
        placeLeafCanopy(instance, x, baseY + trunkHeight - 2, z, Block.OAK_LEAVES, 2, 3)
    }

    private fun placeBirchTree(instance: InstanceContainer, x: Int, baseY: Int, z: Int, trunkHeight: Int) {
        for (y in 0 until trunkHeight) instance.setBlock(x, baseY + y, z, Block.BIRCH_LOG)
        placeLeafCanopy(instance, x, baseY + trunkHeight - 2, z, Block.BIRCH_LEAVES, 2, 3)
    }

    private fun placeSpruceTree(instance: InstanceContainer, x: Int, baseY: Int, z: Int, trunkHeight: Int) {
        for (y in 0 until trunkHeight) instance.setBlock(x, baseY + y, z, Block.SPRUCE_LOG)
        var radius = 1
        for (dy in trunkHeight - 1 downTo 2) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (dx == 0 && dz == 0) continue
                    if (dx * dx + dz * dz > radius * radius) continue
                    val bx = x + dx; val bz = z + dz; val by = baseY + dy
                    if (instance.getBlock(bx, by, bz).isAir) instance.setBlock(bx, by, bz, Block.SPRUCE_LEAVES)
                }
            }
            if (dy % 2 == 0 && radius < 3) radius++
        }
        instance.setBlock(x, baseY + trunkHeight, z, Block.SPRUCE_LEAVES)
    }

    private fun placeDarkOakTree(instance: InstanceContainer, x: Int, baseY: Int, z: Int, trunkHeight: Int) {
        for (dx in 0..1) {
            for (dz in 0..1) {
                for (y in 0 until trunkHeight) {
                    instance.setBlock(x + dx, baseY + y, z + dz, Block.DARK_OAK_LOG)
                }
            }
        }
        val leafY = baseY + trunkHeight - 2
        for (dy in 0..2) {
            val radius = if (dy < 2) 3 else 2
            for (dx in -radius..radius + 1) {
                for (dz in -radius..radius + 1) {
                    if ((dx in 0..1 && dz in 0..1) && dy < 2) continue
                    val dist = kotlin.math.sqrt(((dx - 0.5) * (dx - 0.5) + (dz - 0.5) * (dz - 0.5)))
                    if (dist > radius + 0.5) continue
                    val bx = x + dx; val bz = z + dz; val by = leafY + dy
                    if (instance.getBlock(bx, by, bz).isAir) instance.setBlock(bx, by, bz, Block.DARK_OAK_LEAVES)
                }
            }
        }
    }

    private fun placeAcaciaTree(instance: InstanceContainer, x: Int, baseY: Int, z: Int, trunkHeight: Int, random: Random) {
        val bendY = trunkHeight / 2
        val bendDx = if (random.nextBoolean()) 1 else -1
        val bendDz = if (random.nextBoolean()) 1 else -1
        var cx = x; var cz = z
        for (y in 0 until trunkHeight) {
            instance.setBlock(cx, baseY + y, cz, Block.ACACIA_LOG)
            if (y == bendY) { cx += bendDx; cz += bendDz }
        }
        val leafY = baseY + trunkHeight - 1
        for (dy in 0..1) {
            val radius = if (dy == 0) 3 else 2
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (dx * dx + dz * dz > radius * radius) continue
                    val bx = cx + dx; val bz = cz + dz; val by = leafY + dy
                    if (instance.getBlock(bx, by, bz).isAir) instance.setBlock(bx, by, bz, Block.ACACIA_LEAVES)
                }
            }
        }
    }

    private fun placeJungleTree(instance: InstanceContainer, x: Int, baseY: Int, z: Int, trunkHeight: Int) {
        for (y in 0 until trunkHeight) instance.setBlock(x, baseY + y, z, Block.JUNGLE_LOG)
        placeLeafCanopy(instance, x, baseY + trunkHeight - 3, z, Block.JUNGLE_LEAVES, 2, 4)
        for (y in 1 until trunkHeight - 2 step 3) {
            val vineY = baseY + y
            for ((dx, dz, facing) in listOf(
                Triple(-1, 0, "east"), Triple(1, 0, "west"),
                Triple(0, -1, "south"), Triple(0, 1, "north"),
            )) {
                if (instance.getBlock(x + dx, vineY, z + dz).isAir) {
                    instance.setBlock(x + dx, vineY, z + dz, Block.VINE.withProperty(facing, "true"))
                }
            }
        }
    }

    private fun placeLeafCanopy(instance: InstanceContainer, x: Int, baseY: Int, z: Int, leaf: Block, innerR: Int, layers: Int) {
        for (dy in 0 until layers) {
            val radius = if (dy < layers - 1) innerR else innerR - 1
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (dx == 0 && dz == 0 && dy < layers - 1) continue
                    if (dx * dx + dz * dz > radius * radius + 1) continue
                    val bx = x + dx; val bz = z + dz; val by = baseY + dy
                    if (instance.getBlock(bx, by, bz).isAir) instance.setBlock(bx, by, bz, leaf)
                }
            }
        }
    }

    private fun populateVegetation(instance: InstanceContainer, radiusChunks: Int) {
        val vegRandom = Random(seed + 400)
        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                if (instance.getChunk(cx, cz) == null) continue
                for (attempt in 0 until 16) {
                    val lx = vegRandom.nextInt(16)
                    val lz = vegRandom.nextInt(16)
                    val wx = cx * 16 + lx
                    val wz = cz * 16 + lz
                    val biome = terrain.biomes.biomeAt(wx, wz)
                    if (vegRandom.nextDouble() > biome.vegetationDensity) continue
                    val height = terrain.surfaceHeight(wx, wz)
                    if (height < terrain.seaLevel) continue
                    val aboveY = height + 1
                    if (!instance.getBlock(wx, aboveY, wz).isAir) continue
                    val surface = instance.getBlock(wx, height, wz)
                    if (!surface.compare(Block.GRASS_BLOCK) && !surface.compare(Block.PODZOL)) continue
                    instance.setBlock(wx, aboveY, wz, pickVegetation(biome, vegRandom))
                }

                if (config.mushroomsEnabled) {
                    for (attempt in 0 until 2) {
                        val lx = vegRandom.nextInt(16)
                        val lz = vegRandom.nextInt(16)
                        val wx = cx * 16 + lx
                        val wz = cz * 16 + lz
                        val biome = terrain.biomes.biomeAt(wx, wz)
                        if (biome.id !in setOf("dark_forest", "taiga", "swamp")) continue
                        if (vegRandom.nextDouble() > 0.15) continue
                        val height = terrain.surfaceHeight(wx, wz)
                        if (height < terrain.seaLevel) continue
                        val aboveY = height + 1
                        if (!instance.getBlock(wx, aboveY, wz).isAir) continue
                        val mushroom = if (vegRandom.nextBoolean()) Block.RED_MUSHROOM else Block.BROWN_MUSHROOM
                        instance.setBlock(wx, aboveY, wz, mushroom)
                    }
                }
            }
        }
    }

    private fun pickVegetation(biome: BiomeDefinition, random: Random): Block = when (biome.id) {
        "desert", "badlands" -> Block.DEAD_BUSH
        "flower_plains" -> when (random.nextInt(10)) {
            0 -> Block.DANDELION
            1 -> Block.POPPY
            2 -> Block.CORNFLOWER
            3 -> Block.OXEYE_DAISY
            4 -> Block.AZURE_BLUET
            5 -> Block.ALLIUM
            6 -> Block.BLUE_ORCHID
            else -> Block.SHORT_GRASS
        }
        "taiga" -> when (random.nextInt(3)) {
            0 -> Block.FERN
            else -> Block.SHORT_GRASS
        }
        "swamp" -> when (random.nextInt(4)) {
            0 -> Block.BLUE_ORCHID
            else -> Block.SHORT_GRASS
        }
        "jungle" -> when (random.nextInt(3)) {
            0 -> Block.FERN
            else -> Block.SHORT_GRASS
        }
        "meadow" -> when (random.nextInt(6)) {
            0 -> Block.DANDELION
            1 -> Block.POPPY
            2 -> Block.CORNFLOWER
            3 -> Block.ALLIUM
            else -> Block.SHORT_GRASS
        }
        else -> when (random.nextInt(5)) {
            0 -> Block.DANDELION
            1 -> Block.POPPY
            else -> Block.SHORT_GRASS
        }
    }

    private fun populateTallPlants(instance: InstanceContainer, radiusChunks: Int) {
        val random = Random(seed + 740)
        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                if (instance.getChunk(cx, cz) == null) continue
                for (attempt in 0 until 4) {
                    val wx = cx * 16 + random.nextInt(16)
                    val wz = cz * 16 + random.nextInt(16)
                    val biome = terrain.biomes.biomeAt(wx, wz)
                    if (random.nextDouble() > biome.vegetationDensity * 0.3) continue
                    val height = terrain.surfaceHeight(wx, wz)
                    if (height < terrain.seaLevel) continue
                    val aboveY = height + 1
                    if (!instance.getBlock(wx, aboveY, wz).isAir) continue
                    if (!instance.getBlock(wx, aboveY + 1, wz).isAir) continue
                    val surface = instance.getBlock(wx, height, wz)
                    if (!surface.compare(Block.GRASS_BLOCK)) continue

                    val plant = pickTallPlant(biome, random) ?: continue
                    instance.setBlock(wx, aboveY, wz, plant.withProperty("half", "lower"))
                    instance.setBlock(wx, aboveY + 1, wz, plant.withProperty("half", "upper"))
                }
            }
        }
    }

    private fun pickTallPlant(biome: BiomeDefinition, random: Random): Block? = when (biome.id) {
        "flower_plains" -> when (random.nextInt(4)) {
            0 -> Block.SUNFLOWER
            1 -> Block.ROSE_BUSH
            2 -> Block.LILAC
            else -> Block.TALL_GRASS
        }
        "meadow" -> when (random.nextInt(3)) {
            0 -> Block.SUNFLOWER
            1 -> Block.PEONY
            else -> Block.TALL_GRASS
        }
        "plains" -> if (random.nextInt(3) == 0) Block.TALL_GRASS else null
        "forest", "birch_forest" -> when (random.nextInt(4)) {
            0 -> Block.LARGE_FERN
            1 -> Block.PEONY
            else -> null
        }
        "jungle" -> Block.LARGE_FERN
        "taiga" -> if (random.nextBoolean()) Block.LARGE_FERN else Block.TALL_GRASS
        else -> null
    }

    private fun populateFallenLogs(instance: InstanceContainer, radiusChunks: Int) {
        val random = Random(seed + 750)
        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                if (instance.getChunk(cx, cz) == null) continue
                if (random.nextDouble() > 0.02) continue
                val wx = cx * 16 + random.nextInt(16)
                val wz = cz * 16 + random.nextInt(16)
                val biome = terrain.biomes.biomeAt(wx, wz)
                if (biome.treeDensity < 0.05) continue
                val height = terrain.surfaceHeight(wx, wz)
                if (height < terrain.seaLevel) continue

                val logBlock = when {
                    TreeType.BIRCH in biome.treeTypes -> Block.BIRCH_LOG
                    TreeType.SPRUCE in biome.treeTypes -> Block.SPRUCE_LOG
                    TreeType.JUNGLE in biome.treeTypes -> Block.JUNGLE_LOG
                    TreeType.DARK_OAK in biome.treeTypes -> Block.DARK_OAK_LOG
                    else -> Block.OAK_LOG
                }

                val logLength = random.nextInt(3, 7)
                val horizontal = random.nextBoolean()
                val axis = if (horizontal) "x" else "z"
                val aboveY = height + 1

                var canPlace = true
                for (i in 0 until logLength) {
                    val lx = if (horizontal) wx + i else wx
                    val lz = if (!horizontal) wz + i else wz
                    if (!instance.getBlock(lx, aboveY, lz).isAir) { canPlace = false; break }
                }
                if (!canPlace) continue

                for (i in 0 until logLength) {
                    val lx = if (horizontal) wx + i else wx
                    val lz = if (!horizontal) wz + i else wz
                    instance.setBlock(lx, aboveY, lz, logBlock.withProperty("axis", axis))
                }
            }
        }
    }

    private fun populateUnderwaterVegetation(instance: InstanceContainer, radiusChunks: Int) {
        val random = Random(seed + 700)
        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                if (instance.getChunk(cx, cz) == null) continue
                for (attempt in 0 until 6) {
                    val wx = cx * 16 + random.nextInt(16)
                    val wz = cz * 16 + random.nextInt(16)
                    val height = terrain.surfaceHeight(wx, wz)
                    if (height >= terrain.seaLevel - 1) continue
                    val biome = terrain.biomes.biomeAt(wx, wz)
                    if (biome.frozen) continue

                    val waterDepth = terrain.seaLevel - height

                    if (waterDepth >= 4 && random.nextDouble() < 0.12) {
                        val kelpHeight = random.nextInt(2, waterDepth.coerceAtMost(10))
                        for (dy in 1 until kelpHeight) {
                            instance.setBlock(wx, height + dy, wz, Block.KELP_PLANT)
                        }
                        instance.setBlock(wx, height + kelpHeight, wz, Block.KELP)
                        continue
                    }

                    if (random.nextDouble() < 0.25) {
                        val aboveY = height + 1
                        if (instance.getBlock(wx, aboveY, wz).compare(Block.WATER)) {
                            if (waterDepth >= 3 && random.nextBoolean()) {
                                instance.setBlock(wx, aboveY, wz, Block.TALL_SEAGRASS.withProperty("half", "lower"))
                                instance.setBlock(wx, aboveY + 1, wz, Block.TALL_SEAGRASS.withProperty("half", "upper"))
                            } else {
                                instance.setBlock(wx, aboveY, wz, Block.SEAGRASS)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun populateSugarCane(instance: InstanceContainer, radiusChunks: Int) {
        val random = Random(seed + 710)
        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                if (instance.getChunk(cx, cz) == null) continue
                for (attempt in 0 until 3) {
                    val wx = cx * 16 + random.nextInt(16)
                    val wz = cz * 16 + random.nextInt(16)
                    val height = terrain.surfaceHeight(wx, wz)
                    if (height < terrain.seaLevel || height > terrain.seaLevel + 3) continue

                    val surface = instance.getBlock(wx, height, wz)
                    if (!surface.compare(Block.SAND) && !surface.compare(Block.DIRT) && !surface.compare(Block.GRASS_BLOCK)) continue

                    val hasWater = ADJACENT_OFFSETS.any { (dx, dz) ->
                        instance.getBlock(wx + dx, height, wz + dz).compare(Block.WATER) ||
                            instance.getBlock(wx + dx, height - 1, wz + dz).compare(Block.WATER)
                    }
                    if (!hasWater) continue

                    val aboveY = height + 1
                    if (!instance.getBlock(wx, aboveY, wz).isAir) continue

                    val caneHeight = random.nextInt(1, 4)
                    for (dy in 0 until caneHeight) {
                        instance.setBlock(wx, aboveY + dy, wz, Block.SUGAR_CANE)
                    }
                }
            }
        }
    }

    private fun populateCactus(instance: InstanceContainer, radiusChunks: Int) {
        val random = Random(seed + 720)
        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                if (instance.getChunk(cx, cz) == null) continue
                for (attempt in 0 until 2) {
                    val wx = cx * 16 + random.nextInt(16)
                    val wz = cz * 16 + random.nextInt(16)
                    val biome = terrain.biomes.biomeAt(wx, wz)
                    if (biome.id != "desert") continue
                    if (random.nextDouble() > 0.08) continue

                    val height = terrain.surfaceHeight(wx, wz)
                    if (height < terrain.seaLevel) continue

                    val surface = instance.getBlock(wx, height, wz)
                    if (!surface.compare(Block.SAND)) continue

                    val aboveY = height + 1
                    if (!instance.getBlock(wx, aboveY, wz).isAir) continue

                    val blocked = ADJACENT_OFFSETS.any { (dx, dz) ->
                        !instance.getBlock(wx + dx, aboveY, wz + dz).isAir
                    }
                    if (blocked) continue

                    val cactusHeight = random.nextInt(1, 4)
                    for (dy in 0 until cactusHeight) {
                        instance.setBlock(wx, aboveY + dy, wz, Block.CACTUS)
                    }
                }
            }
        }
    }

    private fun populateLilyPads(instance: InstanceContainer, radiusChunks: Int) {
        val random = Random(seed + 730)
        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                if (instance.getChunk(cx, cz) == null) continue
                for (attempt in 0 until 4) {
                    val wx = cx * 16 + random.nextInt(16)
                    val wz = cz * 16 + random.nextInt(16)
                    val biome = terrain.biomes.biomeAt(wx, wz)
                    if (biome.id != "swamp") continue
                    if (random.nextDouble() > 0.3) continue

                    val height = terrain.surfaceHeight(wx, wz)
                    if (height >= terrain.seaLevel) continue

                    val waterSurface = terrain.seaLevel
                    if (instance.getBlock(wx, waterSurface, wz).compare(Block.WATER) &&
                        instance.getBlock(wx, waterSurface + 1, wz).isAir) {
                        instance.setBlock(wx, waterSurface + 1, wz, Block.LILY_PAD)
                    }
                }
            }
        }
    }

    private fun populateBoulders(instance: InstanceContainer, radiusChunks: Int) {
        val boulderRandom = Random(seed + 500)
        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                if (instance.getChunk(cx, cz) == null) continue
                if (boulderRandom.nextDouble() > config.boulderChance * 16) continue
                val wx = cx * 16 + boulderRandom.nextInt(16)
                val wz = cz * 16 + boulderRandom.nextInt(16)
                val biome = terrain.biomes.biomeAt(wx, wz)
                if (biome.id in setOf("desert", "swamp")) continue
                val height = terrain.surfaceHeight(wx, wz)
                if (height < terrain.seaLevel) continue
                placeBoulder(instance, wx, height + 1, wz, boulderRandom)
            }
        }
    }

    private fun placeBoulder(instance: InstanceContainer, x: Int, y: Int, z: Int, random: Random) {
        val radius = random.nextInt(2, 4)
        val block = when (random.nextInt(4)) {
            0 -> Block.MOSSY_COBBLESTONE
            1 -> Block.GRANITE
            2 -> Block.ANDESITE
            else -> Block.STONE
        }
        for (dx in -radius..radius) {
            for (dy in 0..radius) {
                for (dz in -radius..radius) {
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy * 1.5 + dz * dz)
                    if (dist > radius - random.nextDouble() * 0.5) continue
                    instance.setBlock(x + dx, y + dy, z + dz, block)
                }
            }
        }
    }

    private fun populatePonds(instance: InstanceContainer, radiusChunks: Int) {
        val pondRandom = Random(seed + 550)
        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                if (instance.getChunk(cx, cz) == null) continue
                if (pondRandom.nextDouble() > config.pondChance * 16) continue
                val wx = cx * 16 + pondRandom.nextInt(16)
                val wz = cz * 16 + pondRandom.nextInt(16)
                val height = terrain.surfaceHeight(wx, wz)
                if (height < terrain.seaLevel + 2) continue
                placePond(instance, wx, height, wz, pondRandom)
            }
        }
    }

    private fun placePond(instance: InstanceContainer, x: Int, y: Int, z: Int, random: Random) {
        val radiusX = random.nextInt(3, 6)
        val radiusZ = random.nextInt(3, 6)
        val depth = random.nextInt(2, 4)

        for (dx in -radiusX..radiusX) {
            for (dz in -radiusZ..radiusZ) {
                val dist = (dx.toDouble() * dx / (radiusX * radiusX) + dz.toDouble() * dz / (radiusZ * radiusZ))
                if (dist > 1.0) continue
                for (dy in 0 until depth) {
                    val depthFactor = 1.0 - dy.toDouble() / depth
                    if (dist > depthFactor) continue
                    instance.setBlock(x + dx, y - dy, z + dz, Block.WATER)
                }
                instance.setBlock(x + dx, y - depth, z + dz, Block.CLAY)
                if (dist > 0.7 && dist <= 1.0) {
                    instance.setBlock(x + dx, y, z + dz, Block.SAND)
                }
            }
        }
    }

    private fun packPos(x: Int, z: Int): Long =
        (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    companion object {
        private val SPACING_OFFSETS = listOf(-3 to 0, 3 to 0, 0 to -3, 0 to 3, -2 to -2, 2 to -2, -2 to 2, 2 to 2)
        private val ADJACENT_OFFSETS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    }
}
