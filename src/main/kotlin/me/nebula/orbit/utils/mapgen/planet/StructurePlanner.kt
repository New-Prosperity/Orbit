package me.nebula.orbit.utils.mapgen.planet

import kotlin.random.Random

private const val STRUCTURE_PLAN_CACHE_CAP = 8192
private val STRUCTURE_PLAN_SALT: Long = 0x9E3779B97F4A7C15uL.toLong()

data class PlannedStructure(
    val schematicId: String,
    val originX: Int,
    val originY: Int,
    val originZ: Int,
    val rotation: Int,
    val socketY: Int,
    val transitionRadius: Int,
    val footprint: Footprint,
    val protectBox: AABB,
    val bbox: AABB,
)

class StructurePlanner(
    private val spec: PlanetSpec,
    private val baseHeightFn: (Int, Int) -> Int,
) {

    private val cache = BoundedCache<Long, List<PlannedStructure>>(STRUCTURE_PLAN_CACHE_CAP)

    fun planFor(chunkX: Int, chunkZ: Int): List<PlannedStructure> =
        cache.computeIfAbsent(packKey(chunkX, chunkZ)) { computePlans(chunkX, chunkZ) }

    fun planAround(chunkX: Int, chunkZ: Int, radiusChunks: Int = spec.structureRadiusChunks): List<PlannedStructure> {
        val out = ArrayList<PlannedStructure>()
        for (dx in -radiusChunks..radiusChunks) {
            for (dz in -radiusChunks..radiusChunks) {
                out += planFor(chunkX + dx, chunkZ + dz)
            }
        }
        return out
    }

    private fun computePlans(chunkX: Int, chunkZ: Int): List<PlannedStructure> {
        if (spec.structures.isEmpty()) return emptyList()

        val rng = Random(SeedMix.chunkSeed(spec.seed, chunkX, chunkZ, STRUCTURE_PLAN_SALT))

        val out = ArrayList<PlannedStructure>()
        for (entry in spec.structures) {
            if (rng.nextDouble() >= entry.chancePerChunk) continue
            val structure = StructureLibrary[entry.schematicId] ?: continue

            if (!enforceSpacing(chunkX, chunkZ, entry.minSpacingChunks, rng)) continue

            val rotation = if (structure.metadata.rotatable) rng.nextInt(4) else 0
            val (footW, footL) = rotatedSize(structure.width, structure.length, rotation)
            val originX = chunkX * 16 + rng.nextInt(16 - (footW % 16).coerceAtMost(15))
            val originZ = chunkZ * 16 + rng.nextInt(16 - (footL % 16).coerceAtMost(15))

            val centerX = originX + footW / 2
            val centerZ = originZ + footL / 2
            val groundY = baseHeightFn(centerX, centerZ)
            val originY = groundY - structure.metadata.socketY

            val footprint = Footprint(originX, originZ, originX + footW - 1, originZ + footL - 1)
            val bbox = AABB(originX, originY, originZ, originX + footW - 1, originY + structure.height - 1, originZ + footL - 1)
            val protect = rotateProtectBox(structure.protectBoxLocal, rotation, structure.width, structure.length)
                .translated(originX, originY, originZ)

            out += PlannedStructure(
                schematicId = entry.schematicId,
                originX = originX,
                originY = originY,
                originZ = originZ,
                rotation = rotation,
                socketY = groundY,
                transitionRadius = structure.metadata.transitionRadius,
                footprint = footprint,
                protectBox = protect,
                bbox = bbox,
            )
        }
        return out
    }

    private fun enforceSpacing(chunkX: Int, chunkZ: Int, minSpacing: Int, rng: Random): Boolean {
        if (minSpacing <= 1) return true
        return (chunkX % minSpacing == 0 || rng.nextInt(minSpacing) == 0) &&
            (chunkZ % minSpacing == 0 || rng.nextInt(minSpacing) == 0)
    }

    private fun rotatedSize(width: Int, length: Int, rotation: Int): Pair<Int, Int> =
        if (rotation % 2 == 0) width to length else length to width

    private fun rotateProtectBox(local: AABB, rotation: Int, width: Int, length: Int): AABB {
        val corners = listOf(
            rotateXZ(local.x1, local.z1, rotation, width, length),
            rotateXZ(local.x2, local.z2, rotation, width, length),
        )
        val xs = corners.map { it.first }
        val zs = corners.map { it.second }
        return AABB(xs.min(), local.y1, zs.min(), xs.max(), local.y2, zs.max())
    }

    private fun rotateXZ(x: Int, z: Int, rotation: Int, width: Int, length: Int): Pair<Int, Int> =
        when (rotation % 4) {
            1 -> (length - 1 - z) to x
            2 -> (width - 1 - x) to (length - 1 - z)
            3 -> z to (width - 1 - x)
            else -> x to z
        }

    private fun packKey(cx: Int, cz: Int): Long =
        (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
}
