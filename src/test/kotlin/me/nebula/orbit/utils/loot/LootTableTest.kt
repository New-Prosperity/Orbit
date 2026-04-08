package me.nebula.orbit.utils.loot

import net.minestom.server.MinecraftServer
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LootTableTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            try {
                MinecraftServer.init()
            } catch (_: Throwable) {
            }
        }
    }

    @Test
    fun `empty loot table returns empty list`() {
        val table = LootTable("empty", emptyList())
        assertEquals(emptyList(), table.roll())
    }

    @Test
    fun `empty loot table single roll returns null`() {
        val table = LootTable("empty", emptyList())
        assertNull(table.rollSingle())
    }

    @Test
    fun `single entry table always returns that entry`() {
        val item = ItemStack.of(Material.DIAMOND)
        val table = LootTable("single", listOf(LootEntry(item, weight = 1)))
        repeat(20) {
            val result = table.rollSingle()
            assertNotNull(result)
            assertEquals(Material.DIAMOND, result.material())
        }
    }

    @Test
    fun `loot entry roll respects min and max count`() {
        val entry = LootEntry(ItemStack.of(Material.IRON_INGOT), minCount = 3, maxCount = 7)
        repeat(50) {
            val rolled = entry.roll()
            assertTrue(rolled.amount() in 3..7)
        }
    }

    @Test
    fun `loot entry fixed count when min equals max`() {
        val entry = LootEntry(ItemStack.of(Material.GOLD_INGOT), minCount = 5, maxCount = 5)
        repeat(20) {
            assertEquals(5, entry.roll().amount())
        }
    }

    @Test
    fun `loot table with rolls range produces multiple items`() {
        val table = LootTable(
            "multi",
            listOf(LootEntry(ItemStack.of(Material.STONE), weight = 1)),
            rolls = 3..3,
        )
        val results = table.roll()
        assertEquals(3, results.size)
    }

    @Test
    fun `weighted distribution favors higher-weight entries`() {
        val table = LootTable(
            "weighted",
            listOf(
                LootEntry(ItemStack.of(Material.DIAMOND), weight = 1),
                LootEntry(ItemStack.of(Material.STONE), weight = 99),
            ),
        )
        var stoneCount = 0
        repeat(1000) {
            if (table.rollSingle()?.material() == Material.STONE) stoneCount++
        }
        assertTrue(stoneCount > 900, "Expected stone to dominate, got $stoneCount/1000")
    }

    @Test
    fun `loot table builder DSL works`() {
        val table = lootTable("test") {
            entry(Material.DIAMOND, weight = 1)
            entry(Material.IRON_INGOT, weight = 2, minCount = 1, maxCount = 3)
            rolls(2)
        }
        assertEquals(2, table.roll().size)
    }
}
