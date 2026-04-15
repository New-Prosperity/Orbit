package me.nebula.orbit.script

import me.nebula.ether.utils.logging.logger

class ScriptRunner(private val steps: List<ScriptStep>) {

    private val logger = logger("ScriptRunner")
    private val fired = HashSet<ScriptStep>()

    fun tick(ctx: GameTickContext) {
        if (steps.isEmpty()) return
        for (step in steps) {
            if (!step.repeatable && step in fired) continue
            if (!step.trigger.hasFired(ctx)) continue
            for (action in step.actions) {
                runCatching { action.execute(ctx) }.onFailure { error ->
                    logger.warn(error) { "Script action failed: ${action::class.simpleName} (step=${step.id ?: "unnamed"})" }
                }
            }
            if (!step.repeatable) fired += step
        }
    }

    fun reset() {
        fired.clear()
    }
}
