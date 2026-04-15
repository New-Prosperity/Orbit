package me.nebula.orbit.script

import me.nebula.orbit.rules.RuleKey
import kotlin.time.Duration

sealed interface ScriptTrigger {

    fun hasFired(ctx: GameTickContext): Boolean

    data class AtTime(val at: Duration) : ScriptTrigger {
        override fun hasFired(ctx: GameTickContext): Boolean = ctx.gameTime >= at
    }

    data class AfterTime(val gap: Duration) : ScriptTrigger {
        override fun hasFired(ctx: GameTickContext): Boolean = ctx.gameTime >= gap
    }

    data class WhenAliveAtOrBelow(val count: Int) : ScriptTrigger {
        override fun hasFired(ctx: GameTickContext): Boolean = ctx.tracker.aliveCount <= count
    }

    data class WhenAliveAtOrAbove(val count: Int) : ScriptTrigger {
        override fun hasFired(ctx: GameTickContext): Boolean = ctx.tracker.aliveCount >= count
    }

    data class OnRuleEquals<T : Any>(val key: RuleKey<T>, val value: T) : ScriptTrigger {
        override fun hasFired(ctx: GameTickContext): Boolean = ctx.rules[key] == value
    }

    data class EveryTick(val intervalTicks: Int) : ScriptTrigger {
        override fun hasFired(ctx: GameTickContext): Boolean =
            intervalTicks > 0 && ctx.tickCount % intervalTicks == 0L
    }
}
