package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticCategory

enum class CosmeticRarity(val colorTag: String) {
    COMMON("<gray>"),
    RARE("<blue>"),
    EPIC("<dark_purple>"),
    LEGENDARY("<gold>"),
}

data class CosmeticDefinition(
    val id: String,
    val category: CosmeticCategory,
    val nameKey: String,
    val descriptionKey: String,
    val rarity: CosmeticRarity,
    val material: String,
    val data: Map<String, String> = emptyMap(),
    val maxLevel: Int = 1,
    val levelOverrides: Map<Int, Map<String, String>> = emptyMap(),
) {

    fun resolveData(level: Int): Map<String, String> {
        val resolved = data.toMutableMap()
        levelOverrides.keys.sorted().filter { it <= level }.forEach { resolved.putAll(levelOverrides.getValue(it)) }
        return resolved
    }
}
