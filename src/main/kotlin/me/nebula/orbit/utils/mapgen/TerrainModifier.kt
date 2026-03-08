package me.nebula.orbit.utils.mapgen

import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import kotlin.random.Random

data class ModifierConfig(
    val enabled: Boolean = true,
    val iceOnFrozenWater: Boolean = true,
    val snowOnCold: Boolean = true,
    val surfacePatchesEnabled: Boolean = true,
    val patchScale: Double = 0.08,
    val patchThreshold: Double = 0.6,
    val coarseDirtPatches: Boolean = true,
    val gravelPatches: Boolean = true,
    val podzolPatches: Boolean = true,
    val clayUnderwaterEnabled: Boolean = true,
    val iceSpikesEnabled: Boolean = true,
)

class TerrainModifier(
    private val seed: Long,
    private val config: ModifierConfig,
    private val terrain: TerrainGenerator,
) {

    private val patchNoise = OctaveNoise(PerlinNoise(seed + 800), octaves = 3, lacunarity = 3.0, persistence = 0.4)
    private val clayNoise = OctaveNoise(PerlinNoise(seed + 850), octaves = 2, lacunarity = 2.0, persistence = 0.5)

    fun applyAll(instance: InstanceContainer, radiusChunks: Int) {
        if (!config.enabled) return

        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                if (instance.getChunk(cx, cz) == null) continue
                applyChunk(instance, cx, cz)
            }
        }
    }

    private fun applyChunk(instance: InstanceContainer, cx: Int, cz: Int) {
        val chunkRandom = Random(seed xor (cx.toLong() * 48271L) xor (cz.toLong() * 69621L))

        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                val wx = cx * 16 + lx
                val wz = cz * 16 + lz
                val biome = terrain.biomes.biomeAt(wx, wz)
                val height = terrain.surfaceHeight(wx, wz)

                if (config.iceOnFrozenWater && biome.frozen) {
                    freezeWater(instance, wx, wz, height)
                }

                if (config.surfacePatchesEnabled && height >= terrain.seaLevel) {
                    applySurfacePatches(instance, wx, wz, height, biome)
                }

                if (config.clayUnderwaterEnabled && height < terrain.seaLevel) {
                    applyUnderwaterClay(instance, wx, wz, height)
                }

                if (config.iceSpikesEnabled && biome.id == "ice_spikes") {
                    placeIceSpike(instance, wx, wz, height, chunkRandom)
                }
            }
        }
    }

    private fun freezeWater(instance: InstanceContainer, x: Int, z: Int, surfaceY: Int) {
        for (y in surfaceY..terrain.seaLevel + 1) {
            if (instance.getBlock(x, y, z).compare(Block.WATER)) {
                val above = instance.getBlock(x, y + 1, z)
                if (above.isAir || above.compare(Block.SNOW)) {
                    instance.setBlock(x, y, z, Block.ICE)
                }
            }
        }
    }

    private fun applySurfacePatches(instance: InstanceContainer, x: Int, z: Int, height: Int, biome: BiomeDefinition) {
        val noise = patchNoise.sample2D(x * config.patchScale, z * config.patchScale)
        val surface = instance.getBlock(x, height, z)

        if (config.coarseDirtPatches && noise > config.patchThreshold && surface.compare(Block.GRASS_BLOCK)) {
            instance.setBlock(x, height, z, Block.COARSE_DIRT)
        }

        val noise2 = patchNoise.sample2D(x * config.patchScale + 100, z * config.patchScale + 100)
        if (config.gravelPatches && noise2 > config.patchThreshold + 0.1 && surface.compare(Block.GRASS_BLOCK)) {
            instance.setBlock(x, height, z, Block.GRAVEL)
        }

        val noise3 = patchNoise.sample2D(x * config.patchScale + 200, z * config.patchScale + 200)
        if (config.podzolPatches && noise3 > config.patchThreshold + 0.05 && surface.compare(Block.GRASS_BLOCK)
            && biome.id in setOf("taiga", "dark_forest")) {
            instance.setBlock(x, height, z, Block.PODZOL)
        }
    }

    private fun applyUnderwaterClay(instance: InstanceContainer, x: Int, z: Int, height: Int) {
        val noise = clayNoise.sample2D(x * 0.1, z * 0.1)
        if (noise > 0.5) {
            val surface = instance.getBlock(x, height, z)
            if (surface.compare(Block.SAND) || surface.compare(Block.GRAVEL) || surface.compare(Block.DIRT)) {
                instance.setBlock(x, height, z, Block.CLAY)
            }
        }
    }

    private fun placeIceSpike(instance: InstanceContainer, x: Int, z: Int, height: Int, random: Random) {
        if (random.nextDouble() > 0.03) return
        val spikeHeight = random.nextInt(4, 26)
        val baseRadius = if (spikeHeight > 15) 2 else 1
        for (dy in 0 until spikeHeight) {
            val radius = (baseRadius * (1.0 - dy.toDouble() / spikeHeight)).toInt()
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (dx * dx + dz * dz > radius * radius) continue
                    instance.setBlock(x + dx, height + 1 + dy, z + dz, Block.PACKED_ICE)
                }
            }
        }
    }

    companion object {

        fun flattenArea(instance: InstanceContainer, centerX: Int, centerZ: Int, radius: Int, targetY: Int) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (dx * dx + dz * dz > radius * radius) continue
                    val x = centerX + dx
                    val z = centerZ + dz

                    for (y in targetY + 1..targetY + 20) {
                        val block = instance.getBlock(x, y, z)
                        if (!block.isAir && !block.compare(Block.WATER)) {
                            instance.setBlock(x, y, z, Block.AIR)
                        }
                    }

                    for (y in targetY downTo targetY - 10) {
                        val block = instance.getBlock(x, y, z)
                        if (block.isAir || block.compare(Block.WATER)) {
                            instance.setBlock(x, y, z, if (y == targetY) Block.GRASS_BLOCK else Block.DIRT)
                        } else {
                            break
                        }
                    }
                }
            }
        }
    }
}
