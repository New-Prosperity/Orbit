package me.nebula.orbit.utils.gametest

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import kotlin.math.max
import kotlin.math.min

internal fun GameTestContext.fillBlocks(from: Point, to: Point, block: Block) {
    val minX = min(from.blockX(), to.blockX())
    val minY = min(from.blockY(), to.blockY())
    val minZ = min(from.blockZ(), to.blockZ())
    val maxX = max(from.blockX(), to.blockX())
    val maxY = max(from.blockY(), to.blockY())
    val maxZ = max(from.blockZ(), to.blockZ())
    for (x in minX..maxX) {
        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                instance.setBlock(x, y, z, block)
            }
        }
    }
}

internal fun GameTestContext.setBlock(pos: Point, block: Block) {
    instance.setBlock(pos, block)
}

internal fun GameTestContext.platform(centerX: Int, centerZ: Int, radius: Int, y: Int, block: Block = Block.STONE) {
    for (x in (centerX - radius)..(centerX + radius)) {
        for (z in (centerZ - radius)..(centerZ + radius)) {
            instance.setBlock(x, y, z, block)
        }
    }
}

internal fun GameTestContext.arena(
    center: Point,
    radiusX: Int,
    radiusZ: Int,
    height: Int,
    wallBlock: Block = Block.BARRIER,
    floorBlock: Block = Block.STONE,
) {
    val cx = center.blockX()
    val cy = center.blockY()
    val cz = center.blockZ()
    for (x in (cx - radiusX)..(cx + radiusX)) {
        for (z in (cz - radiusZ)..(cz + radiusZ)) {
            instance.setBlock(x, cy, z, floorBlock)
        }
    }
    for (dy in 1..height) {
        for (x in (cx - radiusX)..(cx + radiusX)) {
            instance.setBlock(x, cy + dy, cz - radiusZ, wallBlock)
            instance.setBlock(x, cy + dy, cz + radiusZ, wallBlock)
        }
        for (z in (cz - radiusZ)..(cz + radiusZ)) {
            instance.setBlock(cx - radiusX, cy + dy, z, wallBlock)
            instance.setBlock(cx + radiusX, cy + dy, z, wallBlock)
        }
    }
}

internal fun GameTestContext.spawnItem(pos: Point, item: ItemStack): Entity {
    val entity = ItemEntity(item)
    entity.setInstance(instance, Pos(pos.x(), pos.y(), pos.z()))
    return entity
}

internal fun GameTestContext.spawnEntity(type: EntityType, pos: Point): Entity {
    val entity = Entity(type)
    entity.setInstance(instance, Pos(pos.x(), pos.y(), pos.z()))
    return entity
}

internal fun GameTestContext.placeChest(pos: Point, items: Map<Int, ItemStack>) {
    instance.setBlock(pos, Block.CHEST)
    val inventory = Inventory(InventoryType.CHEST_3_ROW, "Chest")
    for ((slot, stack) in items) {
        inventory.setItemStack(slot, stack)
    }
}

internal fun GameTestContext.clearEntities() {
    for (entity in instance.entities.toList()) {
        if (entity is Player) continue
        entity.remove()
    }
}

internal fun GameTestContext.blockAt(pos: Point): Block = instance.getBlock(pos)
