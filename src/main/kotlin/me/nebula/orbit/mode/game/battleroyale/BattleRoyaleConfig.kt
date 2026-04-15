package me.nebula.orbit.mode.game.battleroyale

import me.nebula.ether.utils.translation.TranslationKey

data class BorderConfig(
    val initialDiameter: Double,
    val finalDiameter: Double,
    val centerX: Double,
    val centerZ: Double,
    val shrinkStartSeconds: Int,
    val shrinkDurationSeconds: Int,
)

data class BorderPhaseConfig(
    val startAfterSeconds: Int,
    val targetDiameter: Double,
    val shrinkDurationSeconds: Int,
    val damagePerSecond: Float = 1f,
)

data class StarterKitConfig(
    val helmet: String? = null,
    val chestplate: String? = null,
    val leggings: String? = null,
    val boots: String? = null,
    val items: List<StarterKitItemConfig> = emptyList(),
)

data class StarterKitItemConfig(
    val slot: Int,
    val material: String,
    val amount: Int = 1,
)

data class GoldenHeadConfig(
    val enabled: Boolean = true,
    val healAmount: Float = 8f,
    val absorptionHearts: Float = 4f,
    val regenDurationTicks: Int = 100,
    val regenAmplifier: Int = 1,
)

data class DeathmatchConfig(
    val enabled: Boolean = true,
    val triggerAtPlayers: Int = 3,
    val teleportToCenter: Boolean = true,
    val borderDiameter: Double = 50.0,
    val borderShrinkSeconds: Int = 60,
    val checkDelaySeconds: Int = 600,
)

data class KitDefinitionConfig(
    val id: String,
    val nameKey: TranslationKey,
    val descriptionKey: TranslationKey,
    val material: String,
    val locked: Boolean = false,
    val maxLevel: Int = 3,
    val xpPerLevel: List<Long> = listOf(100, 250, 500),
    val tiers: Map<Int, KitTierConfig> = emptyMap(),
)

data class KitTierConfig(
    val helmet: String? = null,
    val chestplate: String? = null,
    val leggings: String? = null,
    val boots: String? = null,
    val items: List<StarterKitItemConfig> = emptyList(),
)
