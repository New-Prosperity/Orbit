package me.nebula.orbit.loadout

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.loadout.BonusPayload
import me.nebula.gravity.loadout.BonusTier
import me.nebula.gravity.loadout.DeliveryPolicy
import me.nebula.gravity.loadout.LoadoutBonusDefinition
import me.nebula.gravity.loadout.LoadoutCatalog
import me.nebula.gravity.loadout.LoadoutItemDefinition
import me.nebula.gravity.loadout.LoadoutPayload
import me.nebula.gravity.loadout.LoadoutPresetDefinition
import me.nebula.gravity.loadout.UnlockRequirement

private const val MODE = "battleroyale"

private fun brItem(
    id: String,
    cost: Int,
    payload: LoadoutPayload,
    material: String,
    tags: Set<String> = emptySet(),
    conflictGroup: String? = null,
    unlock: UnlockRequirement = UnlockRequirement.Free,
    delivery: DeliveryPolicy = DeliveryPolicy.Immediate,
): LoadoutItemDefinition = LoadoutItemDefinition(
    id = id,
    modeId = MODE,
    cost = cost,
    tags = tags,
    conflictGroup = conflictGroup,
    unlock = unlock,
    delivery = delivery,
    payload = payload,
    nameKey = "orbit.loadout.br.item.$id.name".asTranslationKey(),
    descriptionKey = "orbit.loadout.br.item.$id.desc".asTranslationKey(),
    material = material,
)

private fun brBonus(
    id: String,
    cost: Int,
    tier: BonusTier,
    payload: BonusPayload,
    material: String,
    tags: Set<String> = emptySet(),
    conflictGroup: String? = null,
    unlock: UnlockRequirement = UnlockRequirement.Free,
    delivery: DeliveryPolicy = DeliveryPolicy.Immediate,
): LoadoutBonusDefinition = LoadoutBonusDefinition(
    id = id,
    modeId = MODE,
    cost = cost,
    tier = tier,
    tags = tags,
    conflictGroup = conflictGroup,
    unlock = unlock,
    delivery = delivery,
    payload = payload,
    nameKey = "orbit.loadout.br.bonus.$id.name".asTranslationKey(),
    descriptionKey = "orbit.loadout.br.bonus.$id.desc".asTranslationKey(),
    material = material,
)

private fun brPreset(
    id: String,
    itemIds: List<String>,
    bonusIds: List<String> = emptyList(),
    material: String,
    unlock: UnlockRequirement = UnlockRequirement.Free,
): LoadoutPresetDefinition = LoadoutPresetDefinition(
    id = id,
    modeId = MODE,
    itemIds = itemIds,
    bonusIds = bonusIds,
    unlock = unlock,
    nameKey = "orbit.loadout.br.preset.$id.name".asTranslationKey(),
    descriptionKey = "orbit.loadout.br.preset.$id.desc".asTranslationKey(),
    material = material,
)

