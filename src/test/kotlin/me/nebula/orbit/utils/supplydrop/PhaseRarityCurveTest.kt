package me.nebula.orbit.utils.supplydrop

import me.nebula.orbit.utils.chestloot.LootRarity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhaseRarityCurveTest {

    @Test
    fun `phase 1 weights favor common and exclude legendary`() {
        val weights = PhaseRarityCurve.rarityWeights(1)
        assertNotNull(weights[LootRarity.COMMON])
        assertTrue(weights.getValue(LootRarity.COMMON) > weights.getValue(LootRarity.UNCOMMON))
        assertTrue(LootRarity.LEGENDARY !in weights)
    }

    @Test
    fun `phase 3 introduces legendary at low weight`() {
        val weights = PhaseRarityCurve.rarityWeights(3)
        assertEquals(1, weights[LootRarity.LEGENDARY])
    }

    @Test
    fun `late phases drop the COMMON tier entirely`() {
        val weights = PhaseRarityCurve.rarityWeights(6)
        assertTrue(LootRarity.COMMON !in weights)
        assertTrue((weights[LootRarity.LEGENDARY] ?: 0) >= (weights[LootRarity.COMMON] ?: 0))
    }

    @Test
    fun `overflow phases reuse the late-game curve`() {
        val weights = PhaseRarityCurve.rarityWeights(12)
        assertTrue(LootRarity.COMMON !in weights)
        assertTrue(LootRarity.LEGENDARY in weights)
    }

    @Test
    fun `headlineRarity returns the heaviest rarity of the phase`() {
        assertEquals(LootRarity.COMMON, PhaseRarityCurve.headlineRarity(1))
        assertEquals(LootRarity.RARE, PhaseRarityCurve.headlineRarity(4))
        assertEquals(LootRarity.LEGENDARY, PhaseRarityCurve.headlineRarity(6))
    }
}
