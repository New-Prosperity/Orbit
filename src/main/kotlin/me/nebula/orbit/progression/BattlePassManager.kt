package me.nebula.orbit.progression

import me.nebula.gravity.battlepass.AddBattlePassXpProcessor
import me.nebula.gravity.battlepass.BattlePassDefinition
import me.nebula.gravity.battlepass.AddXpToAllPassesProcessor
import me.nebula.gravity.battlepass.BattlePassData
import me.nebula.gravity.battlepass.BattlePassStore
import me.nebula.gravity.battlepass.ClaimBattlePassRewardProcessor
import me.nebula.gravity.battlepass.PurchaseTierProcessor
import me.nebula.gravity.battlepass.SetBattlePassPremiumProcessor
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.cosmetic.UnlockCosmeticProcessor
import me.nebula.gravity.economy.AddBalanceProcessor
import me.nebula.gravity.economy.EconomyStore
import me.nebula.gravity.economy.PurchaseCosmeticProcessor
import me.nebula.gravity.messaging.BattlePassTierUpMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.perks.PerkResolver
import me.nebula.gravity.perks.Perks
import me.nebula.orbit.perks.EconomyPerks
import me.nebula.orbit.Orbit
import me.nebula.orbit.translation.translate
import net.minestom.server.entity.Player
import kotlin.math.roundToLong
import me.nebula.gravity.translation.Keys

object BattlePassManager {

    private fun perkMultiplied(player: Player, amount: Long): Long {
        val multiplier = PerkResolver.resolve(player.uuid, Perks.XP_MULTIPLIER)
        if (multiplier == 1.0) return amount
        return (amount * multiplier).roundToLong()
    }

    private fun boostedAmount(player: Player, passId: String, amount: Long): Long {
        val definition = BattlePassRegistry[passId] ?: return amount
        val data = BattlePassStore.load(player.uuid) ?: BattlePassData()
        val progress = data.passes[passId] ?: return amount
        if (!progress.premium) return amount
        return (amount * definition.xpBoostPremium).roundToLong()
    }

    fun addXp(player: Player, passId: String, amount: Long) {
        val definition = BattlePassRegistry[passId] ?: return
        val perkAdjusted = perkMultiplied(player, amount)
        val effective = boostedAmount(player, passId, perkAdjusted)
        val result = BattlePassStore.executeOnKey(
            player.uuid,
            AddBattlePassXpProcessor(passId, effective, definition.xpPerTier),
        )
        if (result.tiersGained > 0) {
            player.sendMessage(player.translate(
                Keys.Orbit.Battlepass.TierUp,
                "tier" to result.newTier.toString(),
                "gained" to result.tiersGained.toString(),
            ))
            NetworkMessenger.publish(BattlePassTierUpMessage(
                playerId = player.uuid,
                passId = passId,
                newTier = result.newTier,
                tiersGained = result.tiersGained,
                playerName = player.username,
                isPremium = BattlePassStore.load(player.uuid)?.passes?.get(passId)?.premium == true,
                serverName = Orbit.serverName,
                reachedAt = System.currentTimeMillis(),
            ))
        }
    }

    fun addXpToAll(player: Player, amount: Long, activePasses: List<BattlePassDefinition>? = null) {
        val active = activePasses ?: BattlePassRegistry.activePasses()
        if (active.isEmpty()) return
        val perkAdjusted = perkMultiplied(player, amount)
        val data = BattlePassStore.load(player.uuid) ?: BattlePassData()
        val passConfigs = active.associate { def ->
            val progress = data.passes[def.id]
            val effective = if (progress?.premium == true) (perkAdjusted * def.xpBoostPremium).roundToLong() else perkAdjusted
            def.id to (effective to def.xpPerTier)
        }
        val boostedConfigs = passConfigs.mapValues { (_, pair) -> pair.second }
        val boostedAmounts = passConfigs.mapValues { (_, pair) -> pair.first }
        val maxAmount = boostedAmounts.values.maxOrNull() ?: perkAdjusted

        val results = BattlePassStore.executeOnKey(
            player.uuid,
            AddXpToAllPassesProcessor(maxAmount, boostedConfigs),
        )
        for ((passId, result) in results) {
            if (result.tiersGained > 0) {
                player.sendMessage(player.translate(
                    Keys.Orbit.Battlepass.TierUp,
                    "tier" to result.newTier.toString(),
                    "gained" to result.tiersGained.toString(),
                ))
                NetworkMessenger.publish(BattlePassTierUpMessage(
                playerId = player.uuid,
                passId = passId,
                newTier = result.newTier,
                tiersGained = result.tiersGained,
                playerName = player.username,
                isPremium = BattlePassStore.load(player.uuid)?.passes?.get(passId)?.premium == true,
                serverName = Orbit.serverName,
                reachedAt = System.currentTimeMillis(),
            ))
            }
        }
    }

    fun purchasePremium(player: Player, passId: String): Boolean {
        val definition = BattlePassRegistry[passId] ?: return false
        if (definition.premiumPrice <= 0) return false
        val purchased = EconomyStore.executeOnKey(player.uuid, PurchaseCosmeticProcessor("coins", definition.premiumPrice.toDouble()))
        if (!purchased) {
            player.sendMessage(player.translate(Keys.Orbit.Battlepass.PremiumInsufficient))
            return false
        }
        val set = BattlePassStore.executeOnKey(player.uuid, SetBattlePassPremiumProcessor(passId))
        if (!set) {
            EconomyStore.executeOnKey(player.uuid, AddBalanceProcessor("coins", definition.premiumPrice.toDouble()))
            return false
        }
        player.sendMessage(player.translate(Keys.Orbit.Battlepass.PremiumPurchased))
        return true
    }

    fun purchaseTier(player: Player, passId: String): Boolean {
        val definition = BattlePassRegistry[passId] ?: return false
        val purchased = EconomyStore.executeOnKey(
            player.uuid,
            PurchaseCosmeticProcessor("coins", definition.tierPurchasePrice.toDouble()),
        )
        if (!purchased) {
            player.sendMessage(player.translate(Keys.Orbit.Battlepass.TierPurchaseInsufficient))
            return false
        }
        val advanced = BattlePassStore.executeOnKey(
            player.uuid,
            PurchaseTierProcessor(passId, definition.xpPerTier),
        )
        if (!advanced) {
            EconomyStore.executeOnKey(player.uuid, AddBalanceProcessor("coins", definition.tierPurchasePrice.toDouble()))
            player.sendMessage(player.translate(Keys.Orbit.Battlepass.TierPurchaseMax))
            return false
        }
        val data = BattlePassStore.load(player.uuid) ?: BattlePassData()
        val newTier = data.passes[passId]?.tier ?: 0
        player.sendMessage(player.translate(
            Keys.Orbit.Battlepass.TierPurchased,
            "tier" to newTier.toString(),
        ))
        NetworkMessenger.publish(BattlePassTierUpMessage(
            playerId = player.uuid,
            passId = passId,
            newTier = newTier,
            tiersGained = 1,
            playerName = player.username,
            isPremium = data.passes[passId]?.premium == true,
            serverName = Orbit.serverName,
            reachedAt = System.currentTimeMillis(),
        ))
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
            "coins" -> EconomyPerks.grantCoins(player.uuid, reward.amount.toDouble())
            "cosmetic" -> CosmeticStore.executeOnKey(player.uuid, UnlockCosmeticProcessor(reward.value))
        }

        player.sendMessage(player.translate(
            Keys.Orbit.Battlepass.RewardClaimed,
            "tier" to tier.toString(),
            "reward" to reward.value,
        ))
        return true
    }
}
