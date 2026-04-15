package me.nebula.orbit.utils.mapvote

import me.nebula.ether.utils.translation.TranslationKey
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapVoteManagerTest {

    private fun categories() = listOf(
        VoteCategory(
            id = "duration",
            nameKey = TranslationKey("vote.duration"),
            material = "minecraft:clock",
            defaultIndex = 1,
            options = listOf(
                VoteOption(nameKey = TranslationKey("vote.short"), material = "minecraft:clock", value = 600),
                VoteOption(nameKey = TranslationKey("vote.normal"), material = "minecraft:clock", value = 900),
                VoteOption(nameKey = TranslationKey("vote.long"), material = "minecraft:clock", value = 1200),
            ),
        ),
        VoteCategory(
            id = "health",
            nameKey = TranslationKey("vote.health"),
            material = "minecraft:apple",
            defaultIndex = 0,
            options = listOf(
                VoteOption(nameKey = TranslationKey("vote.normal_hp"), material = "minecraft:apple", value = 20),
                VoteOption(nameKey = TranslationKey("vote.enhanced_hp"), material = "minecraft:apple", value = 30),
            ),
        ),
    )

    private fun manager() = MapVoteManager(categoriesProvider = ::categories)

    @Test
    fun `vote records player choice`() {
        val m = manager()
        val player = UUID.randomUUID()
        m.vote(player, "duration", 2)
        assertEquals(2, m.getVote(player, "duration"))
    }

    @Test
    fun `getVote returns null when not voted`() {
        val m = manager()
        assertNull(m.getVote(UUID.randomUUID(), "duration"))
    }

    @Test
    fun `resolve returns default when no votes`() {
        val m = manager()
        assertEquals(1, m.resolve("duration"))
        assertEquals(0, m.resolve("health"))
    }

    @Test
    fun `resolve returns majority vote`() {
        val m = manager()
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val c = UUID.randomUUID()
        m.vote(a, "duration", 0)
        m.vote(b, "duration", 0)
        m.vote(c, "duration", 2)
        assertEquals(0, m.resolve("duration"))
    }

    @Test
    fun `resolveValue maps index to option value`() {
        val m = manager()
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        m.vote(a, "duration", 2)
        m.vote(b, "duration", 2)
        assertEquals(1200, m.resolveValue("duration"))
    }

    @Test
    fun `resolveValue returns default value when no votes`() {
        val m = manager()
        assertEquals(900, m.resolveValue("duration"))
    }

    @Test
    fun `clear removes all votes`() {
        val m = manager()
        m.vote(UUID.randomUUID(), "duration", 0)
        m.vote(UUID.randomUUID(), "health", 1)
        m.clear()
        assertEquals(1, m.resolve("duration"))
        assertEquals(0, m.resolve("health"))
    }

    @Test
    fun `vote overrides previous choice`() {
        val m = manager()
        val player = UUID.randomUUID()
        m.vote(player, "duration", 0)
        m.vote(player, "duration", 2)
        assertEquals(2, m.getVote(player, "duration"))
    }

    @Test
    fun `resolve unknown category returns 0`() {
        val m = manager()
        assertEquals(0, m.resolve("nonexistent"))
    }

    @Test
    fun `recent selection penalty reduces repeat votes`() {
        val m = manager()
        m.recordSelection("duration", 0)
        m.recordSelection("duration", 0)

        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        m.vote(a, "duration", 0)
        m.vote(b, "duration", 1)
        val result = m.resolve("duration")
        assertEquals(1, result)
    }

    @Test
    fun `recentSelections returns recorded history`() {
        val m = manager()
        m.recordSelection("duration", 0)
        m.recordSelection("duration", 1)
        m.recordSelection("duration", 2)
        assertEquals(listOf(0, 1, 2), m.recentSelections("duration"))
    }

    @Test
    fun `recentSelections bounded by history size`() {
        val m = MapVoteManager(recentHistorySize = 2, categoriesProvider = ::categories)
        m.recordSelection("duration", 0)
        m.recordSelection("duration", 1)
        m.recordSelection("duration", 2)
        assertEquals(listOf(1, 2), m.recentSelections("duration"))
    }
}
