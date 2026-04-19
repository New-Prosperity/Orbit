package me.nebula.orbit.utils.challenge

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.challenge.ChallengeCatalog
import me.nebula.gravity.challenge.ChallengeDefinition
import me.nebula.gravity.messaging.ChallengeMaxedMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.ranking.Periodicity
import me.nebula.gravity.ranking.RankingLookup
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.achievement.Achievement
import me.nebula.orbit.utils.achievement.AchievementCategories
import me.nebula.orbit.utils.achievement.AchievementCategory
import me.nebula.orbit.utils.achievement.AchievementRarity
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.orbit.utils.achievement.AchievementReward
import me.nebula.orbit.utils.toast.ToastFrame
import me.nebula.gravity.achievement.AchievementCategory as GravityCategory
import me.nebula.gravity.achievement.AchievementRarity as GravityRarity
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ChallengeTier(
    val threshold: Long,
    val rarity: AchievementRarity,
    val rewards: List<AchievementReward> = emptyList(),
    val points: Int = 5 + rarity.ordinal * 10,
)

data class Challenge(
    val id: String,
    val statKey: String,
    val name: Component,
    val description: Component,
    val category: AchievementCategory = AchievementCategories.GENERAL,
    val icon: Material = Material.DIAMOND,
    val tiers: List<ChallengeTier>,
    val rankingStatKey: String = statKey,
) {

    init {
        require(tiers.isNotEmpty()) { "Challenge '$id' must define at least one tier" }
    }

    fun toDefinition(): ChallengeDefinition = ChallengeDefinition(
        id = id,
        nameKey = "orbit.challenge.$id.name".asTranslationKey(),
        descriptionKey = "orbit.challenge.$id.description".asTranslationKey(),
        statKey = statKey,
        tiers = tiers.map { it.threshold },
        tierRarities = tiers.map { it.rarity.toGravity() },
        category = GravityCategory(category.id, category.displayKey),
        iconMaterialKey = icon.key().asString(),
        rankingStatKey = rankingStatKey,
    )
}

private fun AchievementRarity.toGravity(): GravityRarity = when (this) {
    AchievementRarity.COMMON -> GravityRarity.COMMON
    AchievementRarity.UNCOMMON -> GravityRarity.UNCOMMON
    AchievementRarity.RARE -> GravityRarity.RARE
    AchievementRarity.EPIC -> GravityRarity.EPIC
    AchievementRarity.LEGENDARY -> GravityRarity.LEGENDARY
}

object ChallengeRegistry {

    private val challenges = ConcurrentHashMap<String, Challenge>()
    private val byStatKey = ConcurrentHashMap<String, MutableList<Challenge>>()
    private val localMaxed = ConcurrentHashMap<UUID, MutableSet<String>>()

    fun register(challenge: Challenge) {
        challenges[challenge.id] = challenge
        byStatKey.getOrPut(challenge.statKey) { mutableListOf() }.add(challenge)
        ChallengeCatalog.register(challenge.toDefinition())

        for ((index, tier) in challenge.tiers.withIndex()) {
            val tierId = "${challenge.id}_tier_${index + 1}"
            val tierName = Component.text("${challenge.name} ${romanNumeral(index + 1)}")
            val tierDescription = Component.text("Reach ${tier.threshold} ${challenge.statKey}")
            AchievementRegistry.register(Achievement(
                id = tierId,
                name = tierName,
                description = tierDescription,
                category = challenge.category,
                icon = challenge.icon,
                hidden = false,
                maxProgress = 1,
                toastFrame = ToastFrame.CHALLENGE,
                rewards = tier.rewards,
                prerequisites = if (index == 0) emptyList() else listOf("${challenge.id}_tier_${index}"),
                points = tier.points,
                rarity = tier.rarity,
                tierGroup = challenge.id,
                tierLevel = index + 1,
            ))
        }
    }

    fun unregister(id: String) {
        val challenge = challenges.remove(id) ?: return
        byStatKey[challenge.statKey]?.removeAll { it.id == id }
        for (index in challenge.tiers.indices) {
            AchievementRegistry.unregister("${challenge.id}_tier_${index + 1}")
        }
        ChallengeCatalog.unregister(id)
    }

    operator fun get(id: String): Challenge? = challenges[id]

    fun all(): Collection<Challenge> = challenges.values.toList()

    fun forStatKey(statKey: String): List<Challenge> = byStatKey[statKey]?.toList() ?: emptyList()

