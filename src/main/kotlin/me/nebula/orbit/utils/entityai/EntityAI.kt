package me.nebula.orbit.utils.entityai

import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.ai.GoalSelector
import net.minestom.server.entity.ai.TargetSelector
import net.minestom.server.entity.ai.goal.MeleeAttackGoal
import net.minestom.server.entity.ai.goal.RandomLookAroundGoal
import net.minestom.server.entity.ai.goal.RandomStrollGoal
import net.minestom.server.entity.ai.target.ClosestEntityTarget
import net.minestom.server.entity.ai.target.LastEntityDamagerTarget
import java.time.Duration

class AIGroupBuilder(private val creature: EntityCreature) {
    private val goals = mutableListOf<GoalSelector>()
    private val targets = mutableListOf<TargetSelector>()

    fun meleeAttack(speed: Double = 1.6, cooldownMs: Long = 1000) {
        goals.add(MeleeAttackGoal(creature, speed, Duration.ofMillis(cooldownMs)))
    }

    fun randomStroll(radius: Int = 10) {
        goals.add(RandomStrollGoal(creature, radius))
    }

    fun randomLookAround(chancePerTick: Int = 20) {
        goals.add(RandomLookAroundGoal(creature, chancePerTick))
    }

    fun goal(selector: GoalSelector) {
        goals.add(selector)
    }

    fun targetClosest(range: Float = 32f, targetType: Class<out net.minestom.server.entity.LivingEntity> = Player::class.java) {
        targets.add(ClosestEntityTarget(creature, range, targetType))
    }

    fun targetLastDamager(range: Float = 16f) {
        targets.add(LastEntityDamagerTarget(creature, range))
    }

    fun target(selector: TargetSelector) {
        targets.add(selector)
    }

    fun apply() {
        creature.addAIGroup(goals, targets)
    }
}

class EntityAIBuilder(private val creature: EntityCreature) {

    fun aiGroup(block: AIGroupBuilder.() -> Unit) {
        AIGroupBuilder(creature).apply(block).apply()
    }

    fun hostile(range: Float = 32f, attackSpeed: Double = 1.6, attackCooldownMs: Long = 1000) {
        aiGroup {
            meleeAttack(attackSpeed, attackCooldownMs)
            randomStroll()
            randomLookAround()
            targetClosest(range)
            targetLastDamager()
        }
    }

    fun passive(strollRadius: Int = 10) {
        aiGroup {
            randomStroll(strollRadius)
            randomLookAround()
        }
    }

    fun neutral(range: Float = 16f, attackSpeed: Double = 1.6, strollRadius: Int = 10) {
        aiGroup {
            meleeAttack(attackSpeed)
            randomStroll(strollRadius)
            randomLookAround()
            targetLastDamager(range)
        }
    }
}

fun EntityCreature.configureAI(block: EntityAIBuilder.() -> Unit): EntityCreature {
    EntityAIBuilder(this).apply(block)
    return this
}

fun hostileCreature(type: EntityType): EntityCreature {
    val creature = EntityCreature(type)
    creature.configureAI { hostile() }
    return creature
}

fun passiveCreature(type: EntityType): EntityCreature {
    val creature = EntityCreature(type)
    creature.configureAI { passive() }
    return creature
}

fun neutralCreature(type: EntityType): EntityCreature {
    val creature = EntityCreature(type)
    creature.configureAI { neutral() }
    return creature
}
