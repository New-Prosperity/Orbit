package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.orbit.utils.modelengine.generator.BbElement
import me.nebula.orbit.utils.modelengine.generator.BbGroup
import me.nebula.orbit.utils.modelengine.generator.BbGroupChild
import me.nebula.orbit.utils.modelengine.generator.BlockbenchModel

object BlockbenchColliderParser {

    fun readFootprintCells(model: BlockbenchModel, prefix: String = "collider"): List<FootprintCell> {
        val elementsById = model.elements.associateBy { it.uuid }
        val matchingElements = mutableListOf<BbElement>()
        for (group in model.groups) {
            collectMatching(group, prefix, elementsById, matchingElements, parentMatches = false)
        }
        if (matchingElements.isEmpty()) return emptyList()
        return cellsFromElements(matchingElements)
    }

    fun colliderPrefixOf(bone: String): String = bone

    fun classifyCells(model: BlockbenchModel, prefix: String = "collider"): Map<FootprintCell, CellDecision> {
        val elementsById = model.elements.associateBy { it.uuid }
        val raw = mutableListOf<RawEntry>()
        for (group in model.groups) {
            collectClassified(group, prefix, elementsById, raw, parentMode = null)
        }
        if (raw.isEmpty()) return emptyMap()

        val grouped = raw.groupBy { it.cell }
        val result = mutableMapOf<FootprintCell, CellDecision>()
        for ((cell, entries) in grouped) {
            val anySolid = entries.any { it.mode == CellCollisionMode.Solid }
            if (anySolid) {
                result[cell] = CellDecision.Barrier
                continue
            }
            val unioned = entries.map { it.aabb }.reduce(CubeAabb::union)
            val hitbox = HitboxInferrer.bestFit(unioned.toCellLocal(cell))
            result[cell] = when (hitbox) {
                me.nebula.orbit.utils.customcontent.block.BlockHitbox.Full -> CellDecision.Barrier
                else -> CellDecision.Shaped(hitbox)
            }
        }
        if (result.isNotEmpty() && !result.containsKey(FootprintCell(0, 0, 0))) {
            result[FootprintCell(0, 0, 0)] = CellDecision.Barrier
        }
        return result
    }

    fun elementUuidsUnderColliderBones(model: BlockbenchModel, prefix: String = "collider"): Set<String> {
        val collected = mutableSetOf<String>()
        for (group in model.groups) {
            collectUuids(group, prefix, collected, parentMatches = false)
        }
        return collected
    }

    fun stripColliders(model: BlockbenchModel, prefix: String = "collider"): BlockbenchModel {
        val colliderUuids = elementUuidsUnderColliderBones(model, prefix)
        val filteredElements = model.elements.filter { it.uuid !in colliderUuids }
        val filteredGroups = model.groups.mapNotNull { pruneColliderBones(it, prefix) }
        return model.copy(elements = filteredElements, groups = filteredGroups)
    }

    private fun pruneColliderBones(group: BbGroup, prefix: String): BbGroup? {
        if (group.name.startsWith(prefix, ignoreCase = true)) return null
        val keptChildren = group.children.mapNotNull { child ->
            when (child) {
                is BbGroupChild.ElementRef -> child
                is BbGroupChild.SubGroup -> pruneColliderBones(child.group, prefix)?.let { BbGroupChild.SubGroup(it) }
            }
        }
        return group.copy(children = keptChildren)
    }

    private fun collectMatching(
        group: BbGroup,
        prefix: String,
        elementsById: Map<String, BbElement>,
        out: MutableList<BbElement>,
        parentMatches: Boolean,
    ) {
        val selfMatches = parentMatches || group.name.startsWith(prefix, ignoreCase = true)
        for (child in group.children) {
            when (child) {
                is BbGroupChild.ElementRef -> {
                    if (selfMatches) {
                        elementsById[child.uuid]?.let(out::add)
                    }
                }
                is BbGroupChild.SubGroup -> collectMatching(child.group, prefix, elementsById, out, selfMatches)
            }
        }
    }

