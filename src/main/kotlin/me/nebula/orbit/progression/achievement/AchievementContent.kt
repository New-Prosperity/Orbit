package me.nebula.orbit.progression.achievement

import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.cosmetic.UnlockCosmeticProcessor
import me.nebula.orbit.utils.achievement.AchievementCategories
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.orbit.utils.achievement.AchievementTriggerManager
import me.nebula.orbit.utils.achievement.achievement
import net.kyori.adventure.text.Component
import net.minestom.server.advancements.FrameType
import net.minestom.server.item.Material

fun registerAchievementContent() {
    AchievementRegistry.register(achievement("first_game") {
        name = Component.text("First Steps")
        description = Component.text("Play your first game")
        category = AchievementCategories.GENERAL
        icon = Material.WOODEN_SWORD
        maxProgress = 1
    })
    AchievementRegistry.register(achievement("veteran") {
        name = Component.text("Veteran")
        description = Component.text("Play 100 games")
        category = AchievementCategories.GENERAL
        icon = Material.IRON_SWORD
        maxProgress = 100
    })
    AchievementRegistry.register(achievement("dedicated") {
        name = Component.text("Dedicated")
        description = Component.text("Play 500 games")
        category = AchievementCategories.GENERAL
        icon = Material.DIAMOND_SWORD
        maxProgress = 500
        frameType = FrameType.CHALLENGE
    })
    AchievementRegistry.register(achievement("collector") {
        name = Component.text("Collector")
        description = Component.text("Own 10 cosmetics")
        category = AchievementCategories.GENERAL
        icon = Material.CHEST
        maxProgress = 10
    })

    AchievementRegistry.register(achievement("first_blood") {
        name = Component.text("First Blood")
        description = Component.text("Get your first kill")
        category = AchievementCategories.COMBAT
        icon = Material.IRON_SWORD
        maxProgress = 1
    })
    AchievementRegistry.register(achievement("warrior") {
        name = Component.text("Warrior")
        description = Component.text("Get 100 kills")
        category = AchievementCategories.COMBAT
        icon = Material.DIAMOND_SWORD
        maxProgress = 100
    })
    AchievementRegistry.register(achievement("slayer") {
        name = Component.text("Slayer")
        description = Component.text("Get 500 kills")
        category = AchievementCategories.COMBAT
        icon = Material.NETHERITE_SWORD
        maxProgress = 500
        frameType = FrameType.CHALLENGE
    })
    AchievementRegistry.register(achievement("mass_murderer") {
        name = Component.text("Mass Murderer")
        description = Component.text("Get 1000 kills")
        category = AchievementCategories.COMBAT
        icon = Material.WITHER_SKELETON_SKULL
        maxProgress = 1000
        frameType = FrameType.CHALLENGE
        hidden = true
    })
    AchievementRegistry.register(achievement("double_trouble") {
        name = Component.text("Double Trouble")
        description = Component.text("Get a 2 kill streak in a single game")
        category = AchievementCategories.COMBAT
        icon = Material.STONE_SWORD
        maxProgress = 1
    })
    AchievementRegistry.register(achievement("unstoppable") {
        name = Component.text("Unstoppable")
        description = Component.text("Get a 5 kill streak in a single game")
        category = AchievementCategories.COMBAT
        icon = Material.GOLDEN_SWORD
        maxProgress = 1
        frameType = FrameType.GOAL
    })
    AchievementRegistry.register(achievement("rampage") {
        name = Component.text("Rampage")
        description = Component.text("Get a 10 kill streak in a single game")
        category = AchievementCategories.COMBAT
        icon = Material.NETHERITE_SWORD
        maxProgress = 1
        frameType = FrameType.CHALLENGE
        hidden = true
    })

    AchievementRegistry.register(achievement("survivor") {
        name = Component.text("Survivor")
        description = Component.text("Win your first game")
        category = AchievementCategories.SURVIVAL
        icon = Material.GOLDEN_APPLE
        maxProgress = 1
    })
    AchievementRegistry.register(achievement("champion") {
        name = Component.text("Champion")
        description = Component.text("Win 10 games")
        category = AchievementCategories.SURVIVAL
        icon = Material.ENCHANTED_GOLDEN_APPLE
        maxProgress = 10
    })
    AchievementRegistry.register(achievement("legend") {
        name = Component.text("Legend")
        description = Component.text("Win 50 games")
        category = AchievementCategories.SURVIVAL
        icon = Material.NETHER_STAR
        maxProgress = 50
        frameType = FrameType.CHALLENGE
    })
    AchievementRegistry.register(achievement("invincible") {
        name = Component.text("Invincible")
        description = Component.text("Win a game without dying")
        category = AchievementCategories.SURVIVAL
        icon = Material.TOTEM_OF_UNDYING
        maxProgress = 1
        frameType = FrameType.CHALLENGE
        hidden = true
    })

    AchievementRegistry.register(achievement("party_animal") {
        name = Component.text("Party Animal")
        description = Component.text("Play 10 games in a party")
        category = AchievementCategories.SOCIAL
        icon = Material.CAKE
        maxProgress = 10
    })
    AchievementRegistry.register(achievement("host_master") {
        name = Component.text("Host Master")
        description = Component.text("Host 5 games")
        category = AchievementCategories.SOCIAL
        icon = Material.BEACON
        maxProgress = 5
    })

    AchievementRegistry.register(achievement("map_explorer") {
        name = Component.text("Map Explorer")
        description = Component.text("Play on 10 different maps")
        category = AchievementCategories.EXPLORATION
        icon = Material.FILLED_MAP
        maxProgress = 10
    })

    AchievementRegistry.register(achievement("bp_complete") {
        name = Component.text("Season Veteran")
        description = Component.text("Complete a battle pass")
        category = AchievementCategories.MASTERY
        icon = Material.EXPERIENCE_BOTTLE
        maxProgress = 1
        frameType = FrameType.CHALLENGE
    })
    AchievementRegistry.register(achievement("mission_master") {
        name = Component.text("Mission Master")
        description = Component.text("Complete 100 missions")
        category = AchievementCategories.MASTERY
        icon = Material.COMPASS
        maxProgress = 100
    })
    AchievementRegistry.register(achievement("wealthy") {
        name = Component.text("Wealthy")
        description = Component.text("Earn 10,000 coins total")
        category = AchievementCategories.MASTERY
        icon = Material.GOLD_INGOT
        maxProgress = 10000
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

    AchievementRegistry.onUnlock { player, achievement ->
        val notification = net.minestom.server.advancements.Notification(
            achievement.name,
            achievement.frameType,
            net.minestom.server.item.ItemStack.of(achievement.icon),
        )
        player.sendNotification(notification)
        player.playSound(net.kyori.adventure.sound.Sound.sound(
            net.minestom.server.sound.SoundEvent.UI_TOAST_CHALLENGE_COMPLETE.key(),
            net.kyori.adventure.sound.Sound.Source.MASTER, 1f, 1f,
        ))

        when (achievement.id) {
            "legend" -> CosmeticStore.executeOnKey(player.uuid, UnlockCosmeticProcessor("win_effect_legend"))
            "mass_murderer" -> CosmeticStore.executeOnKey(player.uuid, UnlockCosmeticProcessor("kill_effect_blood"))
            "bp_complete" -> CosmeticStore.executeOnKey(player.uuid, UnlockCosmeticProcessor("aura_champion"))
        }
    }
}
