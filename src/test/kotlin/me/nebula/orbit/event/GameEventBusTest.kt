package me.nebula.orbit.event

import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.rules.RuleKey
import me.nebula.orbit.rules.RuleRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GameEventBusTest {

    @Test
    fun `publish fires subscribers matching the event type`() {
        val bus = GameEventBus()
        val seen = mutableListOf<GameEvent.PhaseChanged>()
        bus.subscribe<GameEvent.PhaseChanged> { seen += it }
        bus.publish(GameEvent.PhaseChanged(GamePhase.WAITING, GamePhase.STARTING))
        assertEquals(1, seen.size)
        assertEquals(GamePhase.WAITING, seen.first().from)
    }

    @Test
    fun `subscribers receive only their type`() {
        val bus = GameEventBus()
        val phaseEvents = mutableListOf<GameEvent.PhaseChanged>()
        val ruleEvents = mutableListOf<GameEvent.RuleChanged<*>>()
        bus.subscribe<GameEvent.PhaseChanged> { phaseEvents += it }
        bus.subscribe<GameEvent.RuleChanged<*>> { ruleEvents += it }

        bus.publish(GameEvent.PhaseChanged(GamePhase.STARTING, GamePhase.PLAYING))
        val key = RuleRegistry.register("bus_test_rule", default = false)
        @Suppress("UNCHECKED_CAST")
        bus.publish(GameEvent.RuleChanged(key as RuleKey<Any>, false, true))

        assertEquals(1, phaseEvents.size)
        assertEquals(1, ruleEvents.size)
    }

    @Test
    fun `subscription cancel removes the handler`() {
        val bus = GameEventBus()
        val seen = mutableListOf<GameEvent>()
        val sub = bus.subscribe<GameEvent.PhaseChanged> { seen += it }
        sub.cancel()
        bus.publish(GameEvent.PhaseChanged(GamePhase.WAITING, GamePhase.STARTING))
        assertEquals(0, seen.size)
    }

    @Test
    fun `clear removes every subscriber`() {
        val bus = GameEventBus()
        bus.subscribe<GameEvent.PhaseChanged> { }
        bus.subscribe<GameEvent.RuleChanged<*>> { }
        assertEquals(2, bus.size())
        bus.clear()
        assertEquals(0, bus.size())
    }

    @Test
    fun `handler exception does not break other handlers`() {
        val bus = GameEventBus()
        var secondRan = false
        bus.subscribe<GameEvent.PhaseChanged> { error("boom") }
        bus.subscribe<GameEvent.PhaseChanged> { secondRan = true }
        bus.publish(GameEvent.PhaseChanged(GamePhase.WAITING, GamePhase.STARTING))
        assertEquals(true, secondRan)
    }

    @Test
    fun `multiple handlers of the same type all fire`() {
        val bus = GameEventBus()
        var count = 0
        repeat(3) { bus.subscribe<GameEvent.PhaseChanged> { count++ } }
        bus.publish(GameEvent.PhaseChanged(GamePhase.PLAYING, GamePhase.ENDING))
        assertEquals(3, count)
    }
}
