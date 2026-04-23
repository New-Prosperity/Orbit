package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.logging.logger
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object FurnitureLightingController {

    private val logger = logger("FurnitureLightingController")
    private data class LightCell(val x: Int, val y: Int, val z: Int)
    private val activeCells = ConcurrentHashMap<UUID, List<LightCell>>()

    private val lightBlockTemplate: Block? by lazy {
        try { Block.fromKey("minecraft:light") } catch (_: Throwable) { null }
    }

    fun onPlaced(instance: Instance, furniture: FurnitureInstance, definition: FurnitureDefinition) {
        val level = effectiveLevel(furniture, definition)
        if (level <= 0) return
        apply(instance, furniture, definition, level)
    }

    fun onToggled(instance: Instance, furniture: FurnitureInstance, definition: FurnitureDefinition) {
        clear(instance, furniture)
        val level = effectiveLevel(furniture, definition)
        if (level > 0) apply(instance, furniture, definition, level)
    }

    fun onBroken(instance: Instance, furniture: FurnitureInstance) {
        clear(instance, furniture)
    }

    fun clearAll() { activeCells.clear() }

    private fun effectiveLevel(furniture: FurnitureInstance, definition: FurnitureDefinition): Int {
        if (definition.lightLevel <= 0) return 0
        if (!definition.lightOnlyWhenOpen) return definition.lightLevel
        return if (FurnitureInstanceState.isOpen(furniture.uuid)) definition.lightLevel else 0
    }

    private fun apply(instance: Instance, furniture: FurnitureInstance, definition: FurnitureDefinition, level: Int) {
        val template = lightBlockTemplate ?: run {
            logger.warn { "minecraft:light block not available; dynamic lighting disabled" }
            return
        }
        val lightBlock = template.withProperty("level", level.coerceIn(1, 15).toString())
        val topY = furniture.cellKeys.maxOfOrNull { FurnitureInstance.unpackKey(it).second } ?: furniture.anchorY
        val lightY = topY + 1
        val candidates = listOf(
            LightCell(furniture.anchorX, lightY, furniture.anchorZ),
        )
        val placed = mutableListOf<LightCell>()
        for (cell in candidates) {
            val existing = instance.getBlock(cell.x, cell.y, cell.z)
            if (!existing.isAir) continue
            runCatching { instance.setBlock(cell.x, cell.y, cell.z, lightBlock) }
                .onSuccess { placed += cell }
                .onFailure { e -> logger.warn { "Failed to place furniture light at (${cell.x},${cell.y},${cell.z}): ${e.message}" } }
        }
        if (placed.isNotEmpty()) activeCells[furniture.uuid] = placed
    }

    private fun clear(instance: Instance, furniture: FurnitureInstance) {
        val cells = activeCells.remove(furniture.uuid) ?: return
        for (cell in cells) {
            val current = instance.getBlock(cell.x, cell.y, cell.z)
            if (current.name() == "minecraft:light") instance.setBlock(cell.x, cell.y, cell.z, Block.AIR)
        }
    }
}
