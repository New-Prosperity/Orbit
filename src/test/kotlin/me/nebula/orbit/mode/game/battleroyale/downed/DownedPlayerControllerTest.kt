package me.nebula.orbit.mode.game.battleroyale.downed

import me.nebula.orbit.mode.game.battleroyale.BattleRoyaleTeamConfig
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownedPlayerControllerTest {

    private class Clock(start: Long = 1_000_000L) {
        var now: Long = start
        fun advance(ms: Long) { now += ms }
        fun lambda(): () -> Long = { now }
    }

    private class FinalCapture {
        val events = mutableListOf<Pair<UUID, FinalReason>>()
        fun handler(): (UUID, FinalReason) -> Unit = { u, r -> events += u to r }
    }

    private class ReviveCapture {
        val events = mutableListOf<Pair<UUID, UUID>>()
        fun handler(): (UUID, UUID) -> Unit = { rv, rd -> events += rv to rd }
    }

    private fun buildController(
        clock: Clock = Clock(),
        config: BattleRoyaleTeamConfig = BattleRoyaleTeamConfig(
            enabled = true,
            teamSize = 2,
            reviveEnabled = true,
            reviveTimeSeconds = 2,
            bleedoutSeconds = 5,
            bleedoutHp = 10,
        ),
        onFinal: FinalCapture = FinalCapture(),
        onRevive: ReviveCapture = ReviveCapture(),
    ): Triple<DownedPlayerController, FinalCapture, ReviveCapture> {
        val ctrl = DownedPlayerController(
            config = config,
            onFinalEliminate = onFinal.handler(),
            onRevived = onRevive.handler(),
            clock = clock.lambda(),
        )
        return Triple(ctrl, onFinal, onRevive)
    }

    @Test
    fun `knock creates a record when revive is enabled`() {
        val (ctrl, _, _) = buildController()
        val victim = UUID.randomUUID()
        val attacker = UUID.randomUUID()
        val result = ctrl.knock(victim, attacker)
        assertTrue(result.isSuccess)
        assertTrue(ctrl.isDowned(victim))
        val rec = ctrl.record(victim)
        assertNotNull(rec)
        assertEquals(attacker, rec.attackerUuid)
    }

    @Test
    fun `knock rejects when revive is disabled`() {
        val (ctrl, _, _) = buildController(
            config = BattleRoyaleTeamConfig(enabled = true, reviveEnabled = false),
        )
        val uuid = UUID.randomUUID()
        val result = ctrl.knock(uuid, null)
        assertTrue(result.isFailure)
        assertFalse(ctrl.isDowned(uuid))
    }

    @Test
    fun `knock rejects when already downed`() {
        val (ctrl, _, _) = buildController()
        val uuid = UUID.randomUUID()
        ctrl.knock(uuid, null)
        val second = ctrl.knock(uuid, null)
        assertTrue(second.isFailure)
    }

    @Test
    fun `absorbDamage reduces bleedoutHp and finalizes when lethal`() {
        val (ctrl, finals, _) = buildController()
        val victim = UUID.randomUUID()
        ctrl.knock(victim, null)
        val nonLethal = ctrl.absorbDamage(victim, 5)
        assertFalse(nonLethal)
        assertTrue(ctrl.isDowned(victim))
        val lethal = ctrl.absorbDamage(victim, 10)
        assertTrue(lethal)
        assertFalse(ctrl.isDowned(victim))
        assertEquals(listOf(victim to FinalReason.EXECUTED), finals.events)
    }

    @Test
    fun `bleedout tick finalizes the record after configured seconds`() {
        val clock = Clock()
        val (ctrl, finals, _) = buildController(clock = clock)
        val victim = UUID.randomUUID()
        ctrl.knock(victim, null)
        clock.advance(3_000L)
        ctrl.tick()
        assertTrue(ctrl.isDowned(victim))
        clock.advance(3_000L)
        ctrl.tick()
        assertFalse(ctrl.isDowned(victim))
        assertEquals(listOf(victim to FinalReason.BLEEDOUT), finals.events)
    }

    @Test
    fun `startRevive assigns the reviver and advances progress`() {
        val (ctrl, _, revives) = buildController()
        val victim = UUID.randomUUID()
        val reviver = UUID.randomUUID()
        ctrl.knock(victim, null)
        assertTrue(ctrl.startRevive(reviver, victim))
        assertEquals(reviver, ctrl.reviverOf(victim))
        repeat(20) { ctrl.tick() }
        assertTrue(ctrl.reviveProgress(victim) > 0.0)
        assertTrue(revives.events.isEmpty())
    }

    @Test
    fun `revive completes after configured ticks and fires callback`() {
        val (ctrl, finals, revives) = buildController(
            config = BattleRoyaleTeamConfig(
                enabled = true, reviveEnabled = true,
                reviveTimeSeconds = 1, bleedoutSeconds = 60, bleedoutHp = 10,
            ),
        )
        val victim = UUID.randomUUID()
        val reviver = UUID.randomUUID()
        ctrl.knock(victim, null)
        ctrl.startRevive(reviver, victim)
        repeat(20) { ctrl.tick() }
        assertFalse(ctrl.isDowned(victim))
        assertEquals(listOf(reviver to victim), revives.events)
        assertTrue(finals.events.isEmpty())
    }

    @Test
    fun `cancelRevive clears reviver and resets progress`() {
        val (ctrl, _, _) = buildController()
        val victim = UUID.randomUUID()
        val reviver = UUID.randomUUID()
        ctrl.knock(victim, null)
        ctrl.startRevive(reviver, victim)
        repeat(5) { ctrl.tick() }
        assertTrue(ctrl.cancelRevive(reviver, victim))
        assertNull(ctrl.reviverOf(victim))
        assertEquals(0.0, ctrl.reviveProgress(victim))
    }

    @Test
    fun `forceFinal removes record and fires callback with given reason`() {
        val (ctrl, finals, _) = buildController()
        val victim = UUID.randomUUID()
        ctrl.knock(victim, null)
        ctrl.forceFinal(victim, FinalReason.MANUAL)
        assertFalse(ctrl.isDowned(victim))
        assertEquals(listOf(victim to FinalReason.MANUAL), finals.events)
    }

    @Test
    fun `rise removes record without firing callback`() {
        val (ctrl, finals, revives) = buildController()
        val victim = UUID.randomUUID()
        ctrl.knock(victim, null)
        val rec = ctrl.rise(victim)
        assertNotNull(rec)
        assertFalse(ctrl.isDowned(victim))
        assertTrue(finals.events.isEmpty())
        assertTrue(revives.events.isEmpty())
    }

    @Test
    fun `bleedoutRemainingSeconds counts down from configured value`() {
        val clock = Clock()
        val (ctrl, _, _) = buildController(clock = clock)
        val victim = UUID.randomUUID()
        ctrl.knock(victim, null)
        assertEquals(5, ctrl.bleedoutRemainingSeconds(victim))
        clock.advance(2_000L)
        assertEquals(3, ctrl.bleedoutRemainingSeconds(victim))
        clock.advance(10_000L)
        assertEquals(0, ctrl.bleedoutRemainingSeconds(victim))
    }

    @Test
    fun `startRevive rejects when reviver is same as downed`() {
        val (ctrl, _, _) = buildController()
        val uuid = UUID.randomUUID()
        ctrl.knock(uuid, null)
        assertFalse(ctrl.startRevive(uuid, uuid))
    }

    @Test
    fun `startRevive rejects when reviver is already downed`() {
        val (ctrl, _, _) = buildController()
        val victim = UUID.randomUUID()
        val reviver = UUID.randomUUID()
        ctrl.knock(victim, null)
        ctrl.knock(reviver, null)
        assertFalse(ctrl.startRevive(reviver, victim))
    }
}
