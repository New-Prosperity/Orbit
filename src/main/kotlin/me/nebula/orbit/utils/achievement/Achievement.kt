package me.nebula.orbit.utils.achievement

import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.achievement.AchievementCatalog
import me.nebula.gravity.achievement.AchievementDefinition
import me.nebula.gravity.achievement.AchievementStore
import me.nebula.gravity.achievement.ClaimMilestoneProcessor
import me.nebula.gravity.achievement.IncrementAchievementProcessor
import me.nebula.gravity.achievement.SetAchievementCompletedProcessor
import me.nebula.gravity.messaging.AchievementUnlockedMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.achievement.AchievementCategory as GravityCategory
import me.nebula.gravity.achievement.AchievementRarity as GravityRarity
import me.nebula.gravity.achievement.AchievementReward as GravityReward
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.cosmetic.UnlockCosmeticProcessor
import me.nebula.orbit.perks.EconomyPerks
import me.nebula.gravity.notification.notify
import me.nebula.gravity.translation.Keys
import me.nebula.orbit.Orbit
import me.nebula.orbit.notification.ToastFrame as NotifyToastFrame
import me.nebula.orbit.notification.actionBar
import me.nebula.orbit.notification.title
import me.nebula.orbit.notification.toast
import me.nebula.orbit.progression.BattlePassManager
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.user.asNebulaUser
import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.utils.toast.ToastFrame
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class AchievementCategory(val id: String, val displayKey: TranslationKey)

object AchievementCategories {

    private val registry = ConcurrentHashMap<String, AchievementCategory>()

    val GENERAL = register("general", "orbit.achievement.category.general")
    val COMBAT = register("combat", "orbit.achievement.category.combat")
    val SURVIVAL = register("survival", "orbit.achievement.category.survival")
    val SOCIAL = register("social", "orbit.achievement.category.social")
    val EXPLORATION = register("exploration", "orbit.achievement.category.exploration")
    val MASTERY = register("mastery", "orbit.achievement.category.mastery")

    fun register(id: String, displayKey: String): AchievementCategory {
        val category = AchievementCategory(id, displayKey.asTranslationKey())
        registry[id] = category
        return category
    }

    operator fun get(id: String): AchievementCategory? = registry[id]

    fun all(): Collection<AchievementCategory> = registry.values.toList()

    fun unregister(id: String) = registry.remove(id)
}

enum class AchievementRarity(val colorTag: String, val labelKey: TranslationKey) {
    COMMON("<gray>", "orbit.achievement.rarity.common".asTranslationKey()),
    UNCOMMON("<green>", "orbit.achievement.rarity.uncommon".asTranslationKey()),
    RARE("<blue>", "orbit.achievement.rarity.rare".asTranslationKey()),
    EPIC("<dark_purple>", "orbit.achievement.rarity.epic".asTranslationKey()),
    LEGENDARY("<gold>", "orbit.achievement.rarity.legendary".asTranslationKey()),
}

data class AchievementReward(val type: String, val amount: Int, val value: String = "")

data class AchievementMilestone(
    val threshold: Int,
    val nameKey: TranslationKey,
    val rewards: List<AchievementReward>,
)

data class Achievement(
    val id: String,
    val name: Component,
    val description: Component,
    val category: AchievementCategory = AchievementCategories.GENERAL,
    val icon: Material = Material.DIAMOND,
    val hidden: Boolean = false,
    val maxProgress: Int = 1,
    val toastFrame: ToastFrame = ToastFrame.TASK,
    val rewards: List<AchievementReward> = emptyList(),
    val prerequisites: List<String> = emptyList(),
    val points: Int = 5,
    val rarity: AchievementRarity = AchievementRarity.COMMON,
    val tierGroup: String? = null,
    val tierLevel: Int = 0,
) {

    fun toDefinition(): AchievementDefinition = AchievementDefinition(
        id = id,
        nameKey = "orbit.achievement.$id.name".asTranslationKey(),
        descriptionKey = "orbit.achievement.$id.description".asTranslationKey(),
        category = GravityCategory(category.id, category.displayKey),
        iconMaterialKey = icon.key().asString(),
        hidden = hidden,
        maxProgress = maxProgress,
        rewards = rewards.map { GravityReward(it.type, it.amount, it.value) },
        prerequisites = prerequisites,
        points = points,
        rarity = when (rarity) {
            AchievementRarity.COMMON -> GravityRarity.COMMON
            AchievementRarity.UNCOMMON -> GravityRarity.UNCOMMON
            AchievementRarity.RARE -> GravityRarity.RARE
            AchievementRarity.EPIC -> GravityRarity.EPIC
            AchievementRarity.LEGENDARY -> GravityRarity.LEGENDARY
        },
        tierGroup = tierGroup,
        tierLevel = tierLevel,
    )
}

