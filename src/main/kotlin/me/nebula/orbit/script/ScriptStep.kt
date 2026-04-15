package me.nebula.orbit.script

data class ScriptStep(
    val trigger: ScriptTrigger,
    val actions: List<ScriptAction>,
    val repeatable: Boolean = false,
    val id: String? = null,
) {
    constructor(trigger: ScriptTrigger, action: ScriptAction, repeatable: Boolean = false, id: String? = null)
        : this(trigger, listOf(action), repeatable, id)
}
