package me.nebula.orbit.perks

import me.nebula.gravity.perks.PerkContribution
import me.nebula.gravity.perks.PerkKey
import me.nebula.gravity.perks.PerkResolver
import me.nebula.gravity.perks.Perks
import me.nebula.gravity.perks.RankPerkContribution
import me.nebula.gravity.perks.RankPerkRegistry
import me.nebula.gravity.progression.ModeLevelPerkContribution
import me.nebula.gravity.progression.ModeLevelPerkTable
import me.nebula.gravity.progression.ModeLevelPerks
import me.nebula.gravity.rank.PlayerRankStore
import java.util.UUID

fun installDefaultPerks() {
    RankPerkRegistry.configure("default", mapOf(
        Perks.XP_MULTIPLIER to 1.0,
    ))
    RankPerkRegistry.configure("iron", mapOf(
        Perks.XP_MULTIPLIER to 1.05,
        Perks.COSMETIC_DISCOUNT to 0.05,
    ))
    RankPerkRegistry.configure("gold", mapOf(
        Perks.XP_MULTIPLIER to 1.15,
        Perks.COIN_MULTIPLIER to 1.10,
        Perks.COSMETIC_DISCOUNT to 0.10,
        Perks.QUEUE_PRIORITY to 1,
    ))
    RankPerkRegistry.configure("diamond", mapOf(
        Perks.XP_MULTIPLIER to 1.25,
        Perks.COIN_MULTIPLIER to 1.20,
        Perks.COSMETIC_DISCOUNT to 0.15,
        Perks.QUEUE_PRIORITY to 2,
        Perks.PARTY_MAX_SIZE to 6,
    ))
    RankPerkRegistry.configure("mythic", mapOf(
        Perks.XP_MULTIPLIER to 1.50,
        Perks.COIN_MULTIPLIER to 1.35,
        Perks.COSMETIC_DISCOUNT to 0.25,
        Perks.QUEUE_PRIORITY to 3,
        Perks.PARTY_MAX_SIZE to 8,
        Perks.DAILY_REWARD_MULTIPLIER to 2.0,
    ))

    PerkResolver.register(RankPerkContribution { uuid ->
        PlayerRankStore.load(uuid)?.rank
    })

    ModeLevelPerkTable.configure("battleroyale", ModeLevelPerks(
        itemPointsPerLevel = 1,
        bonusPointsPerLevel = 0,
        slotsPerPrestige = 1,
        itemPointsCap = 7,
        bonusPointsCap = 3,
    ))
    PerkResolver.register(ModeLevelPerkContribution())

    PerkResolver.register(BattleRoyaleBaseContribution)
}

object BattleRoyaleBaseContribution : PerkContribution {
    override val id: String = "br_base"

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> valueFor(uuid: UUID, key: PerkKey<T>, modeId: String?): T? {
        if (modeId != "battleroyale") return null
        return when (key.id) {
            Perks.LOADOUT_ITEM_POINTS.id -> 2 as T
            Perks.LOADOUT_BONUS_POINTS.id -> 3 as T
            Perks.LOADOUT_SLOT_COUNT.id -> 2 as T
            else -> null
        }
    }
}
