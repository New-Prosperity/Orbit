package me.nebula.orbit.progression.challenge

import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.achievement.AchievementCategories
import me.nebula.orbit.utils.achievement.AchievementRarity
import me.nebula.orbit.utils.challenge.ChallengeRegistry
import me.nebula.orbit.utils.challenge.challenge
import net.minestom.server.item.Material

fun registerChallengeContent() {
    val locale = Orbit.translations.defaultLocale

    ChallengeRegistry.register(challenge("killer") {
        statKey = "br_kills"
        rankingStatKey = "br_kills"
        name = Orbit.deserialize("orbit.challenge.killer.name", locale)
        description = Orbit.deserialize("orbit.challenge.killer.description", locale)
        category = AchievementCategories.COMBAT
        icon = Material.IRON_SWORD
        tier(100, AchievementRarity.COMMON, points = 10) {
            reward("coins", 250)
        }
        tier(500, AchievementRarity.UNCOMMON, points = 20) {
            reward("coins", 750)
        }
        tier(2_500, AchievementRarity.RARE, points = 40) {
            reward("coins", 2_000)
        }
        tier(10_000, AchievementRarity.EPIC, points = 80) {
            reward("coins", 6_000)
            reward("cosmetic", 0, "title_killer")
        }
        tier(50_000, AchievementRarity.LEGENDARY, points = 200) {
            reward("coins", 25_000)
            reward("cosmetic", 0, "aura_killer")
        }
    })
}