object AchievementRegistry {

    private val achievements = ConcurrentHashMap<String, Achievement>()
    private val localProgress = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Int>>()
    private val localCompleted = ConcurrentHashMap<UUID, MutableSet<String>>()
    private val localPoints = ConcurrentHashMap<UUID, Int>()
    private val localClaimedMilestones = ConcurrentHashMap<UUID, MutableSet<Int>>()
    private var onUnlock: ((Player, Achievement) -> Unit)? = null

    fun register(achievement: Achievement) {
        achievements[achievement.id] = achievement
        val definition = achievement.toDefinition()
        AchievementCatalog.register(definition)
        AchievementCatalog.registerCategory(definition.category)
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
        localPoints[uuid] = data.points
        localClaimedMilestones[uuid] = ConcurrentHashMap.newKeySet<Int>().also { it.addAll(data.claimedMilestones) }
    }

    fun unloadPlayer(uuid: UUID) {
        localProgress.remove(uuid)
        localCompleted.remove(uuid)
        localPoints.remove(uuid)
        localClaimedMilestones.remove(uuid)
    }

    fun progress(player: Player, achievementId: String, amount: Int = 1) {
        val achievement = achievements[achievementId] ?: return
        if (isCompleted(player, achievementId)) return

        val progress = localProgress.computeIfAbsent(player.uuid) { ConcurrentHashMap() }
        val current = progress.getOrDefault(achievementId, 0) + amount
        progress[achievementId] = current

        val unlocked = AchievementStore.executeOnKey(
            player.uuid,
            IncrementAchievementProcessor(achievementId, amount, achievement.maxProgress, achievement.points),
        )

        if (unlocked) {
            localCompleted.computeIfAbsent(player.uuid) { ConcurrentHashMap.newKeySet() }.add(achievementId)
            localPoints.compute(player.uuid) { _, v -> (v ?: 0) + achievement.points }
            notifyUnlock(player, achievement)
            distributeRewards(player, achievement)
            checkMilestones(player)
        }
    }

