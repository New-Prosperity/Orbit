package me.nebula.orbit.utils.gametest

import me.nebula.orbit.utils.botai.AttackNearestGoal
import me.nebula.orbit.utils.botai.BotAI
import me.nebula.orbit.utils.botai.BotAction
import me.nebula.orbit.utils.botai.BotBrain
import me.nebula.orbit.utils.botai.BotGoal
import me.nebula.orbit.utils.botai.BotPersonalities
import me.nebula.orbit.utils.botai.BotPersonality
import me.nebula.orbit.utils.botai.EquipBestArmorGoal
import me.nebula.orbit.utils.botai.ExploreGoal
import me.nebula.orbit.utils.botai.FleeGoal
import me.nebula.orbit.utils.botai.GatherWoodGoal
import me.nebula.orbit.utils.botai.LookAt
import me.nebula.orbit.utils.botai.SprintTo
import me.nebula.orbit.utils.botai.SurviveGoal
import me.nebula.orbit.utils.botai.Wait
import me.nebula.orbit.utils.botai.WalkTo
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import java.util.UUID

enum class TestBehavior {
    IDLE,
    WANDER,
    AGGRESSIVE,
    DEFENSIVE,
    PATROL,
    FOLLOW,
    FLEE,
    RANDOM_ACTIONS,
    CHAOS,
}

data class BehaviorConfig(
    val targetPlayer: UUID? = null,
    val waypoints: List<Pos>? = null,
    val attackRange: Double = 3.0,
    val fleeHealth: Float = 5.0f,
    val wanderRadius: Double = 10.0,
)

object TestBotController {

    fun setBehavior(player: Player, behavior: TestBehavior, config: BehaviorConfig = BehaviorConfig()) {
        BotAI.detach(player)
        val goals = behaviorToGoals(behavior, config)
        val personality = behaviorToPersonality(behavior)
        if (goals.isEmpty()) return
        BotAI.attach(player, *goals.toTypedArray(), personality = personality)
    }

    fun clearBehavior(player: Player) {
        BotAI.detach(player)
    }

    fun clearAll() {
        BotAI.uninstall()
        BotAI.install()
    }

    fun getBrain(player: Player): BotBrain? = BotAI.getBrain(player)

    private fun behaviorToGoals(behavior: TestBehavior, config: BehaviorConfig): List<BotGoal> =
        when (behavior) {
            TestBehavior.IDLE -> emptyList()
            TestBehavior.WANDER -> listOf(ExploreGoal())
            TestBehavior.AGGRESSIVE -> listOf(
                AttackNearestGoal(range = config.attackRange.coerceAtLeast(16.0)),
                SurviveGoal(),
                EquipBestArmorGoal(),
            )
            TestBehavior.DEFENSIVE -> listOf(
                FleeGoal(healthThreshold = config.fleeHealth),
                AttackNearestGoal(range = config.attackRange.coerceAtLeast(16.0)),
                SurviveGoal(),
                EquipBestArmorGoal(),
            )
            TestBehavior.PATROL -> {
                val waypoints = config.waypoints
                if (waypoints.isNullOrEmpty()) listOf(ExploreGoal())
                else listOf(PatrolGoal(waypoints))
            }
            TestBehavior.FOLLOW -> {
                val targetUuid = config.targetPlayer
                if (targetUuid == null) listOf(ExploreGoal())
                else listOf(FollowGoal(targetUuid))
            }
            TestBehavior.FLEE -> listOf(FleeGoal(healthThreshold = 20f))
            TestBehavior.RANDOM_ACTIONS -> listOf(
                SurviveGoal(),
                AttackNearestGoal(range = 8.0),
                GatherWoodGoal(),
                ExploreGoal(),
            )
            TestBehavior.CHAOS -> listOf(
                AttackNearestGoal(range = 24.0),
                SurviveGoal(),
                ExploreGoal(),
            )
        }

    private fun behaviorToPersonality(behavior: TestBehavior): BotPersonality =
        when (behavior) {
            TestBehavior.IDLE -> BotPersonality()
            TestBehavior.WANDER -> BotPersonalities.EXPLORER
            TestBehavior.AGGRESSIVE -> BotPersonality(aggression = 0.9f, caution = 0.1f, resourcefulness = 0.2f)
            TestBehavior.DEFENSIVE -> BotPersonality(aggression = 0.4f, caution = 0.9f, resourcefulness = 0.5f)
            TestBehavior.PATROL -> BotPersonality(aggression = 0.3f, caution = 0.5f, curiosity = 0.2f)
            TestBehavior.FOLLOW -> BotPersonality(aggression = 0.2f, caution = 0.6f, curiosity = 0.3f)
            TestBehavior.FLEE -> BotPersonality(aggression = 0.0f, caution = 1.0f, resourcefulness = 0.3f)
            TestBehavior.RANDOM_ACTIONS -> BotPersonalities.random()
            TestBehavior.CHAOS -> BotPersonalities.BERSERKER
        }
}

class PatrolGoal(private val waypoints: List<Pos>) : BotGoal() {
    private var currentIndex = 0

    override fun calculateUtility(brain: BotBrain): Float = 0.5f

    override fun shouldActivate(brain: BotBrain): Boolean = waypoints.isNotEmpty()

    override fun createActions(brain: BotBrain): List<BotAction> {
        if (waypoints.isEmpty()) return listOf(Wait(20))
        val target = waypoints[currentIndex % waypoints.size]
        currentIndex = (currentIndex + 1) % waypoints.size
        return listOf(WalkTo(target))
    }
}

class FollowGoal(private val targetUuid: UUID, private val stopDistance: Double = 2.0) : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val target = brain.findEntityByUuid(targetUuid) ?: return 0f
        return 0.7f
    }

    override fun shouldActivate(brain: BotBrain): Boolean = brain.findEntityByUuid(targetUuid) != null

    override fun createActions(brain: BotBrain): List<BotAction> {
        val target = brain.findEntityByUuid(targetUuid)
            ?: return listOf(Wait(20))
        val dist = brain.player.position.distance(target.position)
        if (dist <= stopDistance) {
            return listOf(LookAt(target.position.add(0.0, target.eyeHeight, 0.0)))
        }
        return if (dist > 8.0) {
            listOf(SprintTo(target.position))
        } else {
            listOf(WalkTo(target.position))
        }
    }

    override fun shouldCancel(brain: BotBrain): Boolean = brain.findEntityByUuid(targetUuid) == null
}
