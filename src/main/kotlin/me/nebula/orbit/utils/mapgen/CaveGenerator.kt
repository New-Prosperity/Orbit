package me.nebula.orbit.utils.mapgen

import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class CaveConfig(
    val enabled: Boolean = true,
    val wormsPerChunk: Int = 2,
    val wormFrequency: Double = 0.4,
    val wormMinLength: Int = 40,
    val wormMaxLength: Int = 150,
    val wormMinRadius: Double = 1.5,
    val wormMaxRadius: Double = 4.0,
    val wormMinY: Int = 8,
    val wormMaxY: Int = 54,
    val ravinesEnabled: Boolean = true,
    val ravineFrequency: Double = 0.02,
    val ravineMinLength: Int = 60,
    val ravineMaxLength: Int = 200,
    val ravineMinRadius: Double = 1.0,
    val ravineMaxRadius: Double = 3.0,
    val ravineVerticalStretch: Double = 3.0,
    val ravineMinY: Int = 20,
    val ravineMaxY: Int = 68,
    val roomsEnabled: Boolean = true,
    val roomFrequency: Double = 0.05,
    val roomMinRadius: Double = 4.0,
    val roomMaxRadius: Double = 9.0,
    val roomMinY: Int = 12,
    val roomMaxY: Int = 40,
    val lavaLevel: Int = 10,
    val bedrockFloor: Int = 1,
    val decorationEnabled: Boolean = true,
    val mossEnabled: Boolean = true,
    val glowLichenEnabled: Boolean = true,
    val dripstoneEnabled: Boolean = true,
    val hangingRootsEnabled: Boolean = true,
    val aquifersEnabled: Boolean = true,
    val aquiferMaxY: Int = 30,
    val aquiferThreshold: Double = 0.3,
) {

    companion object {
        fun vanilla() = CaveConfig()
        fun dense() = CaveConfig(wormsPerChunk = 4, wormFrequency = 0.7, roomFrequency = 0.12)
        fun sparse() = CaveConfig(wormsPerChunk = 1, wormFrequency = 0.2, ravineFrequency = 0.01, roomFrequency = 0.02)
        fun none() = CaveConfig(enabled = false, ravinesEnabled = false, roomsEnabled = false, decorationEnabled = false)
    }
}

