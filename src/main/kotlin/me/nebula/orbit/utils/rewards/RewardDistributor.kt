package me.nebula.orbit.utils.rewards

import me.nebula.gravity.economy.AddBalanceProcessor
import me.nebula.gravity.economy.EconomyStore
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.matchresult.MatchResult
import me.nebula.orbit.utils.stattracker.StatTracker
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.util.UUID

data class RewardEntry(
    val currency: String,
    val amount: Double,
    val reason: String,
)

fun interface RewardCondition {
    fun evaluate(uuid: UUID, result: MatchResult): Boolean
}

data class RewardRule(
    val name: String,
    val condition: RewardCondition,
    val rewards: List<RewardEntry>,
)

class RewardDistributor @PublishedApi internal constructor(
    private val rules: List<RewardRule>,
    private val participationRewards: List<RewardEntry>,
    private val perKillRewards: List<RewardEntry>,
    private val announcementKey: String?,
) {

    fun distribute(result: MatchResult, participants: Set<UUID>) {
        for (uuid in participants) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            val earned = mutableListOf<RewardEntry>()

            for (reward in participationRewards) {
                grantReward(uuid, reward)
                earned.add(reward)
            }

            if (perKillRewards.isNotEmpty()) {
                val kills = StatTracker.get(uuid, "kills")
                if (kills > 0) {
                    for (reward in perKillRewards) {
                        val scaled = reward.copy(amount = reward.amount * kills)
                        grantReward(uuid, scaled)
                        earned.add(scaled)
                    }
                }
            }

            for (rule in rules) {
                if (rule.condition.evaluate(uuid, result)) {
                    for (reward in rule.rewards) {
                        grantReward(uuid, reward)
                        earned.add(reward)
                    }
                }
            }

            if (announcementKey != null && player != null && earned.isNotEmpty()) {
                val totalByCurrency = earned.groupBy { it.currency }
                    .mapValues { (_, entries) -> entries.sumOf { it.amount } }
                for ((currency, total) in totalByCurrency) {
                    player.sendMessage(player.translate(announcementKey,
                        "amount" to formatAmount(total),
                        "currency" to currency,
                    ))
                }
            }
        }
    }

    private fun grantReward(uuid: UUID, reward: RewardEntry) {
        EconomyStore.submitToKey(uuid, AddBalanceProcessor(reward.currency, reward.amount))
    }

    private fun formatAmount(amount: Double): String =
        if (amount == amount.toLong().toDouble()) amount.toLong().toString()
        else "%.1f".format(amount)
}

class RewardDistributorBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val rules = mutableListOf<RewardRule>()
    @PublishedApi internal val participationRewards = mutableListOf<RewardEntry>()
    @PublishedApi internal val perKillRewards = mutableListOf<RewardEntry>()
    @PublishedApi internal var announcementKey: String? = null

    fun participation(currency: String, amount: Double, reason: String = "participation") {
        participationRewards.add(RewardEntry(currency, amount, reason))
    }

    fun perKill(currency: String, amount: Double, reason: String = "kill") {
        perKillRewards.add(RewardEntry(currency, amount, reason))
    }

    fun rule(name: String, condition: RewardCondition, block: RewardRuleBuilder.() -> Unit) {
        val ruleBuilder = RewardRuleBuilder(name, condition).apply(block)
        rules.add(ruleBuilder.build())
    }

    fun winnerRule(block: RewardRuleBuilder.() -> Unit) {
        rule("winner", RewardCondition { uuid, result -> result.winner?.first == uuid }) {
            block()
        }
    }

    fun topKillerRule(block: RewardRuleBuilder.() -> Unit) {
        rule("top_killer", RewardCondition { uuid, _ ->
            StatTracker.top("kills", 1).firstOrNull()?.first == uuid
        }) {
            block()
        }
    }

    fun announcement(translationKey: String) { announcementKey = translationKey }

    @PublishedApi internal fun build(): RewardDistributor =
        RewardDistributor(rules.toList(), participationRewards.toList(), perKillRewards.toList(), announcementKey)
}

class RewardRuleBuilder(
    private val name: String,
    private val condition: RewardCondition,
) {
    private val rewards = mutableListOf<RewardEntry>()

    fun reward(currency: String, amount: Double, reason: String = name) {
        rewards.add(RewardEntry(currency, amount, reason))
    }

    internal fun build(): RewardRule = RewardRule(name, condition, rewards.toList())
}

inline fun rewardDistributor(block: RewardDistributorBuilder.() -> Unit): RewardDistributor =
    RewardDistributorBuilder().apply(block).build()
