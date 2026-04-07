package me.nebula.orbit.mutator

data class Mutator(
    val id: String,
    val nameKey: String,
    val descriptionKey: String,
    val material: String,
    val overrides: Map<String, Map<String, Any>>,
    val disables: Set<String> = emptySet(),
    val conflictGroup: String? = null,
    val random: Boolean = false,
    val weight: Int = 1,
)
