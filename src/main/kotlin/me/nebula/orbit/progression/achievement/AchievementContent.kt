package me.nebula.orbit.progression.achievement

import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.achievement.AchievementCategories
import me.nebula.orbit.utils.achievement.AchievementRarity
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.orbit.utils.achievement.AchievementTriggerManager
import me.nebula.orbit.utils.achievement.achievement
import me.nebula.orbit.utils.toast.ToastFrame
import net.minestom.server.item.Material

fun registerAchievementContent() {
    val locale = Orbit.translations.defaultLocale

    AchievementRegistry.register(achievement("first_game") {
        name = Orbit.deserialize("orbit.achievement.first_game.name", locale)
        description = Orbit.deserialize("orbit.achievement.first_game.description", locale)
        category = AchievementCategories.GENERAL
        icon = Material.WOODEN_SWORD
        maxProgress = 1
        points = 5
        rarity = AchievementRarity.COMMON
        tierGroup = "player"
        tierLevel = 1
    })
    AchievementRegistry.register(achievement("veteran") {
        name = Orbit.deserialize("orbit.achievement.veteran.name", locale)
        description = Orbit.deserialize("orbit.achievement.veteran.description", locale)
        category = AchievementCategories.GENERAL
        icon = Material.IRON_SWORD
        maxProgress = 100
        points = 15
        rarity = AchievementRarity.UNCOMMON
        tierGroup = "player"
        tierLevel = 2
    })
    AchievementRegistry.register(achievement("dedicated") {
        name = Orbit.deserialize("orbit.achievement.dedicated.name", locale)
        description = Orbit.deserialize("orbit.achievement.dedicated.description", locale)
        category = AchievementCategories.GENERAL
        icon = Material.DIAMOND_SWORD
        maxProgress = 500
        toastFrame = ToastFrame.CHALLENGE
        points = 50
        rarity = AchievementRarity.RARE
        tierGroup = "player"
        tierLevel = 3
    })
    AchievementRegistry.register(achievement("collector") {
        name = Orbit.deserialize("orbit.achievement.collector.name", locale)
        description = Orbit.deserialize("orbit.achievement.collector.description", locale)
        category = AchievementCategories.GENERAL
        icon = Material.CHEST
        maxProgress = 10
        points = 15
        rarity = AchievementRarity.UNCOMMON
    })

    AchievementRegistry.register(achievement("first_blood") {
        name = Orbit.deserialize("orbit.achievement.first_blood.name", locale)
        description = Orbit.deserialize("orbit.achievement.first_blood.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.IRON_SWORD
        maxProgress = 1
        points = 5
        rarity = AchievementRarity.COMMON
        tierGroup = "kill_master"
        tierLevel = 1
    })
    AchievementRegistry.register(achievement("warrior") {
        name = Orbit.deserialize("orbit.achievement.warrior.name", locale)
        description = Orbit.deserialize("orbit.achievement.warrior.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.DIAMOND_SWORD
        maxProgress = 100
        points = 15
        rarity = AchievementRarity.UNCOMMON
        tierGroup = "kill_master"
        tierLevel = 2
    })
    AchievementRegistry.register(achievement("slayer") {
        name = Orbit.deserialize("orbit.achievement.slayer.name", locale)
        description = Orbit.deserialize("orbit.achievement.slayer.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.NETHERITE_SWORD
        maxProgress = 500
        toastFrame = ToastFrame.CHALLENGE
        points = 50
        rarity = AchievementRarity.RARE
        tierGroup = "kill_master"
        tierLevel = 3
    })
    AchievementRegistry.register(achievement("mass_murderer") {
        name = Orbit.deserialize("orbit.achievement.mass_murderer.name", locale)
        description = Orbit.deserialize("orbit.achievement.mass_murderer.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.WITHER_SKELETON_SKULL
        maxProgress = 1000
        toastFrame = ToastFrame.CHALLENGE
        hidden = true
        points = 100
        rarity = AchievementRarity.LEGENDARY
        tierGroup = "kill_master"
        tierLevel = 4
        reward("cosmetic", 0, "kill_effect_blood")
    })
    AchievementRegistry.register(achievement("double_trouble") {
        name = Orbit.deserialize("orbit.achievement.double_trouble.name", locale)
        description = Orbit.deserialize("orbit.achievement.double_trouble.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.STONE_SWORD
        maxProgress = 1
        points = 5
        rarity = AchievementRarity.COMMON
    })
    AchievementRegistry.register(achievement("unstoppable") {
        name = Orbit.deserialize("orbit.achievement.unstoppable.name", locale)
        description = Orbit.deserialize("orbit.achievement.unstoppable.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.GOLDEN_SWORD
        maxProgress = 1
        toastFrame = ToastFrame.GOAL
        points = 15
        rarity = AchievementRarity.UNCOMMON
    })
    AchievementRegistry.register(achievement("rampage") {
        name = Orbit.deserialize("orbit.achievement.rampage.name", locale)
        description = Orbit.deserialize("orbit.achievement.rampage.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.NETHERITE_SWORD
        maxProgress = 1
        toastFrame = ToastFrame.CHALLENGE
        hidden = true
        points = 100
        rarity = AchievementRarity.LEGENDARY
    })
    AchievementRegistry.register(achievement("sharpshooter") {
        name = Orbit.deserialize("orbit.achievement.sharpshooter.name", locale)
        description = Orbit.deserialize("orbit.achievement.sharpshooter.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.BOW
        maxProgress = 50
        points = 15
        rarity = AchievementRarity.UNCOMMON
    })
    AchievementRegistry.register(achievement("berserker") {
        name = Orbit.deserialize("orbit.achievement.berserker.name", locale)
        description = Orbit.deserialize("orbit.achievement.berserker.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.IRON_AXE
        maxProgress = 10
        points = 15
        rarity = AchievementRarity.UNCOMMON
    })
    AchievementRegistry.register(achievement("pacifist") {
        name = Orbit.deserialize("orbit.achievement.pacifist.name", locale)
        description = Orbit.deserialize("orbit.achievement.pacifist.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.POPPY
        maxProgress = 1
        toastFrame = ToastFrame.CHALLENGE
        hidden = true
        points = 100
        rarity = AchievementRarity.LEGENDARY
    })

    AchievementRegistry.register(achievement("survivor") {
        name = Orbit.deserialize("orbit.achievement.survivor.name", locale)
        description = Orbit.deserialize("orbit.achievement.survivor.description", locale)
        category = AchievementCategories.SURVIVAL
        icon = Material.GOLDEN_APPLE
        maxProgress = 1
        points = 5
        rarity = AchievementRarity.COMMON
        tierGroup = "winner"
        tierLevel = 1
    })
    AchievementRegistry.register(achievement("champion") {
        name = Orbit.deserialize("orbit.achievement.champion.name", locale)
        description = Orbit.deserialize("orbit.achievement.champion.description", locale)
        category = AchievementCategories.SURVIVAL
        icon = Material.ENCHANTED_GOLDEN_APPLE
        maxProgress = 10
        points = 15
        rarity = AchievementRarity.UNCOMMON
        tierGroup = "winner"
        tierLevel = 2
    })
    AchievementRegistry.register(achievement("legend") {
        name = Orbit.deserialize("orbit.achievement.legend.name", locale)
        description = Orbit.deserialize("orbit.achievement.legend.description", locale)
        category = AchievementCategories.SURVIVAL
        icon = Material.NETHER_STAR
        maxProgress = 50
        toastFrame = ToastFrame.CHALLENGE
        points = 50
        rarity = AchievementRarity.RARE
        tierGroup = "winner"
        tierLevel = 3
        reward("cosmetic", 0, "win_effect_legend")
    })
    AchievementRegistry.register(achievement("invincible") {
        name = Orbit.deserialize("orbit.achievement.invincible.name", locale)
        description = Orbit.deserialize("orbit.achievement.invincible.description", locale)
        category = AchievementCategories.SURVIVAL
        icon = Material.TOTEM_OF_UNDYING
        maxProgress = 1
        toastFrame = ToastFrame.CHALLENGE
        hidden = true
        points = 100
        rarity = AchievementRarity.LEGENDARY
    })
    AchievementRegistry.register(achievement("close_call") {
        name = Orbit.deserialize("orbit.achievement.close_call.name", locale)
        description = Orbit.deserialize("orbit.achievement.close_call.description", locale)
        category = AchievementCategories.SURVIVAL
        icon = Material.GOLDEN_APPLE
        maxProgress = 1
        toastFrame = ToastFrame.CHALLENGE
        points = 50
        rarity = AchievementRarity.EPIC
    })
    AchievementRegistry.register(achievement("iron_skin") {
        name = Orbit.deserialize("orbit.achievement.iron_skin.name", locale)
        description = Orbit.deserialize("orbit.achievement.iron_skin.description", locale)
        category = AchievementCategories.SURVIVAL
        icon = Material.IRON_CHESTPLATE
        maxProgress = 1000
        points = 15
        rarity = AchievementRarity.UNCOMMON
    })
    AchievementRegistry.register(achievement("healer") {
        name = Orbit.deserialize("orbit.achievement.healer.name", locale)
        description = Orbit.deserialize("orbit.achievement.healer.description", locale)
        category = AchievementCategories.SURVIVAL
        icon = Material.COOKED_BEEF
        maxProgress = 100
        points = 15
        rarity = AchievementRarity.UNCOMMON
    })

    AchievementRegistry.register(achievement("party_animal") {
        name = Orbit.deserialize("orbit.achievement.party_animal.name", locale)
        description = Orbit.deserialize("orbit.achievement.party_animal.description", locale)
        category = AchievementCategories.SOCIAL
        icon = Material.CAKE
        maxProgress = 10
        points = 15
        rarity = AchievementRarity.UNCOMMON
    })
    AchievementRegistry.register(achievement("host_master") {
        name = Orbit.deserialize("orbit.achievement.host_master.name", locale)
        description = Orbit.deserialize("orbit.achievement.host_master.description", locale)
        category = AchievementCategories.SOCIAL
        icon = Material.BEACON
        maxProgress = 5
        points = 15
        rarity = AchievementRarity.UNCOMMON
    })
    AchievementRegistry.register(achievement("friendly") {
        name = Orbit.deserialize("orbit.achievement.friendly.name", locale)
        description = Orbit.deserialize("orbit.achievement.friendly.description", locale)
        category = AchievementCategories.SOCIAL
        icon = Material.PLAYER_HEAD
        maxProgress = 5
        points = 5
        rarity = AchievementRarity.COMMON
    })
    AchievementRegistry.register(achievement("team_player") {
        name = Orbit.deserialize("orbit.achievement.team_player.name", locale)
        description = Orbit.deserialize("orbit.achievement.team_player.description", locale)
        category = AchievementCategories.SOCIAL
        icon = Material.SHIELD
        maxProgress = 10
        points = 15
        rarity = AchievementRarity.UNCOMMON
    })

    AchievementRegistry.register(achievement("map_explorer") {
        name = Orbit.deserialize("orbit.achievement.map_explorer.name", locale)
        description = Orbit.deserialize("orbit.achievement.map_explorer.description", locale)
        category = AchievementCategories.EXPLORATION
        icon = Material.FILLED_MAP
        maxProgress = 10
        points = 15
        rarity = AchievementRarity.UNCOMMON
        tierGroup = "explorer"
        tierLevel = 1
    })
    AchievementRegistry.register(achievement("world_traveler") {
        name = Orbit.deserialize("orbit.achievement.world_traveler.name", locale)
        description = Orbit.deserialize("orbit.achievement.world_traveler.description", locale)
        category = AchievementCategories.EXPLORATION
        icon = Material.FILLED_MAP
        maxProgress = 25
        toastFrame = ToastFrame.CHALLENGE
        points = 50
        rarity = AchievementRarity.RARE
        tierGroup = "explorer"
        tierLevel = 2
    })

    AchievementRegistry.register(achievement("bp_complete") {
        name = Orbit.deserialize("orbit.achievement.bp_complete.name", locale)
        description = Orbit.deserialize("orbit.achievement.bp_complete.description", locale)
        category = AchievementCategories.MASTERY
        icon = Material.EXPERIENCE_BOTTLE
        maxProgress = 1
        toastFrame = ToastFrame.CHALLENGE
        points = 50
        rarity = AchievementRarity.RARE
        reward("cosmetic", 0, "aura_champion")
    })
    AchievementRegistry.register(achievement("mission_master") {
        name = Orbit.deserialize("orbit.achievement.mission_master.name", locale)
        description = Orbit.deserialize("orbit.achievement.mission_master.description", locale)
        category = AchievementCategories.MASTERY
        icon = Material.COMPASS
        maxProgress = 100
        points = 15
        rarity = AchievementRarity.UNCOMMON
    })
    AchievementRegistry.register(achievement("wealthy") {
        name = Orbit.deserialize("orbit.achievement.wealthy.name", locale)
        description = Orbit.deserialize("orbit.achievement.wealthy.description", locale)
        category = AchievementCategories.MASTERY
        icon = Material.GOLD_INGOT
        maxProgress = 10000
        points = 50
        rarity = AchievementRarity.RARE
    })
    AchievementRegistry.register(achievement("streak_master") {
        name = Orbit.deserialize("orbit.achievement.streak_master.name", locale)
        description = Orbit.deserialize("orbit.achievement.streak_master.description", locale)
        category = AchievementCategories.MASTERY
        icon = Material.BLAZE_ROD
        maxProgress = 1
        toastFrame = ToastFrame.CHALLENGE
        points = 50
        rarity = AchievementRarity.RARE
    })
    AchievementRegistry.register(achievement("completionist") {
        name = Orbit.deserialize("orbit.achievement.completionist.name", locale)
        description = Orbit.deserialize("orbit.achievement.completionist.description", locale)
        category = AchievementCategories.MASTERY
        icon = Material.DIAMOND
        maxProgress = 1
        toastFrame = ToastFrame.CHALLENGE
        hidden = true
        points = 100
        rarity = AchievementRarity.LEGENDARY
    })
    AchievementRegistry.register(achievement("speed_demon") {
        name = Orbit.deserialize("orbit.achievement.speed_demon.name", locale)
        description = Orbit.deserialize("orbit.achievement.speed_demon.description", locale)
        category = AchievementCategories.MASTERY
        icon = Material.SUGAR
        maxProgress = 1
        toastFrame = ToastFrame.CHALLENGE
        hidden = true
        points = 100
        rarity = AchievementRarity.LEGENDARY
    })

    AchievementTriggerManager.bindThreshold("first_game", "br_games_played", 1)
    AchievementTriggerManager.bindThreshold("veteran", "br_games_played", 100)
    AchievementTriggerManager.bindThreshold("dedicated", "br_games_played", 500)
    AchievementTriggerManager.bindThreshold("first_blood", "br_kills", 1)
    AchievementTriggerManager.bindThreshold("warrior", "br_kills", 100)
    AchievementTriggerManager.bindThreshold("slayer", "br_kills", 500)
    AchievementTriggerManager.bindThreshold("mass_murderer", "br_kills", 1000)
    AchievementTriggerManager.bindThreshold("survivor", "br_wins", 1)
    AchievementTriggerManager.bindThreshold("champion", "br_wins", 10)
    AchievementTriggerManager.bindThreshold("legend", "br_wins", 50)
    AchievementTriggerManager.bindThreshold("collector", "cosmetics_owned", 10)
    AchievementTriggerManager.bindThreshold("friendly", "friends_count", 5)
    AchievementTriggerManager.bindThreshold("wealthy", "total_coins_earned", 10000)
    AchievementTriggerManager.bindThreshold("iron_skin", "total_damage_taken", 1000)
    AchievementTriggerManager.bindThreshold("healer", "food_eaten", 100)
    AchievementTriggerManager.bindThreshold("sharpshooter", "bow_kills", 50)
    AchievementTriggerManager.bindThreshold("map_explorer", "unique_maps_played", 10)
    AchievementTriggerManager.bindThreshold("world_traveler", "unique_maps_played", 25)
    AchievementTriggerManager.bindThreshold("team_player", "team_wins", 10)

    AchievementRegistry.onUnlock { player, achievement ->
        AchievementRegistry.defaultUnlockNotification(player, achievement)
    }
}
