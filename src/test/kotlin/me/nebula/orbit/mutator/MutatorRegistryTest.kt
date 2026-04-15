package me.nebula.orbit.mutator

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutatorRegistryTest {

    @AfterEach
    fun reset() {
        clearRegistry()
    }

    private fun clearRegistry() {
        val field = MutatorRegistry::class.java.getDeclaredField("registry")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(MutatorRegistry) as MutableMap<String, Mutator>).clear()
    }

    private fun mutator(
        id: String,
        random: Boolean = false,
        weight: Int = 1,
        conflictGroup: String? = null,
    ) = Mutator(
        id = id,
        nameKey = me.nebula.ether.utils.translation.TranslationKey("mutator.$id.name"),
        descriptionKey = me.nebula.ether.utils.translation.TranslationKey("mutator.$id.desc"),
        material = "STONE",
        overrides = emptyMap(),
        random = random,
        weight = weight,
        conflictGroup = conflictGroup,
    )

    @Test
    fun `register and get returns the same mutator`() {
        val m = mutator("speed")
        MutatorRegistry.register(m)
        assertEquals(m, MutatorRegistry["speed"])
    }

    @Test
    fun `get unknown id returns null`() {
        assertNull(MutatorRegistry["nonexistent"])
    }

    @Test
    fun `register replaces same id`() {
        val first = mutator("speed", weight = 1)
        val second = mutator("speed", weight = 5)
        MutatorRegistry.register(first)
        MutatorRegistry.register(second)
        assertEquals(5, MutatorRegistry["speed"]?.weight)
    }

    @Test
    fun `randomPool only includes random mutators`() {
        MutatorRegistry.register(mutator("a", random = true))
        MutatorRegistry.register(mutator("b", random = false))
        MutatorRegistry.register(mutator("c", random = true))
        val pool = MutatorRegistry.randomPool().map { it.id }.toSet()
        assertEquals(setOf("a", "c"), pool)
    }

    @Test
    fun `resolve returns mutators for known ids`() {
        MutatorRegistry.register(mutator("a"))
        MutatorRegistry.register(mutator("b"))
        val resolved = MutatorRegistry.resolve(listOf("a", "b"))
        assertEquals(listOf("a", "b"), resolved.map { it.id })
    }

    @Test
    fun `resolve skips unknown ids without throwing`() {
        MutatorRegistry.register(mutator("a"))
        val resolved = MutatorRegistry.resolve(listOf("a", "ghost", "b"))
        assertEquals(listOf("a"), resolved.map { it.id })
    }

    @Test
    fun `selectRandom returns empty when pool is empty`() {
        assertEquals(emptyList(), MutatorRegistry.selectRandom(3))
    }

    @Test
    fun `selectRandom returns empty when count is non-positive`() {
        MutatorRegistry.register(mutator("a", random = true))
        assertEquals(emptyList(), MutatorRegistry.selectRandom(0))
        assertEquals(emptyList(), MutatorRegistry.selectRandom(-1))
    }

    @Test
    fun `selectRandom caps at pool size`() {
        MutatorRegistry.register(mutator("a", random = true))
        MutatorRegistry.register(mutator("b", random = true))
        val picked = MutatorRegistry.selectRandom(10)
        assertEquals(2, picked.size)
        assertEquals(setOf("a", "b"), picked.map { it.id }.toSet())
    }

    @Test
    fun `selectRandom respects conflictGroup`() {
        MutatorRegistry.register(mutator("speed_low", random = true, conflictGroup = "speed"))
        MutatorRegistry.register(mutator("speed_high", random = true, conflictGroup = "speed"))
        MutatorRegistry.register(mutator("jump", random = true, conflictGroup = "movement"))
        val picked = MutatorRegistry.selectRandom(3)
        val groups = picked.mapNotNull { it.conflictGroup }
        assertEquals(groups.distinct().size, groups.size, "no duplicate conflict groups")
        assertTrue(picked.size <= 2, "max 2 picks given conflict groups speed+movement")
    }

    @Test
    fun `all returns every registered mutator`() {
        MutatorRegistry.register(mutator("a"))
        MutatorRegistry.register(mutator("b"))
        MutatorRegistry.register(mutator("c"))
        assertEquals(setOf("a", "b", "c"), MutatorRegistry.all().map { it.id }.toSet())
    }

    @Test
    fun `selectRandom is deterministic enough to pick high-weight items more often`() {
        MutatorRegistry.register(mutator("rare", random = true, weight = 1))
        MutatorRegistry.register(mutator("common", random = true, weight = 99))
        val picks = (1..200).flatMap { MutatorRegistry.selectRandom(1).map { m -> m.id } }
        val commonCount = picks.count { it == "common" }
        assertTrue(commonCount > picks.size * 0.8, "expected common to dominate, got $commonCount / ${picks.size}")
    }

    @Test
    fun `selectRandom does not repeat a mutator within one selection`() {
        MutatorRegistry.register(mutator("a", random = true))
        MutatorRegistry.register(mutator("b", random = true))
        MutatorRegistry.register(mutator("c", random = true))
        val picked = MutatorRegistry.selectRandom(3).map { it.id }
        assertEquals(picked.distinct().size, picked.size)
    }

    @Test
    fun `register requires no exception on multiple registrations`() {
        repeat(100) { i ->
            MutatorRegistry.register(mutator("m$i"))
        }
        assertEquals(100, MutatorRegistry.all().size)
    }
}
