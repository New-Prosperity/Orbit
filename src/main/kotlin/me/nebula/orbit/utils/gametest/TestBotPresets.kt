package me.nebula.orbit.utils.gametest

import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

data class TestBotPreset(
    val behavior: TestBehavior,
    val config: BehaviorConfig = BehaviorConfig(),
    val equipment: Map<EquipmentSlot, ItemStack> = emptyMap(),
    val health: Float = 20f,
    val food: Int = 20,
)

object TestBotPresets {

    val DIAMOND_AGGRESSIVE = TestBotPreset(
        behavior = TestBehavior.AGGRESSIVE,
        equipment = mapOf(
            EquipmentSlot.MAIN_HAND to ItemStack.of(Material.DIAMOND_SWORD),
            EquipmentSlot.HELMET to ItemStack.of(Material.DIAMOND_HELMET),
            EquipmentSlot.CHESTPLATE to ItemStack.of(Material.DIAMOND_CHESTPLATE),
            EquipmentSlot.LEGGINGS to ItemStack.of(Material.DIAMOND_LEGGINGS),
            EquipmentSlot.BOOTS to ItemStack.of(Material.DIAMOND_BOOTS),
        ),
    )

    val IRON_DEFENSIVE = TestBotPreset(
        behavior = TestBehavior.DEFENSIVE,
        config = BehaviorConfig(fleeHealth = 8.0f),
        equipment = mapOf(
            EquipmentSlot.MAIN_HAND to ItemStack.of(Material.IRON_SWORD),
            EquipmentSlot.HELMET to ItemStack.of(Material.IRON_HELMET),
            EquipmentSlot.CHESTPLATE to ItemStack.of(Material.IRON_CHESTPLATE),
            EquipmentSlot.LEGGINGS to ItemStack.of(Material.IRON_LEGGINGS),
            EquipmentSlot.BOOTS to ItemStack.of(Material.IRON_BOOTS),
        ),
    )

    val NAKED_WANDERER = TestBotPreset(
        behavior = TestBehavior.WANDER,
        config = BehaviorConfig(wanderRadius = 15.0),
    )

    val ARCHER = TestBotPreset(
        behavior = TestBehavior.AGGRESSIVE,
        config = BehaviorConfig(attackRange = 16.0),
        equipment = mapOf(
            EquipmentSlot.MAIN_HAND to ItemStack.of(Material.BOW),
            EquipmentSlot.HELMET to ItemStack.of(Material.LEATHER_HELMET),
            EquipmentSlot.CHESTPLATE to ItemStack.of(Material.LEATHER_CHESTPLATE),
            EquipmentSlot.LEGGINGS to ItemStack.of(Material.LEATHER_LEGGINGS),
            EquipmentSlot.BOOTS to ItemStack.of(Material.LEATHER_BOOTS),
        ),
    )

    val CHAOTIC = TestBotPreset(
        behavior = TestBehavior.CHAOS,
    )

    private val registry = mapOf(
        "diamond_aggressive" to DIAMOND_AGGRESSIVE,
        "iron_defensive" to IRON_DEFENSIVE,
        "naked_wanderer" to NAKED_WANDERER,
        "archer" to ARCHER,
        "chaotic" to CHAOTIC,
    )

    fun get(name: String): TestBotPreset? = registry[name.lowercase()]

    fun names(): Set<String> = registry.keys
}
