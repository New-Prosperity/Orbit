package me.nebula.orbit.utils.mapgen

import me.nebula.orbit.utils.biome.BiomePresets
import kotlin.random.Random

object MapPresets {

    operator fun get(name: String): MapGenerationConfig = when (name.lowercase()) {
        "perfect", "battleroyale" -> battleRoyale()
        else -> throw IllegalArgumentException("Unknown map preset: $name")
    }

    fun battleRoyale(seed: Long = System.nanoTime()): MapGenerationConfig {
        val random = Random(seed)

        val mapRadius = random.nextInt(200, 301)
        val seaLevel = random.nextInt(60, 66)

        val wormFrequency = vary(random, 0.5, 0.3)
        val wormsPerChunk = random.nextInt(2, 5)
        val ravineFrequency = vary(random, 0.03, 0.3)
        val roomFrequency = vary(random, 0.08, 0.3)

        val globalOreMultiplier = vary(random, 1.3, 0.2)

        val boulderChance = vary(random, 0.025, 0.3)
        val pondChance = vary(random, 0.01, 0.3)

        val continentalInfluence = vary(random, 18.0, 0.2)
        val erosionStrength = vary(random, 0.35, 0.2)

        return MapGenerationConfig(
            seed = seed,
            mapRadius = mapRadius,
            terrain = TerrainConfig(
                seaLevel = seaLevel,
                bedrockHeight = 1,
                fillerDepth = 3,
                deepslateLevel = 8,
                beachesEnabled = true,
                terrainScale = 0.008,
                continentalScale = 0.0015,
                continentalInfluence = continentalInfluence,
                erosionScale = 0.004,
                erosionStrength = erosionStrength,
                riversEnabled = true,
                riverScale = 0.003,
                riverThreshold = 0.018,
                riverDepth = random.nextInt(2, 5),
                riverWidth = vary(random, 0.025, 0.2),
                overhangsEnabled = true,
                overhangScale = 0.025,
                overhangThreshold = 0.5,
            ),
            biomeZones = BiomeZoneConfig(
                biomeScale = vary(random, 0.0025, 0.2),
                blendRadius = random.nextInt(10, 15),
                fallbackBiomeId = "plains",
            ),
            caves = CaveConfig(
                enabled = true,
                wormsPerChunk = wormsPerChunk,
                wormFrequency = wormFrequency,
                wormMinLength = 50,
                wormMaxLength = random.nextInt(150, 210),
                wormMinRadius = 1.5,
                wormMaxRadius = vary(random, 4.5, 0.2),
                wormMinY = 8,
                wormMaxY = random.nextInt(50, 62),
                ravinesEnabled = true,
                ravineFrequency = ravineFrequency,
                ravineMinLength = 70,
                ravineMaxLength = random.nextInt(180, 260),
                ravineMinRadius = 1.0,
                ravineMaxRadius = vary(random, 3.5, 0.2),
                ravineVerticalStretch = vary(random, 3.0, 0.2),
                ravineMinY = 15,
                ravineMaxY = random.nextInt(60, 70),
                roomsEnabled = true,
                roomFrequency = roomFrequency,
                roomMinRadius = 5.0,
                roomMaxRadius = vary(random, 10.0, 0.3),
                roomMinY = 10,
                roomMaxY = random.nextInt(38, 48),
                lavaLevel = 10,
                bedrockFloor = 1,
                decorationEnabled = true,
                mossEnabled = true,
                glowLichenEnabled = true,
                dripstoneEnabled = true,
                hangingRootsEnabled = true,
                aquifersEnabled = true,
                aquiferMaxY = random.nextInt(28, 36),
                aquiferThreshold = vary(random, 0.28, 0.2),
            ),
            ores = OreConfig(
                enabled = true,
                globalMultiplier = globalOreMultiplier,
                veins = listOf(
                    OreVeinConfig(net.minestom.server.instance.block.Block.COAL_ORE, 5, 128, 17, varyInt(random, 22, 0.2)),
                    OreVeinConfig(net.minestom.server.instance.block.Block.IRON_ORE, 5, 64, 9, varyInt(random, 25, 0.2)),
                    OreVeinConfig(net.minestom.server.instance.block.Block.GOLD_ORE, 5, 32, 9, varyInt(random, 4, 0.5)),
                    OreVeinConfig(net.minestom.server.instance.block.Block.DIAMOND_ORE, 5, 16, 8, varyInt(random, 2, 0.5)),
                    OreVeinConfig(net.minestom.server.instance.block.Block.REDSTONE_ORE, 5, 16, 8, varyInt(random, 8, 0.2)),
                    OreVeinConfig(net.minestom.server.instance.block.Block.LAPIS_ORE, 5, 32, 7, varyInt(random, 2, 0.5)),
                    OreVeinConfig(net.minestom.server.instance.block.Block.EMERALD_ORE, 5, 32, 1, varyInt(random, 1, 0.5)),
                    OreVeinConfig(net.minestom.server.instance.block.Block.COPPER_ORE, 5, 96, 10, varyInt(random, 8, 0.2)),
                ),
            ),
            modifiers = ModifierConfig(
                enabled = true,
                iceOnFrozenWater = true,
                snowOnCold = true,
                surfacePatchesEnabled = true,
                patchScale = vary(random, 0.08, 0.2),
                patchThreshold = vary(random, 0.55, 0.15),
                coarseDirtPatches = true,
                gravelPatches = true,
                podzolPatches = true,
                clayUnderwaterEnabled = true,
                iceSpikesEnabled = true,
            ),
            population = PopulationConfig(
                treesEnabled = true,
                vegetationEnabled = true,
                bouldersEnabled = true,
                boulderChance = boulderChance,
                pondsEnabled = true,
                pondChance = pondChance,
                mushroomsEnabled = true,
                underwaterVegetationEnabled = true,
                sugarCaneEnabled = true,
                cactusEnabled = true,
                lilyPadsEnabled = true,
                tallPlantsEnabled = true,
                fallenLogsEnabled = true,
            ),
            customBiomes = BiomePresets.all(),
        )
    }

    private fun vary(random: Random, base: Double, variance: Double): Double {
        val low = base * (1.0 - variance)
        val high = base * (1.0 + variance)
        return low + random.nextDouble() * (high - low)
    }

    private fun varyInt(random: Random, base: Int, variance: Double): Int {
        val low = (base * (1.0 - variance)).toInt().coerceAtLeast(1)
        val high = (base * (1.0 + variance)).toInt().coerceAtLeast(low + 1)
        return random.nextInt(low, high + 1)
    }
}
