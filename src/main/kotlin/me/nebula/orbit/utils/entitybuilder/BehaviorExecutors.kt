package me.nebula.orbit.utils.entitybuilder

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityProjectile
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.Damage
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class IdleExecutor(
    private val minTicks: Int = 40,
    private val maxTicks: Int = 100,
) : BehaviorExecutor {
    private var remaining = 0

    override fun onStart(entity: SmartEntity) {
        remaining = Random.nextInt(minTicks, maxTicks + 1)
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
    }

    override fun execute(entity: SmartEntity): Boolean = --remaining > 0
}

class FlatRoamExecutor(
    private val range: Double = 10.0,
    private val runTicks: Int = 100,
    private val maxRetries: Int = 10,
) : BehaviorExecutor {
    private var ticksLeft = 0

    override fun onStart(entity: SmartEntity) {
        ticksLeft = runTicks
        pickTarget(entity)
    }

    override fun execute(entity: SmartEntity): Boolean {
        if (--ticksLeft <= 0) return false
        val target = entity.memory.get(MemoryKeys.MOVE_TARGET)
        if (target == null || entity.position.distance(target) < 1.5) pickTarget(entity)
        return true
    }

    override fun onStop(entity: SmartEntity) {
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
    }

    private fun pickTarget(entity: SmartEntity) {
        val pos = entity.position
        val instance = entity.instance ?: return
        repeat(maxRetries) {
            val dx = Random.nextDouble(-range, range)
            val dz = Random.nextDouble(-range, range)
            val target = Pos(pos.x() + dx, pos.y(), pos.z() + dz)
            val below = instance.getBlock(target.blockX(), target.blockY() - 1, target.blockZ())
            if (below.isSolid) {
                entity.memory.set(MemoryKeys.MOVE_TARGET, target)
                return
            }
        }
    }
}

class LookAroundExecutor(
    private val minTicks: Int = 20,
    private val maxTicks: Int = 60,
) : BehaviorExecutor {
    private var remaining = 0

    override fun onStart(entity: SmartEntity) {
        remaining = Random.nextInt(minTicks, maxTicks + 1)
        val yaw = Random.nextFloat() * 360f - 180f
        entity.setView(yaw, 0f)
    }

    override fun execute(entity: SmartEntity): Boolean = --remaining > 0
}

class FollowEntityExecutor(
    private val memoryKey: MemoryKey<*> = MemoryKeys.NEAREST_PLAYER,
    private val minRange: Double = 2.0,
    private val maxRange: Double = 32.0,
) : BehaviorExecutor {
    override fun execute(entity: SmartEntity): Boolean {
        val target = entity.memory.get(memoryKey) as? Entity ?: return false
        if (target.isRemoved) return false
        val dist = entity.position.distance(target.position)
        if (dist > maxRange) return false
        entity.memory.set(MemoryKeys.LOOK_TARGET, target.position.add(0.0, 1.5, 0.0))
        if (dist > minRange) {
            entity.memory.set(MemoryKeys.MOVE_TARGET, target.position)
        } else {
            entity.memory.clear(MemoryKeys.MOVE_TARGET)
        }
        return true
    }

    override fun onStop(entity: SmartEntity) {
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
        entity.memory.clear(MemoryKeys.LOOK_TARGET)
    }
}

class MeleeAttackExecutor(
    private val attackRange: Double = 2.5,
    private val cooldownTicks: Int = 20,
    private val damage: Float = 4f,
    private val onHit: ((SmartEntity, LivingEntity) -> Unit)? = null,
) : BehaviorExecutor {
    private var cooldown = 0

    override fun execute(entity: SmartEntity): Boolean {
        val target = entity.memory.get(MemoryKeys.ATTACK_TARGET) as? LivingEntity ?: return false
        if (target.isRemoved || target.isDead) return false
        entity.memory.set(MemoryKeys.LOOK_TARGET, target.position.add(0.0, 1.5, 0.0))
        entity.memory.set(MemoryKeys.MOVE_TARGET, target.position)

        if (cooldown > 0) { cooldown--; return true }
        if (entity.position.distance(target.position) <= attackRange) {
            target.damage(Damage.fromEntity(entity, damage))
            onHit?.invoke(entity, target)
            cooldown = cooldownTicks
        }
        return true
    }

    override fun onStop(entity: SmartEntity) {
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
        entity.memory.clear(MemoryKeys.LOOK_TARGET)
    }
}

