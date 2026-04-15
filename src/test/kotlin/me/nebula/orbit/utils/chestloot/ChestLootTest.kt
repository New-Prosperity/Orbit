package me.nebula.orbit.utils.chestloot

import net.minestom.server.MinecraftServer
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ChestLootTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootServer() {
            if (MinecraftServer.process() == null) MinecraftServer.init()
        }
    }

    @AfterEach
    fun reset() {
        ChestLootManager.clear()
        ChestLootManager.mode = LootMode.GLOBAL
    }

    private fun simpleTable(name: String = "test", itemsPerChest: IntRange = 3..3): ChestLootTable {
        val tier = LootTier(
            name = "common",
            items = listOf(
                LootItem(ItemStack.of(Material.STONE), 1..1, weight = 1),
                LootItem(ItemStack.of(Material.DIRT), 1..1, weight = 1),
            ),
            totalWeight = 2,
        )
        return ChestLootTable(
            name = name,
            tiers = mapOf("common" to tier),
            distribution = listOf(TierDistribution("common", 100)),
            itemsPerChest = itemsPerChest,
        )
    }

    @Test
    fun `generateItems returns the configured count`() {
        val table = simpleTable(itemsPerChest = 5..5)
        val items = table.generateItems()
        assertEquals(5, items.size)
    }

    @Test
    fun `generateItems uses random count within range`() {
        val table = simpleTable(itemsPerChest = 2..6)
        val sizes = (1..50).map { table.generateItems().size }
        assertTrue(sizes.all { it in 2..6 }, "all sizes should fall in [2,6], got $sizes")
        assertTrue(sizes.distinct().size > 1, "expected variation, got $sizes")
    }

    @Test
    fun `generateItems with empty tiers returns empty`() {
        val table = ChestLootTable("empty", emptyMap(), emptyList(), 5..5)
        assertEquals(emptyList(), table.generateItems())
    }

    @Test
    fun `tier weight overrides change selection distribution`() {
        val rareTier = LootTier(
            "rare",
            listOf(LootItem(ItemStack.of(Material.DIAMOND), 1..1, weight = 1)),
            totalWeight = 1,
        )
        val commonTier = LootTier(
            "common",
            listOf(LootItem(ItemStack.of(Material.STONE), 1..1, weight = 1)),
            totalWeight = 1,
        )
        val table = ChestLootTable(
            name = "t",
            tiers = mapOf("rare" to rareTier, "common" to commonTier),
            distribution = listOf(
                TierDistribution("common", 99),
                TierDistribution("rare", 1),
            ),
            itemsPerChest = 1..1,
        )
        val withOverride = (1..200).flatMap {
            table.generateItems(mapOf("rare" to 99, "common" to 1))
        }
        val diamondShare = withOverride.count { it.material() == Material.DIAMOND }
        assertTrue(diamondShare > withOverride.size * 0.6, "override should make rare dominant: got $diamondShare / ${withOverride.size}")
    }

    @Test
    fun `LootItem maxPerChest caps occurrences`() {
        val tier = LootTier(
            "t",
            listOf(LootItem(ItemStack.of(Material.DIAMOND), 1..1, weight = 100, maxPerChest = 1)),
            totalWeight = 100,
        )
        val table = ChestLootTable(
            name = "t",
            tiers = mapOf("t" to tier),
            distribution = listOf(TierDistribution("t", 1)),
            itemsPerChest = 10..10,
        )
        val items = table.generateItems()
        val diamondCount = items.count { it.material() == Material.DIAMOND }
        assertTrue(diamondCount <= 1, "maxPerChest=1 violated: $diamondCount diamonds")
    }

    @Test
    fun `LootTier rollEntry returns null when all items capped`() {
        val tier = LootTier(
            "t",
            listOf(LootItem(ItemStack.of(Material.STONE), 1..1, weight = 1, maxPerChest = 0)),
            totalWeight = 1,
        )
        assertNull(tier.rollEntry())
    }

    @Test
    fun `LootTierBuilder builds tier with summed weights`() {
        val builder = LootTierBuilder("common")
        builder.item(Material.STONE, weight = 3)
        builder.item(Material.DIRT, weight = 7)
        val tier = builder.build()
        assertEquals(10, tier.totalWeight)
        assertEquals(2, tier.items.size)
    }

    @Test
    fun `LootTierBuilder rejects non-positive weight`() {
        val builder = LootTierBuilder("t")
        assertFailsWith<IllegalArgumentException> { builder.item(Material.STONE, weight = 0) }
        assertFailsWith<IllegalArgumentException> { builder.item(Material.STONE, weight = -1) }
    }

    @Test
    fun `ChestLootBuilder default tier weights match name conventions`() {
        val builder = ChestLootBuilder("loot")
        builder.tier("common") { item(Material.STONE) }
        builder.tier("rare") { item(Material.DIAMOND) }
        val table = builder.build()
        val byName = table.distribution.associateBy { it.tierName }
        assertEquals(50, byName.getValue("common").weight)
        assertEquals(15, byName.getValue("rare").weight)
    }

    @Test
    fun `ChestLootBuilder distribution call replaces existing entry`() {
        val builder = ChestLootBuilder("loot")
        builder.tier("common") { item(Material.STONE) }
        builder.distribution("common", 999)
        val table = builder.build()
        assertEquals(1, table.distribution.count { it.tierName == "common" })
        assertEquals(999, table.distribution.first().weight)
    }

    @Test
    fun `ChestLootManager register rejects duplicate names`() {
        val table = simpleTable("dup")
        ChestLootManager.register(table)
        assertFailsWith<IllegalArgumentException> { ChestLootManager.register(table) }
    }

    @Test
    fun `ChestLootManager get returns null for unknown table`() {
        assertNull(ChestLootManager["ghost"])
    }

    @Test
    fun `ChestLootManager require throws for unknown table`() {
        assertFailsWith<IllegalArgumentException> { ChestLootManager.require("ghost") }
    }

    @Test
    fun `ChestLootManager registerZone adds to zones list`() {
        val table = simpleTable()
        ChestLootManager.register(table)
        ChestLootManager.fillChestAt(table, 100, 64, 100)
        assertNotNull(ChestLootManager.getChestInventory(100, 64, 100))
    }

    @Test
    fun `fillChestAt populates global inventory in GLOBAL mode`() {
        ChestLootManager.mode = LootMode.GLOBAL
        val table = simpleTable("g")
        ChestLootManager.register(table)
        ChestLootManager.fillChestAt(table, 1, 2, 3)
        val inv = ChestLootManager.getChestInventory(1, 2, 3)
        assertNotNull(inv)
    }

    @Test
    fun `fillChestAt does not create global inventory in PER_PLAYER mode`() {
        ChestLootManager.mode = LootMode.PER_PLAYER
        val table = simpleTable("p")
        ChestLootManager.register(table)
        ChestLootManager.fillChestAt(table, 5, 5, 5)
        assertNull(ChestLootManager.getChestInventory(5, 5, 5))
    }

    @Test
    fun `getChestInventory PER_PLAYER returns distinct inventories per player`() {
        ChestLootManager.mode = LootMode.PER_PLAYER
        val table = simpleTable("p")
        ChestLootManager.register(table)
        ChestLootManager.fillChestAt(table, 0, 64, 0)
        val a = ChestLootManager.getChestInventory(0, 64, 0, java.util.UUID.randomUUID())
        val b = ChestLootManager.getChestInventory(0, 64, 0, java.util.UUID.randomUUID())
        assertNotNull(a)
        assertNotNull(b)
        assertTrue(a !== b, "different players should get distinct inventory instances")
    }

    @Test
    fun `getChestInventory PER_PLAYER returns same inventory for same player`() {
        ChestLootManager.mode = LootMode.PER_PLAYER
        val table = simpleTable("p")
        ChestLootManager.register(table)
        ChestLootManager.fillChestAt(table, 0, 64, 0)
        val playerId = java.util.UUID.randomUUID()
        val first = ChestLootManager.getChestInventory(0, 64, 0, playerId)
        val second = ChestLootManager.getChestInventory(0, 64, 0, playerId)
        assertTrue(first === second, "same player should get same inventory")
    }

    @Test
    fun `resetChests clears inventories but keeps tables`() {
        ChestLootManager.mode = LootMode.GLOBAL
        val table = simpleTable("r")
        ChestLootManager.register(table)
        ChestLootManager.fillChestAt(table, 0, 0, 0)
        assertNotNull(ChestLootManager.getChestInventory(0, 0, 0))
        ChestLootManager.resetChests()
        assertNull(ChestLootManager.getChestInventory(0, 0, 0))
        assertNotNull(ChestLootManager["r"])
    }

    @Test
    fun `clear removes everything`() {
        ChestLootManager.register(simpleTable("a"))
        ChestLootManager.register(simpleTable("b"))
        ChestLootManager.fillChestAt(ChestLootManager.require("a"), 1, 2, 3)
        ChestLootManager.clear()
        assertNull(ChestLootManager["a"])
        assertNull(ChestLootManager["b"])
        assertNull(ChestLootManager.getChestInventory(1, 2, 3))
    }

    @Test
    fun `position packing is unique per coordinate`() {
        val keys = mutableSetOf<Triple<Int, Int, Int>>()
        ChestLootManager.mode = LootMode.GLOBAL
        val table = simpleTable("p")
        ChestLootManager.register(table)
        for (x in 0..3) for (y in 0..3) for (z in 0..3) {
            ChestLootManager.fillChestAt(table, x, y, z)
            keys += Triple(x, y, z)
        }
        for ((x, y, z) in keys) {
            assertNotNull(ChestLootManager.getChestInventory(x, y, z), "missing inventory for ($x,$y,$z)")
        }
    }
}
