package me.nebula.orbit.utils.mapgen.planet

import me.nebula.orbit.utils.mapgen.BiomeDefinition
import me.nebula.orbit.utils.mapgen.BiomeProvider
import me.nebula.orbit.utils.mapgen.BiomeRegistry
import me.nebula.orbit.utils.mapgen.HeightCurve
import me.nebula.orbit.utils.mapgen.OctaveNoise
import me.nebula.orbit.utils.mapgen.PerlinNoise
import me.nebula.orbit.utils.mapgen.RidgedNoise
import me.nebula.orbit.utils.mapgen.planet.decoration.TreeShape
import me.nebula.orbit.utils.mapgen.planet.decoration.TreeShapeRegistry
import me.nebula.orbit.utils.customcontent.furniture.FurniturePersistence
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceChunkLoadEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import net.minestom.server.instance.generator.UnitModifier
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.biome.Biome
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

abstract class PlanetGenerator(val spec: PlanetSpec) : Generator {

    private val heightNoise = OctaveNoise(PerlinNoise(spec.seed), octaves = 6)
    private val detailNoise = OctaveNoise(PerlinNoise(spec.seed + 100), octaves = 3, lacunarity = 3.0, persistence = 0.3)
    private val continentalNoise = OctaveNoise(PerlinNoise(spec.seed + 150), octaves = 4, lacunarity = 2.0, persistence = 0.6)
    private val erosionNoise = OctaveNoise(PerlinNoise(spec.seed + 250), octaves = 4, lacunarity = 2.5, persistence = 0.4)
    private val ridgedNoise = RidgedNoise(PerlinNoise(spec.seed + 550), octaves = 4)
    private val overhangNoise = OctaveNoise(PerlinNoise(spec.seed + 600), octaves = 3, lacunarity = 2.5, persistence = 0.4)
    private val caveNoiseA = OctaveNoise(PerlinNoise(spec.seed + 700), octaves = 3)
    private val caveNoiseB = OctaveNoise(PerlinNoise(spec.seed + 800), octaves = 3)
    private val noodleNoise = OctaveNoise(PerlinNoise(spec.seed + 900), octaves = 2, lacunarity = 2.0, persistence = 0.4)
    private val riverNoise = OctaveNoise(PerlinNoise(spec.seed + 1100), octaves = 4, lacunarity = 2.0, persistence = 0.5)
    private val biomeEdgeNoise = OctaveNoise(PerlinNoise(spec.seed + 1300), octaves = 2, lacunarity = 2.0, persistence = 0.5)
    private val coastlineNoise = OctaveNoise(PerlinNoise(spec.seed + 1500), octaves = 4, lacunarity = 2.0, persistence = 0.5)
    private val surfaceJitterNoise = OctaveNoise(PerlinNoise(spec.seed + 1700), octaves = 2, lacunarity = 2.0, persistence = 0.4)

    private val groundLevelCache = ConcurrentHashMap<Long, Double>()
    private val chunkContextCache = ConcurrentHashMap<Long, ChunkContext>()

    private val biomeProvider: BiomeProvider? = if (spec.biomes.isEmpty()) null else BiomeProvider(
        seed = spec.seed,
        config = spec.biomeZoning.copy(includedBiomeIds = spec.biomes.toSet()),
    )

    val structurePlanner = StructurePlanner(spec) { x, z -> groundLevelAt(x, z).toInt() }

    private val spawnedPlanKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    init {
        ensureGlobalListenerInstalled()
    }

    internal fun spawnFurnitureForChunk(instance: Instance, chunkX: Int, chunkZ: Int) {
        val plans = chunkContextOf(chunkX, chunkZ).plans
        for (plan in plans) {
            val originChunkX = plan.originX shr 4
            val originChunkZ = plan.originZ shr 4
            if (originChunkX != chunkX || originChunkZ != chunkZ) continue
            val key = (plan.originX.toLong() shl 32) or (plan.originZ.toLong() and 0xFFFFFFFFL)
            if (!spawnedPlanKeys.add(key)) continue
            val structure = StructureLibrary[plan.schematicId] ?: continue
            val manifest = structure.furnitureManifest ?: continue
            if (manifest.pieces.isEmpty()) continue

            val transformed = manifest.pieces.map { piece ->
                val (rx, rz) = rotateXZ(piece.anchorX, piece.anchorZ, plan.rotation, structure.width, structure.length)
                piece.copy(
                    uuid = java.util.UUID.randomUUID().toString(),
                    anchorX = plan.originX + rx,
                    anchorY = plan.originY + piece.anchorY,
                    anchorZ = plan.originZ + rz,
                    yawDegrees = (piece.yawDegrees + plan.rotation * 90f) % 360f,
                )
            }
            FurniturePersistence.restorePieces(instance, transformed)
        }
    }