    fun loadPlayer(uuid: UUID) {
        localMaxed[uuid] = ConcurrentHashMap.newKeySet()
    }

    fun unloadPlayer(uuid: UUID) {
        localMaxed.remove(uuid)
    }

    fun onStatUpdate(player: Player, statKey: String, newTotal: Long) {
        val matching = byStatKey[statKey] ?: return
        val maxedSet = localMaxed.computeIfAbsent(player.uuid) { ConcurrentHashMap.newKeySet() }
        for (challenge in matching) {
            val definition = ChallengeCatalog.get(challenge.id) ?: continue
            val tierIndex = definition.tierForProgress(newTotal)
            for (i in 0 until tierIndex) {
                val tierId = definition.tierAchievementId(i)
                if (!AchievementRegistry.isCompleted(player, tierId)) {
                    AchievementRegistry.complete(player, tierId)
                }
            }
            if (definition.isMaxed(newTotal) && maxedSet.add(challenge.id)) {
                publishMaxed(player, challenge, newTotal)
            }
        }
    }

    fun tierOf(uuid: UUID, challengeId: String): Int {
        val definition = ChallengeCatalog.get(challengeId) ?: return 0
        var completed = 0
        for (i in 0 until definition.maxTierCount) {
            if (AchievementRegistry.isCompleted(uuid, definition.tierAchievementId(i))) completed = i + 1 else break
        }
        return completed
    }

    fun isMaxed(uuid: UUID, challengeId: String): Boolean {
        val definition = ChallengeCatalog.get(challengeId) ?: return false
        return AchievementRegistry.isCompleted(uuid, definition.tierAchievementId(definition.maxTierCount - 1))
    }

    data class RankInfo(val rank: Int?, val percentile: Int, val population: Int, val score: Double)

    fun rankInfo(uuid: UUID, challengeId: String): RankInfo? {
        val challenge = challenges[challengeId] ?: return null
        if (!isMaxed(uuid, challengeId)) return null
        val result = RankingLookup.query(uuid, challenge.rankingStatKey, Periodicity.ALL_TIME) ?: return null
        return RankInfo(
            rank = result.rank,
            percentile = result.percentile,
            population = result.population,
            score = result.score,
        )
    }

    fun clear() {
        challenges.clear()
        byStatKey.clear()
        localMaxed.clear()
    }

    private fun publishMaxed(player: Player, challenge: Challenge, finalScore: Long) {
        try {
            NetworkMessenger.publish(ChallengeMaxedMessage(
                playerId = player.uuid,
                playerName = player.username,
                challengeId = challenge.id,
                finalScore = finalScore,
                serverName = Orbit.serverName,
                maxedAt = System.currentTimeMillis(),
            ))
        } catch (_: Throwable) {
        }
    }

    private fun romanNumeral(n: Int): String = when (n) {
        1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"; 5 -> "V"
        6 -> "VI"; 7 -> "VII"; 8 -> "VIII"; 9 -> "IX"; 10 -> "X"
        else -> n.toString()
    }
}

class ChallengeBuilder {
    lateinit var id: String
    lateinit var statKey: String
    var name: Component = Component.empty()
    var description: Component = Component.empty()
    var category: AchievementCategory = AchievementCategories.GENERAL
    var icon: Material = Material.DIAMOND
    var rankingStatKey: String? = null
    @PublishedApi internal val tiers = mutableListOf<ChallengeTier>()

    fun tier(threshold: Long, rarity: AchievementRarity, points: Int = 5 + rarity.ordinal * 10, block: TierRewardBuilder.() -> Unit = {}) {
        val rewardsBuilder = TierRewardBuilder().apply(block)
        tiers += ChallengeTier(threshold, rarity, rewardsBuilder.rewards.toList(), points)
    }

    fun build(): Challenge = Challenge(
        id = id,
        statKey = statKey,
        name = name,
        description = description,
        category = category,
        icon = icon,
        tiers = tiers.toList(),
        rankingStatKey = rankingStatKey ?: statKey,
    )
}

class TierRewardBuilder {
    @PublishedApi internal val rewards = mutableListOf<AchievementReward>()

    fun reward(type: String, amount: Int, value: String = "") {
        rewards += AchievementReward(type, amount, value)
    }
}

inline fun challenge(id: String, block: ChallengeBuilder.() -> Unit): Challenge =
    ChallengeBuilder().apply { this.id = id }.apply(block).build()
