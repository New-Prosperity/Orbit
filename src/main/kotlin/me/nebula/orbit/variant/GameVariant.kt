package me.nebula.orbit.variant

import me.nebula.ether.utils.translation.TranslationKey

data class GameVariant(
    val id: String,
    val nameKey: TranslationKey,
    val descriptionKey: TranslationKey,
    val material: String = "minecraft:paper",
    val components: List<GameComponent>,
    val random: Boolean = true,
    val weight: Int = 1,
    val conflictGroup: String? = null,
) {
    inline fun <reified T : GameComponent> find(): T? = components.firstOrNull { it is T } as? T
    inline fun <reified T : GameComponent> all(): List<T> = components.filterIsInstance<T>()
}
