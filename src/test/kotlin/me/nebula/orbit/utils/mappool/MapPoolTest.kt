package me.nebula.orbit.utils.mappool

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MapPoolTest {

    private fun samplePool(strategy: SelectionStrategy = SelectionStrategy.RANDOM): MapPool = mapPool("sample") {
        map("alpha", "Alpha")
        map("beta", "Beta")
        map("gamma", "Gamma")
        map("delta", "Delta")
        strategy(strategy)
        recentExclusion(2)
    }

    @Test
    fun `pool reports correct size`() {
        val pool = samplePool()
        assertEquals(4, pool.size)
    }

    @Test
    fun `getMap finds by name`() {
        val pool = samplePool()
        val map = pool.getMap("alpha")
        assertNotNull(map)
        assertEquals("Alpha", map.displayName)
    }

    @Test
    fun `getMap returns null for missing`() {
        val pool = samplePool()
        assertEquals(null, pool.getMap("zeta"))
    }

    @Test
    fun `addMap rejects duplicate name`() {
        val pool = samplePool()
        assertThrows<IllegalArgumentException> {
            pool.addMap(GameMap("alpha", "Alpha 2"))
        }
    }

    @Test
    fun `addMap accepts unique name`() {
        val pool = samplePool()
        pool.addMap(GameMap("epsilon", "Epsilon"))
        assertEquals(5, pool.size)
    }

    @Test
    fun `removeMap removes by name`() {
        val pool = samplePool()
        assertTrue(pool.removeMap("alpha"))
        assertEquals(3, pool.size)
    }

    @Test
    fun `removeMap returns false for missing`() {
        val pool = samplePool()
        assertFalse(pool.removeMap("zeta"))
    }

    @Test
    fun `selectNext on empty pool throws`() {
        val pool = mapPool("empty") {}
        assertThrows<IllegalArgumentException> { pool.selectNext() }
    }

    @Test
    fun `selectNext returns a map`() {
        val pool = samplePool()
        val map = pool.selectNext()
        assertTrue(map.name in setOf("alpha", "beta", "gamma", "delta"))
    }

    @Test
    fun `recent exclusion prevents recent maps from being picked`() {
        val pool = mapPool("ex") {
            map("a", "A")
            map("b", "B")
            map("c", "C")
            recentExclusion(2)
        }
        val first = pool.selectNext().name
        val second = pool.selectNext().name
        val third = pool.selectNext().name
        assertEquals(setOf("a", "b", "c"), setOf(first, second, third))
    }

    @Test
    fun `rotation strategy cycles through maps in order`() {
        val pool = mapPool("rot") {
            map("a", "A")
            map("b", "B")
            map("c", "C")
            strategy(SelectionStrategy.ROTATION)
            recentExclusion(0)
        }
        val seq = (1..6).map { pool.selectNext().name }
        assertEquals(listOf("a", "b", "c", "a", "b", "c"), seq)
    }

    @Test
    fun `vote increments tally`() {
        val pool = samplePool(SelectionStrategy.VOTE)
        pool.vote(UUID.randomUUID(), "alpha")
        pool.vote(UUID.randomUUID(), "alpha")
        pool.vote(UUID.randomUUID(), "beta")
        assertEquals(2, pool.voteCount("alpha"))
        assertEquals(1, pool.voteCount("beta"))
    }

    @Test
    fun `vote rejects unknown map`() {
        val pool = samplePool(SelectionStrategy.VOTE)
        assertFalse(pool.vote(UUID.randomUUID(), "zeta"))
    }

    @Test
    fun `voteTally aggregates correctly`() {
        val pool = samplePool(SelectionStrategy.VOTE)
        repeat(3) { pool.vote(UUID.randomUUID(), "alpha") }
        repeat(2) { pool.vote(UUID.randomUUID(), "beta") }
        val tally = pool.voteTally()
        assertEquals(3, tally["alpha"])
        assertEquals(2, tally["beta"])
    }

    @Test
    fun `vote replaces previous vote of same voter`() {
        val pool = samplePool(SelectionStrategy.VOTE)
        val voter = UUID.randomUUID()
        pool.vote(voter, "alpha")
        pool.vote(voter, "beta")
        assertEquals(0, pool.voteCount("alpha"))
        assertEquals(1, pool.voteCount("beta"))
    }

    @Test
    fun `removeVote removes a voter's vote`() {
        val pool = samplePool(SelectionStrategy.VOTE)
        val voter = UUID.randomUUID()
        pool.vote(voter, "alpha")
        pool.removeVote(voter)
        assertEquals(0, pool.voteCount("alpha"))
    }

    @Test
    fun `clearVotes removes all`() {
        val pool = samplePool(SelectionStrategy.VOTE)
        pool.vote(UUID.randomUUID(), "alpha")
        pool.vote(UUID.randomUUID(), "beta")
        pool.clearVotes()
        assertEquals(0, pool.voteCount("alpha"))
        assertEquals(0, pool.voteCount("beta"))
    }

    @Test
    fun `eligible filters by player count`() {
        val pool = mapPool("pcount") {
            map("small", "Small") { minPlayers(2); maxPlayers(4) }
            map("large", "Large") { minPlayers(8); maxPlayers(16) }
        }
        val small = pool.eligible(playerCount = 3)
        assertEquals(1, small.size)
        assertEquals("small", small[0].name)
    }

    @Test
    fun `recentExclusion negative throws`() {
        assertThrows<IllegalArgumentException> {
            MapPool("bad", listOf(GameMap("a", "A")), recentExclusion = -1)
        }
    }

    @Test
    fun `recentWeight outside range throws`() {
        assertThrows<IllegalArgumentException> {
            MapPool("bad", listOf(GameMap("a", "A")), recentWeight = 1.5)
        }
    }

    @Test
    fun `vote selection picks top voted`() {
        val pool = mapPool("vote") {
            map("a", "A")
            map("b", "B")
            map("c", "C")
            strategy(SelectionStrategy.VOTE)
            recentExclusion(0)
        }
        repeat(5) { pool.vote(UUID.randomUUID(), "b") }
        repeat(2) { pool.vote(UUID.randomUUID(), "a") }
        assertEquals("b", pool.selectNext().name)
    }

    @Test
    fun `selectNext clears votes after selection`() {
        val pool = samplePool(SelectionStrategy.VOTE)
        pool.vote(UUID.randomUUID(), "alpha")
        pool.selectNext()
        assertEquals(0, pool.voteCount("alpha"))
    }
}
