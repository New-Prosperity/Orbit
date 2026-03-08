package me.nebula.orbit.utils.chestloot

import me.nebula.orbit.utils.itemresolver.ItemResolver
import me.nebula.orbit.utils.region.*
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

enum class LootMode { GLOBAL, PER_PLAYER }

data class LootItem(
    val baseItem: ItemStack,
    val amountRange: IntRange,
    val weight: Int,
    val maxPerChest: Int = Int.MAX_VALUE,
)

data class LootZone(
    val name: String,
    val region: Region,
    val tierWeights: Map<String, Int>,
)

class LootTier @PublishedApi internal constructor(
    val name: String,
    val items: List<LootItem>,
    val totalWeight: Int,
) {

    fun rollItem(): ItemStack {
        val entry = rollEntry() ?: return ItemStack.AIR
        return entry.roll()
    }

    fun rollEntry(itemCounts: Map<ItemStack, Int> = emptyMap()): LootItem? {
        val available = items.filter { item ->
            val key = item.baseItem.withAmount(1)
            (itemCounts[key] ?: 0) < item.maxPerChest
        }
        if (available.isEmpty()) return null
        val availableWeight = available.sumOf { it.weight }
        var remaining = Random.nextInt(availableWeight)
        for (item in available) {
            remaining -= item.weight
            if (remaining < 0) return item
        }
        return available.last()
    }

    private fun LootItem.roll(): ItemStack {
        val amount = if (amountRange.first == amountRange.last) amountRange.first
        else Random.nextInt(amountRange.first, amountRange.last + 1)
        return baseItem.withAmount(amount)
    }
}

class LootTierBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal val items = mutableListOf<LootItem>()

    fun item(material: Material, amount: IntRange = 1..1, weight: Int = 1, maxPerChest: Int = Int.MAX_VALUE) {
        require(weight > 0) { "Weight must be positive" }
        items += LootItem(ItemStack.of(material), amount, weight, maxPerChest)
    }

    fun item(material: Material, amount: Int, weight: Int = 1, maxPerChest: Int = Int.MAX_VALUE) {
        item(material, amount..amount, weight, maxPerChest)
    }

    fun item(stack: ItemStack, amount: IntRange = 1..1, weight: Int = 1, maxPerChest: Int = Int.MAX_VALUE) {
        require(weight > 0) { "Weight must be positive" }
        items += LootItem(stack, amount, weight, maxPerChest)
    }

    fun item(key: String, amount: IntRange = 1..1, weight: Int = 1, maxPerChest: Int = Int.MAX_VALUE) {
        require(weight > 0) { "Weight must be positive" }
        items += LootItem(ItemResolver.resolve(key), amount, weight, maxPerChest)
    }

    fun item(key: String, amount: Int, weight: Int = 1, maxPerChest: Int = Int.MAX_VALUE) {
        item(key, amount..amount, weight, maxPerChest)
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

    fun generateItems(tierWeightOverrides: Map<String, Int>? = null): List<ItemStack> {
        if (tiers.isEmpty()) return emptyList()
        val count = if (itemsPerChest.first == itemsPerChest.last) {
            itemsPerChest.first
        } else {
            Random.nextInt(itemsPerChest.first, itemsPerChest.last + 1)
        }

        val effectiveDist = tierWeightOverrides?.map { (tier, weight) -> TierDistribution(tier, weight) }
            ?: distribution
        val effectiveTotalWeight = effectiveDist.sumOf { it.weight }

        val itemCounts = mutableMapOf<ItemStack, Int>()
        val result = mutableListOf<ItemStack>()

        repeat(count) {
            val tier = selectTier(effectiveDist, effectiveTotalWeight) ?: return@repeat
            val entry = tier.rollEntry(itemCounts) ?: return@repeat
            val key = entry.baseItem.withAmount(1)
            itemCounts[key] = (itemCounts[key] ?: 0) + 1
            result += entry.baseItem.withAmount(rollAmount(entry))
        }

        return result.filter { it != ItemStack.AIR }
    }

    private fun selectTier(dist: List<TierDistribution>, totalWeight: Int): LootTier? {
        if (dist.isEmpty()) return tiers.values.firstOrNull()
        var remaining = Random.nextInt(totalWeight)
        for (d in dist) {
            remaining -= d.weight
            if (remaining < 0) return tiers[d.tierName]
        }
        return tiers[dist.last().tierName]
    }

    private fun rollAmount(item: LootItem): Int =
        if (item.amountRange.first == item.amountRange.last) item.amountRange.first
        else Random.nextInt(item.amountRange.first, item.amountRange.last + 1)
}

object ChestLootManager {

    var mode: LootMode = LootMode.GLOBAL

    private val tables = ConcurrentHashMap<String, ChestLootTable>()
    private val globalChests = ConcurrentHashMap<Long, Inventory>()
    private val chestTables = ConcurrentHashMap<Long, ChestLootTable>()
    private val playerChests = ConcurrentHashMap<Long, ConcurrentHashMap<UUID, Inventory>>()
    private val zones = mutableListOf<LootZone>()
    private val chestZoneWeights = ConcurrentHashMap<Long, Map<String, Int>>()

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

    fun registerZone(zone: LootZone) {
        zones += zone
    }

    fun clearZones() {
        zones.clear()
        chestZoneWeights.clear()
    }

