package me.nebula.orbit.progression.mission

import me.nebula.gravity.achievement.IncrementAchievementProcessor
import me.nebula.gravity.achievement.AchievementStore
import me.nebula.gravity.economy.AddBalanceProcessor
import me.nebula.gravity.economy.EconomyStore
import me.nebula.gravity.mission.CompletedMission
import me.nebula.gravity.mission.IncrementMissionProcessor
import me.nebula.gravity.mission.MissionStore
import me.nebula.gravity.mission.MissionType
import me.nebula.orbit.progression.BattlePassManager
import me.nebula.orbit.translation.translate
import net.minestom.server.entity.Player

object MissionTracker {

    fun onKill(player: Player) =
        increment(player, MissionType.KILL, 1)

    fun onWin(player: Player) =
        increment(player, MissionType.WIN, 1)

    fun onGamePlayed(player: Player) =
        increment(player, MissionType.PLAY_GAMES, 1)

    fun onSurvivalMinute(player: Player) =
        increment(player, MissionType.SURVIVE_MINUTES, 1)

    fun onDamageDealt(player: Player, amount: Int) =
        increment(player, MissionType.DEAL_DAMAGE, amount)

    fun onTopPlacement(player: Player) =
        increment(player, MissionType.TOP_PLACEMENT, 1)

    fun onUseCategory(player: Player, category: String) =
        increment(player, MissionType.USE_CATEGORY, 1, category)

    private fun increment(player: Player, type: MissionType, amount: Int, parameter: String = "") {
        val completed = MissionStore.executeOnKey(
            player.uuid,
            IncrementMissionProcessor(type, amount, parameter),
        )
        handleCompleted(player, completed)
    }

    private fun handleCompleted(player: Player, completed: List<CompletedMission>) {
        for (mission in completed) {
            if (mission.coinReward > 0) {
                EconomyStore.executeOnKey(player.uuid, AddBalanceProcessor("coins", mission.coinReward.toDouble()))
            }
            if (mission.xpReward > 0) {
                BattlePassManager.addXpToAll(player, mission.xpReward.toLong())
            }
            AchievementStore.executeOnKey(player.uuid, IncrementAchievementProcessor("mission_master", 1, 100))
            player.sendMessage(player.translate(
                "orbit.mission.completed",
                "mission" to mission.templateId,
                "xp" to mission.xpReward.toString(),
                "coins" to mission.coinReward.toString(),
            ))
        }
    }
}
