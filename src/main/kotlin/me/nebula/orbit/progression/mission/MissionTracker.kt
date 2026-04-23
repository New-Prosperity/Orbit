package me.nebula.orbit.progression.mission

import me.nebula.gravity.achievement.AchievementStore
import me.nebula.gravity.achievement.IncrementAchievementProcessor
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.gravity.battlepass.BattlePassDefinition
import me.nebula.orbit.perks.EconomyPerks
import me.nebula.gravity.mission.CheckDailyCompletionProcessor
import me.nebula.gravity.mission.CompletedMission
import me.nebula.gravity.mission.IncrementMissionProcessor
import me.nebula.gravity.mission.MissionStore
import me.nebula.gravity.mission.MissionType
import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.progression.BattlePassManager
import me.nebula.orbit.translation.translate
import net.minestom.server.entity.Player
import me.nebula.gravity.translation.Keys

object MissionTracker {

    private fun activeSeasonSnapshot(): List<BattlePassDefinition>? =
        (Orbit.mode as? GameMode)?.activeSeasonPasses?.ifEmpty { null }

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

    fun onAssist(player: Player) =
        increment(player, MissionType.ASSISTS, 1)

    fun onKillStreak(player: Player) =
        increment(player, MissionType.KILL_STREAK, 1)

    fun onCraftItem(player: Player) =
        increment(player, MissionType.CRAFT_ITEMS, 1)

    fun onMineBlock(player: Player) =
        increment(player, MissionType.MINE_BLOCKS, 1)

    fun onPlaceBlock(player: Player) =
        increment(player, MissionType.PLACE_BLOCKS, 1)

    fun onEatFood(player: Player) =
        increment(player, MissionType.EAT_FOOD, 1)

    fun onWalkDistance(player: Player, blocks: Int) =
        increment(player, MissionType.WALK_DISTANCE, blocks)

    fun onPlayGamemode(player: Player, gamemode: String) =
        increment(player, MissionType.PLAY_GAMEMODE, 1, gamemode)

    fun onWinGamemode(player: Player, gamemode: String) =
        increment(player, MissionType.WIN_GAMEMODE, 1, gamemode)

    fun onOpenContainer(player: Player) =
        increment(player, MissionType.OPEN_CONTAINERS, 1)

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
                EconomyPerks.grantCoins(player.uuid, mission.coinReward.toDouble())
            }
            if (mission.xpReward > 0) {
                BattlePassManager.addXpToAll(player, mission.xpReward.toLong(), activeSeasonSnapshot())
            }
            AchievementStore.executeOnKey(player.uuid, IncrementAchievementProcessor("mission_master", 1, 100))
            player.sendMessage(player.translate(
                Keys.Orbit.Mission.Completed,
                "mission" to mission.templateId,
                "xp" to mission.xpReward.toString(),
                "coins" to mission.coinReward.toString(),
            ))
        }
        if (completed.isNotEmpty()) checkDailyStreak(player)
    }

    private fun checkDailyStreak(player: Player) {
        val allDone = MissionStore.executeOnKey(player.uuid, CheckDailyCompletionProcessor())
        if (allDone != true) return

        val data = MissionStore.load(player.uuid) ?: return
        val streak = data.dailyStreak
        if (streak >= 7) {
            AchievementRegistry.complete(player, "streak_master")
        }
        val bonusXp = (50 * streak).coerceAtMost(350)
        val bonusCoins = (25 * streak).coerceAtMost(175)

        EconomyPerks.grantCoins(player.uuid, bonusCoins.toDouble())
        BattlePassManager.addXpToAll(player, bonusXp.toLong(), activeSeasonSnapshot())

        player.sendMessage(player.translate(
            Keys.Orbit.Mission.StreakBonus,
            "streak" to streak.toString(),
            "xp" to bonusXp.toString(),
            "coins" to bonusCoins.toString(),
        ))
    }
}