    private fun collectUuids(
        group: BbGroup,
        prefix: String,
        out: MutableSet<String>,
        parentMatches: Boolean,
    ) {
        val selfMatches = parentMatches || group.name.startsWith(prefix, ignoreCase = true)
        for (child in group.children) {
            when (child) {
                is BbGroupChild.ElementRef -> if (selfMatches) out += child.uuid
                is BbGroupChild.SubGroup -> collectUuids(child.group, prefix, out, selfMatches)
            }
        }
    }

    private data class RawEntry(
        val cell: FootprintCell,
        val mode: CellCollisionMode,
        val aabb: CubeAabb,
    )

    private fun collectClassified(
        group: BbGroup,
        prefix: String,
        elementsById: Map<String, BbElement>,
        out: MutableList<RawEntry>,
        parentMode: CellCollisionMode?,
    ) {
        val selfMode = if (parentMode != null) {
            parentMode
        } else if (group.name.startsWith(prefix, ignoreCase = true)) {
            classifyBoneName(group.name)
        } else {
            null
        }
        for (child in group.children) {
            when (child) {
                is BbGroupChild.ElementRef -> {
                    if (selfMode != null) {
                        val element = elementsById[child.uuid] ?: continue
                        val aabb = toAabb(element)
                        for (cell in aabb.cellsTouched()) {
                            val clipped = aabb.clipToCell(cell) ?: continue
                            out += RawEntry(cell, selfMode, clipped)
                        }
                    }
                }
                is BbGroupChild.SubGroup -> collectClassified(child.group, prefix, elementsById, out, selfMode)
            }
        }
    }

    internal fun classifyBoneName(name: String): CellCollisionMode {
        val tokens = name.lowercase().split('_')
        return when {
            tokens.getOrNull(1) == "soft" -> CellCollisionMode.Soft
            tokens.getOrNull(1) == "solid" -> CellCollisionMode.Solid
            else -> CellCollisionMode.Solid
        }
    }

    private fun toAabb(element: BbElement): CubeAabb {
        val fromX = minOf(element.from.x(), element.to.x())
        val toX = maxOf(element.from.x(), element.to.x())
        val fromY = minOf(element.from.y(), element.to.y())
        val toY = maxOf(element.from.y(), element.to.y())
        val fromZ = minOf(element.from.z(), element.to.z())
        val toZ = maxOf(element.from.z(), element.to.z())
        return CubeAabb(fromX, fromY, fromZ, toX, toY, toZ)
    }

    private fun cellsFromElements(elements: List<BbElement>): List<FootprintCell> {
        val seen = LinkedHashSet<FootprintCell>()
        for (element in elements) {
            val fromX = minOf(element.from.x(), element.to.x())
            val toX = maxOf(element.from.x(), element.to.x())
            val fromY = minOf(element.from.y(), element.to.y())
            val toY = maxOf(element.from.y(), element.to.y())
            val fromZ = minOf(element.from.z(), element.to.z())
            val toZ = maxOf(element.from.z(), element.to.z())

            val minCellX = pixelToCell(fromX)
            val maxCellX = pixelToCell(toX - PIXEL_EDGE_EPSILON)
            val minCellY = pixelToCell(fromY)
            val maxCellY = pixelToCell(toY - PIXEL_EDGE_EPSILON)
            val minCellZ = pixelToCell(fromZ)
            val maxCellZ = pixelToCell(toZ - PIXEL_EDGE_EPSILON)

            for (cellX in minCellX..maxCellX) {
                for (cellY in minCellY..maxCellY) {
                    for (cellZ in minCellZ..maxCellZ) {
                        seen += FootprintCell(cellX, cellY, cellZ)
                    }
                }
            }
        }
        if (seen.none { it.dx == 0 && it.dy == 0 && it.dz == 0 }) {
            seen += FootprintCell(0, 0, 0)
        }
        return seen.toList()
    }

    private fun pixelToCell(pixel: Double): Int =
        kotlin.math.floor(pixel / PIXELS_PER_BLOCK).toInt()

    private const val PIXELS_PER_BLOCK = 16.0
    private const val PIXEL_EDGE_EPSILON = 0.0001
}