    val density: DensityFn = buildDensity()

    open fun buildDensity(): DensityFn = Density.add(
        Density.heightDelta { x, z -> groundLevelAt(x, z) },
        Density.noise3D(overhangNoise, spec.heightProfile.overhangScaleXZ, spec.heightProfile.overhangScaleY, spec.heightProfile.overhangAmplitude),
    )

    override fun generate(unit: GenerationUnit) {
        val start = unit.absoluteStart()
        val end = unit.absoluteEnd()
        val chunkX = start.blockX() shr 4
        val chunkZ = start.blockZ() shr 4
        val ctx = chunkContextOf(chunkX, chunkZ)
        val modifier = unit.modifier()

        stageFill(modifier, ctx, start, end)
        stageCarveCheese(modifier, ctx, start, end)
        stageCarveNoodle(modifier, ctx, start, end)
        stageCarveRavines(modifier, ctx, start, end)
        stageOres(modifier, ctx, start, end)
        stagePasteStructures(modifier, ctx, start, end)
        stageDecorate(modifier, ctx, start, end)
        stageBiomeWrite(modifier, ctx, start, end)
    }

    open fun customSurfaceBlock(x: Int, z: Int, surfaceY: Int, defaultBlock: Block): Block = defaultBlock

    private fun chunkContextOf(chunkX: Int, chunkZ: Int): ChunkContext =
        chunkContextCache.computeIfAbsent(packKey(chunkX, chunkZ)) { computeChunkContext(chunkX, chunkZ) }