class PanicExecutor(
    private val range: Double = 8.0,
) : BehaviorExecutor {
    override fun onStart(entity: SmartEntity) { pickTarget(entity) }

    override fun execute(entity: SmartEntity): Boolean {
        val panicTicks = entity.memory.get(MemoryKeys.PANIC_TICKS) ?: return false
        if (panicTicks <= 0) return false
        entity.memory.set(MemoryKeys.PANIC_TICKS, panicTicks - 1)
        val target = entity.memory.get(MemoryKeys.MOVE_TARGET)
        if (target == null || entity.position.distance(target) < 1.5) pickTarget(entity)
        return true
    }

    override fun onStop(entity: SmartEntity) {
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
        entity.memory.clear(MemoryKeys.PANIC_TICKS)
    }

    private fun pickTarget(entity: SmartEntity) {
        val pos = entity.position
        val angle = Random.nextDouble() * Math.PI * 2
        val dist = Random.nextDouble(range * 0.5, range)
        entity.memory.set(MemoryKeys.MOVE_TARGET, Pos(pos.x() + cos(angle) * dist, pos.y(), pos.z() + sin(angle) * dist))
    }
}

class RangedAttackExecutor(
    private val projectileType: EntityType = EntityType.ARROW,
    private val attackRange: Double = 16.0,
    private val minRange: Double = 4.0,
    private val cooldownTicks: Int = 40,
    private val projectileSpeed: Double = 1.5,
    private val projectileDamage: Float = 4f,
    private val onShoot: ((SmartEntity, Entity) -> Unit)? = null,
) : BehaviorExecutor {
    private var cooldown = 0

    override fun execute(entity: SmartEntity): Boolean {
        val target = entity.memory.get(MemoryKeys.ATTACK_TARGET) as? LivingEntity ?: return false
        if (target.isRemoved || target.isDead) return false
        val dist = entity.position.distance(target.position)
        if (dist > attackRange) return false
        entity.memory.set(MemoryKeys.LOOK_TARGET, target.position.add(0.0, 1.5, 0.0))

        if (dist < minRange) {
            val away = entity.position.sub(target.position).asVec().normalize().mul(minRange)
            entity.memory.set(MemoryKeys.MOVE_TARGET, entity.position.add(away))
        } else {
            entity.memory.clear(MemoryKeys.MOVE_TARGET)
        }

        if (cooldown > 0) { cooldown--; return true }
        cooldown = cooldownTicks

        val instance = entity.instance ?: return false
        val projectile = EntityProjectile(entity, projectileType)
        val eyePos = entity.position.add(0.0, entity.eyeHeight, 0.0)
        val direction = target.position.add(0.0, target.eyeHeight, 0.0).sub(eyePos).asVec().normalize()
        projectile.setInstance(instance, eyePos)
        projectile.velocity = direction.mul(projectileSpeed * 20.0)
        onShoot?.invoke(entity, projectile)
        return true
    }

    override fun onStop(entity: SmartEntity) {
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
        entity.memory.clear(MemoryKeys.LOOK_TARGET)
    }
}

