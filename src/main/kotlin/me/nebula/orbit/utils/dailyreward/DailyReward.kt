package me.nebula.orbit.utils.dailyreward

import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.time.LocalDate
import java.time.ZoneOffset

data class RewardItem(val type: String, val amount: Int, val extra: String = "")

data class DailyRewardEntry(val day: Int, val rewards: List<RewardItem>)

data class DailyRewardConfig(
    val entries: Map<Int, DailyRewardEntry>,
    val resetAfterMiss: Boolean,
    val cycleDays: Int,
)

data class DailyRewardState(val streak: Int, val lastClaimEpochDay: Long)

data class DailyRewardResult(
    val rewards: List<RewardItem>,
    val newStreak: Int,
    val isNewDay: Boolean,
)

fun DailyRewardConfig.claim(state: DailyRewardState): Pair<DailyRewardResult, DailyRewardState> {
    val today = LocalDate.now(ZoneOffset.UTC).toEpochDay()
    if (today == state.lastClaimEpochDay) {
        return DailyRewardResult(
            rewards = emptyList(),
            newStreak = state.streak,
            isNewDay = false,
        ) to state
    }
    val isConsecutive = today == state.lastClaimEpochDay + 1
    val newStreak = when {
        state.lastClaimEpochDay == 0L -> 1
        isConsecutive -> state.streak + 1
        resetAfterMiss -> 1
        else -> state.streak + 1
    }
    val effectiveDay = when {
        cycleDays > 0 -> ((newStreak - 1) % cycleDays) + 1
        else -> newStreak
    }
    val entry = resolveEntry(effectiveDay)
    val newState = DailyRewardState(streak = newStreak, lastClaimEpochDay = today)
    return DailyRewardResult(
        rewards = entry?.rewards.orEmpty(),
        newStreak = newStreak,
        isNewDay = true,
    ) to newState
}

private fun DailyRewardConfig.resolveEntry(day: Int): DailyRewardEntry? {
    entries[day]?.let { return it }
    val closest = entries.keys.filter { it <= day }.maxOrNull() ?: return null
    return entries[closest]
}

fun DailyRewardConfig.buildGuiItems(currentStreak: Int): List<Pair<Int, ItemStack>> =
    entries.keys.sorted().map { day ->
        val entry = entries.getValue(day)
        val (material, glowing) = when {
            day <= currentStreak -> Material.GREEN_STAINED_GLASS_PANE to false
            day == currentStreak + 1 -> Material.ORANGE_STAINED_GLASS_PANE to true
            else -> Material.GRAY_STAINED_GLASS_PANE to false
        }
        day to itemStack(material) {
            name("<white>Day $day")
            entry.rewards.forEach { reward ->
                val label = when {
                    reward.extra.isNotEmpty() -> "${reward.type}: ${reward.extra} x${reward.amount}"
                    else -> "${reward.type}: x${reward.amount}"
                }
                lore("<gray>$label")
            }
            if (glowing) glowing()
            clean()
        }
    }

class DailyRewardEntryBuilder @PublishedApi internal constructor(
    @PublishedApi internal val day: Int,
) {
    @PublishedApi internal val rewards = mutableListOf<RewardItem>()

    fun reward(type: String, amount: Int, extra: String = "") {
        rewards += RewardItem(type, amount, extra)
    }

    @PublishedApi internal fun build(): DailyRewardEntry =
        DailyRewardEntry(day, rewards.toList())
}

class DailyRewardConfigBuilder @PublishedApi internal constructor() {
    @PublishedApi internal val entries = mutableMapOf<Int, DailyRewardEntry>()
    @PublishedApi internal var resetAfterMiss = false
    @PublishedApi internal var cycleDays = 0

    inline fun day(day: Int, block: DailyRewardEntryBuilder.() -> Unit) {
        entries[day] = DailyRewardEntryBuilder(day).apply(block).build()
    }

    fun resetAfterMiss(value: Boolean) {
        resetAfterMiss = value
    }

    fun cycleDays(value: Int) {
        cycleDays = value
    }

    @PublishedApi internal fun build(): DailyRewardConfig =
        DailyRewardConfig(entries.toMap(), resetAfterMiss, cycleDays)
}

inline fun dailyReward(block: DailyRewardConfigBuilder.() -> Unit): DailyRewardConfig =
    DailyRewardConfigBuilder().apply(block).build()