    fun populateChest(table: ChestLootTable, inventory: Inventory, tierWeightOverrides: Map<String, Int>? = null) {
        val items = table.generateItems(tierWeightOverrides)
        val slots = (0 until inventory.size).shuffled().take(items.size)
        items.forEachIndexed { index, item ->
            if (index < slots.size) {
                inventory.setItemStack(slots[index], item)
            }
        }
    }

    fun fillChestsInRegion(table: ChestLootTable, region: Region, instance: Instance) {
        val minX = when (region) {
            is CuboidRegion -> region.min.blockX()
            is SphereRegion -> (region.center.blockX() - region.radius).toInt()
            is CylinderRegion -> (region.center.blockX() - region.radius).toInt()
        }
        val maxX = when (region) {
            is CuboidRegion -> region.max.blockX()
            is SphereRegion -> (region.center.blockX() + region.radius).toInt()
            is CylinderRegion -> (region.center.blockX() + region.radius).toInt()
        }
        val minY = when (region) {
            is CuboidRegion -> region.min.blockY()
            is SphereRegion -> (region.center.blockY() - region.radius).toInt()
            is CylinderRegion -> region.center.blockY()
        }
        val maxY = when (region) {
            is CuboidRegion -> region.max.blockY()
            is SphereRegion -> (region.center.blockY() + region.radius).toInt()
            is CylinderRegion -> (region.center.blockY() + region.height).toInt()
        }
        val minZ = when (region) {
            is CuboidRegion -> region.min.blockZ()
            is SphereRegion -> (region.center.blockZ() - region.radius).toInt()
            is CylinderRegion -> (region.center.blockZ() - region.radius).toInt()
        }
        val maxZ = when (region) {
            is CuboidRegion -> region.max.blockZ()
            is SphereRegion -> (region.center.blockZ() + region.radius).toInt()
            is CylinderRegion -> (region.center.blockZ() + region.radius).toInt()
        }

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val block = instance.getBlock(x, y, z)
                    if (block.compare(Block.CHEST) || block.compare(Block.TRAPPED_CHEST) || block.compare(Block.BARREL)) {
                        fillChestAt(table, x, y, z)
                    }
                }
            }
        }
    }

    fun fillChestAt(table: ChestLootTable, x: Int, y: Int, z: Int) {
        val key = packPosition(x, y, z)
        chestTables[key] = table
        val zoneWeights = findZoneWeights(x, y, z)
        if (zoneWeights != null) chestZoneWeights[key] = zoneWeights
        if (mode == LootMode.GLOBAL) {
            val inventory = Inventory(InventoryType.CHEST_3_ROW, "Loot")
            populateChest(table, inventory, zoneWeights)
            globalChests[key] = inventory
        }
    }

    fun getChestInventory(x: Int, y: Int, z: Int): Inventory? {
        val key = packPosition(x, y, z)
        return globalChests[key]
    }

    fun getChestInventory(x: Int, y: Int, z: Int, playerId: UUID): Inventory? {
        val key = packPosition(x, y, z)
        return when (mode) {
            LootMode.GLOBAL -> globalChests[key]
            LootMode.PER_PLAYER -> {
                val table = chestTables[key] ?: return null
                val zoneWeights = chestZoneWeights[key]
                playerChests.computeIfAbsent(key) { ConcurrentHashMap() }
                    .computeIfAbsent(playerId) {
                        Inventory(InventoryType.CHEST_3_ROW, "Loot").also { populateChest(table, it, zoneWeights) }
                    }
            }
        }
    }

    fun resetChests() {
        globalChests.clear()
        playerChests.clear()
        chestZoneWeights.clear()
    }

    fun clear() {
        tables.clear()
        globalChests.clear()
        chestTables.clear()
        playerChests.clear()
        zones.clear()
        chestZoneWeights.clear()
    }

    private fun findZoneWeights(x: Int, y: Int, z: Int): Map<String, Int>? =
        zones.firstOrNull { it.region.contains(x.toDouble(), y.toDouble(), z.toDouble()) }?.tierWeights

    private fun packPosition(x: Int, y: Int, z: Int): Long =
        (x.toLong() and 0x3FFFFFF shl 38) or (z.toLong() and 0x3FFFFFF shl 12) or (y.toLong() and 0xFFF)
}

class LootZoneBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var region: Region? = null
    @PublishedApi internal val tierWeights = mutableMapOf<String, Int>()

    fun sphere(center: Pos, radius: Double) {
        region = SphereRegion(name, center, radius)
    }

    fun cylinder(center: Pos, radius: Double, height: Double = 384.0) {
        region = CylinderRegion(name, center, radius, height)
    }

    fun cuboid(min: Pos, max: Pos) {
        region = cuboidRegion(name, min, max)
    }

    fun tier(tierName: String, weight: Int) {
        tierWeights[tierName] = weight
    }

    @PublishedApi internal fun build(): LootZone {
        val r = requireNotNull(region) { "LootZone '$name' must define a region" }
        require(tierWeights.isNotEmpty()) { "LootZone '$name' must define at least one tier weight" }
        return LootZone(name, r, tierWeights.toMap())
    }
}

inline fun lootZone(name: String, block: LootZoneBuilder.() -> Unit): LootZone =
    LootZoneBuilder(name).apply(block).build()

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
