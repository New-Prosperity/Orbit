package me.nebula.orbit.utils.teambalance

import io.mockk.mockk
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamBalanceTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootServer() {
            if (MinecraftServer.process() == null) MinecraftServer.init()
        }
    }

    private fun player(): Player = mockk<Player>(relaxed = true)

    @Test
    fun `balance splits players evenly across teams`() {
        val players = (1..8).map { player() }
        val teams = TeamBalance.balance(players, teamCount = 2)
        assertEquals(2, teams.size)
        assertEquals(8, teams.values.sumOf { it.size })
        assertEquals(4, teams.getValue(0).size)
        assertEquals(4, teams.getValue(1).size)
    }

    @Test
    fun `balance with odd count keeps teams within one`() {
        val players = (1..7).map { player() }
        val teams = TeamBalance.balance(players, teamCount = 2)
        val sizes = teams.values.map { it.size }.sorted()
        assertEquals(listOf(3, 4), sizes)
    }

    @Test
    fun `balance by score is better than naive split`() {
        val players = (1..6).map { player() }
        val scores = players.mapIndexed { i, p -> p to listOf(100.0, 50.0, 40.0, 30.0, 20.0, 10.0)[i] }.toMap()
        val teams = TeamBalance.balance(players, teamCount = 2) { scores.getValue(it) }
        val totals = teams.values.map { team -> team.sumOf { scores.getValue(it) } }
        val snakeDiff = kotlin.math.abs(totals[0] - totals[1])
        val naiveFirstHalf = players.take(3).sumOf { scores.getValue(it) }
        val naiveSecondHalf = players.drop(3).sumOf { scores.getValue(it) }
        val naiveDiff = kotlin.math.abs(naiveFirstHalf - naiveSecondHalf)
        assertTrue(snakeDiff < naiveDiff, "Expected snake draft diff ($snakeDiff) < naive diff ($naiveDiff)")
    }

    @Test
    fun `balance single team returns all in one`() {
        val players = (1..5).map { player() }
        val teams = TeamBalance.balance(players, teamCount = 1)
        assertEquals(1, teams.size)
        assertEquals(5, teams.getValue(0).size)
    }

    @Test
    fun `balance empty input returns empty teams`() {
        val teams = TeamBalance.balance(emptyList(), teamCount = 3)
        assertEquals(3, teams.size)
        assertTrue(teams.values.all { it.isEmpty() })
    }

    @Test
    fun `balance rejects zero team count`() {
        val e = runCatching { TeamBalance.balance(emptyList(), teamCount = 0) }.exceptionOrNull()
        assertNotNull(e)
        assertTrue(e is IllegalArgumentException)
    }

    @Test
    fun `suggestSwap returns null when single team`() {
        val players = (1..4).map { player() }
        val teams = mapOf(0 to players)
        assertNull(TeamBalance.suggestSwap(teams) { 1.0 })
    }

    @Test
    fun `suggestSwap finds better variance pair`() {
        val p1 = player(); val p2 = player(); val p3 = player(); val p4 = player()
        val scores = mapOf(p1 to 100.0, p2 to 90.0, p3 to 20.0, p4 to 10.0)
        val teams = mapOf(0 to listOf(p1, p2), 1 to listOf(p3, p4))
        val swap = TeamBalance.suggestSwap(teams) { scores.getValue(it) }
        assertNotNull(swap)
    }

    @Test
    fun `suggestSwap returns null when balanced`() {
        val p1 = player(); val p2 = player(); val p3 = player(); val p4 = player()
        val scores = mapOf(p1 to 50.0, p2 to 50.0, p3 to 50.0, p4 to 50.0)
        val teams = mapOf(0 to listOf(p1, p2), 1 to listOf(p3, p4))
        assertNull(TeamBalance.suggestSwap(teams) { scores.getValue(it) })
    }

    @Test
    fun `autoBalance picks smallest team`() {
        val existing = mutableMapOf(
            0 to mutableListOf(player(), player()),
            1 to mutableListOf(player()),
            2 to mutableListOf(player(), player()),
        )
        val newPlayer = player()
        val chosen = TeamBalance.autoBalance(existing, newPlayer)
        assertEquals(1, chosen)
        assertEquals(2, existing.getValue(1).size)
    }

    @Test
    fun `autoBalance with empty teams throws`() {
        val newPlayer = player()
        val e = runCatching { TeamBalance.autoBalance(mutableMapOf(), newPlayer) }.exceptionOrNull()
        assertNotNull(e)
        assertTrue(e is IllegalArgumentException)
    }
}
