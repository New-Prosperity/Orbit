package me.nebula.orbit.mechanic.experience

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.ExperienceOrb
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityDeathEvent
import net.minestom.server.event.item.PickupExperienceEvent

class ExperienceModule : OrbitModule("experience") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PickupExperienceEvent::class.java) { event ->
            val player = event.player ?: return@addListener
            val xp = event.experienceCount
            player.giveExperience(xp.toInt())
        }

        eventNode.addListener(EntityDeathEvent::class.java) { event ->
            val entity = event.entity
            val instance = entity.instance ?: return@addListener
            val xp = when {
                entity is Player -> 7 * entity.level.coerceAtMost(100)
                else -> 0
            }
            if (xp > 0) {
                val orb = ExperienceOrb(xp.toShort())
                orb.setInstance(instance, entity.position.add(0.0, 1.0, 0.0))
            }
        }
    }
}

fun Player.giveExperience(amount: Int) {
    val totalXp = experienceToTotal(level, exp) + amount
    val (newLevel, newProgress) = totalToExperience(totalXp)
    level = newLevel
    exp = newProgress
}

fun Player.totalExperience(): Int = experienceToTotal(level, exp)

private fun experienceToTotal(level: Int, progress: Float): Int {
    val xpForLevel = when {
        level <= 16 -> level * level + 6 * level
        level <= 31 -> (2.5 * level * level - 40.5 * level + 360).toInt()
        else -> (4.5 * level * level - 162.5 * level + 2220).toInt()
    }
    val xpForNext = xpForNextLevel(level)
    return xpForLevel + (progress * xpForNext).toInt()
}

private fun totalToExperience(total: Int): Pair<Int, Float> {
    var remaining = total
    var level = 0
    while (true) {
        val needed = xpForNextLevel(level)
        if (remaining < needed) return level to (remaining.toFloat() / needed)
        remaining -= needed
        level++
    }
}

private fun xpForNextLevel(level: Int): Int = when {
    level <= 15 -> 2 * level + 7
    level <= 30 -> 5 * level - 38
    else -> 9 * level - 158
}