class LeapAttackExecutor(
    private val leapRange: Double = 6.0,
    private val minRange: Double = 3.0,
    private val horizontalForce: Double = 1.2,
    private val verticalForce: Double = 0.5,
    private val damage: Float = 6f,
    private val cooldownTicks: Int = 60,
    private val onLeap: ((SmartEntity) -> Unit)? = null,
) : BehaviorExecutor {
    private var cooldown = 0
    private var leaping = false

    override fun execute(entity: SmartEntity): Boolean {
        val target = entity.memory.get(MemoryKeys.ATTACK_TARGET) as? LivingEntity ?: return false
        if (target.isRemoved || target.isDead) return false
        val dist = entity.position.distance(target.position)
        entity.memory.set(MemoryKeys.LOOK_TARGET, target.position.add(0.0, 1.5, 0.0))

        if (cooldown > 0) {
            cooldown--
            entity.memory.set(MemoryKeys.MOVE_TARGET, target.position)
            return true
        }

        if (leaping) {
            if (entity.isOnGround) {
                leaping = false
                if (dist <= 2.5) {
                    target.damage(Damage.fromEntity(entity, damage))
                }
                cooldown = cooldownTicks
            }
            return true
        }

        if (dist in minRange..leapRange && entity.isOnGround) {
            val direction = target.position.sub(entity.position).asVec().normalize()
            entity.velocity = direction.mul(horizontalForce * 20.0).withY(verticalForce * 20.0)
            leaping = true
            onLeap?.invoke(entity)
            return true
        }

        entity.memory.set(MemoryKeys.MOVE_TARGET, target.position)
        return true
    }

    override fun onStop(entity: SmartEntity) {
        leaping = false
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
        entity.memory.clear(MemoryKeys.LOOK_TARGET)
    }
}

class StrafeExecutor(
    private val strafeRange: Double = 8.0,
    private val strafeSpeed: Double = 0.1,
    private val changeDirTicks: Int = 40,
) : BehaviorExecutor {
    private var ticksUntilChange = 0

    override fun onStart(entity: SmartEntity) {
        entity.memory.set(MemoryKeys.STRAFE_DIRECTION, if (Random.nextBoolean()) 1 else -1)
        ticksUntilChange = changeDirTicks
    }

    override fun execute(entity: SmartEntity): Boolean {
        val target = entity.memory.get(MemoryKeys.ATTACK_TARGET) ?: return false
        if (target.isRemoved) return false
        val dist = entity.position.distance(target.position)
        if (dist > strafeRange * 1.5) return false
        entity.memory.set(MemoryKeys.LOOK_TARGET, target.position.add(0.0, 1.5, 0.0))

        if (--ticksUntilChange <= 0) {
            ticksUntilChange = changeDirTicks
            entity.memory.set(MemoryKeys.STRAFE_DIRECTION, -(entity.memory.get(MemoryKeys.STRAFE_DIRECTION) ?: 1))
        }

        val dir = entity.memory.get(MemoryKeys.STRAFE_DIRECTION) ?: 1
        val toTarget = target.position.sub(entity.position).asVec().normalize()
        val perpendicular = Vec(-toTarget.z() * dir, 0.0, toTarget.x() * dir).normalize()
        val strafeTarget = entity.position.add(perpendicular.mul(3.0))
        entity.memory.set(MemoryKeys.MOVE_TARGET, strafeTarget)
        return true
    }

    override fun onStop(entity: SmartEntity) {
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
        entity.memory.clear(MemoryKeys.LOOK_TARGET)
        entity.memory.clear(MemoryKeys.STRAFE_DIRECTION)
    }
}

class ReturnHomeExecutor(
    private val maxDistance: Double = 30.0,
    private val arrivedDistance: Double = 2.0,
) : BehaviorExecutor {
    override fun execute(entity: SmartEntity): Boolean {
        val home = entity.memory.get(MemoryKeys.HOME_POSITION) ?: return false
        val dist = entity.position.distance(home)
        if (dist <= arrivedDistance) {
            entity.memory.clear(MemoryKeys.MOVE_TARGET)
            return false
        }
        entity.memory.set(MemoryKeys.MOVE_TARGET, home)
        return true
    }

    override fun onStop(entity: SmartEntity) {
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
    }
}

class FleeEntityExecutor(
    private val memoryKey: MemoryKey<*> = MemoryKeys.ATTACK_TARGET,
    private val fleeRange: Double = 12.0,
    private val safeDistance: Double = 16.0,
) : BehaviorExecutor {
    override fun execute(entity: SmartEntity): Boolean {
        val threat = entity.memory.get(memoryKey) as? Entity ?: return false
        if (threat.isRemoved) return false
        val dist = entity.position.distance(threat.position)
        if (dist > safeDistance) return false

        val away = entity.position.sub(threat.position).asVec().normalize().mul(fleeRange)
        entity.memory.set(MemoryKeys.MOVE_TARGET, entity.position.add(away))
        return true
    }

    override fun onStop(entity: SmartEntity) {
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
    }
}

