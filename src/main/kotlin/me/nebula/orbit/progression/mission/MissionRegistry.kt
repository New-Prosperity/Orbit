package me.nebula.orbit.progression.mission

import me.nebula.gravity.mission.MissionTemplate
import me.nebula.gravity.mission.MissionTemplates

object MissionRegistry {

    fun dailyPool(): List<MissionTemplate> = MissionTemplates.dailyPool

    fun weeklyPool(): List<MissionTemplate> = MissionTemplates.weeklyPool

    fun randomDaily(count: Int): List<MissionTemplate> =
        MissionTemplates.randomDaily(count)

    fun randomWeekly(count: Int): List<MissionTemplate> =
        MissionTemplates.randomWeekly(count)
}
