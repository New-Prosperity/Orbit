package me.nebula.orbit.utils.botai

import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BotAI {

    private val brains = ConcurrentHashMap<UUID, BotBrain>()
    internal val skillLevels = ConcurrentHashMap<UUID, BotSkillLevel>()
    private var tickTask: Task? = null

    fun install() {
        require(tickTask == null) { "BotAI already installed" }
        tickTask = repeat(1) { tick() }
    }

    fun uninstall() {
        brains.values.forEach { it.clear() }
        brains.clear()
        skillLevels.clear()
        BotLobbyFiller.stopAll()
        tickTask?.cancel()
        tickTask = null
    }

    fun attach(
        player: Player,
        vararg goals: BotGoal,
        personality: BotPersonality = BotPersonality(),
        skill: BotSkillLevel = BotSkillLevels.AVERAGE,
    ): BotBrain {
        val brain = BotBrain(player, goals.toList(), personality, skill)
        brains[player.uuid] = brain
        return brain
    }

    fun detach(player: Player) {
        brains.remove(player.uuid)?.clear()
    }

    fun getBrain(player: Player): BotBrain? = brains[player.uuid]

    fun attachSurvivalAI(
        player: Player,
        personality: BotPersonality = BotPersonalities.SURVIVOR,
        skill: BotSkillLevel = BotSkillLevels.AVERAGE,
    ): BotBrain = attach(
        player,
        SurviveGoal(),
        FleeGoal(healthThreshold = 6f),
        AttackNearestGoal(range = 16.0),
        EquipBestArmorGoal(),
        ToolProgressionGoal(),
        GatherWoodGoal(),
        StripMineGoal(),
        SmeltOresGoal(),
        CraftToolGoal(Material.WOODEN_PICKAXE),
        InventoryManagementGoal(),
        ExploreGoal(),
        personality = personality,
        skill = skill,
    )

    fun attachMinerAI(
        player: Player,
        personality: BotPersonality = BotPersonalities.BUILDER,
        skill: BotSkillLevel = BotSkillLevels.AVERAGE,
    ): BotBrain = attach(
        player,
        SurviveGoal(),
        FleeGoal(healthThreshold = 6f),
        ToolProgressionGoal(),
        GatherWoodGoal(),
        StripMineGoal(),
        CaveExploreGoal(),
        SmeltOresGoal(),
        PlaceFurnaceGoal(),
        EquipBestArmorGoal(),
        InventoryManagementGoal(),
        GatherResourceGoal(Material.DIAMOND, minCount = 4),
        ExploreGoal(),
        personality = personality,
        skill = skill,
    )

    fun attachCombatAI(
        player: Player,
        personality: BotPersonality = BotPersonalities.WARRIOR,
        skill: BotSkillLevel = BotSkillLevels.AVERAGE,
    ): BotBrain = attach(
        player,
        SurviveGoal(),
        CriticalAttackGoal(range = 16.0),
        AttackNearestGoal(range = 24.0),
        ShieldDefenseGoal(),
        RangedAttackGoal(range = 24.0),
        UsePotionGoal(),
        FleeGoal(healthThreshold = 3f),
        EquipBestArmorGoal(),
        personality = personality,
        skill = skill,
    )

    fun attachPvPAI(
        player: Player,
        personality: BotPersonality = BotPersonalities.WARRIOR,
        skill: BotSkillLevel = BotSkillLevels.AVERAGE,
    ): BotBrain = attach(
        player,
        SurviveGoal(),
        CriticalAttackGoal(range = 16.0),
        AttackNearestGoal(range = 24.0),
        ShieldDefenseGoal(),
        RangedAttackGoal(range = 24.0),
        UsePotionGoal(),
        FleeGoal(healthThreshold = 4f),
        EquipBestArmorGoal(),
        BridgeGoal(),
        personality = personality,
        skill = skill,
    )

    fun attachGathererAI(
        player: Player,
        personality: BotPersonality = BotPersonalities.BUILDER,
        skill: BotSkillLevel = BotSkillLevels.AVERAGE,
    ): BotBrain = attach(
        player,
        SurviveGoal(),
        GatherWoodGoal(),
        CraftToolGoal(Material.WOODEN_PICKAXE),
        CraftToolGoal(Material.WOODEN_AXE),
        EquipBestArmorGoal(),
        ExploreGoal(),
        personality = personality,
        skill = skill,
    )

    fun attachPassiveAI(
        player: Player,
        personality: BotPersonality = BotPersonalities.EXPLORER,
        skill: BotSkillLevel = BotSkillLevels.AVERAGE,
    ): BotBrain = attach(
        player,
        SurviveGoal(),
        ExploreGoal(),
        personality = personality,
        skill = skill,
    )

    private fun tick() {
        val dead = mutableListOf<UUID>()
        for ((uuid, brain) in brains) {
            if (!brain.player.isOnline) {
                dead.add(uuid)
                continue
            }
            brain.tick()
        }
        dead.forEach { brains.remove(it)?.clear() }
    }
}