    private fun computeChunkContext(chunkX: Int, chunkZ: Int): ChunkContext {
        val biomes = arrayOfNulls<BiomeDefinition>(256)
        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                val wx = chunkX * 16 + lx
                val wz = chunkZ * 16 + lz
                biomes[lx + lz * 16] = pickBiomeWithEdgeJitter(wx, wz)
            }
        }

        val plans = structurePlanner.planAround(chunkX, chunkZ)
        val approxHeights = IntArray(256) { i ->
            val lx = i and 0xF
            val lz = (i shr 4) and 0xF
            val wx = chunkX * 16 + lx
            val wz = chunkZ * 16 + lz
            blendHeightWithStructures(wx, wz, groundLevelAt(wx, wz).toInt(), plans)
        }

        return ChunkContext(chunkX, chunkZ, approxHeights, biomes, plans)
    }

    private fun pickBiomeWithEdgeJitter(x: Int, z: Int): BiomeDefinition? {
        val provider = biomeProvider ?: return null
        val jitter = biomeEdgeNoise.sample2D(x * 0.07, z * 0.07) * 2.0
        val jx = (x + jitter).toInt()
        val jz = (z + jitter).toInt()
        return provider.biomeAt(jx, jz)
    }

    private fun blendHeightWithStructures(x: Int, z: Int, baseHeight: Int, plans: List<PlannedStructure>): Int {
        var bestSocket = -1
        var bestWeight = 0.0
        var insideAny = false

        for (plan in plans) {
            if (plan.footprint.contains(x, z)) return plan.socketY
            val d = plan.footprint.distanceTo(x, z)
            if (d > plan.transitionRadius) continue
            val t = (1.0 - d / plan.transitionRadius).coerceIn(0.0, 1.0)
            val w = t * t * (3.0 - 2.0 * t)
            if (w > bestWeight) {
                bestWeight = w
                bestSocket = plan.socketY
                insideAny = true
            }
        }

        if (!insideAny) return baseHeight
        return (baseHeight * (1.0 - bestWeight) + bestSocket * bestWeight).toInt()
    }

    fun groundLevelAt(x: Int, z: Int): Double = groundLevelCache.computeIfAbsent(packKey(x, z)) {
        computeGroundLevel(x, z)
    }.let { applyRiver(x, z, applyIsland(x, z, it)) }

    private fun applyIsland(x: Int, z: Int, baseGround: Double): Double {
        val island = spec.island
        if (!island.enabled) return baseGround
        val dx = (x - island.centerX).toDouble()
        val dz = (z - island.centerZ).toDouble()
        val dist = sqrt(dx * dx + dz * dz)
        val perturb = coastlineNoise.sample2D(x * island.coastlineNoiseScale, z * island.coastlineNoiseScale) * island.coastlineNoiseAmplitude
        val effectiveDist = dist + perturb
        val coreEdge = island.radius - island.falloffWidth
        val seaFloor = (spec.seaLevel - island.oceanDepth).toDouble()
        return when {
            effectiveDist <= coreEdge -> baseGround
            effectiveDist >= island.radius -> seaFloor
            else -> {
                val t = ((effectiveDist - coreEdge) / island.falloffWidth).coerceIn(0.0, 1.0)
                val smooth = t * t * (3.0 - 2.0 * t)
                baseGround * (1.0 - smooth) + seaFloor * smooth
            }
        }
    }

    private fun islandStateAt(x: Int, z: Int): IslandState {
        val island = spec.island
        if (!island.enabled) return IslandState.LAND
        val dx = (x - island.centerX).toDouble()
        val dz = (z - island.centerZ).toDouble()
        val dist = sqrt(dx * dx + dz * dz)
        val perturb = coastlineNoise.sample2D(x * island.coastlineNoiseScale, z * island.coastlineNoiseScale) * island.coastlineNoiseAmplitude
        val effectiveDist = dist + perturb
        return when {
            effectiveDist >= island.radius -> IslandState.OCEAN
            effectiveDist >= island.radius - island.falloffWidth -> IslandState.COAST
            else -> IslandState.LAND
        }
    }

    enum class IslandState { LAND, COAST, OCEAN }

    private fun computeGroundLevel(x: Int, z: Int): Double {
        val biome = biomeProvider?.biomeAt(x, z)
        val hp = spec.heightProfile

        val noise = heightNoise.sample2D(x * hp.terrainScale, z * hp.terrainScale)
        val detail = detailNoise.sample2D(x * hp.terrainScale * 2, z * hp.terrainScale * 2) * 0.3
        val continental = continentalNoise.sample2D(x * hp.continentalScale, z * hp.continentalScale) * hp.continentalInfluence
        val erosion = erosionNoise.sample2D(x * hp.erosionScale, z * hp.erosionScale)
        val erosionFactor = 1.0 - erosion.coerceIn(0.0, 1.0) * hp.erosionStrength
        val curve = biome?.heightCurve ?: hp.heightCurve

        val combined = when (curve) {
            HeightCurve.LINEAR -> (noise + detail) * erosionFactor
            HeightCurve.SMOOTH -> smoothstep(noise + detail) * erosionFactor
            HeightCurve.AMPLIFIED -> {
                val r = ridgedNoise.sample2D(x * hp.terrainScale, z * hp.terrainScale)
                ((noise * 0.5 + r * 0.5) + detail) * erosionFactor
            }
            HeightCurve.RIDGED -> {
                val r = ridgedNoise.sample2D(x * hp.terrainScale, z * hp.terrainScale)
                (r + detail * 0.5) * erosionFactor
            }
            HeightCurve.CLIFF -> {
                val raw = noise + detail
                if (raw > 0) raw * raw * erosionFactor else raw * erosionFactor
            }
            HeightCurve.TERRACE -> terrace(noise + detail, 6) * erosionFactor
            HeightCurve.MESA -> {
                val raw = (noise + detail).coerceIn(-1.0, 1.0)
                val flat = when {
                    raw > 0.15 -> 1.0
                    raw < -0.15 -> -1.0
                    else -> raw / 0.15
                }
                flat * erosionFactor
            }
            HeightCurve.ROLLING -> smoothstep(smoothstep(noise + detail)) * erosionFactor
        }

        val mixed = if (hp.ridgedMix > 0) {
            val r = ridgedNoise.sample2D(x * hp.terrainScale, z * hp.terrainScale)
            combined * (1 - hp.ridgedMix) + r * hp.ridgedMix
        } else combined

        val baseH = biome?.baseHeight ?: hp.baseHeight.toDouble()
        val variation = biome?.heightVariation ?: hp.heightVariation.toDouble()

        return (baseH + continental + mixed * variation).coerceIn(2.0, 250.0)
    }

    private fun applyRiver(x: Int, z: Int, baseGround: Double): Double {
        val rc = spec.rivers
        if (!rc.enabled) return baseGround
        val n = abs(riverNoise.sample2D(x * rc.noiseScale, z * rc.noiseScale))
        if (n >= rc.widthThreshold) return baseGround
        val depthFactor = 1.0 - (n / rc.widthThreshold)
        val targetY = (spec.seaLevel - rc.depth).toDouble()
        return baseGround * (1.0 - depthFactor) + targetY * depthFactor
    }

    private fun isRiverColumn(x: Int, z: Int): Boolean {
        val rc = spec.rivers
        if (!rc.enabled) return false
        return abs(riverNoise.sample2D(x * rc.noiseScale, z * rc.noiseScale)) < rc.widthThreshold
    }

    private fun stageFill(modifier: UnitModifier, ctx: ChunkContext, start: Point, end: Point) {
        val seaLevel = spec.seaLevel
        val sectionMinY = start.blockY()
        val sectionMaxY = end.blockY() - 1
        val pal = spec.palette
        val riverBank: Block = BlockResolver.resolveOrNull(spec.rivers.bankBlock) ?: Block.SAND
        val islandConfig = spec.island
        val oceanFloorBlock: Block = BlockResolver.resolveOrNull(islandConfig.oceanFloorBlock) ?: Block.GRAVEL
        val beachBlock: Block = BlockResolver.resolveOrNull(islandConfig.beachBlock) ?: Block.SAND

        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                val wx = ctx.chunkX * 16 + lx
                val wz = ctx.chunkZ * 16 + lz
                val biome = ctx.biomes[lx + lz * 16]
                val isRiver = isRiverColumn(wx, wz)
                val islandState = islandStateAt(wx, wz)
                val surfaceBlock = biome?.surfaceBlock ?: pal.surfaceBlock
                val fillerBlock = biome?.fillerBlock ?: pal.fillerBlock
                val underwater = biome?.underwaterSurface ?: pal.underwaterSurface
                val stoneBlock = biome?.stoneBlock ?: pal.stoneBlock
                val deepslateBlock = pal.deepslateBlock
                val frozen = biome?.frozen ?: false
                val snowLine = biome?.snowLine ?: Int.MAX_VALUE
                val surfaceDepth = biome?.surfaceDepth ?: 1
                val subsurfaceBlock = biome?.subsurfaceBlock
                val subsurfaceDepth = biome?.subsurfaceDepth ?: 0

                var topSolid = -1
                for (y in sectionMaxY downTo sectionMinY) {
                    if (y < spec.bedrockHeight) {
                        modifier.setBlock(wx, y, wz, pal.bedrockBlock)
                        if (topSolid == -1) topSolid = y
                        continue
                    }
                    val isSolid = density.sample(wx, y, wz) > 0.0
                    if (!isSolid) {
                        if (y in (spec.worldMinY + 1)..seaLevel) {
                            val waterBlock = if (frozen && y == seaLevel && !isRiver) Block.ICE else Block.WATER
                            modifier.setBlock(wx, y, wz, waterBlock)
                        }
                        continue
                    }
                    if (topSolid == -1) topSolid = y
                }

                if (topSolid < 0) continue

                for (y in sectionMinY..topSolid) {
                    if (y < spec.bedrockHeight) continue
                    if (density.sample(wx, y, wz) <= 0.0) continue
                    val depthFromTop = topSolid - y
                    val nearWaterline = topSolid in (seaLevel - islandConfig.beachWidth)..(seaLevel + 1)
                    val isBeachColumn = islandConfig.enabled
                        && nearWaterline
                        && (islandState == PlanetGenerator.IslandState.COAST || islandState == PlanetGenerator.IslandState.LAND)
                    val block: Block = when {
                        y < spec.deepslateLevel -> deepslateBlock
                        y == topSolid -> {
                            val s = when {
                                islandState == PlanetGenerator.IslandState.OCEAN -> oceanFloorBlock
                                isBeachColumn -> beachBlock
                                isRiver -> riverBank
                                topSolid >= seaLevel -> surfaceBlock
                                else -> underwater
                            }
                            customSurfaceBlock(wx, topSolid, y, s)
                        }
                        depthFromTop < surfaceDepth -> if (isBeachColumn) beachBlock else surfaceBlock
                        depthFromTop < surfaceDepth + subsurfaceDepth && subsurfaceBlock != null -> subsurfaceBlock
                        depthFromTop < surfaceDepth + spec.fillerDepth -> fillerBlock
                        else -> stoneBlock
                    }
                    modifier.setBlock(wx, y, wz, block)
                }

                if (snowLine != Int.MAX_VALUE && topSolid >= snowLine && topSolid >= seaLevel
                    && (topSolid + 1) in sectionMinY..sectionMaxY) {
                    modifier.setBlock(wx, topSolid + 1, wz, Block.SNOW)
                }
            }
        }
    }

    private fun stageCarveCheese(modifier: UnitModifier, ctx: ChunkContext, start: Point, end: Point) {
        val cave = spec.caveProfile
        if (!cave.enabled) return
        val sectionMinY = maxOf(start.blockY(), cave.minY)
        val sectionMaxY = minOf(end.blockY() - 1, cave.maxY)
        if (sectionMinY > sectionMaxY) return

        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                val wx = ctx.chunkX * 16 + lx
                val wz = ctx.chunkZ * 16 + lz
                val surfaceY = ctx.approxHeights[lx + lz * 16]
                if (surfaceY <= sectionMinY) continue
                val carveTop = minOf(sectionMaxY, surfaceY - 4)
                if (carveTop < sectionMinY) continue

                val biomeMul = ctx.biomes[lx + lz * 16]?.caveFrequencyMultiplier ?: 1.0
                val effectiveThreshold = cave.threshold / biomeMul.coerceAtLeast(0.05)

                for (y in sectionMinY..carveTop) {
                    if (isInsideAnyProtect(wx, y, wz, ctx)) continue
                    val a = caveNoiseA.sample3D(wx * cave.noiseScale, y * cave.noiseScale * 2, wz * cave.noiseScale)
                    val b = caveNoiseB.sample3D(wx * cave.noiseScale, y * cave.noiseScale * 2, wz * cave.noiseScale)
                    if (a > effectiveThreshold && b > effectiveThreshold) {
                        modifier.setBlock(wx, y, wz, Block.CAVE_AIR)
                    }
                }
            }
        }
    }

    private fun stageCarveNoodle(modifier: UnitModifier, ctx: ChunkContext, start: Point, end: Point) {
        val cave = spec.caveProfile
        if (!cave.enabled || !cave.noodleEnabled) return
        val sectionMinY = maxOf(start.blockY(), cave.minY)
        val sectionMaxY = minOf(end.blockY() - 1, cave.maxY)
        if (sectionMinY > sectionMaxY) return

        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                val wx = ctx.chunkX * 16 + lx
                val wz = ctx.chunkZ * 16 + lz
                val surfaceY = ctx.approxHeights[lx + lz * 16]
                if (surfaceY <= sectionMinY) continue
                val carveTop = minOf(sectionMaxY, surfaceY - 6)
                if (carveTop < sectionMinY) continue

                for (y in sectionMinY..carveTop) {
                    if (isInsideAnyProtect(wx, y, wz, ctx)) continue
                    val n1 = noodleNoise.sample3D(wx * cave.noodleScale, y * cave.noodleScale, wz * cave.noodleScale)
                    val n2 = noodleNoise.sample3D(wx * cave.noodleScale * 1.7, y * cave.noodleScale * 1.7, wz * cave.noodleScale * 1.7)
                    val ridge = 1.0 - abs(n1) - abs(n2)
                    if (ridge > cave.noodleThreshold) {
                        modifier.setBlock(wx, y, wz, Block.CAVE_AIR)
                    }
                }
            }
        }
    }

    private fun stageCarveRavines(modifier: UnitModifier, ctx: ChunkContext, start: Point, end: Point) {
        val cave = spec.caveProfile
        if (!cave.ravinesEnabled) return
        val rng = Random(spec.seed xor (ctx.chunkX.toLong() * 13441L) xor (ctx.chunkZ.toLong() * 31337L))
        if (rng.nextDouble() >= cave.ravineChancePerChunk) return

        val originX = ctx.chunkX * 16 + rng.nextInt(16)
        val originZ = ctx.chunkZ * 16 + rng.nextInt(16)
        val angle = rng.nextDouble() * 2.0 * Math.PI
        val length = 30 + rng.nextInt(40)
        val maxWidth = 2.0 + rng.nextDouble() * 2.5
        val depth = 18 + rng.nextInt(14)
        val topY = spec.seaLevel - 4
        val bottomY = (topY - depth).coerceAtLeast(spec.bedrockHeight + 1)

        val sectionMinY = start.blockY()
        val sectionMaxY = end.blockY() - 1

        var cx = originX.toDouble()
        var cz = originZ.toDouble()
        for (i in 0 until length) {
            val t = i.toDouble() / length
            val width = maxWidth * (1.0 - kotlin.math.abs(t * 2.0 - 1.0))
            val r = width.toInt() + 1
            for (dx in -r..r) {
                for (dz in -r..r) {
                    val dd = dx * dx + dz * dz
                    if (dd > r * r) continue
                    val wx = (cx + dx).toInt()
                    val wz = (cz + dz).toInt()
                    val cChunkX = wx shr 4
                    val cChunkZ = wz shr 4
                    if (cChunkX != ctx.chunkX || cChunkZ != ctx.chunkZ) continue
                    for (y in maxOf(bottomY, sectionMinY)..minOf(topY, sectionMaxY)) {
                        if (isInsideAnyProtect(wx, y, wz, ctx)) continue
                        modifier.setBlock(wx, y, wz, Block.CAVE_AIR)
                    }
                }
            }
            cx += cos(angle) * 0.8
            cz += sin(angle) * 0.8
        }
    }

    private fun stageOres(modifier: UnitModifier, ctx: ChunkContext, start: Point, end: Point) {
        val rng = Random(spec.seed xor (ctx.chunkX.toLong() * 49829L) xor (ctx.chunkZ.toLong() * 7919L))
        val sectionMinY = start.blockY()
        val sectionMaxY = end.blockY() - 1

        var oreSum = 0.0
        var oreCount = 0
        for (b in ctx.biomes) { if (b != null) { oreSum += b.oreMultiplier; oreCount++ } }
        val avgOreMul = if (oreCount > 0) oreSum / oreCount else 1.0

        for (ore in spec.ores) {
            placeOreEntries(modifier, ctx, ore, rng, avgOreMul, sectionMinY, sectionMaxY)
        }

        val biomeOreSeen = HashSet<String>()
        for (b in ctx.biomes) {
            if (b == null) continue
            if (b.oreOverrides.isEmpty()) continue
            if (!biomeOreSeen.add(b.id)) continue
            for (ore in b.oreOverrides) {
                placeOreEntries(modifier, ctx, ore, rng, avgOreMul, sectionMinY, sectionMaxY, biomeFilter = b.id)
            }
        }
    }

    private fun placeOreEntries(
        modifier: UnitModifier,
        ctx: ChunkContext,
        ore: OreEntry,
        rng: Random,
        avgOreMul: Double,
        sectionMinY: Int,
        sectionMaxY: Int,
        biomeFilter: String? = null,
    ) {
        val effectiveMin = maxOf(ore.minY, sectionMinY)
        val effectiveMax = minOf(ore.maxY, sectionMaxY)
        if (effectiveMin > effectiveMax) return
        val veins = (ore.veinsPerChunk * avgOreMul).toInt().coerceAtLeast(0)
        for (i in 0 until veins) {
            val cx = rng.nextInt(16)
            val cz = rng.nextInt(16)
            val cy = rng.nextInt(effectiveMin, effectiveMax + 1)
            val wx = ctx.chunkX * 16 + cx
            val wz = ctx.chunkZ * 16 + cz
            if (biomeFilter != null && ctx.biomes[cx + cz * 16]?.id != biomeFilter) continue
            if (isInsideAnyProtect(wx, cy, wz, ctx)) continue
            val offsets = ore.clusterShape?.let { OreClusterShapeRegistry[it]?.offsets }
                ?: OreClusterShapeRegistry.amorphous(rng, ore.veinSize)
            for ((dx, dy, dz) in offsets) {
                val ox = wx + dx
                val oy = cy + dy
                val oz = wz + dz
                if (oy !in sectionMinY..sectionMaxY) continue
                if (isInsideAnyProtect(ox, oy, oz, ctx)) continue
                modifier.setBlock(ox, oy, oz, ore.block)
            }
        }
    }

    private fun stagePasteStructures(modifier: UnitModifier, ctx: ChunkContext, start: Point, end: Point) {
        val sectionMinY = start.blockY()
        val sectionMaxY = end.blockY() - 1
        val cMinX = ctx.chunkX * 16
        val cMaxX = cMinX + 15
        val cMinZ = ctx.chunkZ * 16
        val cMaxZ = cMinZ + 15

        for (plan in ctx.plans) {
            if (!plan.bbox.intersectsChunk(ctx.chunkX, ctx.chunkZ)) continue
            val structure = StructureLibrary[plan.schematicId] ?: continue

            for (sy in 0 until structure.height) {
                val wy = plan.originY + sy
                if (wy !in sectionMinY..sectionMaxY) continue

                for (sx in 0 until structure.width) {
                    for (sz in 0 until structure.length) {
                        val (rx, rz) = rotateXZ(sx, sz, plan.rotation, structure.width, structure.length)
                        val wx = plan.originX + rx
                        val wz = plan.originZ + rz
                        if (wx !in cMinX..cMaxX || wz !in cMinZ..cMaxZ) continue

                        val block = structure.blockAt(sx, sy, sz)
                        if (block.isAir) continue
                        modifier.setBlock(wx, wy, wz, rotateBlockYaw(block, plan.rotation))
                    }
                }
            }
        }
    }

    private fun stageDecorate(modifier: UnitModifier, ctx: ChunkContext, start: Point, end: Point) {
        if (!spec.decoration.enabled) return
        val sectionMinY = start.blockY()
        val sectionMaxY = end.blockY() - 1
        val rng = Random(spec.seed xor (ctx.chunkX.toLong() * 921347L) xor (ctx.chunkZ.toLong() * 53761L))

        var treeBudget = spec.decoration.maxTreesPerChunk
        var vegBudget = spec.decoration.maxVegetationPerChunk

        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                val wx = ctx.chunkX * 16 + lx
                val wz = ctx.chunkZ * 16 + lz
                val biome = ctx.biomes[lx + lz * 16] ?: continue
                if (isRiverColumn(wx, wz)) continue
                if (isInsideAnyProtect(wx, ctx.approxHeights[lx + lz * 16], wz, ctx)) continue

                val surfaceY = ctx.approxHeights[lx + lz * 16]
                val standY = surfaceY + 1
                val islandState = islandStateAt(wx, wz)

                if (islandState == IslandState.OCEAN && spec.decoration.underwaterVegetation) {
                    if (rng.nextDouble() < 0.04 && surfaceY < spec.seaLevel - 1) {
                        val kelpY = surfaceY + 1
                        if (kelpY in sectionMinY..sectionMaxY) {
                            val tip = (kelpY + 1 + rng.nextInt(5)).coerceAtMost(spec.seaLevel - 1)
                            for (y in kelpY..tip) {
                                if (y !in sectionMinY..sectionMaxY) continue
                                modifier.setBlock(wx, y, wz, Block.KELP_PLANT)
                            }
                        }
                    } else if (rng.nextDouble() < 0.06 && surfaceY < spec.seaLevel) {
                        val gy = surfaceY + 1
                        if (gy in sectionMinY..sectionMaxY) modifier.setBlock(wx, gy, wz, Block.SEAGRASS)
                    }
                    continue
                }

                if (standY !in sectionMinY..sectionMaxY) continue
                if (surfaceY < spec.seaLevel) continue
                if (islandState == IslandState.COAST && surfaceY <= spec.seaLevel + 1) continue

                if (treeBudget > 0 && rng.nextDouble() < biome.treeDensity && lx in 2..13 && lz in 2..13) {
                    val shape = pickTreeShape(biome, rng)
                    if (shape != null) {
                        shape.place(modifier, wx, standY, wz, rng)
                        treeBudget--
                        continue
                    }
                }

                if (vegBudget > 0 && rng.nextDouble() < biome.vegetationDensity) {
                    val plant = pickPlant(biome, rng)
                    if (plant != null) {
                        modifier.setBlock(wx, standY, wz, plant)
                        vegBudget--
                    }
                }
            }
        }
    }

    private fun pickTreeShape(biome: BiomeDefinition, rng: Random): TreeShape? {
        val pool = mutableListOf<String>()
        for (id in biome.treeShapes) pool += id
        for (t in biome.treeTypes) pool += t.name.lowercase()
        if (pool.isEmpty()) return null
        for (i in 0 until pool.size) {
            val id = pool[rng.nextInt(pool.size)]
            TreeShapeRegistry[id]?.let { return it }
        }
        return null
    }

    private fun pickPlant(biome: BiomeDefinition, rng: Random): Block? {
        val flowers = biome.flowerBlocks
        val tall = biome.tallGrassBlock
        val choice = rng.nextDouble()
        return when {
            flowers.isNotEmpty() && choice < 0.25 -> flowers[rng.nextInt(flowers.size)]
            tall != null && choice < 0.45 -> tall
            else -> biome.grassBlock
        }
    }

    private fun stageBiomeWrite(modifier: UnitModifier, ctx: ChunkContext, start: Point, end: Point) {
        if (biomeProvider == null) return
        val sectionMinY = start.blockY()
        val sectionMaxY = end.blockY() - 1
        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                val wx = ctx.chunkX * 16 + lx
                val wz = ctx.chunkZ * 16 + lz
                val biome = ctx.biomes[lx + lz * 16] ?: continue
                val key = BiomeRegistry.getRegistryKey(biome.id) ?: fallbackBiomeKey
                for (y in sectionMinY..sectionMaxY step 4) {
                    modifier.setBiome(wx, y, wz, key)
                }
            }
        }
    }

    private fun isInsideAnyProtect(x: Int, y: Int, z: Int, ctx: ChunkContext): Boolean {
        for (plan in ctx.plans) {
            if (plan.protectBox.contains(x, y, z)) return true
        }
        return false
    }

    private fun smoothstep(t: Double): Double {
        val clamped = t.coerceIn(-1.0, 1.0)
        val n = (clamped + 1.0) / 2.0
        return (3 * n * n - 2 * n * n * n) * 2.0 - 1.0
    }

    private fun terrace(value: Double, steps: Int): Double {
        val scaled = (value + 1.0) / 2.0 * steps
        return (floor(scaled) / steps) * 2.0 - 1.0
    }

    private fun rotateXZ(x: Int, z: Int, rotation: Int, width: Int, length: Int): Pair<Int, Int> =
        when (rotation % 4) {
            1 -> (length - 1 - z) to x
            2 -> (width - 1 - x) to (length - 1 - z)
            3 -> z to (width - 1 - x)
            else -> x to z
        }

    private fun rotateBlockYaw(block: Block, rotation: Int): Block {
        if (rotation == 0) return block
        val facing = block.getProperty("facing")
        if (facing != null && facing in HORIZONTAL_FACINGS) {
            val idx = HORIZONTAL_FACINGS.indexOf(facing)
            return block.withProperty("facing", HORIZONTAL_FACINGS[(idx + rotation) % 4])
        }
        val axis = block.getProperty("axis")
        if (axis != null && rotation % 2 != 0) {
            return when (axis) {
                "x" -> block.withProperty("axis", "z")
                "z" -> block.withProperty("axis", "x")
                else -> block
            }
        }
        return block
    }

    private fun packKey(a: Int, b: Int): Long =
        (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)

    class ChunkContext(
        val chunkX: Int,
        val chunkZ: Int,
        val approxHeights: IntArray,
        val biomes: Array<BiomeDefinition?>,
        val plans: List<PlannedStructure>,
    )

    companion object {
        private val HORIZONTAL_FACINGS = listOf("north", "east", "south", "west")
        private val fallbackBiomeKey: RegistryKey<Biome> = RegistryKey.unsafeOf("plains")
        private val globalListenerInstalled = java.util.concurrent.atomic.AtomicBoolean(false)

        internal fun ensureGlobalListenerInstalled() {
            if (!globalListenerInstalled.compareAndSet(false, true)) return
            val node = EventNode.all("planet-generator-furniture-spawn")
            node.addListener(InstanceChunkLoadEvent::class.java) { event ->
                val gen = event.instance.generator() as? PlanetGenerator ?: return@addListener
                gen.spawnFurnitureForChunk(event.instance, event.chunkX, event.chunkZ)
            }
            MinecraftServer.getGlobalEventHandler().addChild(node)
        }
    }
}
