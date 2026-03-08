package me.nebula.orbit.progression

import me.nebula.gravity.battlepass.BattlePassDefinition
import me.nebula.gravity.battlepass.BattlePassDefinitions

object BattlePassRegistry {

    fun activePasses(): List<BattlePassDefinition> = BattlePassDefinitions.activePasses

    operator fun get(passId: String): BattlePassDefinition? = BattlePassDefinitions[passId]

    fun activePassIds(): Set<String> = BattlePassDefinitions.activePassIds
}
