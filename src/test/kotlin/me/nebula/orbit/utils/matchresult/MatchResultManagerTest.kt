package me.nebula.orbit.utils.matchresult

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchResultManagerTest {

    @AfterEach
    fun cleanup() {
        MatchResultManager.clear()
    }

    private fun uuid() = UUID.randomUUID()

    private fun result(
        winner: Pair<UUID, String>? = uuid() to "Winner",
        losers: List<Pair<UUID, String>> = listOf(uuid() to "Loser"),
        isDraw: Boolean = false,
        mvp: Pair<UUID, String>? = null,
    ) = MatchResult(
        winner = winner,
        losers = losers,
        isDraw = isDraw,
        mvp = mvp,
        stats = emptyMap(),
        duration = Duration.ofMinutes(5),
        timestamp = System.currentTimeMillis(),
        metadata = emptyMap(),
    )

    @Test
    fun `store adds to history`() {
        MatchResultManager.store(result())
        assertEquals(1, MatchResultManager.recentMatches(10).size)
    }

    @Test
    fun `recentMatches returns most recent first`() {
        val r1 = result()
        val r2 = result()
        MatchResultManager.store(r1)
        MatchResultManager.store(r2)
        val recent = MatchResultManager.recentMatches(10)
        assertEquals(2, recent.size)
        assertEquals(r2, recent[0])
        assertEquals(r1, recent[1])
    }

    @Test
    fun `recentMatches respects limit`() {
        repeat(20) { MatchResultManager.store(result()) }
        assertEquals(5, MatchResultManager.recentMatches(5).size)
    }

    @Test
    fun `history caps at 100`() {
        repeat(120) { MatchResultManager.store(result()) }
        assertEquals(100, MatchResultManager.recentMatches(200).size)
    }

    @Test
    fun `playerWins counts correctly`() {
        val winnerId = uuid()
        MatchResultManager.store(result(winner = winnerId to "P1"))
        MatchResultManager.store(result(winner = winnerId to "P1"))
        MatchResultManager.store(result(winner = uuid() to "Other"))
        assertEquals(2, MatchResultManager.playerWins(winnerId))
    }

    @Test
    fun `playerLosses counts correctly`() {
        val loserId = uuid()
        MatchResultManager.store(result(losers = listOf(loserId to "P1")))
        MatchResultManager.store(result(losers = listOf(loserId to "P1", uuid() to "Other")))
        assertEquals(2, MatchResultManager.playerLosses(loserId))
    }

    @Test
    fun `playerDraws counts correctly`() {
        val pid = uuid()
        MatchResultManager.store(result(winner = pid to "P1", isDraw = true))
        MatchResultManager.store(result(winner = pid to "P1", isDraw = false))
        assertEquals(1, MatchResultManager.playerDraws(pid))
    }

    @Test
    fun `playerMvps counts correctly`() {
        val mvpId = uuid()
        MatchResultManager.store(result(mvp = mvpId to "MVP"))
        MatchResultManager.store(result(mvp = uuid() to "Other"))
        MatchResultManager.store(result(mvp = mvpId to "MVP"))
        assertEquals(2, MatchResultManager.playerMvps(mvpId))
    }

    @Test
    fun `playerMatches returns only that player's matches`() {
        val pid = uuid()
        MatchResultManager.store(result(winner = pid to "P1"))
        MatchResultManager.store(result(winner = uuid() to "Other"))
        MatchResultManager.store(result(losers = listOf(pid to "P1")))
        assertEquals(2, MatchResultManager.playerMatches(pid, 10).size)
    }

    @Test
    fun `clear resets everything`() {
        val pid = uuid()
        MatchResultManager.store(result(winner = pid to "P1"))
        MatchResultManager.clear()
        assertEquals(0, MatchResultManager.recentMatches(10).size)
        assertEquals(0, MatchResultManager.playerWins(pid))
    }

    @Test
    fun `unknown player returns zero for all stats`() {
        val unknown = uuid()
        assertEquals(0, MatchResultManager.playerWins(unknown))
        assertEquals(0, MatchResultManager.playerLosses(unknown))
        assertEquals(0, MatchResultManager.playerDraws(unknown))
        assertEquals(0, MatchResultManager.playerMvps(unknown))
        assertTrue(MatchResultManager.playerMatches(unknown).isEmpty())
    }
}
