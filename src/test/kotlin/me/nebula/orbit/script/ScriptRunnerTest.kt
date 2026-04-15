package me.nebula.orbit.script

import io.mockk.mockk
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.PlayerTracker
import me.nebula.orbit.rules.GameRules
import me.nebula.orbit.rules.RuleRegistry
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration

class ScriptRunnerTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootServer() {
            if (MinecraftServer.process() == null) MinecraftServer.init()
        }

        private val FLAG = RuleRegistry.register("script_test_flag", default = false)
        private val COUNTER = RuleRegistry.register("script_test_counter", default = 0)
    }

    private fun ctx(time: Duration = 0.milliseconds, aliveCount: Int = 5, tickCount: Long = 0L): TestContext =
        TestContext(time, aliveCount, tickCount)

    private class TestContext(
        override val gameTime: Duration,
        aliveCount: Int,
        override val tickCount: Long,
    ) : GameTickContext {
        override val rules = GameRules()
        override val tracker: PlayerTracker = PlayerTracker().apply {
            repeat(aliveCount) { join(java.util.UUID.randomUUID()) }
        }
        override val instance: Instance = mockk(relaxed = true)
        override val gameMode: GameMode = mockk(relaxed = true)
        val broadcastLog = mutableListOf<String>()
        override fun broadcast(translationKey: String, sound: String?) {
            broadcastLog += translationKey
        }
        override fun broadcastPlayers(action: (Player) -> Unit) {}
    }

    @Test
    fun `AtTime trigger fires when game time reached`() {
        val context = ctx(time = 2.minutes + 1.milliseconds)
        val trigger = ScriptTrigger.AtTime(2.minutes)
        assertTrue(trigger.hasFired(context))
    }

    @Test
    fun `AtTime trigger does not fire before time`() {
        val context = ctx(time = 1.minutes)
        val trigger = ScriptTrigger.AtTime(2.minutes)
        assertFalse(trigger.hasFired(context))
    }

    @Test
    fun `WhenAliveAtOrBelow trigger fires when alive count drops`() {
        val above = ctx(aliveCount = 10)
        val below = ctx(aliveCount = 3)
        val trigger = ScriptTrigger.WhenAliveAtOrBelow(5)
        assertFalse(trigger.hasFired(above))
        assertTrue(trigger.hasFired(below))
    }

    @Test
    fun `EveryTick trigger fires on modulo zero tick`() {
        val trigger = ScriptTrigger.EveryTick(20)
        assertTrue(trigger.hasFired(ctx(tickCount = 40)))
        assertFalse(trigger.hasFired(ctx(tickCount = 41)))
    }

    @Test
    fun `SetRule action writes value into rules`() {
        val context = ctx()
        ScriptAction.SetRule(FLAG, true).execute(context)
        assertTrue(context.rules[FLAG])
    }

    @Test
    fun `SetMany writes multiple rules atomically`() {
        val context = ctx()
        ScriptAction.SetMany(mapOf(FLAG to true, COUNTER to 5)).execute(context)
        assertTrue(context.rules[FLAG])
        assertEquals(5, context.rules[COUNTER])
    }

    @Test
    fun `ScriptRunner fires step once and records it`() {
        val step = ScriptStep(
            trigger = ScriptTrigger.AtTime(0.milliseconds),
            actions = listOf(ScriptAction.SetRule(FLAG, true)),
        )
        val runner = ScriptRunner(listOf(step))
        val context = ctx(time = 1.milliseconds)

        runner.tick(context)
        assertTrue(context.rules[FLAG])

        context.rules[FLAG] = false
        runner.tick(context)
        assertFalse(context.rules[FLAG], "non-repeatable step should not re-fire")
    }

    @Test
    fun `ScriptRunner fires repeatable step on every matching tick`() {
        val step = ScriptStep(
            trigger = ScriptTrigger.EveryTick(1),
            actions = listOf(ScriptAction.SetMany(mapOf(COUNTER to 1))),
            repeatable = true,
        )
        val runner = ScriptRunner(listOf(step))
        val c1 = ctx(tickCount = 1); runner.tick(c1)
        val c2 = ctx(tickCount = 2); runner.tick(c2)
        assertEquals(1, c1.rules[COUNTER])
        assertEquals(1, c2.rules[COUNTER])
    }

    @Test
    fun `ScriptRunner announces via Announce action`() {
        val step = ScriptStep(
            trigger = ScriptTrigger.AtTime(0.milliseconds),
            actions = listOf(ScriptAction.Announce(me.nebula.ether.utils.translation.TranslationKey("orbit.test.msg"))),
        )
        val runner = ScriptRunner(listOf(step))
        val context = ctx(time = 1.milliseconds)
        runner.tick(context)
        assertEquals(listOf("orbit.test.msg"), context.broadcastLog)
    }

    @Test
    fun `ScriptRunner reset lets non-repeatable steps fire again`() {
        val step = ScriptStep(
            trigger = ScriptTrigger.AtTime(0.milliseconds),
            actions = listOf(ScriptAction.SetRule(FLAG, true)),
        )
        val runner = ScriptRunner(listOf(step))
        val ctxA = ctx(time = 1.milliseconds); runner.tick(ctxA); assertTrue(ctxA.rules[FLAG])
        runner.reset()
        val ctxB = ctx(time = 1.milliseconds); runner.tick(ctxB); assertTrue(ctxB.rules[FLAG])
    }
}