fun installBattleRoyaleLoadouts() {
    // ITEMS — 20 total

    LoadoutCatalog.registerItem(brItem(
        id = "wooden_sword", cost = 1, material = "minecraft:wooden_sword",
        tags = setOf("weapon", "melee"), conflictGroup = "primary_melee",
        payload = LoadoutPayload.Material(id = "minecraft:wooden_sword"),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "stone_axe", cost = 2, material = "minecraft:stone_axe",
        tags = setOf("weapon", "melee"), conflictGroup = "primary_melee",
        payload = LoadoutPayload.Material(id = "minecraft:stone_axe"),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "iron_sword", cost = 3, material = "minecraft:iron_sword",
        tags = setOf("weapon", "melee"), conflictGroup = "primary_melee",
        unlock = UnlockRequirement.ModeLevel(MODE, 5),
        payload = LoadoutPayload.Material(id = "minecraft:iron_sword"),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "iron_axe", cost = 4, material = "minecraft:iron_axe",
        tags = setOf("weapon", "melee"), conflictGroup = "primary_melee",
        unlock = UnlockRequirement.ModeLevel(MODE, 10),
        payload = LoadoutPayload.Material(id = "minecraft:iron_axe"),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "diamond_sword", cost = 6, material = "minecraft:diamond_sword",
        tags = setOf("weapon", "melee"), conflictGroup = "primary_melee",
        unlock = UnlockRequirement.ModeLevel(MODE, 25),
        delivery = DeliveryPolicy.AfterTruce,
        payload = LoadoutPayload.Material(id = "minecraft:diamond_sword"),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "trident", cost = 5, material = "minecraft:trident",
        tags = setOf("weapon", "melee", "ranged"), conflictGroup = "primary_melee",
        unlock = UnlockRequirement.ModeLevel(MODE, 30),
        delivery = DeliveryPolicy.AfterTruce,
        payload = LoadoutPayload.Material(id = "minecraft:trident"),
    ))

    LoadoutCatalog.registerItem(brItem(
        id = "bow", cost = 3, material = "minecraft:bow",
        tags = setOf("weapon", "ranged"),
        payload = LoadoutPayload.Material(id = "minecraft:bow"),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "crossbow", cost = 4, material = "minecraft:crossbow",
        tags = setOf("weapon", "ranged"),
        unlock = UnlockRequirement.ModeLevel(MODE, 15),
        payload = LoadoutPayload.Material(id = "minecraft:crossbow"),
    ))

    LoadoutCatalog.registerItem(brItem(
        id = "leather_set", cost = 2, material = "minecraft:leather_chestplate",
        tags = setOf("armor"), conflictGroup = "armor_set",
        payload = LoadoutPayload.ArmorSet(
            helmet = "minecraft:leather_helmet",
            chestplate = "minecraft:leather_chestplate",
            leggings = "minecraft:leather_leggings",
            boots = "minecraft:leather_boots",
        ),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "chain_set", cost = 3, material = "minecraft:chainmail_chestplate",
        tags = setOf("armor"), conflictGroup = "armor_set",
        unlock = UnlockRequirement.ModeLevel(MODE, 8),
        payload = LoadoutPayload.ArmorSet(
            helmet = "minecraft:chainmail_helmet",
            chestplate = "minecraft:chainmail_chestplate",
            leggings = "minecraft:chainmail_leggings",
            boots = "minecraft:chainmail_boots",
        ),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "iron_chestplate", cost = 3, material = "minecraft:iron_chestplate",
        tags = setOf("armor"), conflictGroup = "chest_armor",
        unlock = UnlockRequirement.ModeLevel(MODE, 15),
        payload = LoadoutPayload.ArmorSet(chestplate = "minecraft:iron_chestplate"),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "diamond_chestplate", cost = 6, material = "minecraft:diamond_chestplate",
        tags = setOf("armor"), conflictGroup = "chest_armor",
        unlock = UnlockRequirement.ModeLevel(MODE, 35),
        delivery = DeliveryPolicy.AfterTruce,
        payload = LoadoutPayload.ArmorSet(chestplate = "minecraft:diamond_chestplate"),
    ))

    LoadoutCatalog.registerItem(brItem(
        id = "ender_pearl", cost = 1, material = "minecraft:ender_pearl",
        tags = setOf("utility"),
        unlock = UnlockRequirement.ModeLevel(MODE, 3),
        delivery = DeliveryPolicy.AfterSeconds(120),
        payload = LoadoutPayload.Material(id = "minecraft:ender_pearl"),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "ender_pearl_pack", cost = 3, material = "minecraft:ender_pearl",
        tags = setOf("utility"),
        unlock = UnlockRequirement.ModeLevel(MODE, 20),
        delivery = DeliveryPolicy.AfterTruce,
        payload = LoadoutPayload.Material(id = "minecraft:ender_pearl", amount = 3),
    ))

    LoadoutCatalog.registerItem(brItem(
        id = "arrows_8", cost = 1, material = "minecraft:arrow",
        tags = setOf("utility", "ammo"),
        payload = LoadoutPayload.Material(id = "minecraft:arrow", amount = 8),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "arrows_32", cost = 2, material = "minecraft:arrow",
        tags = setOf("utility", "ammo"),
        unlock = UnlockRequirement.ModeLevel(MODE, 10),
        payload = LoadoutPayload.Material(id = "minecraft:arrow", amount = 32),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "throwable_tnt", cost = 3, material = "minecraft:tnt",
        tags = setOf("utility", "explosive"),
        unlock = UnlockRequirement.ModeLevel(MODE, 40),
        delivery = DeliveryPolicy.AfterTruce,
        payload = LoadoutPayload.Material(id = "minecraft:tnt", amount = 2),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "fishing_rod", cost = 2, material = "minecraft:fishing_rod",
        tags = setOf("utility"),
        payload = LoadoutPayload.Material(id = "minecraft:fishing_rod"),
    ))

    LoadoutCatalog.registerItem(brItem(
        id = "golden_apple", cost = 2, material = "minecraft:golden_apple",
        tags = setOf("consumable"),
        payload = LoadoutPayload.Material(id = "minecraft:golden_apple"),
    ))
    LoadoutCatalog.registerItem(brItem(
        id = "golden_apple_pack", cost = 4, material = "minecraft:golden_apple",
        tags = setOf("consumable"),
        unlock = UnlockRequirement.ModeLevel(MODE, 12),
        payload = LoadoutPayload.Material(id = "minecraft:golden_apple", amount = 3),
    ))

    // BONUSES — 15 total across tiers

    LoadoutCatalog.registerBonus(brBonus(
        id = "sprint_boost", cost = 1, tier = BonusTier.MINOR,
        material = "minecraft:feather",
        tags = setOf("movement"), conflictGroup = "movement_minor",
        payload = BonusPayload.SpawnEffect("minecraft:speed", amplifier = 0, durationTicks = 1200),
    ))
    LoadoutCatalog.registerBonus(brBonus(
        id = "silent_steps", cost = 1, tier = BonusTier.MINOR,
        material = "minecraft:wool",
        tags = setOf("movement", "stealth"), conflictGroup = "movement_minor",
        payload = BonusPayload.Hook("silent_steps"),
    ))
    LoadoutCatalog.registerBonus(brBonus(
        id = "hardened", cost = 1, tier = BonusTier.MINOR,
        material = "minecraft:iron_boots",
        tags = setOf("survival"),
        unlock = UnlockRequirement.ModeLevel(MODE, 5),
        payload = BonusPayload.Hook("hardened"),
    ))
    LoadoutCatalog.registerBonus(brBonus(
        id = "scavenger", cost = 1, tier = BonusTier.MINOR,
        material = "minecraft:gold_nugget",
        tags = setOf("economy"),
        payload = BonusPayload.Hook("scavenger"),
    ))

    LoadoutCatalog.registerBonus(brBonus(
        id = "thermal_sight", cost = 2, tier = BonusTier.STANDARD,
        material = "minecraft:spyglass",
        tags = setOf("perception"),
        unlock = UnlockRequirement.ModeLevel(MODE, 8),
        payload = BonusPayload.SpawnEffect("minecraft:night_vision", 0, durationTicks = 6000),
    ))
    LoadoutCatalog.registerBonus(brBonus(
        id = "regenerator", cost = 2, tier = BonusTier.STANDARD,
        material = "minecraft:glistering_melon_slice",
        tags = setOf("survival"),
        unlock = UnlockRequirement.ModeLevel(MODE, 10),
        payload = BonusPayload.SpawnEffect("minecraft:regeneration", 0, durationTicks = 600),
    ))
    LoadoutCatalog.registerBonus(brBonus(
        id = "forager", cost = 2, tier = BonusTier.STANDARD,
        material = "minecraft:chest",
        tags = setOf("economy"),
        unlock = UnlockRequirement.ModeLevel(MODE, 12),
        payload = BonusPayload.Hook("forager"),
    ))
    LoadoutCatalog.registerBonus(brBonus(
        id = "frenzy", cost = 2, tier = BonusTier.STANDARD,
        material = "minecraft:blaze_powder",
        tags = setOf("combat"), conflictGroup = "on_kill_buff",
        unlock = UnlockRequirement.ModeLevel(MODE, 15),
        payload = BonusPayload.Hook("frenzy"),
    ))
    LoadoutCatalog.registerBonus(brBonus(
        id = "vampiric", cost = 2, tier = BonusTier.STANDARD,
        material = "minecraft:ghast_tear",
        tags = setOf("combat", "survival"),
        unlock = UnlockRequirement.ModeLevel(MODE, 18),
        payload = BonusPayload.Hook("vampiric"),
    ))

    LoadoutCatalog.registerBonus(brBonus(
        id = "tracker", cost = 3, tier = BonusTier.STRONG,
        material = "minecraft:compass",
        tags = setOf("perception"), conflictGroup = "enemy_sense",
        unlock = UnlockRequirement.ModeLevel(MODE, 20),
        payload = BonusPayload.Hook("tracker"),
    ))
    LoadoutCatalog.registerBonus(brBonus(
        id = "bloodhound", cost = 3, tier = BonusTier.STRONG,
        material = "minecraft:recovery_compass",
        tags = setOf("perception"), conflictGroup = "enemy_sense",
        unlock = UnlockRequirement.ModeLevel(MODE, 25),
        payload = BonusPayload.Hook("bloodhound"),
    ))
    LoadoutCatalog.registerBonus(brBonus(
        id = "radar_silence", cost = 3, tier = BonusTier.STRONG,
        material = "minecraft:phantom_membrane",
        tags = setOf("stealth"),
        unlock = UnlockRequirement.ModeLevel(MODE, 30),
        payload = BonusPayload.Hook("radar_silence"),
    ))

    LoadoutCatalog.registerBonus(brBonus(
        id = "sixth_sense", cost = 4, tier = BonusTier.ELITE,
        material = "minecraft:ender_eye",
        tags = setOf("perception"), conflictGroup = "enemy_sense",
        unlock = UnlockRequirement.ModeLevel(MODE, 35),
        payload = BonusPayload.Hook("sixth_sense"),
    ))
    LoadoutCatalog.registerBonus(brBonus(
        id = "overload", cost = 4, tier = BonusTier.ELITE,
        material = "minecraft:netherite_scrap",
        tags = setOf("combat"), conflictGroup = "on_kill_buff",
        unlock = UnlockRequirement.ModeLevel(MODE, 40),
        payload = BonusPayload.Hook("overload"),
    ))

    LoadoutCatalog.registerBonus(brBonus(
        id = "last_stand", cost = 5, tier = BonusTier.LEGENDARY,
        material = "minecraft:totem_of_undying",
        tags = setOf("survival"),
        unlock = UnlockRequirement.Composite(listOf(
            UnlockRequirement.ModeLevel(MODE, 45),
            UnlockRequirement.Challenge("br_wins_100"),
        )),
        payload = BonusPayload.Hook("last_stand"),
    ))

    // PRESETS — 5 total

    LoadoutCatalog.registerPreset(brPreset(
        id = "aggressive",
        itemIds = listOf("diamond_sword", "iron_chestplate", "golden_apple"),
        bonusIds = listOf("frenzy"),
        material = "minecraft:diamond_sword",
        unlock = UnlockRequirement.ModeLevel(MODE, 15),
    ))
    LoadoutCatalog.registerPreset(brPreset(
        id = "ranged",
        itemIds = listOf("bow", "arrows_32", "leather_set"),
        bonusIds = listOf("thermal_sight"),
        material = "minecraft:bow",
        unlock = UnlockRequirement.ModeLevel(MODE, 10),
    ))
    LoadoutCatalog.registerPreset(brPreset(
        id = "survivor",
        itemIds = listOf("iron_sword", "chain_set", "golden_apple_pack"),
        bonusIds = listOf("regenerator", "hardened"),
        material = "minecraft:golden_apple",
        unlock = UnlockRequirement.ModeLevel(MODE, 12),
    ))
    LoadoutCatalog.registerPreset(brPreset(
        id = "speedster",
        itemIds = listOf("stone_axe", "leather_set"),
        bonusIds = listOf("sprint_boost", "forager"),
        material = "minecraft:feather",
    ))
    LoadoutCatalog.registerPreset(brPreset(
        id = "hunter",
        itemIds = listOf("crossbow", "iron_sword", "arrows_8"),
        bonusIds = listOf("tracker", "scavenger"),
        material = "minecraft:crossbow",
        unlock = UnlockRequirement.ModeLevel(MODE, 20),
    ))
}
