package me.nebula.orbit.progression.mission

import me.nebula.ether.utils.duration.DurationFormatter
import me.nebula.gravity.mission.MissionData
import me.nebula.gravity.mission.MissionStore
import me.nebula.gravity.mission.ActiveMission
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.GuiBuilder
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

object MissionMenu {

    fun open(player: Player) {
        val data = MissionStore.load(player.uuid) ?: MissionData()

        val missionGui = gui(player.translateRaw("orbit.mission.title"), rows = 4) {
            slot(4, itemStack(Material.CLOCK) {
                name(player.translateRaw("orbit.mission.daily_header"))
                val resetIn = DurationFormatter.formatCompact(data.dailyResetAt - System.currentTimeMillis())
                lore(player.translateRaw("orbit.mission.reset_timer", "time" to resetIn))
                clean()
            })

            buildMissionSlots(player, data.dailyMissions, intArrayOf(10, 12, 14))

            slot(22, itemStack(Material.COMPASS) {
                name(player.translateRaw("orbit.mission.weekly_header"))
                val resetIn = DurationFormatter.formatCompact(data.weeklyResetAt - System.currentTimeMillis())
                lore(player.translateRaw("orbit.mission.reset_timer", "time" to resetIn))
                clean()
            })

            buildMissionSlots(player, data.weeklyMissions, intArrayOf(19, 21, 23))

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
                name(player.translateRaw("orbit.mission.type.${mission.type.name.lowercase()}"))
                lore(player.translateRaw("orbit.mission.progress",
                    "current" to mission.progress.toString(),
                    "target" to mission.target.toString(),
                ))
                lore(player.translateRaw("orbit.mission.rewards",
                    "xp" to mission.xpReward.toString(),
                    "coins" to mission.coinReward.toString(),
                ))
                if (mission.completed) glowing()
                clean()
            })
        }
    }

}
