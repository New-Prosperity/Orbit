package me.nebula.orbit.mode.game

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerTrackerTest {

    private fun tracker() = PlayerTracker()
    private fun uuid() = UUID.randomUUID()

    @Test
    fun `join adds player as alive`() {
        val t = tracker()
        val id = uuid()
        t.join(id)
        assertTrue(t.isAlive(id))
        assertEquals(1, t.aliveCount)
        assertTrue(id in t.all)
    }

    @Test
    fun `eliminate moves alive to spectating`() {
        val t = tracker()
        val id = uuid()
        t.join(id)
        t.eliminate(id)
        assertFalse(t.isAlive(id))
        assertTrue(t.isSpectating(id))
        assertEquals(0, t.aliveCount)
    }

    @Test
    fun `revive moves spectating back to alive`() {
        val t = tracker()
        val id = uuid()
        t.join(id)
        t.eliminate(id)
        t.revive(id)
        assertTrue(t.isAlive(id))
        assertFalse(t.isSpectating(id))
    }

    @Test
    fun `disconnect moves alive to disconnected`() {
        val t = tracker()
        val id = uuid()
        t.join(id)
        t.disconnect(id)
        assertTrue(t.isDisconnected(id))
        assertFalse(t.isAlive(id))
    }

    @Test
    fun `reconnect moves disconnected back to alive`() {
        val t = tracker()
        val id = uuid()
        t.join(id)
        t.disconnect(id)
        t.reconnect(id)
        assertTrue(t.isAlive(id))
        assertFalse(t.isDisconnected(id))
    }

    @Test
    fun `remove fully removes player from tracker`() {
        val t = tracker()
        val id = uuid()
        t.join(id)
        t.remove(id)
        assertFalse(t.contains(id))
        assertEquals(0, t.size)
    }

    @Test
    fun `effectiveAliveCount includes disconnected players`() {
        val t = tracker()
        val a = uuid()
        val b = uuid()
        t.join(a)
        t.join(b)
        t.disconnect(b)
        assertEquals(1, t.aliveCount)
        assertEquals(2, t.effectiveAliveCount)
    }

    @Test
    fun `team assignment and query`() {
        val t = tracker()
        val a = uuid()
        val b = uuid()
        val c = uuid()
        t.join(a)
        t.join(b)
        t.join(c)
        t.assignTeam(a, "red")
        t.assignTeam(b, "red")
        t.assignTeam(c, "blue")
        assertEquals("red", t.teamOf(a))
        assertEquals(setOf(a, b), t.teamMembers("red"))
        assertEquals(setOf(c), t.teamMembers("blue"))
        assertTrue(t.areTeammates(a, b))
        assertFalse(t.areTeammates(a, c))
    }

    @Test
    fun `aliveInTeam excludes eliminated`() {
        val t = tracker()
        val a = uuid()
        val b = uuid()
        t.join(a)
        t.join(b)
        t.assignTeam(a, "red")
        t.assignTeam(b, "red")
        t.eliminate(b)
        assertEquals(setOf(a), t.aliveInTeam("red"))
    }

    @Test
    fun `isTeamEliminated returns true when all dead`() {
        val t = tracker()
        val a = uuid()
        val b = uuid()
        t.join(a)
        t.join(b)
        t.assignTeam(a, "red")
        t.assignTeam(b, "red")
        assertFalse(t.isTeamEliminated("red"))
        t.eliminate(a)
        t.eliminate(b)
        assertTrue(t.isTeamEliminated("red"))
    }

    @Test
    fun `kill and death tracking`() {
        val t = tracker()
        val killer = uuid()
        val victim = uuid()
        t.join(killer)
        t.join(victim)
        t.recordKill(killer)
        t.recordDeath(victim)
        assertEquals(1, t.killsOf(killer))
        assertEquals(1, t.deathsOf(victim))
        assertEquals(1, t.streakOf(killer))
    }

    @Test
    fun `streak resets on death`() {
        val t = tracker()
        val id = uuid()
        t.join(id)
        t.recordKill(id)
        t.recordKill(id)
        assertEquals(2, t.streakOf(id))
        t.recordDeath(id)
        assertEquals(0, t.streakOf(id))
    }

    @Test
    fun `lives management`() {
        val t = tracker()
        val id = uuid()
        t.join(id)
        t.setLives(id, 3)
        assertEquals(3, t.livesOf(id))
        assertTrue(t.hasLivesRemaining(id))
        assertEquals(2, t.decrementLives(id))
        assertEquals(1, t.decrementLives(id))
        assertEquals(0, t.decrementLives(id))
        assertFalse(t.hasLivesRemaining(id))
    }

    @Test
    fun `score tracking and leaderboard`() {
        val t = tracker()
        val a = uuid()
        val b = uuid()
        t.join(a)
        t.join(b)
        t.addScore(a, 100.0)
        t.addScore(b, 200.0)
        t.addScore(a, 50.0)
        assertEquals(150.0, t.scoreOf(a))
        assertEquals(200.0, t.scoreOf(b))
        val board = t.scoreboard()
        assertEquals(b, board.first().first)
    }

    @Test
    fun `team score aggregates members`() {
        val t = tracker()
        val a = uuid()
        val b = uuid()
        t.join(a)
        t.join(b)
        t.assignTeam(a, "red")
        t.assignTeam(b, "red")
        t.addScore(a, 100.0)
        t.addScore(b, 50.0)
        assertEquals(150.0, t.teamScoreOf("red"))
    }

    @Test
    fun `damage tracking and recent damagers`() {
        val t = tracker()
        val attacker = uuid()
        val victim = uuid()
        t.join(attacker)
        t.join(victim)
        t.recordDamage(attacker, victim)
        val damagers = t.recentDamagersOf(victim, 60_000L)
        assertTrue(attacker in damagers)
    }

    @Test
    fun `combat and activity tracking`() {
        val t = tracker()
        val id = uuid()
        t.join(id)
        t.recordDamage(id, uuid())
        assertTrue(t.isInCombat(id, 60_000L))
        t.markActivity(id)
        assertFalse(t.isAfk(id, 60_000L))
    }

    @Test
    fun `markRespawning sets respawning state`() {
        val t = tracker()
        val id = uuid()
        t.join(id)
        t.eliminate(id)
        t.markRespawning(id)
        assertTrue(t.isRespawning(id))
        assertTrue(id in t.respawning)
    }

    @Test
    fun `clear resets everything`() {
        val t = tracker()
        val a = uuid()
        val b = uuid()
        t.join(a)
        t.join(b)
        t.recordKill(a)
        t.assignTeam(a, "red")
        t.clear()
        assertEquals(0, t.size)
        assertFalse(t.contains(a))
        assertEquals(0, t.killsOf(a))
    }

    @Test
    fun `stateOf returns correct state`() {
        val t = tracker()
        val id = uuid()
        assertNull(t.stateOf(id))
        t.join(id)
        assertTrue(t.stateOf(id) is PlayerState.Alive)
        t.eliminate(id)
        assertTrue(t.stateOf(id) is PlayerState.Spectating)
    }

    @Test
    fun `elimination order tracks placement`() {
        val t = tracker()
        val a = uuid()
        val b = uuid()
        val c = uuid()
        t.join(a)
        t.join(b)
        t.join(c)
        t.eliminate(a)
        t.eliminate(b)
        assertEquals(1, t.eliminationOrderOf(a))
        assertEquals(2, t.eliminationOrderOf(b))
        assertEquals(3, t.placementOf(a, 3))
        assertEquals(2, t.placementOf(b, 3))
    }

    @Test
    fun `aliveTeams returns only teams with living members`() {
        val t = tracker()
        val a = uuid()
        val b = uuid()
        t.join(a)
        t.join(b)
        t.assignTeam(a, "red")
        t.assignTeam(b, "blue")
        assertEquals(setOf("red", "blue"), t.aliveTeams())
        t.eliminate(b)
        assertEquals(setOf("red"), t.aliveTeams())
    }

    @Test
    fun `assists tracking`() {
        val t = tracker()
        val id = uuid()
        t.join(id)
        t.recordAssist(id)
        t.recordAssist(id)
        assertEquals(2, t.assistsOf(id))
    }
}
