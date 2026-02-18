package me.nebula.orbit.utils.chestloot

import me.nebula.orbit.utils.region.Region
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class LootItem(
    val material: Material,
    val amountRange: IntRange,
    val weight: Int,
)

class LootTier @PublishedApi internal constructor(
    val name: String,
    val items: List<LootItem>,
    val totalWeight: Int,
) {

    fun rollItem(): ItemStack {
        if (items.isEmpty()) return ItemStack.AIR
        var remaining = Random.nextInt(totalWeight)
        for (item in items) {
            remaining -= item.weight
            if (remaining < 0) {
                val amount = if (item.amountRange.first == item.amountRange.last) {
                    item.amountRange.first
                } else {
                    Random.nextInt(item.amountRange.first, item.amountRange.last + 1)
                }
                return ItemStack.of(item.material, amount)
            }
        }
        val last = items.last()
        return ItemStack.of(last.material, Random.nextInt(last.amountRange.first, last.amountRange.last + 1))
    }
}

class LootTierBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal val items = mutableListOf<LootItem>()

    fun item(material: Material, amount: IntRange = 1..1, weight: Int = 1) {
        require(weight > 0) { "Weight must be positive" }
        items.add(LootItem(material, amount, weight))
    }

    fun item(material: Material, amount: Int, weight: Int = 1) {
        item(material, amount..amount, weight)
    }

    @PublishedApi internal fun build(): LootTier = LootTier(name, items.toList(), items.sumOf { it.weight })
}

data class TierDistribution(
    val tierName: String,
    val weight: Int,
)

data class ChestLootTable(
    val name: String,
    val tiers: Map<String, LootTier>,
    val distribution: List<TierDistribution>,
    val itemsPerChest: IntRange,
) {

    private val totalDistWeight = distribution.sumOf { it.weight }

    fun generateItems(): List<ItemStack> {
        if (tiers.isEmpty()) return emptyList()
        val count = if (itemsPerChest.first == itemsPerChest.last) {
            itemsPerChest.first
        } else {
            Random.nextInt(itemsPerChest.first, itemsPerChest.last + 1)
        }
        return (1..count).mapNotNull {
            val tier = selectTier() ?: return@mapNotNull null
            tier.rollItem()
        }.filter { it != ItemStack.AIR }
    }

    private fun selectTier(): LootTier? {
        if (distribution.isEmpty()) return tiers.values.firstOrNull()
        var remaining = Random.nextInt(totalDistWeight)
        for (dist in distribution) {
            remaining -= dist.weight
            if (remaining < 0) return tiers[dist.tierName]
        }
        return tiers[distribution.last().tierName]
    }
}

object ChestLootManager {

    private val tables = ConcurrentHashMap<String, ChestLootTable>()
    private val populatedChests = ConcurrentHashMap<Long, Inventory>()

    fun register(table: ChestLootTable) {
        require(!tables.containsKey(table.name)) { "Loot table '${table.name}' already exists" }
        tables[table.name] = table
    }

    fun unregister(name: String) {
        tables.remove(name)
    }

    operator fun get(name: String): ChestLootTable? = tables[name]
    fun require(name: String): ChestLootTable = requireNotNull(tables[name]) { "Loot table '$name' not found" }
    fun all(): Map<String, ChestLootTable> = tables.toMap()

    fun populateChest(table: ChestLootTable, inventory: Inventory) {
        val items = table.generateItems()
        val slots = (0 until inventory.size).shuffled().take(items.size)
        items.forEachIndexed { index, item ->
            if (index < slots.size) {
                inventory.setItemStack(slots[index], item)
            }
        }
    }