class ChargeAttackExecutor(
    private val chargeUpTicks: Int = 40,
    private val chargeSpeed: Double = 2.0,
    private val damage: Float = 10f,
    private val hitRadius: Double = 1.5,
    private val cooldownTicks: Int = 100,
    private val onChargeStart: ((SmartEntity) -> Unit)? = null,
    private val onChargeRelease: ((SmartEntity) -> Unit)? = null,
    private val onHit: ((SmartEntity, LivingEntity) -> Unit)? = null,
) : BehaviorExecutor {
    private var phase = 0
    private var chargeTicks = 0
    private var dashDirection: Vec = Vec.ZERO
    private var dashTicks = 0
    private var cooldown = 0

    override fun onStart(entity: SmartEntity) {
        phase = 0
        cooldown = 0
    }

    override fun execute(entity: SmartEntity): Boolean {
        val target = entity.memory.get(MemoryKeys.ATTACK_TARGET) as? LivingEntity ?: return false
        if (target.isRemoved || target.isDead) return false
        entity.memory.set(MemoryKeys.LOOK_TARGET, target.position.add(0.0, 1.5, 0.0))

        if (cooldown > 0) {
            cooldown--
            entity.memory.set(MemoryKeys.MOVE_TARGET, target.position)
            return true
        }

        when (phase) {
            0 -> {
                entity.memory.clear(MemoryKeys.MOVE_TARGET)
                chargeTicks = chargeUpTicks
                onChargeStart?.invoke(entity)
                phase = 1
            }
            1 -> {
                if (--chargeTicks <= 0) {
                    dashDirection = target.position.sub(entity.position).asVec().normalize()
                    dashTicks = 20
                    onChargeRelease?.invoke(entity)
                    phase = 2
                }
            }
            2 -> {
                entity.velocity = dashDirection.mul(chargeSpeed * 20.0).withY(0.0)
                val instance = entity.instance ?: return false
                instance.getNearbyEntities(entity.position, hitRadius)
                    .filterIsInstance<LivingEntity>()
                    .filter { it !== entity && !it.isDead }
                    .forEach { hit ->
                        hit.damage(Damage.fromEntity(entity, damage))
                        onHit?.invoke(entity, hit)
                    }
                if (--dashTicks <= 0) {
                    cooldown = cooldownTicks
                    phase = 0
                }
            }
        }
        return true
    }

    override fun onStop(entity: SmartEntity) {
        phase = 0
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
        entity.memory.clear(MemoryKeys.LOOK_TARGET)
    }
}

class TeleportBehindExecutor(
    private val cooldownTicks: Int = 80,
    private val distance: Double = 3.0,
    private val onTeleport: ((SmartEntity) -> Unit)? = null,
) : BehaviorExecutor {
    private var cooldown = 0

    override fun execute(entity: SmartEntity): Boolean {
        val target = entity.memory.get(MemoryKeys.ATTACK_TARGET) ?: return false
        if (target.isRemoved) return false
        if (cooldown > 0) { cooldown--; return false }

        val behind = target.position.direction().mul(-distance)
        val dest = target.position.add(behind.x(), 0.0, behind.z())
        entity.teleport(dest)
        entity.memory.set(MemoryKeys.LOOK_TARGET, target.position.add(0.0, 1.5, 0.0))
        onTeleport?.invoke(entity)
        cooldown = cooldownTicks
        return false
    }
}

class SummonMinionsExecutor(
    private val minionType: EntityType,
    private val count: Int = 3,
    private val radius: Double = 4.0,
    private val cooldownTicks: Int = 200,
    private val onSummon: ((SmartEntity, SmartEntity) -> Unit)? = null,
    private val minionSetup: ((EntityBuilder) -> Unit)? = null,
) : BehaviorExecutor {
    private var cooldown = 0

    override fun execute(entity: SmartEntity): Boolean {
        if (cooldown > 0) { cooldown--; return false }
        val instance = entity.instance ?: return false
        val pos = entity.position

        repeat(count) {
            val angle = (it.toDouble() / count) * Math.PI * 2
            val spawnPos = Pos(pos.x() + cos(angle) * radius, pos.y(), pos.z() + sin(angle) * radius)
            val minion = spawnSmartEntity(minionType, instance, spawnPos) {
                health(10f)
                hostile()
                minionSetup?.invoke(this)
            }
            onSummon?.invoke(entity, minion)
        }

        cooldown = cooldownTicks
        return false
    }
}

