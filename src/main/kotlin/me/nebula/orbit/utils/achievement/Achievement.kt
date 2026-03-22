package me.nebula.orbit.utils.achievement

import me.nebula.gravity.achievement.AchievementData
import me.nebula.gravity.achievement.AchievementStore
import me.nebula.gravity.achievement.IncrementAchievementProcessor
import me.nebula.gravity.achievement.SetAchievementCompletedProcessor
import net.kyori.adventure.text.Component
import me.nebula.orbit.utils.toast.ToastFrame
import me.nebula.orbit.utils.toast.showToast
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AchievementCategory(val id: String, val displayKey: String)

object AchievementCategories {

    private val registry = ConcurrentHashMap<String, AchievementCategory>()

    val GENERAL = register("general", "orbit.achievement.category.general")
    val COMBAT = register("combat", "orbit.achievement.category.combat")
    val SURVIVAL = register("survival", "orbit.achievement.category.survival")
    val SOCIAL = register("social", "orbit.achievement.category.social")
    val EXPLORATION = register("exploration", "orbit.achievement.category.exploration")
    val MASTERY = register("mastery", "orbit.achievement.category.mastery")

    fun register(id: String, displayKey: String): AchievementCategory {
        val category = AchievementCategory(id, displayKey)
        registry[id] = category
        return category
    }

    operator fun get(id: String): AchievementCategory? = registry[id]

    fun all(): Collection<AchievementCategory> = registry.values.toList()

    fun unregister(id: String) = registry.remove(id)
}

data class Achievement(
    val id: String,
    val name: Component,
    val description: Component,
    val category: AchievementCategory = AchievementCategories.GENERAL,
    val icon: Material = Material.DIAMOND,
    val hidden: Boolean = false,
    val maxProgress: Int = 1,
    val toastFrame: ToastFrame = ToastFrame.TASK,
)

object AchievementRegistry {

    private val achievements = ConcurrentHashMap<String, Achievement>()
    private val localProgress = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Int>>()
    private val localCompleted = ConcurrentHashMap<UUID, MutableSet<String>>()
    private var onUnlock: ((Player, Achievement) -> Unit)? = null

    fun register(achievement: Achievement) {
        achievements[achievement.id] = achievement
    }

    fun unregister(id: String) = achievements.remove(id)

    operator fun get(id: String): Achievement? = achievements[id]

    fun all(): Map<String, Achievement> = achievements.toMap()

    fun byCategory(category: AchievementCategory): List<Achievement> =
        achievements.values.filter { it.category == category }

    fun onUnlock(handler: (Player, Achievement) -> Unit) {
        onUnlock = handler
    }

    fun loadPlayer(uuid: UUID) {
        val data = AchievementStore.load(uuid) ?: return
        localProgress[uuid] = ConcurrentHashMap(data.progress)
        localCompleted[uuid] = ConcurrentHashMap.newKeySet<String>().also { it.addAll(data.completed) }
    }

    fun unloadPlayer(uuid: UUID) {
        localProgress.remove(uuid)
        localCompleted.remove(uuid)
    }

    fun progress(player: Player, achievementId: String, amount: Int = 1) {
        val achievement = achievements[achievementId] ?: return
        if (isCompleted(player, achievementId)) return

        val progress = localProgress.computeIfAbsent(player.uuid) { ConcurrentHashMap() }
        val current = progress.getOrDefault(achievementId, 0) + amount
        progress[achievementId] = current

        val unlocked = AchievementStore.executeOnKey(
            player.uuid,
            IncrementAchievementProcessor(achievementId, amount, achievement.maxProgress),
        )

        if (unlocked) {
            localCompleted.computeIfAbsent(player.uuid) { ConcurrentHashMap.newKeySet() }.add(achievementId)
            notifyUnlock(player, achievement)
        }
    }

    fun complete(player: Player, achievementId: String) {
        val achievement = achievements[achievementId] ?: return
        if (isCompleted(player, achievementId)) return

        val unlocked = AchievementStore.executeOnKey(
            player.uuid,
            SetAchievementCompletedProcessor(achievementId),
        )

        if (unlocked) {
            localProgress.computeIfAbsent(player.uuid) { ConcurrentHashMap() }[achievementId] = achievement.maxProgress
            localCompleted.computeIfAbsent(player.uuid) { ConcurrentHashMap.newKeySet() }.add(achievementId)
            notifyUnlock(player, achievement)
        }
    }

    fun getProgress(player: Player, achievementId: String): Int =
        localProgress[player.uuid]?.getOrDefault(achievementId, 0) ?: 0

    fun getProgress(uuid: UUID, achievementId: String): Int =
        localProgress[uuid]?.getOrDefault(achievementId, 0) ?: 0

    fun isCompleted(player: Player, achievementId: String): Boolean =
        localCompleted[player.uuid]?.contains(achievementId) == true

    fun isCompleted(uuid: UUID, achievementId: String): Boolean =
        localCompleted[uuid]?.contains(achievementId) == true

    fun completedCount(player: Player): Int =
        localCompleted[player.uuid]?.size ?: 0

    fun completedCount(uuid: UUID): Int =
        localCompleted[uuid]?.size ?: 0

    fun totalCount(): Int = achievements.size

    fun completedInCategory(uuid: UUID, category: AchievementCategory): Int =
        localCompleted[uuid]?.count { id -> achievements[id]?.category == category } ?: 0

    fun totalInCategory(category: AchievementCategory): Int =
        achievements.values.count { it.category == category }

    fun clear() {
        achievements.clear()
        localProgress.clear()
        localCompleted.clear()
    }

    private fun notifyUnlock(player: Player, achievement: Achievement) {
        val handler = onUnlock
        if (handler != null) {
            handler(player, achievement)
            return
        }

        player.showToast {
            title(achievement.name)
            icon(achievement.icon)
            frame(achievement.toastFrame)
        }
    }
}

class AchievementBuilder {
    lateinit var id: String
    var name: Component = Component.empty()
    var description: Component = Component.empty()
    var category: AchievementCategory = AchievementCategories.GENERAL
    var icon: Material = Material.DIAMOND
    var hidden: Boolean = false
    var maxProgress: Int = 1
    var toastFrame: ToastFrame = ToastFrame.TASK

    fun build(): Achievement = Achievement(id, name, description, category, icon, hidden, maxProgress, toastFrame)
}

inline fun achievement(id: String, block: AchievementBuilder.() -> Unit): Achievement =
    AchievementBuilder().apply { this.id = id }.apply(block).build()

fun interface AchievementTrigger {
    fun check(uuid: UUID, statKey: String, value: Long): Boolean
}

data class AchievementTriggerBinding(
    val achievementId: String,
    val statKey: String,
    val trigger: AchievementTrigger,
)

object AchievementTriggerManager {

    private val bindings = java.util.concurrent.CopyOnWriteArrayList<AchievementTriggerBinding>()

    fun bind(achievementId: String, statKey: String, trigger: AchievementTrigger) {
        bindings += AchievementTriggerBinding(achievementId, statKey, trigger)
    }

    fun bindThreshold(achievementId: String, statKey: String, threshold: Long) {
        bind(achievementId, statKey) { _, _, value -> value >= threshold }
    }

    fun evaluate(player: Player, statKey: String, value: Long) {
        for (binding in bindings) {
            if (binding.statKey != statKey) continue
            if (AchievementRegistry.isCompleted(player, binding.achievementId)) continue
            if (binding.trigger.check(player.uuid, statKey, value)) {
                AchievementRegistry.complete(player, binding.achievementId)
            }
        }
    }

    fun clear() {
        bindings.clear()
    }
}
