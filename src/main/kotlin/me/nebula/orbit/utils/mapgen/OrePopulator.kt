package me.nebula.orbit.utils.mapgen

import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import kotlin.random.Random

data class OreVeinConfig(
    val block: Block,
    val minY: Int,
    val maxY: Int,
    val veinSize: Int,
    val veinsPerChunk: Int,
    val multiplier: Double = 1.0,
)

data class OreConfig(
    val enabled: Boolean = true,
    val globalMultiplier: Double = 1.0,
    val veins: List<OreVeinConfig> = defaultVeins(),
) {

    companion object {
        fun vanilla() = OreConfig()

        fun boosted() = OreConfig(
            veins = defaultVeins().map { vein ->
                when (vein.block) {
                    Block.IRON_ORE -> vein.copy(multiplier = 1.5)
                    Block.GOLD_ORE -> vein.copy(multiplier = 2.0)
                    Block.DIAMOND_ORE -> vein.copy(multiplier = 2.0)
                    else -> vein
                }
            }
        )

        fun none() = OreConfig(enabled = false)

        fun defaultVeins() = listOf(
            OreVeinConfig(Block.COAL_ORE, 5, 128, 17, 20),
            OreVeinConfig(Block.IRON_ORE, 5, 64, 9, 20),
            OreVeinConfig(Block.GOLD_ORE, 5, 32, 9, 2),
            OreVeinConfig(Block.DIAMOND_ORE, 5, 16, 8, 1),
            OreVeinConfig(Block.REDSTONE_ORE, 5, 16, 8, 8),
            OreVeinConfig(Block.LAPIS_ORE, 5, 32, 7, 1),
            OreVeinConfig(Block.EMERALD_ORE, 5, 32, 1, 1),
            OreVeinConfig(Block.COPPER_ORE, 5, 96, 10, 6),
        )
    }
}

class OrePopulator(
    private val seed: Long,
    private val config: OreConfig,
) {

    fun populateAll(instance: InstanceContainer, radiusChunks: Int, biomeProvider: BiomeProvider) {
        if (!config.enabled) return

        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                val chunk = instance.getChunk(cx, cz) ?: continue
                populateChunk(instance, cx, cz, biomeProvider)
            }
        }
    }

    private fun populateChunk(instance: InstanceContainer, cx: Int, cz: Int, biomeProvider: BiomeProvider) {
        val chunkRandom = Random(seed xor (cx.toLong() * 48271L) xor (cz.toLong() * 69621L))
        val biome = biomeProvider.biomeAt(cx * 16 + 8, cz * 16 + 8)
        val biomeMult = biome.oreMultiplier

        for (vein in config.veins) {
            val effectiveMult = vein.multiplier * config.globalMultiplier * biomeMult
            val count = (vein.veinsPerChunk * effectiveMult).toInt()
            val remainder = (vein.veinsPerChunk * effectiveMult) - count

            val totalVeins = count + if (chunkRandom.nextDouble() < remainder) 1 else 0

            for (i in 0 until totalVeins) {
                val x = cx * 16 + chunkRandom.nextInt(16)
                val y = chunkRandom.nextInt(vein.minY, vein.maxY + 1)
                val z = cz * 16 + chunkRandom.nextInt(16)
                placeVein(instance, x, y, z, vein, chunkRandom)
            }
        }
    }

    private fun placeVein(instance: InstanceContainer, x: Int, y: Int, z: Int, vein: OreVeinConfig, random: Random) {
        if (vein.veinSize <= 1) {
            tryPlaceOre(instance, x, y, z, vein.block)
            return
        }

        val angle = random.nextDouble() * Math.PI * 2
        val startX = x + kotlin.math.cos(angle) * (vein.veinSize / 8.0)
        val startZ = z + kotlin.math.sin(angle) * (vein.veinSize / 8.0)
        val endX = x - kotlin.math.cos(angle) * (vein.veinSize / 8.0)
        val endZ = z - kotlin.math.sin(angle) * (vein.veinSize / 8.0)
        val startY = y + random.nextInt(3) - 1
        val endY = y + random.nextInt(3) - 1

        for (i in 0 until vein.veinSize) {
            val progress = i.toDouble() / vein.veinSize
            val cx = startX + (endX - startX) * progress
            val cy = startY + (endY - startY) * progress + kotlin.math.sin(progress * Math.PI) * 0.5
            val cz = startZ + (endZ - startZ) * progress
            val radius = ((kotlin.math.sin(progress * Math.PI) + 1) * 0.5 * random.nextDouble() + 0.5)

            val ri = radius.toInt() + 1
            for (dx in -ri..ri) {
                for (dy in -ri..ri) {
                    for (dz in -ri..ri) {
                        if (dx * dx + dy * dy + dz * dz > radius * radius + 0.5) continue
                        tryPlaceOre(instance, (cx + dx).toInt(), (cy + dy).toInt(), (cz + dz).toInt(), vein.block)
                    }
                }
            }
        }
    }

    private fun tryPlaceOre(instance: InstanceContainer, x: Int, y: Int, z: Int, ore: Block) {
        val existing = instance.getBlock(x, y, z)
        if (existing.compare(Block.STONE) || existing.compare(Block.DEEPSLATE) || existing.compare(Block.TERRACOTTA)) {
            instance.setBlock(x, y, z, ore)
        }
    }
}