class AreaDamageExecutor(
    private val radius: Double = 5.0,
    private val damage: Float = 6f,
    private val cooldownTicks: Int = 60,
    private val onActivate: ((SmartEntity) -> Unit)? = null,
) : BehaviorExecutor {
    private var cooldown = 0

    override fun execute(entity: SmartEntity): Boolean {
        if (cooldown > 0) { cooldown--; return false }
        val instance = entity.instance ?: return false
        onActivate?.invoke(entity)

        instance.getNearbyEntities(entity.position, radius)
            .filterIsInstance<LivingEntity>()
            .filter { it !== entity && !it.isDead }
            .forEach { it.damage(Damage.fromEntity(entity, damage)) }

        cooldown = cooldownTicks
        return false
    }
}

class ShieldExecutor(
    private val durationTicks: Int = 60,
    private val cooldownTicks: Int = 200,
    private val damageReduction: Float = 0.5f,
    private val onActivate: ((SmartEntity) -> Unit)? = null,
    private val onDeactivate: ((SmartEntity) -> Unit)? = null,
) : BehaviorExecutor {
    private var remaining = 0
    private var cooldown = 0

    override fun execute(entity: SmartEntity): Boolean {
        if (cooldown > 0) { cooldown--; return false }
        if (remaining <= 0) {
            remaining = durationTicks
            entity.memory.set(MemoryKeys.SHIELD_ACTIVE, true)
            onActivate?.invoke(entity)
        }
        if (--remaining <= 0) {
            entity.memory.set(MemoryKeys.SHIELD_ACTIVE, false)
            onDeactivate?.invoke(entity)
            cooldown = cooldownTicks
            return false
        }
        return true
    }

    override fun onInterrupt(entity: SmartEntity) {
        entity.memory.set(MemoryKeys.SHIELD_ACTIVE, false)
        onDeactivate?.invoke(entity)
    }
}

class ComboAttackExecutor(
    private val attacks: List<ComboStep>,
    private val resetTicks: Int = 40,
) : BehaviorExecutor {
    private var comboIndex = 0
    private var ticksSinceLastHit = 0
    private var stepCooldown = 0

    override fun execute(entity: SmartEntity): Boolean {
        val target = entity.memory.get(MemoryKeys.ATTACK_TARGET) as? LivingEntity ?: return false
        if (target.isRemoved || target.isDead) return false
        entity.memory.set(MemoryKeys.LOOK_TARGET, target.position.add(0.0, 1.5, 0.0))
        entity.memory.set(MemoryKeys.MOVE_TARGET, target.position)

        ticksSinceLastHit++
        if (ticksSinceLastHit > resetTicks) comboIndex = 0
        if (stepCooldown > 0) { stepCooldown--; return true }

        val step = attacks[comboIndex]
        if (entity.position.distance(target.position) <= step.range) {
            target.damage(Damage.fromEntity(entity, step.damage))
            step.onHit?.invoke(entity, target)
            entity.memory.set(MemoryKeys.COMBO_COUNT, comboIndex + 1)
            ticksSinceLastHit = 0
            stepCooldown = step.cooldownTicks
            comboIndex = (comboIndex + 1) % attacks.size
        }
        return true
    }

    override fun onStop(entity: SmartEntity) {
        comboIndex = 0
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
        entity.memory.clear(MemoryKeys.LOOK_TARGET)
        entity.memory.clear(MemoryKeys.COMBO_COUNT)
    }
}

data class ComboStep(
    val damage: Float,
    val range: Double = 2.5,
    val cooldownTicks: Int = 15,
    val onHit: ((SmartEntity, LivingEntity) -> Unit)? = null,
)

