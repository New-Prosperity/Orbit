package me.nebula.orbit.utils.botai

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BotPersonalityTest {

    @Test
    fun `default personality has neutral 0_5 traits`() {
        val p = BotPersonality()
        assertEquals(0.5f, p.aggression)
        assertEquals(0.5f, p.caution)
        assertEquals(0.5f, p.resourcefulness)
        assertEquals(0.5f, p.curiosity)
        assertEquals(0.5f, p.teamwork)
    }

    @Test
    fun `WARRIOR has high aggression and low caution`() {
        val p = BotPersonalities.WARRIOR
        assertTrue(p.aggression > 0.8f)
        assertTrue(p.caution < 0.3f)
    }

    @Test
    fun `SURVIVOR is cautious and resourceful`() {
        val p = BotPersonalities.SURVIVOR
        assertTrue(p.caution > 0.7f)
        assertTrue(p.resourcefulness > 0.8f)
    }

    @Test
    fun `EXPLORER has high curiosity`() {
        val p = BotPersonalities.EXPLORER
        assertTrue(p.curiosity > 0.8f)
    }

    @Test
    fun `BERSERKER has maximum aggression and zero caution`() {
        val p = BotPersonalities.BERSERKER
        assertEquals(1.0f, p.aggression)
        assertEquals(0.0f, p.caution)
    }

    @Test
    fun `BUILDER has maximum resourcefulness`() {
        val p = BotPersonalities.BUILDER
        assertEquals(1.0f, p.resourcefulness)
    }

    @Test
    fun `BALANCED is the same as default`() {
        assertEquals(BotPersonality(), BotPersonalities.BALANCED)
    }

    @Test
    fun `random personality stays within 0_0 to 1_0 bounds`() {
        repeat(100) {
            val p = BotPersonalities.random()
            assertTrue(p.aggression in 0.0f..1.0f)
            assertTrue(p.caution in 0.0f..1.0f)
            assertTrue(p.resourcefulness in 0.0f..1.0f)
            assertTrue(p.curiosity in 0.0f..1.0f)
            assertTrue(p.teamwork in 0.0f..1.0f)
        }
    }

    @Test
    fun `random produces variation across calls`() {
        val first = BotPersonalities.random()
        val second = BotPersonalities.random()
        assertNotEquals(first, second)
    }

    @Test
    fun `data class equality works`() {
        val a = BotPersonality(aggression = 0.7f, caution = 0.3f)
        val b = BotPersonality(aggression = 0.7f, caution = 0.3f)
        assertEquals(a, b)
    }

    @Test
    fun `data class copy works`() {
        val warrior = BotPersonalities.WARRIOR
        val pacifist = warrior.copy(aggression = 0.0f)
        assertEquals(0.0f, pacifist.aggression)
        assertEquals(warrior.caution, pacifist.caution)
    }
}
