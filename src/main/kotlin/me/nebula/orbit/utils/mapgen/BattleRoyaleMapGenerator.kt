package me.nebula.orbit.utils.mapgen

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.chestloot.ChestLootManager
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.InstanceContainer
import java.util.concurrent.CompletableFuture

data class MapGenerationConfig(
    val seed: Long = System.currentTimeMillis(),
    val mapRadius: Int = 200,
    val terrain: TerrainConfig = TerrainConfig(),
    val biomeZones: BiomeZoneConfig = BiomeZoneConfig(),
    val caves: CaveConfig = CaveConfig(),
    val ores: OreConfig = OreConfig(),
    val modifiers: ModifierConfig = ModifierConfig(),
    val population: PopulationConfig = PopulationConfig(),
    val customBiomes: List<BiomeDefinition> = emptyList(),
    val schematics: SchematicPopulationConfig = SchematicPopulationConfig(),
)

data class GeneratedMap(
    val instance: InstanceContainer,
    val center: Pos,
    val mapRadius: Int,
    val schematicPlacements: List<SchematicPlacement> = emptyList(),
)

object BattleRoyaleMapGenerator {

    private val logger = logger("BattleRoyaleMapGenerator")

    fun generate(config: MapGenerationConfig): GeneratedMap {
        val seed = config.seed
        logger.info { "Generating procedural map (seed=$seed, radius=${config.mapRadius})" }

        BiomeRegistry.registerDefaults()
        config.customBiomes.forEach { BiomeRegistry.register(it) }
        BiomeRegistry.registerMinestomBiomes()

        val terrainConfig = config.terrain.copy(
            seed = seed,
            biomeZones = config.biomeZones,
        )
        val terrain = TerrainGenerator(terrainConfig)

        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator(terrain)

        val radiusChunks = (config.mapRadius / 16) + 1
        val totalChunks = (radiusChunks * 2 + 1).let { it * it }
        logger.info { "Preloading $totalChunks chunks..." }

        val futures = mutableListOf<CompletableFuture<*>>()
        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                futures.add(instance.loadChunk(cx, cz))
            }
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        logger.info { "Terrain generation complete" }

        val caveCarver = CaveCarver(seed, config.caves, terrain)
        if (config.caves.enabled || config.caves.ravinesEnabled || config.caves.roomsEnabled) {
            caveCarver.carveAll(instance, radiusChunks)
            logger.info { "Cave carving complete" }
        }

        if (config.ores.enabled) {
            val orePopulator = OrePopulator(seed, config.ores)
            orePopulator.populateAll(instance, radiusChunks, terrain.biomes)
            logger.info { "Ore population complete" }
        }

        if (config.modifiers.enabled) {
            val terrainModifier = TerrainModifier(seed, config.modifiers, terrain)
            terrainModifier.applyAll(instance, radiusChunks)
            logger.info { "Terrain modifiers applied" }
        }

        val populator = MapPopulator(seed, terrain, config.population)
        populator.populate(instance, radiusChunks)
        logger.info { "Population complete" }

        if (config.caves.decorationEnabled) {
            caveCarver.decorateAll(instance, radiusChunks)
            logger.info { "Cave decoration complete" }
        }

        val schematicPopulator = SchematicPopulator(seed, terrain, config.schematics)
        schematicPopulator.populate(instance, radiusChunks)
        if (schematicPopulator.placements.isNotEmpty()) {
            logger.info { "Schematic population complete: ${schematicPopulator.placements.size} schematics placed" }
            fillSchematicChests(schematicPopulator.placements, config.schematics.definitions)
        }

        val centerBiome = terrain.biomes.biomeAt(0, 0)
        val centerHeight = terrain.computeRawHeight(0, 0, centerBiome)
        val center = Pos(0.5, centerHeight + 1.0, 0.5)

        return GeneratedMap(instance, center, config.mapRadius, schematicPopulator.placements)
    }

    private fun fillSchematicChests(placements: List<SchematicPlacement>, definitions: List<SchematicStructureDef>) {
        val defMap = definitions.associateBy { it.id }
        var filled = 0
        for (placement in placements) {
            if (placement.chestPositions.isEmpty()) continue
            val def = defMap[placement.definitionId] ?: continue
            val tableId = def.lootTableId ?: continue
            val table = ChestLootManager[tableId] ?: continue
            for (pos in placement.chestPositions) {
                ChestLootManager.fillChestAt(table, pos.blockX(), pos.blockY(), pos.blockZ())
                filled++
            }
        }
        if (filled > 0) logger.info { "Filled $filled loot chests" }
    }

}
