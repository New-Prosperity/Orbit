package me.nebula.orbit.utils.gamestate

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private enum class TestPhase { WAITING, STARTING, PLAYING, ENDING }

class GameStateMachineTest {

    private fun newMachine(): GameStateMachine<TestPhase> = gameStateMachine(TestPhase.WAITING) {
        allow(TestPhase.WAITING, TestPhase.STARTING)
        allow(TestPhase.STARTING, TestPhase.PLAYING, TestPhase.WAITING)
        allow(TestPhase.PLAYING, TestPhase.ENDING)
        allow(TestPhase.ENDING, TestPhase.WAITING)
    }

    @Test
    fun `initial state is the constructor argument`() {
        val machine = newMachine()
        assertEquals(TestPhase.WAITING, machine.current)
    }

    @Test
    fun `allowed transition succeeds`() {
        val machine = newMachine()
        assertTrue(machine.transition(TestPhase.STARTING))
        assertEquals(TestPhase.STARTING, machine.current)
    }

    @Test
    fun `disallowed transition is rejected`() {
        val machine = newMachine()
        assertFalse(machine.transition(TestPhase.PLAYING))
        assertEquals(TestPhase.WAITING, machine.current)
    }

    @Test
    fun `canTransition matches transition behavior`() {
        val machine = newMachine()
        assertTrue(machine.canTransition(TestPhase.STARTING))
        assertFalse(machine.canTransition(TestPhase.PLAYING))
    }

    @Test
    fun `forceTransition bypasses guards`() {
        val machine = gameStateMachine(TestPhase.WAITING) {
            allow(TestPhase.WAITING, TestPhase.STARTING)
            guard(TestPhase.WAITING, TestPhase.STARTING) { false }
        }
        assertFalse(machine.transition(TestPhase.STARTING))
        machine.forceTransition(TestPhase.STARTING)
        assertEquals(TestPhase.STARTING, machine.current)
    }

    @Test
    fun `onEnter callback fires on transition`() {
        var entered = 0
        val machine = gameStateMachine(TestPhase.WAITING) {
            allow(TestPhase.WAITING, TestPhase.STARTING)
            onEnter(TestPhase.STARTING) { entered++ }
        }
        machine.transition(TestPhase.STARTING)
        assertEquals(1, entered)
    }

    @Test
    fun `onExit callback fires on transition`() {
        var exited = 0
        val machine = gameStateMachine(TestPhase.WAITING) {
            allow(TestPhase.WAITING, TestPhase.STARTING)
            onExit(TestPhase.WAITING) { exited++ }
        }
        machine.transition(TestPhase.STARTING)
        assertEquals(1, exited)
    }

    @Test
    fun `onTransition listeners receive from and to`() {
        val log = mutableListOf<Pair<TestPhase, TestPhase>>()
        val machine = newMachine()
        machine.onTransition { from, to -> log += from to to }
        machine.transition(TestPhase.STARTING)
        machine.transition(TestPhase.PLAYING)
        assertEquals(listOf(TestPhase.WAITING to TestPhase.STARTING, TestPhase.STARTING to TestPhase.PLAYING), log)
    }

    @Test
    fun `multiple listeners all fire`() {
        var aCount = 0
        var bCount = 0
        val machine = newMachine()
        machine.onTransition { _, _ -> aCount++ }
        machine.onTransition { _, _ -> bCount++ }
        machine.transition(TestPhase.STARTING)
        assertEquals(1, aCount)
        assertEquals(1, bCount)
    }

    @Test
    fun `removeListener stops a listener`() {
        var count = 0
        val machine = newMachine()
        val cb: StateTransitionCallback<TestPhase> = { _, _ -> count++ }
        machine.onTransition(cb)
        machine.transition(TestPhase.STARTING)
        assertEquals(1, count)
        machine.removeListener(cb)
        machine.transition(TestPhase.PLAYING)
        assertEquals(1, count)
    }

    @Test
    fun `state history records every transition`() {
        val machine = newMachine()
        machine.transition(TestPhase.STARTING)
        machine.transition(TestPhase.PLAYING)
        machine.transition(TestPhase.ENDING)
        val states = machine.stateHistory.map { it.first }
        assertEquals(listOf(TestPhase.WAITING, TestPhase.STARTING, TestPhase.PLAYING, TestPhase.ENDING), states)
    }

    @Test
    fun `is_ checks current state`() {
        val machine = newMachine()
        assertTrue(machine.is_(TestPhase.WAITING))
        assertFalse(machine.is_(TestPhase.STARTING))
    }

    @Test
    fun `isAny matches against multiple states`() {
        val machine = newMachine()
        assertTrue(machine.isAny(TestPhase.WAITING, TestPhase.PLAYING))
        assertFalse(machine.isAny(TestPhase.STARTING, TestPhase.ENDING))
    }
}
