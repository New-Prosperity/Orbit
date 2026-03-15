package me.nebula.orbit.progression.achievement

import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.cosmetic.UnlockCosmeticProcessor
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.achievement.AchievementCategories
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.orbit.utils.achievement.AchievementTriggerManager
import me.nebula.orbit.utils.achievement.achievement
import net.minestom.server.advancements.FrameType
import net.minestom.server.item.Material

fun registerAchievementContent() {
    val locale = Orbit.translations.defaultLocale
    AchievementRegistry.register(achievement("first_game") {
        name = Orbit.deserialize("orbit.achievement.first_game.name", locale)
        description = Orbit.deserialize("orbit.achievement.first_game.description", locale)
        category = AchievementCategories.GENERAL
        icon = Material.WOODEN_SWORD
        maxProgress = 1
    })
    AchievementRegistry.register(achievement("veteran") {
        name = Orbit.deserialize("orbit.achievement.veteran.name", locale)
        description = Orbit.deserialize("orbit.achievement.veteran.description", locale)
        category = AchievementCategories.GENERAL
        icon = Material.IRON_SWORD
        maxProgress = 100
    })
    AchievementRegistry.register(achievement("dedicated") {
        name = Orbit.deserialize("orbit.achievement.dedicated.name", locale)
        description = Orbit.deserialize("orbit.achievement.dedicated.description", locale)
        category = AchievementCategories.GENERAL
        icon = Material.DIAMOND_SWORD
        maxProgress = 500
        frameType = FrameType.CHALLENGE
    })
    AchievementRegistry.register(achievement("collector") {
        name = Orbit.deserialize("orbit.achievement.collector.name", locale)
        description = Orbit.deserialize("orbit.achievement.collector.description", locale)
        category = AchievementCategories.GENERAL
        icon = Material.CHEST
        maxProgress = 10
    })

    AchievementRegistry.register(achievement("first_blood") {
        name = Orbit.deserialize("orbit.achievement.first_blood.name", locale)
        description = Orbit.deserialize("orbit.achievement.first_blood.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.IRON_SWORD
        maxProgress = 1
    })
    AchievementRegistry.register(achievement("warrior") {
        name = Orbit.deserialize("orbit.achievement.warrior.name", locale)
        description = Orbit.deserialize("orbit.achievement.warrior.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.DIAMOND_SWORD
        maxProgress = 100
    })
    AchievementRegistry.register(achievement("slayer") {
        name = Orbit.deserialize("orbit.achievement.slayer.name", locale)
        description = Orbit.deserialize("orbit.achievement.slayer.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.NETHERITE_SWORD
        maxProgress = 500
        frameType = FrameType.CHALLENGE
    })
    AchievementRegistry.register(achievement("mass_murderer") {
        name = Orbit.deserialize("orbit.achievement.mass_murderer.name", locale)
        description = Orbit.deserialize("orbit.achievement.mass_murderer.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.WITHER_SKELETON_SKULL
        maxProgress = 1000
        frameType = FrameType.CHALLENGE
        hidden = true
    })
    AchievementRegistry.register(achievement("double_trouble") {
        name = Orbit.deserialize("orbit.achievement.double_trouble.name", locale)
        description = Orbit.deserialize("orbit.achievement.double_trouble.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.STONE_SWORD
        maxProgress = 1
    })
    AchievementRegistry.register(achievement("unstoppable") {
        name = Orbit.deserialize("orbit.achievement.unstoppable.name", locale)
        description = Orbit.deserialize("orbit.achievement.unstoppable.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.GOLDEN_SWORD
        maxProgress = 1
        frameType = FrameType.GOAL
    })
    AchievementRegistry.register(achievement("rampage") {
        name = Orbit.deserialize("orbit.achievement.rampage.name", locale)
        description = Orbit.deserialize("orbit.achievement.rampage.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.NETHERITE_SWORD
        maxProgress = 1
        frameType = FrameType.CHALLENGE
        hidden = true
    })

    AchievementRegistry.register(achievement("survivor") {
        name = Orbit.deserialize("orbit.achievement.survivor.name", locale)
        description = Orbit.deserialize("orbit.achievement.survivor.description", locale)
        category = AchievementCategories.SURVIVAL
        icon = Material.GOLDEN_APPLE
        maxProgress = 1
    })
    AchievementRegistry.register(achievement("champion") {
        name = Orbit.deserialize("orbit.achievement.champion.name", locale)
        description = Orbit.deserialize("orbit.achievement.champion.description", locale)
        category = AchievementCategories.SURVIVAL
        icon = Material.ENCHANTED_GOLDEN_APPLE
        maxProgress = 10
    })
    AchievementRegistry.register(achievement("legend") {
        name = Orbit.deserialize("orbit.achievement.legend.name", locale)
        description = Orbit.deserialize("orbit.achievement.legend.description", locale)
        category = AchievementCategories.SURVIVAL
        icon = Material.NETHER_STAR
        maxProgress = 50
        frameType = FrameType.CHALLENGE
    })
    AchievementRegistry.register(achievement("invincible") {
        name = Orbit.deserialize("orbit.achievement.invincible.name", locale)
        description = Orbit.deserialize("orbit.achievement.invincible.description", locale)
        category = AchievementCategories.SURVIVAL
        icon = Material.TOTEM_OF_UNDYING
        maxProgress = 1
        frameType = FrameType.CHALLENGE
        hidden = true
    })

    AchievementRegistry.register(achievement("party_animal") {
        name = Orbit.deserialize("orbit.achievement.party_animal.name", locale)
        description = Orbit.deserialize("orbit.achievement.party_animal.description", locale)
        category = AchievementCategories.SOCIAL
        icon = Material.CAKE
        maxProgress = 10
    })
    AchievementRegistry.register(achievement("host_master") {
        name = Orbit.deserialize("orbit.achievement.host_master.name", locale)
        description = Orbit.deserialize("orbit.achievement.host_master.description", locale)
        category = AchievementCategories.SOCIAL
        icon = Material.BEACON
        maxProgress = 5
    })

    AchievementRegistry.register(achievement("map_explorer") {
        name = Orbit.deserialize("orbit.achievement.map_explorer.name", locale)
        description = Orbit.deserialize("orbit.achievement.map_explorer.description", locale)
        category = AchievementCategories.EXPLORATION
        icon = Material.FILLED_MAP
        maxProgress = 10
    })

    AchievementRegistry.register(achievement("bp_complete") {
        name = Orbit.deserialize("orbit.achievement.bp_complete.name", locale)
        description = Orbit.deserialize("orbit.achievement.bp_complete.description", locale)
        category = AchievementCategories.MASTERY
        icon = Material.EXPERIENCE_BOTTLE
        maxProgress = 1
        frameType = FrameType.CHALLENGE
    })
    AchievementRegistry.register(achievement("mission_master") {
        name = Orbit.deserialize("orbit.achievement.mission_master.name", locale)
        description = Orbit.deserialize("orbit.achievement.mission_master.description", locale)
        category = AchievementCategories.MASTERY
        icon = Material.COMPASS
        maxProgress = 100
    })
    AchievementRegistry.register(achievement("wealthy") {
        name = Orbit.deserialize("orbit.achievement.wealthy.name", locale)
        description = Orbit.deserialize("orbit.achievement.wealthy.description", locale)
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
            Orbit.deserialize("orbit.achievement.${achievement.id}.name", Orbit.localeOf(player.uuid)),
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
