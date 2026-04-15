package me.nebula.orbit.variant

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameVariantPoolTest {

    private fun variant(id: String, random: Boolean = true, weight: Int = 1): GameVariant =
        GameVariant(
            id = id,
            nameKey = me.nebula.ether.utils.translation.TranslationKey(""),
            descriptionKey = me.nebula.ether.utils.translation.TranslationKey(""),
            components = emptyList(),
            random = random,
            weight = weight,
        )

    @Test
    fun `selectRandom returns null when pool empty`() {
        val pool = GameVariantPool(emptyList())
        assertNull(pool.selectRandom())
    }

    @Test
    fun `selectRandom returns null when all variants non-random`() {
        val pool = GameVariantPool(listOf(variant("a", random = false), variant("b", random = false)))
        assertNull(pool.selectRandom())
    }

    @Test
    fun `selectRandom picks from random=true pool only`() {
        val pool = GameVariantPool(listOf(
            variant("a", random = true),
            variant("b", random = false),
            variant("c", random = true),
        ))
        val picked = mutableSetOf<String>()
        repeat(50) { pool.selectRandom()?.let { picked += it.id } }
        assertTrue("a" in picked || "c" in picked)
        assertTrue("b" !in picked)
    }

    @Test
    fun `selectRandom respects weights approximately`() {
        val pool = GameVariantPool(listOf(
            variant("rare", weight = 1),
            variant("common", weight = 9),
        ))
        var commonCount = 0
        val runs = 400
        repeat(runs) { if (pool.selectRandom()?.id == "common") commonCount++ }
        assertTrue(commonCount > runs * 0.75, "weighted selection skewed toward common; got $commonCount / $runs")
    }

    @Test
    fun `resolve returns variant by id`() {
        val v = variant("x")
        val pool = GameVariantPool(listOf(v, variant("y")))
        assertEquals(v, pool.resolve("x"))
        assertNull(pool.resolve("missing"))
    }

    @Test
    fun `GameVariant find returns typed component`() {
        val v = GameVariant(
            id = "t",
            nameKey = me.nebula.ether.utils.translation.TranslationKey(""),
            descriptionKey = me.nebula.ether.utils.translation.TranslationKey(""),
            components = listOf(
                GameComponent.InitialRules(emptyMap()),
                GameComponent.MutatorFilter(forced = listOf("x")),
            ),
        )
        val filter = v.find<GameComponent.MutatorFilter>()
        assertNotNull(filter)
        assertEquals(listOf("x"), filter.forced)
    }

    @Test
    fun `deterministic random uses injected seed`() {
        val pool = GameVariantPool(listOf(variant("a"), variant("b"), variant("c")))
        val picks = (1..10).map { pool.selectRandom(Random(42))?.id }
        assertTrue(picks.all { it != null })
    }
}