class PatrolExecutor(
    private val waypoints: List<Pos>,
    private val waitTicks: Int = 40,
    private val arrivalDistance: Double = 2.0,
) : BehaviorExecutor {
    private var currentIndex = 0
    private var waiting = 0

    override fun onStart(entity: SmartEntity) {
        currentIndex = 0
        waiting = 0
        setTarget(entity)
    }

    override fun execute(entity: SmartEntity): Boolean {
        if (waypoints.isEmpty()) return false
        if (waiting > 0) { waiting--; return true }
        val target = waypoints[currentIndex]
        if (entity.position.distance(target) <= arrivalDistance) {
            waiting = waitTicks
            currentIndex = (currentIndex + 1) % waypoints.size
            setTarget(entity)
        }
        return true
    }

    override fun onStop(entity: SmartEntity) {
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
    }

    private fun setTarget(entity: SmartEntity) {
        if (waypoints.isNotEmpty()) entity.memory.set(MemoryKeys.MOVE_TARGET, waypoints[currentIndex])
    }
}

class CircleTargetExecutor(
    private val radius: Double = 5.0,
    private val speed: Double = 0.08,
) : BehaviorExecutor {
    private var angle = 0.0

    override fun onStart(entity: SmartEntity) {
        angle = Random.nextDouble() * Math.PI * 2
    }

    override fun execute(entity: SmartEntity): Boolean {
        val target = entity.memory.get(MemoryKeys.ATTACK_TARGET) ?: return false
        if (target.isRemoved) return false
        angle += speed
        val circlePos = target.position.add(cos(angle) * radius, 0.0, sin(angle) * radius)
        entity.memory.set(MemoryKeys.MOVE_TARGET, circlePos)
        entity.memory.set(MemoryKeys.LOOK_TARGET, target.position.add(0.0, 1.5, 0.0))
        return true
    }

    override fun onStop(entity: SmartEntity) {
        entity.memory.clear(MemoryKeys.MOVE_TARGET)
        entity.memory.clear(MemoryKeys.LOOK_TARGET)
    }
}

class HealExecutor(
    private val healAmount: Float = 4f,
    private val cooldownTicks: Int = 100,
    private val onHeal: ((SmartEntity) -> Unit)? = null,
) : BehaviorExecutor {
    private var cooldown = 0

    override fun execute(entity: SmartEntity): Boolean {
        if (cooldown > 0) { cooldown--; return false }
        val maxHp = entity.getAttribute(Attribute.MAX_HEALTH).value.toFloat()
        if (entity.health >= maxHp) return false
        entity.health = min(entity.health + healAmount, maxHp)
        onHeal?.invoke(entity)
        cooldown = cooldownTicks
        return false
    }
}

class LookAtTargetExecutor(
    private val memoryKey: MemoryKey<*> = MemoryKeys.NEAREST_PLAYER,
    private val minTicks: Int = 40,
    private val maxTicks: Int = 80,
) : BehaviorExecutor {
    private var remaining = 0

    override fun onStart(entity: SmartEntity) {
        remaining = Random.nextInt(minTicks, maxTicks + 1)
    }

    override fun execute(entity: SmartEntity): Boolean {
        val target = entity.memory.get(memoryKey) as? Entity ?: return false
        if (target.isRemoved) return false
        entity.memory.set(MemoryKeys.LOOK_TARGET, target.position.add(0.0, 1.5, 0.0))
        return --remaining > 0
    }

    override fun onStop(entity: SmartEntity) {
        entity.memory.clear(MemoryKeys.LOOK_TARGET)
    }
}

class TimedActionExecutor(
    private val durationTicks: Int,
    private val onTick: ((SmartEntity, Int) -> Unit)? = null,
    private val onComplete: ((SmartEntity) -> Unit)? = null,
) : BehaviorExecutor {
    private var remaining = 0

    override fun onStart(entity: SmartEntity) {
        remaining = durationTicks
    }

    override fun execute(entity: SmartEntity): Boolean {
        onTick?.invoke(entity, durationTicks - remaining)
        if (--remaining <= 0) {
            onComplete?.invoke(entity)
            return false
        }
        return true
    }
}
