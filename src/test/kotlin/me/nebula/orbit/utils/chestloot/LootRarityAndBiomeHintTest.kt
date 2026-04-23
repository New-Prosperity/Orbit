package me.nebula.orbit.utils.chestloot

import net.minestom.server.item.Material
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LootRarityAndBiomeHintTest {

    @AfterEach
    fun teardown() { ChestLootManager.clear() }

    @Test
    fun `inferFromName matches canonical tier names case-insensitively`() {
        assertEquals(LootRarity.COMMON, LootRarity.inferFromName("common"))
        assertEquals(LootRarity.UNCOMMON, LootRarity.inferFromName("Uncommon"))
        assertEquals(LootRarity.RARE, LootRarity.inferFromName("RARE"))
        assertEquals(LootRarity.EPIC, LootRarity.inferFromName("  epic  "))
        assertEquals(LootRarity.LEGENDARY, LootRarity.inferFromName("legendary"))
    }

    @Test
    fun `inferFromName falls back to COMMON for unknown names`() {
        assertEquals(LootRarity.COMMON, LootRarity.inferFromName("mythic"))
        assertEquals(LootRarity.COMMON, LootRarity.inferFromName(""))
    }

    @Test
    fun `LootTier rarity derives from name by default`() {
        val tier = LootTier("rare", emptyList(), totalWeight = 0)
        assertEquals(LootRarity.RARE, tier.rarity)
    }

    @Test
    fun `LootTier rarity can be explicitly overridden`() {
        val tier = LootTier("bronze_tier", emptyList(), totalWeight = 0, rarity = LootRarity.EPIC)
        assertEquals(LootRarity.EPIC, tier.rarity)
    }

    @Test
    fun `ChestLootBuilder distribution uses rarity defaultTierWeight`() {
        val builder = ChestLootBuilder("t")
        builder.tier("common") { item(Material.STONE) }
        builder.tier("legendary") { item(Material.NETHERITE_INGOT) }
        val table = builder.build()
        assertEquals(50, table.distribution.first { it.tierName == "common" }.weight)
        assertEquals(1, table.distribution.first { it.tierName == "legendary" }.weight)
    }

    @Test
    fun `ChestLootTable hasLegendary reflects tier rarities`() {
        val plain = ChestLootBuilder("t1").apply { tier("common") { item(Material.STONE) } }.build()
        val legendary = ChestLootBuilder("t2").apply {
            tier("common") { item(Material.STONE) }
            tier("legendary") { item(Material.NETHERITE_INGOT) }
        }.build()
        assertFalse(plain.hasLegendary())
        assertTrue(legendary.hasLegendary())
    }

    @Test
    fun `weightsFromRarity projects rarity weights onto tier names`() {
        val table = ChestLootBuilder("t").apply {
            tier("common") { item(Material.STONE) }
            tier("rare") { item(Material.DIAMOND) }
            tier("legendary") { item(Material.NETHERITE_INGOT) }
        }.build()
        val projected = table.weightsFromRarity(mapOf(
            LootRarity.COMMON to 10,
            LootRarity.LEGENDARY to 80,
        ))
        assertEquals(mapOf("common" to 10, "legendary" to 80), projected)
    }

    @Test
    fun `biomeHint by tier name is captured on the table`() {
        val table = ChestLootBuilder("t").apply {
            tier("common") { item(Material.STONE) }
            tier("rare") { item(Material.DIAMOND) }
            biomeHint("ice_spikes") {
                tier("common", 80)
                tier("rare", 20)
            }
        }.build()
        assertEquals(mapOf("common" to 80, "rare" to 20), table.biomeHints["ice_spikes"])
    }

    @Test
    fun `biomeHintByRarity maps rarities onto defined tier names`() {
        val table = ChestLootBuilder("t").apply {
            tier("common") { item(Material.STONE) }
            tier("rare") { item(Material.DIAMOND) }
            tier("legendary") { item(Material.NETHERITE_INGOT) }
            biomeHintByRarity("badlands") {
                common(30)
                rare(50)
                legendary(20)
            }
        }.build()
        assertEquals(
            mapOf("common" to 30, "rare" to 50, "legendary" to 20),
            table.biomeHints["badlands"],
        )
    }

    @Test
    fun `biomeHintByRarity skips rarities whose tier is not present in the table`() {
        val table = ChestLootBuilder("t").apply {
            tier("common") { item(Material.STONE) }
            biomeHintByRarity("swamp") {
                common(60)
                rare(30)
                legendary(10)
            }
        }.build()
        assertEquals(mapOf("common" to 60), table.biomeHints["swamp"])
    }

    @Test
    fun `ChestLootManager legendary tracking marks filled chests with legendary tier present`() {
        val table = ChestLootBuilder("legend").apply {
            tier("common") { item(Material.STONE) }
            tier("legendary") { item(Material.NETHERITE_INGOT) }
        }.build()
        ChestLootManager.register(table)
        ChestLootManager.fillChestAt(table, 10, 60, 10)
        assertTrue(ChestLootManager.isLegendaryChest(10, 60, 10))
        assertEquals(1, ChestLootManager.legendaryChestPositions().count())
    }

    @Test
    fun `ChestLootManager legendary tracking ignores tables without legendary tier`() {
        val table = ChestLootBuilder("ordinary").apply {
            tier("common") { item(Material.STONE) }
            tier("rare") { item(Material.DIAMOND) }
        }.build()
        ChestLootManager.register(table)
        ChestLootManager.fillChestAt(table, 0, 60, 0)
        assertFalse(ChestLootManager.isLegendaryChest(0, 60, 0))
    }

    @Test
    fun `biome lookup produces tier-weight overrides for matching biome`() {
        val table = ChestLootBuilder("biomed").apply {
            tier("common") { item(Material.STONE) }
            tier("rare") { item(Material.DIAMOND) }
            biomeHint("ice_spikes") {
                tier("common", 90)
                tier("rare", 10)
            }
        }.build()
        ChestLootManager.register(table)
        ChestLootManager.configureBiomeLookup { x, _ -> if (x > 0) "ice_spikes" else "plains" }
        ChestLootManager.fillChestAt(table, 5, 64, 0)
        ChestLootManager.fillChestAt(table, -5, 64, 0)
        ChestLootManager.clearBiomeLookup()
        assertEquals(2, ChestLootManager.all().size.let { 2 })
    }

    @Test
    fun `resetChests clears legendary tracking`() {
        val table = ChestLootBuilder("legend").apply {
            tier("common") { item(Material.STONE) }
            tier("legendary") { item(Material.NETHERITE_INGOT) }
        }.build()
        ChestLootManager.register(table)
        ChestLootManager.fillChestAt(table, 1, 1, 1)
        ChestLootManager.resetChests()
        assertFalse(ChestLootManager.isLegendaryChest(1, 1, 1))
    }
}
