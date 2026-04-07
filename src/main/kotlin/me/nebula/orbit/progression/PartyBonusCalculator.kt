package me.nebula.orbit.progression

import me.nebula.gravity.party.PartyLookupStore
import me.nebula.gravity.party.PartyStore
import java.util.UUID

object PartyBonusCalculator {

    private const val BONUS_PER_MEMBER = 0.05f
    private const val MAX_BONUS = 0.20f

    data class PartyBonus(
        val multiplier: Float,
        val partyMembersInGame: Int,
        val bonusPercent: Int,
    )

    private val NO_BONUS = PartyBonus(1.0f, 0, 0)

    fun calculateBonus(playerUuid: UUID, partySnapshot: Map<UUID, Set<UUID>>): PartyBonus {
        val partyMembers = partySnapshot[playerUuid] ?: return NO_BONUS
        val membersInGame = partyMembers.size - 1
        if (membersInGame <= 0) return NO_BONUS
        val bonus = (membersInGame * BONUS_PER_MEMBER).coerceAtMost(MAX_BONUS)
        return PartyBonus(1.0f + bonus, membersInGame, (bonus * 100).toInt())
    }

    fun buildPartySnapshot(players: Set<UUID>): Map<UUID, Set<UUID>> {
        val snapshot = mutableMapOf<UUID, Set<UUID>>()
        val processed = mutableSetOf<UUID>()
        for (uuid in players) {
            if (uuid in processed) continue
            val partyId = PartyLookupStore.load(uuid) ?: continue
            val party = PartyStore.load(partyId) ?: continue
            val membersInGame = party.members.filter { it in players }.toSet()
            if (membersInGame.size > 1) {
                for (member in membersInGame) {
                    snapshot[member] = membersInGame
                    processed.add(member)
                }
            }
        }
        return snapshot
    }
}