    fun fillChestsInRegion(table: ChestLootTable, region: Region, instance: Instance) {
        val minX = when (region) {
            is me.nebula.orbit.utils.region.CuboidRegion -> region.min.blockX()
            is me.nebula.orbit.utils.region.SphereRegion -> (region.center.blockX() - region.radius).toInt()
            is me.nebula.orbit.utils.region.CylinderRegion -> (region.center.blockX() - region.radius).toInt()
        }
        val maxX = when (region) {
            is me.nebula.orbit.utils.region.CuboidRegion -> region.max.blockX()
            is me.nebula.orbit.utils.region.SphereRegion -> (region.center.blockX() + region.radius).toInt()
            is me.nebula.orbit.utils.region.CylinderRegion -> (region.center.blockX() + region.radius).toInt()
        }
        val minY = when (region) {
            is me.nebula.orbit.utils.region.CuboidRegion -> region.min.blockY()
            is me.nebula.orbit.utils.region.SphereRegion -> (region.center.blockY() - region.radius).toInt()
            is me.nebula.orbit.utils.region.CylinderRegion -> region.center.blockY()
        }
        val maxY = when (region) {
            is me.nebula.orbit.utils.region.CuboidRegion -> region.max.blockY()
            is me.nebula.orbit.utils.region.SphereRegion -> (region.center.blockY() + region.radius).toInt()
            is me.nebula.orbit.utils.region.CylinderRegion -> (region.center.blockY() + region.height).toInt()
        }
        val minZ = when (region) {
            is me.nebula.orbit.utils.region.CuboidRegion -> region.min.blockZ()
            is me.nebula.orbit.utils.region.SphereRegion -> (region.center.blockZ() - region.radius).toInt()
            is me.nebula.orbit.utils.region.CylinderRegion -> (region.center.blockZ() - region.radius).toInt()
        }
        val maxZ = when (region) {
            is me.nebula.orbit.utils.region.CuboidRegion -> region.max.blockZ()
            is me.nebula.orbit.utils.region.SphereRegion -> (region.center.blockZ() + region.radius).toInt()
            is me.nebula.orbit.utils.region.CylinderRegion -> (region.center.blockZ() + region.radius).toInt()
        }

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val block = instance.getBlock(x, y, z)
                    if (block.compare(Block.CHEST) || block.compare(Block.TRAPPED_CHEST) || block.compare(Block.BARREL)) {
                        val key = packPosition(x, y, z)
                        val inventory = Inventory(InventoryType.CHEST_3_ROW, "Loot")
                        populateChest(table, inventory)
                        populatedChests[key] = inventory
                    }
                }
            }
        }
    }

    fun resetChests() {
        populatedChests.clear()
    }

    fun getChestInventory(x: Int, y: Int, z: Int): Inventory? =
        populatedChests[packPosition(x, y, z)]

    fun clear() {
        tables.clear()
        populatedChests.clear()
    }

    private fun packPosition(x: Int, y: Int, z: Int): Long =
        (x.toLong() and 0x3FFFFFF shl 38) or (z.toLong() and 0x3FFFFFF shl 12) or (y.toLong() and 0xFFF)
}

class ChestLootBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal val tiers = mutableMapOf<String, LootTier>()
    @PublishedApi internal val distribution = mutableListOf<TierDistribution>()
    @PublishedApi internal var itemsPerChest: IntRange = 3..7

    inline fun tier(name: String, block: LootTierBuilder.() -> Unit) {
        val tier = LootTierBuilder(name).apply(block).build()
        tiers[name] = tier
        if (distribution.none { it.tierName == name }) {
            val defaultWeight = when (name.lowercase()) {
                "common" -> 50
                "uncommon" -> 30
                "rare" -> 15
                "epic" -> 5
                else -> 10
            }
            distribution.add(TierDistribution(name, defaultWeight))
        }
    }

    fun distribution(tierName: String, weight: Int) {
        distribution.removeIf { it.tierName == tierName }
        distribution.add(TierDistribution(tierName, weight))
    }

    fun itemsPerChest(range: IntRange) { itemsPerChest = range }
    fun itemsPerChest(count: Int) { itemsPerChest = count..count }

    fun fillChestsInRegion(region: Region, instance: Instance) {
        val table = build()
        ChestLootManager.register(table)
        ChestLootManager.fillChestsInRegion(table, region, instance)
    }

    @PublishedApi internal fun build(): ChestLootTable = ChestLootTable(
        name = name,
        tiers = tiers.toMap(),
        distribution = distribution.toList(),
        itemsPerChest = itemsPerChest,
    )
}

inline fun chestLoot(name: String, block: ChestLootBuilder.() -> Unit): ChestLootTable {
    val table = ChestLootBuilder(name).apply(block).build()
    ChestLootManager.register(table)
    return table
}
