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
)