    fun complete(player: Player, achievementId: String) {
        val achievement = achievements[achievementId] ?: return
        if (isCompleted(player, achievementId)) return

        val unlocked = AchievementStore.executeOnKey(
            player.uuid,
            SetAchievementCompletedProcessor(achievementId, achievement.points),
        )

        if (unlocked) {
            localProgress.computeIfAbsent(player.uuid) { ConcurrentHashMap() }[achievementId] = achievement.maxProgress
            localCompleted.computeIfAbsent(player.uuid) { ConcurrentHashMap.newKeySet() }.add(achievementId)
            localPoints.compute(player.uuid) { _, v -> (v ?: 0) + achievement.points }
            notifyUnlock(player, achievement)
            distributeRewards(player, achievement)
            checkMilestones(player)
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

    fun totalNonHiddenCount(): Int = achievements.values.count { !it.hidden }

    fun completedNonHiddenCount(uuid: UUID): Int =
        localCompleted[uuid]?.count { id -> achievements[id]?.hidden != true } ?: 0

    fun completedInCategory(uuid: UUID, category: AchievementCategory): Int =
        localCompleted[uuid]?.count { id -> achievements[id]?.category == category } ?: 0

    fun totalInCategory(category: AchievementCategory): Int =
        achievements.values.count { it.category == category }

    fun points(uuid: UUID): Int = localPoints[uuid] ?: 0

    fun points(player: Player): Int = localPoints[player.uuid] ?: 0

    fun pointsInCategory(uuid: UUID, category: AchievementCategory): Int {
        val completed = localCompleted[uuid] ?: return 0
        return completed.sumOf { id -> achievements[id]?.takeIf { it.category == category }?.points ?: 0 }
    }

    fun claimedMilestones(uuid: UUID): Set<Int> =
        localClaimedMilestones[uuid]?.toSet() ?: emptySet()

    fun tierGroupMembers(group: String): List<Achievement> =
        achievements.values.filter { it.tierGroup == group }.sortedBy { it.tierLevel }

    fun canUnlock(playerUuid: UUID, achievementId: String): Boolean {
        val achievement = achievements[achievementId] ?: return false
        return achievement.prerequisites.all { isCompleted(playerUuid, it) }
    }

    fun clear() {
        achievements.clear()
        localProgress.clear()
        localCompleted.clear()
        localPoints.clear()
        localClaimedMilestones.clear()
    }

    private fun distributeRewards(player: Player, achievement: Achievement) {
        for (reward in achievement.rewards) {
            when (reward.type) {
                "coins" -> EconomyPerks.grantCoins(player.uuid, reward.amount.toDouble())
                "xp" -> BattlePassManager.addXpToAll(player, reward.amount.toLong())
                "cosmetic" -> CosmeticStore.executeOnKey(player.uuid, UnlockCosmeticProcessor(reward.value))
            }
        }
    }

    private fun checkMilestones(player: Player) {
        val currentPoints = localPoints[player.uuid] ?: return
        val claimed = localClaimedMilestones[player.uuid] ?: return
        for (milestone in MILESTONES) {
            if (milestone.threshold in claimed) continue
            if (currentPoints < milestone.threshold) continue
            val success = AchievementStore.executeOnKey(player.uuid, ClaimMilestoneProcessor(milestone.threshold))
            if (success) {
                claimed.add(milestone.threshold)
                for (reward in milestone.rewards) {
                    when (reward.type) {
                        "coins" -> EconomyPerks.grantCoins(player.uuid, reward.amount.toDouble())
                        "xp" -> BattlePassManager.addXpToAll(player, reward.amount.toLong())
                        "cosmetic" -> CosmeticStore.executeOnKey(player.uuid, UnlockCosmeticProcessor(reward.value))
                    }
                }
                val locale = Orbit.localeOf(player.uuid)
                val milestoneName = Orbit.translations.get(milestone.nameKey.value, locale) ?: milestone.nameKey.value
                player.sendMessage(player.translate(
                    Keys.Orbit.Achievement.MilestoneReached,
                    "name" to milestoneName,
                    "points" to milestone.threshold.toString(),
                ))
                player.playSound(SoundEvent.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f)
            }
        }
    }

    private fun notifyUnlock(player: Player, achievement: Achievement) {
        publishUnlock(player, achievement)
        val handler = onUnlock
        if (handler != null) {
            handler(player, achievement)
            return
        }

        defaultUnlockNotification(player, achievement)
    }

    private fun publishUnlock(player: Player, achievement: Achievement) {
        try {
            NetworkMessenger.publish(AchievementUnlockedMessage(
                playerId = player.uuid,
                playerName = player.username,
                achievementId = achievement.id,
                rarity = achievement.rarity.name,
                points = achievement.points,
                totalPoints = localPoints[player.uuid] ?: achievement.points,
                serverName = Orbit.serverName,
                unlockedAt = System.currentTimeMillis(),
                category = achievement.category.id,
            ))
        } catch (_: Throwable) {
        }
    }

    fun defaultUnlockNotification(player: Player, achievement: Achievement) {
        val user = player.asNebulaUser()
        val achName = user.translateRaw("orbit.achievement.${achievement.id}.name".asTranslationKey())
        val achDesc = user.translateRaw("orbit.achievement.${achievement.id}.description".asTranslationKey())
        val notifyFrame = when (achievement.toastFrame) {
            ToastFrame.TASK -> NotifyToastFrame.TASK
            ToastFrame.CHALLENGE -> NotifyToastFrame.CHALLENGE
            ToastFrame.GOAL -> NotifyToastFrame.GOAL
        }
        val rarityColor = achievement.rarity.colorTag
        notify(user) {
            chat(user.translate(
                Keys.Orbit.Achievement.UnlockedChat,
                "name" to achName,
                "description" to achDesc,
                "points" to achievement.points.toString(),
            ))
            toast(Component.text(achName), ItemStack.of(achievement.icon), notifyFrame)
            when (achievement.rarity) {
                AchievementRarity.COMMON -> {
                    sound("minecraft:block.note_block.pling", volume = 0.6f, pitch = 1.2f)
                }
                AchievementRarity.UNCOMMON -> {
                    sound("minecraft:entity.player.levelup", pitch = 1.2f)
                }
                AchievementRarity.RARE -> {
                    sound("minecraft:entity.player.levelup", pitch = 1.3f)
                    sound("minecraft:block.note_block.chime", pitch = 1.4f)
                }
                AchievementRarity.EPIC -> {
                    title(
                        title = Component.text("Achievement Unlocked!"),
                        subtitle = Component.text("$rarityColor$achName"),
                        fadeInTicks = 8,
                        stayTicks = 50,
                        fadeOutTicks = 12,
                    )
                    sound("minecraft:entity.player.levelup", pitch = 1.4f)
                    sound("minecraft:ui.toast.challenge_complete", volume = 0.8f, pitch = 1.1f)
                }
                AchievementRarity.LEGENDARY -> {
                    title(
                        title = Component.text("$rarityColor$achName"),
                        subtitle = Component.text("<gold><bold>LEGENDARY UNLOCK"),
                        fadeInTicks = 10,
                        stayTicks = 80,
                        fadeOutTicks = 20,
                    )
                    actionBar(Component.text("$rarityColor$achName <gray>· <white>${achievement.points} pts"))
                    sound("minecraft:entity.ender_dragon.growl", volume = 0.5f, pitch = 1.2f)
                    sound("minecraft:entity.player.levelup", pitch = 0.8f)
                    sound("minecraft:ui.toast.challenge_complete", volume = 1.0f, pitch = 0.9f)
                    sound("minecraft:block.end_portal.spawn", volume = 0.4f, pitch = 1.5f)
                }
            }
        }

        if (achievement.rewards.isNotEmpty()) {
            for (reward in achievement.rewards) {
                val rewardText = when (reward.type) {
                    "coins" -> "${reward.amount} coins"
                    "xp" -> "${reward.amount} XP"
                    "cosmetic" -> reward.value
                    else -> "${reward.amount} ${reward.type}"
                }
                player.sendMessage(player.translate(
                    Keys.Orbit.Achievement.RewardLine,
                    "reward" to rewardText,
                ))
            }
        }

        if (achievement.rarity.ordinal >= AchievementRarity.EPIC.ordinal) {
            for (p in player.instance?.players ?: emptyList()) {
                if (p.uuid == player.uuid) continue
                p.sendMessage(p.translate(
                    Keys.Orbit.Achievement.Broadcast,
                    "player" to player.username,
                    "achievement" to (Orbit.translations.get("orbit.achievement.${achievement.id}.name", Orbit.localeOf(p.uuid)) ?: achievement.id),
                ))
                if (achievement.rarity == AchievementRarity.LEGENDARY) {
                    p.playSound(Sound.sound(
                        Key.key("minecraft:entity.ender_dragon.growl"),
                        Sound.Source.AMBIENT, 0.3f, 1.2f,
                    ))
                }
            }
        }
    }

    val MILESTONES = listOf(
        AchievementMilestone(50, "orbit.achievement.milestone.novice".asTranslationKey(), listOf(AchievementReward("coins", 100))),
        AchievementMilestone(150, "orbit.achievement.milestone.explorer".asTranslationKey(), listOf(AchievementReward("coins", 250))),
        AchievementMilestone(300, "orbit.achievement.milestone.achiever".asTranslationKey(), listOf(
            AchievementReward("coins", 500),
            AchievementReward("cosmetic", 0, "title_achiever"),
        )),
        AchievementMilestone(500, "orbit.achievement.milestone.master".asTranslationKey(), listOf(
            AchievementReward("coins", 1000),
            AchievementReward("cosmetic", 0, "aura_achievement"),
        )),
        AchievementMilestone(750, "orbit.achievement.milestone.grandmaster".asTranslationKey(), listOf(
            AchievementReward("coins", 2000),
            AchievementReward("cosmetic", 0, "mount_achievement"),
        )),
    )
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
    var points: Int = 5
    var rarity: AchievementRarity = AchievementRarity.COMMON
    var tierGroup: String? = null
    var tierLevel: Int = 0
    @PublishedApi internal val rewards = mutableListOf<AchievementReward>()
    @PublishedApi internal val prerequisites = mutableListOf<String>()

    fun reward(type: String, amount: Int, value: String = "") {
        rewards.add(AchievementReward(type, amount, value))
    }

    fun requires(achievementId: String) {
        prerequisites.add(achievementId)
    }

    fun build(): Achievement = Achievement(
        id, name, description, category, icon, hidden, maxProgress, toastFrame,
        rewards.toList(), prerequisites.toList(), points, rarity, tierGroup, tierLevel,
    )
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

    private val bindings = CopyOnWriteArrayList<AchievementTriggerBinding>()

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

fun progressBar(current: Int, max: Int, length: Int = 20): String {
    val filled = if (max <= 0) 0 else (current.coerceIn(0, max).toLong() * length / max).toInt()
    val empty = length - filled
    return "[${"█".repeat(filled)}${"░".repeat(empty)}]"
}