class CaveCarver(
    private val seed: Long,
    private val config: CaveConfig,
    private val terrain: TerrainGenerator,
) {

    private val directionNoise = OctaveNoise(PerlinNoise(seed + 700), octaves = 3, lacunarity = 2.0, persistence = 0.5)
    private val aquiferNoise = OctaveNoise(PerlinNoise(seed + 950), octaves = 2, lacunarity = 2.0, persistence = 0.5)

    fun carveAll(instance: InstanceContainer, radiusChunks: Int) {
        if (!config.enabled && !config.ravinesEnabled && !config.roomsEnabled) return

        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                val chunkRandom = Random(chunkSeed(cx, cz))

                if (config.enabled) {
                    for (i in 0 until config.wormsPerChunk) {
                        if (chunkRandom.nextDouble() < config.wormFrequency) {
                            carveWorm(instance, cx, cz, chunkRandom)
                        }
                    }
                }

                if (config.ravinesEnabled && chunkRandom.nextDouble() < config.ravineFrequency) {
                    carveRavine(instance, cx, cz, chunkRandom)
                }

                if (config.roomsEnabled && chunkRandom.nextDouble() < config.roomFrequency) {
                    carveRoom(instance, cx, cz, chunkRandom)
                }
            }
        }
    }

    fun decorateAll(instance: InstanceContainer, radiusChunks: Int) {
        if (!config.decorationEnabled) return

        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                if (instance.getChunk(cx, cz) == null) continue
                decorateChunk(instance, cx, cz)
            }
        }
    }

    private fun decorateChunk(instance: InstanceContainer, cx: Int, cz: Int) {
        val random = Random(seed xor (cx.toLong() * 73856093L) xor (cz.toLong() * 19349663L))

        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                val wx = cx * 16 + lx
                val wz = cz * 16 + lz

                for (y in (config.bedrockFloor + 1) until 60) {
                    val block = instance.getBlock(wx, y, wz)
                    if (!block.compare(Block.CAVE_AIR)) continue

                    val below = instance.getBlock(wx, y - 1, wz)
                    val above = instance.getBlock(wx, y + 1, wz)

                    if (config.aquifersEnabled && y < config.aquiferMaxY && isSolid(below)) {
                        val aquifer = aquiferNoise.sample2D(wx * 0.05, wz * 0.05)
                        if (aquifer > config.aquiferThreshold) {
                            instance.setBlock(wx, y, wz, Block.WATER)
                            continue
                        }
                    }

                    if (config.mossEnabled && isSolid(below) && random.nextDouble() < 0.12) {
                        instance.setBlock(wx, y - 1, wz, Block.MOSS_BLOCK)
                        if (random.nextDouble() < 0.5) {
                            instance.setBlock(wx, y, wz, Block.MOSS_CARPET)
                            continue
                        }
                    }

                    if (isSolid(above)) {
                        if (config.dripstoneEnabled && random.nextDouble() < 0.04) {
                            instance.setBlock(wx, y, wz, Block.POINTED_DRIPSTONE
                                .withProperty("vertical_direction", "down")
                                .withProperty("thickness", "tip"))
                            if (random.nextDouble() < 0.4) {
                                instance.setBlock(wx, y + 1, wz, Block.DRIPSTONE_BLOCK)
                            }
                            continue
                        }

                        if (config.hangingRootsEnabled && y > 45 && random.nextDouble() < 0.03) {
                            instance.setBlock(wx, y, wz, Block.HANGING_ROOTS)
                            continue
                        }
                    }

                    if (config.dripstoneEnabled && isSolid(below) && random.nextDouble() < 0.03) {
                        instance.setBlock(wx, y, wz, Block.POINTED_DRIPSTONE
                            .withProperty("vertical_direction", "up")
                            .withProperty("thickness", "tip"))
                        if (random.nextDouble() < 0.3) {
                            instance.setBlock(wx, y - 1, wz, Block.DRIPSTONE_BLOCK)
                        }
                        continue
                    }

                    if (config.glowLichenEnabled && random.nextDouble() < 0.03) {
                        for ((dx, dz, face) in WALL_CHECKS) {
                            val neighbor = instance.getBlock(wx + dx, y, wz + dz)
                            if (isSolid(neighbor)) {
                                instance.setBlock(wx, y, wz, Block.GLOW_LICHEN.withProperty(face, "true"))
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isSolid(block: Block): Boolean =
        !block.isAir && !block.compare(Block.WATER) && !block.compare(Block.LAVA)
            && !block.compare(Block.CAVE_AIR) && !block.compare(Block.MOSS_CARPET)
            && !block.compare(Block.GLOW_LICHEN) && !block.compare(Block.HANGING_ROOTS)
            && !block.compare(Block.POINTED_DRIPSTONE)

    private fun carveWorm(instance: InstanceContainer, cx: Int, cz: Int, random: Random) {
        var x = cx * 16.0 + random.nextInt(16)
        var y = random.nextInt(config.wormMinY, config.wormMaxY + 1).toDouble()
        var z = cz * 16.0 + random.nextInt(16)

        var yaw = random.nextDouble() * Math.PI * 2
        var pitch = (random.nextDouble() - 0.5) * 0.8

        val length = random.nextInt(config.wormMinLength, config.wormMaxLength + 1)
        val baseRadius = random.nextDouble() * (config.wormMaxRadius - config.wormMinRadius) + config.wormMinRadius
        val wormSeed = random.nextLong()

        for (step in 0 until length) {
            val progress = step.toDouble() / length
            val radiusFactor = sin(progress * Math.PI) * 0.8 + 0.2
            val radius = baseRadius * radiusFactor

            carveSphere(instance, x.toInt(), y.toInt(), z.toInt(), radius)

            val noiseVal = directionNoise.sample3D(
                x * 0.05 + wormSeed, y * 0.05, z * 0.05,
            )
            yaw += noiseVal * 0.5
            pitch = (pitch + (random.nextDouble() - 0.5) * 0.2).coerceIn(-0.6, 0.6)

            x += cos(yaw) * cos(pitch)
            y += sin(pitch)
            z += sin(yaw) * cos(pitch)

            y = y.coerceIn(config.bedrockFloor + 2.0, 120.0)
        }
    }

    private fun carveRavine(instance: InstanceContainer, cx: Int, cz: Int, random: Random) {
        var x = cx * 16.0 + random.nextInt(16)
        var y = random.nextInt(config.ravineMinY, config.ravineMaxY + 1).toDouble()
        var z = cz * 16.0 + random.nextInt(16)

        var yaw = random.nextDouble() * Math.PI * 2
        val length = random.nextInt(config.ravineMinLength, config.ravineMaxLength + 1)
        val baseRadius = random.nextDouble() * (config.ravineMaxRadius - config.ravineMinRadius) + config.ravineMinRadius

        for (step in 0 until length) {
            val progress = step.toDouble() / length
            val radiusFactor = sin(progress * Math.PI) * 0.6 + 0.4
            val hRadius = baseRadius * radiusFactor
            val vRadius = hRadius * config.ravineVerticalStretch

            carveEllipsoid(instance, x.toInt(), y.toInt(), z.toInt(), hRadius, vRadius)

            yaw += (random.nextDouble() - 0.5) * 0.15
            x += cos(yaw)
            z += sin(yaw)
            y += (random.nextDouble() - 0.5) * 0.3
            y = y.coerceIn(config.bedrockFloor + 2.0, 120.0)
        }
    }

    private fun carveRoom(instance: InstanceContainer, cx: Int, cz: Int, random: Random) {
        val x = cx * 16 + random.nextInt(16)
        val y = random.nextInt(config.roomMinY, config.roomMaxY + 1)
        val z = cz * 16 + random.nextInt(16)
        val radius = random.nextDouble() * (config.roomMaxRadius - config.roomMinRadius) + config.roomMinRadius

        val rInt = radius.toInt() + 1
        for (dx in -rInt..rInt) {
            for (dy in -rInt..rInt) {
                for (dz in -rInt..rInt) {
                    val dist = sqrt(dx * dx + dy * dy * 0.6 + dz * dz)
                    if (dist > radius) continue
                    val bx = x + dx
                    val by = y + dy
                    val bz = z + dz
                    if (by <= config.bedrockFloor) continue
                    carveBlock(instance, bx, by, bz)
                }
            }
        }
    }

    private fun carveSphere(instance: InstanceContainer, x: Int, y: Int, z: Int, radius: Double) {
        val r = radius.toInt() + 1
        for (dx in -r..r) {
            for (dy in -r..r) {
                for (dz in -r..r) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue
                    carveBlock(instance, x + dx, y + dy, z + dz)
                }
            }
        }
    }

    private fun carveEllipsoid(instance: InstanceContainer, x: Int, y: Int, z: Int, hRadius: Double, vRadius: Double) {
        val hr = hRadius.toInt() + 1
        val vr = vRadius.toInt() + 1
        for (dx in -hr..hr) {
            for (dy in -vr..vr) {
                for (dz in -hr..hr) {
                    val hDist = (dx * dx + dz * dz).toDouble() / (hRadius * hRadius)
                    val vDist = (dy * dy).toDouble() / (vRadius * vRadius)
                    if (hDist + vDist > 1.0) continue
                    carveBlock(instance, x + dx, y + dy, z + dz)
                }
            }
        }
    }

    private fun carveBlock(instance: InstanceContainer, x: Int, y: Int, z: Int) {
        if (y <= config.bedrockFloor) return
        val existing = instance.getBlock(x, y, z)
        if (existing.isAir || existing.compare(Block.WATER) || existing.compare(Block.LAVA)) return

        val above = instance.getBlock(x, y + 1, z)
        if (above.compare(Block.WATER)) return

        if (y <= config.lavaLevel) {
            instance.setBlock(x, y, z, Block.LAVA)
        } else {
            instance.setBlock(x, y, z, Block.CAVE_AIR)
        }
    }

    private fun chunkSeed(cx: Int, cz: Int): Long =
        seed xor (cx.toLong() * 341873128712L) xor (cz.toLong() * 132897987541L)

    companion object {
        private val WALL_CHECKS = listOf(
            Triple(0, -1, "north"),
            Triple(0, 1, "south"),
            Triple(-1, 0, "west"),
            Triple(1, 0, "east"),
        )
    }
}
