package me.nebula.orbit.mode.game.battleroyale.seasons

import me.nebula.orbit.utils.chestloot.ChestLootBuilder
import me.nebula.orbit.utils.chestloot.ChestLootTable
import me.nebula.orbit.utils.chestloot.LootRarity
import net.minestom.server.item.Material

internal fun buildStandardChest(): ChestLootTable {
    val builder = ChestLootBuilder("br_chest_standard").apply {
        itemsPerChest(3..6)

        tier("common", LootRarity.COMMON) {
            item(Material.BREAD, 1..3, weight = 20)
            item(Material.COOKED_BEEF, 1..2, weight = 15)
            item(Material.APPLE, 2..4, weight = 15)
            item(Material.STICK, 2..4, weight = 10)
            item(Material.ARROW, 4..12, weight = 18)
            item(Material.TORCH, 4..8, weight = 10)
            item(Material.LEATHER_HELMET, 1..1, weight = 8)
            item(Material.LEATHER_CHESTPLATE, 1..1, weight = 6)
            item(Material.WOODEN_SWORD, 1..1, weight = 8)
            item(Material.STONE_AXE, 1..1, weight = 6)
            item(Material.COBWEB, 1..2, weight = 4)
        }

        tier("uncommon", LootRarity.UNCOMMON) {
            item(Material.IRON_INGOT, 1..3, weight = 18)
            item(Material.IRON_SWORD, 1..1, weight = 12)
            item(Material.IRON_AXE, 1..1, weight = 8)
            item(Material.IRON_HELMET, 1..1, weight = 10)
            item(Material.IRON_CHESTPLATE, 1..1, weight = 8)
            item(Material.CHAINMAIL_CHESTPLATE, 1..1, weight = 6)
            item(Material.BOW, 1..1, weight = 10)
            item(Material.ARROW, 8..16, weight = 10)
            item(Material.GOLDEN_CARROT, 2..4, weight = 8)
            item(Material.FISHING_ROD, 1..1, weight = 6)
        }

        tier("rare", LootRarity.RARE) {
            item(Material.DIAMOND, 1..2, weight = 15)
            item(Material.DIAMOND_SWORD, 1..1, weight = 10, maxPerChest = 1)
            item(Material.DIAMOND_HELMET, 1..1, weight = 8, maxPerChest = 1)
            item(Material.DIAMOND_CHESTPLATE, 1..1, weight = 6, maxPerChest = 1)
            item(Material.ENDER_PEARL, 1..2, weight = 12)
            item(Material.GOLDEN_APPLE, 1..2, weight = 10)
            item(Material.CROSSBOW, 1..1, weight = 8)
            item(Material.SPECTRAL_ARROW, 4..8, weight = 6)
        }

        tier("epic", LootRarity.EPIC) {
            item(Material.ENCHANTED_GOLDEN_APPLE, 1..1, weight = 10, maxPerChest = 1)
            item(Material.DIAMOND_CHESTPLATE, 1..1, weight = 6, maxPerChest = 1)
            item(Material.NETHERITE_SCRAP, 1..1, weight = 4)
            item(Material.TRIDENT, 1..1, weight = 5, maxPerChest = 1)
            item(Material.POTION, 1..2, weight = 6)
            item(Material.SPLASH_POTION, 1..2, weight = 5)
        }

        tier("legendary", LootRarity.LEGENDARY) {
            item(Material.TOTEM_OF_UNDYING, 1..1, weight = 10, maxPerChest = 1)
            item(Material.NETHERITE_INGOT, 1..1, weight = 6, maxPerChest = 1)
            item(Material.NETHERITE_SWORD, 1..1, weight = 4, maxPerChest = 1)
            item(Material.NETHERITE_CHESTPLATE, 1..1, weight = 3, maxPerChest = 1)
        }

        biomeHintByRarity("ice_spikes") {
            common(70)
            uncommon(20)
            rare(9)
            epic(1)
        }

        biomeHintByRarity("mountains") {
            common(40)
            uncommon(30)
            rare(22)
            epic(7)
            legendary(1)
        }

        biomeHintByRarity("swamp") {
            common(55)
            uncommon(30)
            rare(12)
            epic(3)
        }

        biomeHintByRarity("badlands") {
            common(35)
            uncommon(35)
            rare(20)
            epic(9)
            legendary(1)
        }

        biomeHintByRarity("lush_caves") {
            common(60)
            uncommon(28)
            rare(10)
            epic(2)
        }
    }
    return builder.build()
}

