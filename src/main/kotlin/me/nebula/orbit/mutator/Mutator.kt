package me.nebula.orbit.mutator

import me.nebula.ether.utils.translation.TranslationKey

data class Mutator(
    val id: String,
    val nameKey: TranslationKey,
    val descriptionKey: TranslationKey,
    val material: String,
    val overrides: Map<String, Map<String, Any>>,
    val disables: Set<String> = emptySet(),
    val conflictGroup: String? = null,
    val random: Boolean = false,
    val weight: Int = 1,
)
