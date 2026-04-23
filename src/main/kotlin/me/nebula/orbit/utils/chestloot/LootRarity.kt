package me.nebula.orbit.utils.chestloot

enum class LootRarity(
    val colorTag: String,
    val defaultTierWeight: Int,
    val displayLabel: String,
) {
    COMMON(colorTag = "<gray>", defaultTierWeight = 50, displayLabel = "Common"),
    UNCOMMON(colorTag = "<green>", defaultTierWeight = 30, displayLabel = "Uncommon"),
    RARE(colorTag = "<aqua>", defaultTierWeight = 15, displayLabel = "Rare"),
    EPIC(colorTag = "<light_purple>", defaultTierWeight = 5, displayLabel = "Epic"),
    LEGENDARY(colorTag = "<gold>", defaultTierWeight = 1, displayLabel = "Legendary"),
    ;

    companion object {
        fun inferFromName(tierName: String): LootRarity = when (tierName.trim().lowercase()) {
            "common" -> COMMON
            "uncommon" -> UNCOMMON
            "rare" -> RARE
            "epic" -> EPIC
            "legendary" -> LEGENDARY
            else -> COMMON
        }
    }
}
