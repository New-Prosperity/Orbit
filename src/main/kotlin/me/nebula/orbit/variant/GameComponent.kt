package me.nebula.orbit.variant

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.rules.RuleKey
import me.nebula.orbit.script.GameContext
import me.nebula.orbit.script.ScriptRunner
import me.nebula.orbit.script.ScriptStep
import java.util.concurrent.ConcurrentHashMap

sealed interface GameComponent {

    fun apply(ctx: GameContext)
    fun dispose(ctx: GameContext) {}

    data class Spawn(
        val spawnModeId: String,
        val config: Map<String, Any> = emptyMap(),
    ) : GameComponent {
        override fun apply(ctx: GameContext) {}
    }

    data class InitialRules(val values: Map<RuleKey<*>, Any>) : GameComponent {
        override fun apply(ctx: GameContext) { ctx.rules.setAll(values) }
    }

    data class Script(val steps: List<ScriptStep>) : GameComponent {
        private val runners = ConcurrentHashMap<Int, ScriptRunner>()
        override fun apply(ctx: GameContext) {
            runners[System.identityHashCode(ctx)] = ScriptRunner(steps)
        }
        override fun dispose(ctx: GameContext) {
            runners.remove(System.identityHashCode(ctx))?.reset()
        }
        fun runnerFor(ctx: GameContext): ScriptRunner? = runners[System.identityHashCode(ctx)]
    }

    data class MutatorFilter(
        val forced: List<String> = emptyList(),
        val excluded: List<String> = emptyList(),
    ) : GameComponent {
        override fun apply(ctx: GameContext) {}
    }

    data class WinConditionRef(val id: String) : GameComponent {
        override fun apply(ctx: GameContext) {}
    }

    data class Custom(
        val id: String,
        val onApply: (GameContext) -> Unit = {},
        val onDispose: (GameContext) -> Unit = {},
    ) : GameComponent {
        private val log = logger("GameComponent.Custom")
        override fun apply(ctx: GameContext) {
            runCatching { onApply(ctx) }.onFailure { log.warn(it) { "Custom component '$id' apply failed" } }
        }
        override fun dispose(ctx: GameContext) {
            runCatching { onDispose(ctx) }.onFailure { log.warn(it) { "Custom component '$id' dispose failed" } }
        }
    }
}
