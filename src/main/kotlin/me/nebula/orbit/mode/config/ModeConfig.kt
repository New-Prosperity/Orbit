package me.nebula.orbit.mode.config

import net.minestom.server.coordinate.Pos

data class SpawnConfig(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
) {
    fun toPos(): Pos = Pos(x, y, z, yaw, pitch)
}

data class ScoreboardConfig(
    val title: String,
    val refreshSeconds: Int,
    val lines: List<String>,
)

data class TabListConfig(
    val refreshSeconds: Int,
    val header: String,
    val footer: String,
)

data class LobbyConfig(
    val gameMode: String,
    val protectBlocks: Boolean,
    val disableDamage: Boolean,
    val disableHunger: Boolean,
    val lockInventory: Boolean,
    val voidTeleportY: Double,
)

data class HotbarItemConfig(
    val slot: Int,
    val material: String,
    val name: String,
    val glowing: Boolean,
    val action: String,
)

data class CosmeticConfig(
    val enabledCategories: List<String> = listOf("ARMOR_SKIN", "KILL_EFFECT", "TRAIL", "WIN_EFFECT", "PROJECTILE_TRAIL"),
    val blacklist: List<String> = emptyList(),
)