internal fun buildAirdropChest(): ChestLootTable {
    val builder = ChestLootBuilder("br_chest_airdrop").apply {
        itemsPerChest(5..8)

        tier("uncommon", LootRarity.UNCOMMON) {
            item(Material.IRON_INGOT, 2..4, weight = 10)
            item(Material.GOLDEN_CARROT, 4..8, weight = 8)
            item(Material.ARROW, 16..32, weight = 8)
        }

        tier("rare", LootRarity.RARE) {
            item(Material.DIAMOND, 2..3, weight = 12)
            item(Material.DIAMOND_SWORD, 1..1, weight = 8, maxPerChest = 1)
            item(Material.DIAMOND_CHESTPLATE, 1..1, weight = 7, maxPerChest = 1)
            item(Material.ENDER_PEARL, 2..4, weight = 10)
            item(Material.GOLDEN_APPLE, 2..3, weight = 10)
            item(Material.BOW, 1..1, weight = 6)
            item(Material.CROSSBOW, 1..1, weight = 5)
        }

        tier("epic", LootRarity.EPIC) {
            item(Material.ENCHANTED_GOLDEN_APPLE, 1..2, weight = 10)
            item(Material.NETHERITE_SCRAP, 1..2, weight = 6)
            item(Material.TRIDENT, 1..1, weight = 5, maxPerChest = 1)
            item(Material.POTION, 1..3, weight = 8)
            item(Material.SPLASH_POTION, 1..3, weight = 6)
            item(Material.DIAMOND_HELMET, 1..1, weight = 6, maxPerChest = 1)
            item(Material.DIAMOND_LEGGINGS, 1..1, weight = 6, maxPerChest = 1)
        }

        tier("legendary", LootRarity.LEGENDARY) {
            item(Material.TOTEM_OF_UNDYING, 1..1, weight = 12, maxPerChest = 1)
            item(Material.NETHERITE_INGOT, 1..2, weight = 8)
            item(Material.NETHERITE_SWORD, 1..1, weight = 6, maxPerChest = 1)
            item(Material.NETHERITE_CHESTPLATE, 1..1, weight = 5, maxPerChest = 1)
            item(Material.LODESTONE, 1..1, weight = 4, maxPerChest = 1)
            item(Material.ELYTRA, 1..1, weight = 2, maxPerChest = 1)
        }
    }
    return builder.build()
}

internal fun buildKillstreakChest(): ChestLootTable {
    val builder = ChestLootBuilder("br_chest_killstreak").apply {
        itemsPerChest(4..6)

        tier("rare", LootRarity.RARE) {
            item(Material.GOLDEN_APPLE, 3..5, weight = 10)
            item(Material.ENDER_PEARL, 3..5, weight = 8)
            item(Material.DIAMOND_SWORD, 1..1, weight = 8, maxPerChest = 1)
        }

        tier("epic", LootRarity.EPIC) {
            item(Material.ENCHANTED_GOLDEN_APPLE, 2..3, weight = 12)
            item(Material.DIAMOND_HELMET, 1..1, weight = 8, maxPerChest = 1)
            item(Material.DIAMOND_CHESTPLATE, 1..1, weight = 8, maxPerChest = 1)
            item(Material.DIAMOND_LEGGINGS, 1..1, weight = 8, maxPerChest = 1)
            item(Material.DIAMOND_BOOTS, 1..1, weight = 8, maxPerChest = 1)
            item(Material.POTION, 1..3, weight = 10)
            item(Material.SPLASH_POTION, 1..3, weight = 8)
            item(Material.TNT, 2..4, weight = 5)
        }

        tier("legendary", LootRarity.LEGENDARY) {
            item(Material.TOTEM_OF_UNDYING, 1..2, weight = 18)
            item(Material.NETHERITE_INGOT, 2..3, weight = 12)
            item(Material.NETHERITE_SWORD, 1..1, weight = 10, maxPerChest = 1)
            item(Material.NETHERITE_CHESTPLATE, 1..1, weight = 8, maxPerChest = 1)
            item(Material.ELYTRA, 1..1, weight = 4, maxPerChest = 1)
            item(Material.DRAGON_BREATH, 1..2, weight = 3)
        }
    }
    return builder.build()
}

