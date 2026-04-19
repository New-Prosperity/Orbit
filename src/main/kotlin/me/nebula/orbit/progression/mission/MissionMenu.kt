package me.nebula.orbit.progression.mission

import me.nebula.ether.utils.duration.DurationFormatter
import me.nebula.gravity.economy.AddBalanceProcessor
import me.nebula.gravity.economy.EconomyStore
import me.nebula.gravity.economy.PurchaseCosmeticProcessor
import me.nebula.gravity.mission.ActiveMission
import me.nebula.gravity.mission.MissionData
import me.nebula.gravity.mission.MissionStore
import me.nebula.gravity.mission.MissionTemplates
import me.nebula.gravity.mission.RerollMissionProcessor
import me.nebula.gravity.mission.RerollResult
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.GuiBuilder
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import me.nebula.gravity.translation.Keys
import me.nebula.ether.utils.translation.asTranslationKey

object MissionMenu {

    private const val REROLL_COST = 50.0
    private const val MAX_DAILY_REROLLS = 1

    fun open(player: Player) {
        val data = MissionStore.load(player.uuid) ?: MissionData()

        val missionGui = gui(player.translateRaw(Keys.Orbit.Mission.Title), rows = 4) {
            slot(4, itemStack(Material.CLOCK) {
                name(player.translateRaw(Keys.Orbit.Mission.DailyHeader))
                val resetIn = DurationFormatter.formatCompact(data.dailyResetAt - System.currentTimeMillis())
                lore(player.translateRaw(Keys.Orbit.Mission.ResetTimer, "time" to resetIn))
                clean()
            })

            buildMissionSlots(player, data.dailyMissions, intArrayOf(10, 12, 14))
            buildRerollSlots(player, data, intArrayOf(11, 13, 15))

            slot(22, itemStack(Material.COMPASS) {
                name(player.translateRaw(Keys.Orbit.Mission.WeeklyHeader))
                val resetIn = DurationFormatter.formatCompact(data.weeklyResetAt - System.currentTimeMillis())
                lore(player.translateRaw(Keys.Orbit.Mission.ResetTimer, "time" to resetIn))
                clean()
            })

            buildMissionSlots(player, data.weeklyMissions, intArrayOf(19, 21, 23))

            val streak = data.dailyStreak
            val bonusXp = (50 * streak).coerceAtMost(350)
            val bonusCoins = (25 * streak).coerceAtMost(175)
            val streakMaterial = if (data.dailyAllCompleted) Material.BLAZE_POWDER else Material.FIRE_CHARGE
            slot(16, itemStack(streakMaterial) {
                name(player.translateRaw(Keys.Orbit.Mission.StreakDisplay, "streak" to streak.toString()))
                if (data.dailyAllCompleted) {
                    lore(player.translateRaw(Keys.Orbit.Mission.StreakMaintained))
                } else {
                    lore(player.translateRaw(Keys.Orbit.Mission.StreakPrompt))
                }
                if (streak > 0) {
                    lore(player.translateRaw(Keys.Orbit.Mission.Rewards,
                        "xp" to bonusXp.toString(),
                        "coins" to bonusCoins.toString(),
                    ))
                }
                if (data.dailyAllCompleted) glowing()
                clean()
            })

            fillDefault()
        }
        player.openGui(missionGui)
    }

    private fun GuiBuilder.buildMissionSlots(
        player: Player,
        missions: List<ActiveMission>,
        slots: IntArray,
    ) {
        missions.forEachIndexed { index, mission ->
            if (index >= slots.size) return@forEachIndexed
            val material = when {
                mission.completed -> Material.LIME_DYE
                mission.progress > 0 -> Material.YELLOW_DYE
                else -> Material.GRAY_DYE
            }
            slot(slots[index], itemStack(material) {
                name(player.translateRaw("orbit.mission.type.${mission.type.name.lowercase()}".asTranslationKey()))
                lore(player.translateRaw(Keys.Orbit.Mission.Progress,
                    "current" to mission.progress.toString(),
                    "target" to mission.target.toString(),
                ))
                lore(player.translateRaw(Keys.Orbit.Mission.Rewards,
                    "xp" to mission.xpReward.toString(),
                    "coins" to mission.coinReward.toString(),
                ))
                if (mission.completed) glowing()
                clean()
            })
        }
    }

    private fun GuiBuilder.buildRerollSlots(
        player: Player,
        data: MissionData,
        slots: IntArray,
    ) {
        val canReroll = data.dailyRerollsUsed < MAX_DAILY_REROLLS
        data.dailyMissions.forEachIndexed { index, mission ->
            if (index >= slots.size) return@forEachIndexed
            if (mission.completed) return@forEachIndexed
            val material = if (canReroll) Material.BARRIER else Material.LIGHT_GRAY_STAINED_GLASS_PANE
            slot(slots[index], itemStack(material) {
                if (canReroll) {
                    name(player.translateRaw(Keys.Orbit.Mission.Reroll))
                } else {
                    name(player.translateRaw(Keys.Orbit.Mission.RerollLimit))
                }
                clean()
            }) { clicker ->
                if (!canReroll) {
                    clicker.sendMessage(clicker.translate(Keys.Orbit.Mission.RerollLimit))
                    return@slot
                }
                handleReroll(clicker, index)
            }
        }
    }

    private fun handleReroll(player: Player, missionIndex: Int) {
        val purchased = EconomyStore.executeOnKey(player.uuid, PurchaseCosmeticProcessor("coins", REROLL_COST))
        if (purchased != true) {
            player.sendMessage(player.translate(Keys.Orbit.Mission.RerollNoCoins))
            return
        }

        val replacement = MissionTemplates.randomDaily(1).firstOrNull() ?: return
        val activeMission = ActiveMission(
            templateId = replacement.id,
            type = replacement.type,
            target = replacement.target,
            parameter = replacement.parameter,
            xpReward = replacement.xpReward,
            coinReward = replacement.coinReward,
        )

        val result = MissionStore.executeOnKey(
            player.uuid,
            RerollMissionProcessor(missionIndex, activeMission, REROLL_COST, MAX_DAILY_REROLLS),
        )

        when (result) {
            RerollResult.SUCCESS -> {
                player.sendMessage(player.translate(
                    Keys.Orbit.Mission.RerollConfirm,
                    "mission" to replacement.id,
                ))
                open(player)
            }
            RerollResult.LIMIT_REACHED -> {
                EconomyStore.executeOnKey(player.uuid, AddBalanceProcessor("coins", REROLL_COST))
                player.sendMessage(player.translate(Keys.Orbit.Mission.RerollLimit))
            }
            RerollResult.NO_COINS -> {
                EconomyStore.executeOnKey(player.uuid, AddBalanceProcessor("coins", REROLL_COST))
                player.sendMessage(player.translate(Keys.Orbit.Mission.RerollNoCoins))
            }
            RerollResult.NO_DATA -> {
                EconomyStore.executeOnKey(player.uuid, AddBalanceProcessor("coins", REROLL_COST))
            }
        }
    }
}
