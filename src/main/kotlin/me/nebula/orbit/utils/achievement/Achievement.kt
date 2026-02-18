package me.nebula.orbit.utils.achievement

import me.nebula.orbit.translation.translate
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import net.kyori.adventure.sound.Sound
import net.minestom.server.entity.Player
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Achievement(
    val id: String,
    val name: Component,
    val description: Component,
    val icon: net.minestom.server.item.Material = net.minestom.server.item.Material.DIAMOND,
    val hidden: Boolean = false,
    val maxProgress: Int = 1,
)

object AchievementRegistry {

    private val achievements = ConcurrentHashMap<String, Achievement>()
    private val playerProgress = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Int>>()
    private val completedAchievements = ConcurrentHashMap<UUID, MutableSet<String>>()
    private var onUnlock: ((Player, Achievement) -> Unit)? = null

    fun register(achievement: Achievement) {
        achievements[achievement.id] = achievement
    }

    fun unregister(id: String) = achievements.remove(id)

    operator fun get(id: String): Achievement? = achievements[id]

    fun all(): Map<String, Achievement> = achievements.toMap()

    fun onUnlock(handler: (Player, Achievement) -> Unit) {
        onUnlock = handler
    }

    fun progress(player: Player, achievementId: String, amount: Int = 1) {
        val achievement = achievements[achievementId] ?: return
        if (isCompleted(player, achievementId)) return

        val progress = playerProgress.computeIfAbsent(player.uuid) { ConcurrentHashMap() }
        val current = progress.getOrDefault(achievementId, 0) + amount
        progress[achievementId] = current

        if (current >= achievement.maxProgress) {
            completedAchievements.computeIfAbsent(player.uuid) { ConcurrentHashMap.newKeySet() }.add(achievementId)
            notifyUnlock(player, achievement)
        }
    }

    fun getProgress(player: Player, achievementId: String): Int =
        playerProgress[player.uuid]?.getOrDefault(achievementId, 0) ?: 0

    fun isCompleted(player: Player, achievementId: String): Boolean =
        completedAchievements[player.uuid]?.contains(achievementId) == true

    fun completedCount(player: Player): Int =
        completedAchievements[player.uuid]?.size ?: 0

    fun totalCount(): Int = achievements.size

    fun resetPlayer(player: UUID) {
        playerProgress.remove(player)
        completedAchievements.remove(player)
    }

    fun clear() {
        achievements.clear()
        playerProgress.clear()
        completedAchievements.clear()
    }

    private fun notifyUnlock(player: Player, achievement: Achievement) {
        val handler = onUnlock
        if (handler != null) {
            handler(player, achievement)
        } else {
            player.showTitle(Title.title(
                player.translate("orbit.util.achievement.unlocked").color(NamedTextColor.GOLD),
                achievement.name,
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500)),
            ))
            player.playSound(Sound.sound(SoundEvent.UI_TOAST_CHALLENGE_COMPLETE.key(), Sound.Source.MASTER, 1f, 1f))
        }
    }
}

class AchievementBuilder {
    lateinit var id: String
    var name: Component = Component.empty()
    var description: Component = Component.empty()
    var icon: net.minestom.server.item.Material = net.minestom.server.item.Material.DIAMOND
    var hidden: Boolean = false
    var maxProgress: Int = 1

    fun build(): Achievement = Achievement(id, name, description, icon, hidden, maxProgress)
}

inline fun achievement(id: String, block: AchievementBuilder.() -> Unit): Achievement =
    AchievementBuilder().apply { this.id = id }.apply(block).build()
