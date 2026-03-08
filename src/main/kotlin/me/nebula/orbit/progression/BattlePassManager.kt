package me.nebula.orbit.progression

import me.nebula.gravity.battlepass.AddBattlePassXpProcessor
import me.nebula.gravity.battlepass.AddXpToAllPassesProcessor
import me.nebula.gravity.battlepass.BattlePassStore
import me.nebula.gravity.battlepass.ClaimBattlePassRewardProcessor
import me.nebula.gravity.battlepass.SetBattlePassPremiumProcessor
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.cosmetic.UnlockCosmeticProcessor
import me.nebula.gravity.economy.AddBalanceProcessor
import me.nebula.gravity.economy.EconomyStore
import me.nebula.gravity.economy.PurchaseCosmeticProcessor
import me.nebula.gravity.messaging.BattlePassTierUpMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.orbit.translation.translate
import net.minestom.server.entity.Player

object BattlePassManager {

    fun addXp(player: Player, passId: String, amount: Long) {
        val definition = BattlePassRegistry[passId] ?: return
        val result = BattlePassStore.executeOnKey(
            player.uuid,
            AddBattlePassXpProcessor(passId, amount, definition.xpPerTier),
        )
        if (result.tiersGained > 0) {
            player.sendMessage(player.translate(
                "orbit.battlepass.tier_up",
                "tier" to result.newTier.toString(),
                "gained" to result.tiersGained.toString(),
            ))
            NetworkMessenger.publish(BattlePassTierUpMessage(player.uuid, passId, result.newTier, result.tiersGained))
        }
    }

    fun addXpToAll(player: Player, amount: Long) {
        val active = BattlePassRegistry.activePasses()
        if (active.isEmpty()) return
        val passConfigs = active.associate { it.id to it.xpPerTier }
        val results = BattlePassStore.executeOnKey(
            player.uuid,
            AddXpToAllPassesProcessor(amount, passConfigs),
        )
        for ((passId, result) in results) {
            if (result.tiersGained > 0) {
                player.sendMessage(player.translate(
                    "orbit.battlepass.tier_up",
                    "tier" to result.newTier.toString(),
                    "gained" to result.tiersGained.toString(),
                ))
                NetworkMessenger.publish(BattlePassTierUpMessage(player.uuid, passId, result.newTier, result.tiersGained))
            }
        }
    }

    fun purchasePremium(player: Player, passId: String): Boolean {
        val definition = BattlePassRegistry[passId] ?: return false
        if (definition.premiumPrice <= 0) return false
        val purchased = EconomyStore.executeOnKey(player.uuid, PurchaseCosmeticProcessor("coins", definition.premiumPrice.toDouble()))
        if (!purchased) {
            player.sendMessage(player.translate("orbit.battlepass.premium_insufficient"))
            return false
        }
        val set = BattlePassStore.executeOnKey(player.uuid, SetBattlePassPremiumProcessor(passId))
        if (!set) {
            EconomyStore.executeOnKey(player.uuid, AddBalanceProcessor("coins", definition.premiumPrice.toDouble()))
            return false
        }
        player.sendMessage(player.translate("orbit.battlepass.premium_purchased"))
        return true
    }

    fun claimReward(player: Player, passId: String, tier: Int, premium: Boolean): Boolean {
        val definition = BattlePassRegistry[passId] ?: return false
        val claimed = BattlePassStore.executeOnKey(
            player.uuid,
            ClaimBattlePassRewardProcessor(passId, tier, premium),
        )
        if (!claimed) return false

        val rewards = if (premium) definition.premiumRewards else definition.freeRewards
        val reward = rewards[tier] ?: return true

        when (reward.type) {
            "coins" -> EconomyStore.executeOnKey(player.uuid, AddBalanceProcessor("coins", reward.amount.toDouble()))
            "cosmetic" -> CosmeticStore.executeOnKey(player.uuid, UnlockCosmeticProcessor(reward.value))
        }

        player.sendMessage(player.translate(
            "orbit.battlepass.reward_claimed",
            "tier" to tier.toString(),
            "reward" to reward.value,
        ))
        return true
    }
}
