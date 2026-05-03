package me.nebula.orbit.utils.entitybuilder

import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.Damage
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import kotlin.math.sqrt
import kotlin.random.Random

data class EffectApplication(
    val type: PotionEffect,
    val durationTicks: Int,
    val amplifier: Int = 0,
    val chance: Float = 1f,
)

data class EffectResistance(
    val type: PotionEffect,
    val durationMultiplier: Float = 1f,
    val amplifierAdjustment: Int = 0,
    val applyChance: Float = 1f,
)

data class HitOptions(
    val effects: List<EffectApplication> = emptyList(),
    val knockbackHorizontal: Float = 0f,
    val knockbackVertical: Float = 0f,
) {
    companion object {
        val NONE = HitOptions()
    }
}

internal fun applyHit(
    attacker: SmartEntity,
    target: LivingEntity,
    damage: Float,
    options: HitOptions = HitOptions.NONE,
): Boolean {
    val landed = target.damage(Damage.fromEntity(attacker, damage))
    if (!landed) return false

    attacker.memory.set(MemoryKeys.LAST_DAMAGE_DEALT_TIME, System.currentTimeMillis())
    attacker.sounds.attack?.let { attacker.playEntitySound(it) }
    attacker.onDealtDamage?.invoke(attacker, target, damage)

    options.effects.forEach { eff ->
        if (Random.nextFloat() <= eff.chance) {
            target.addEffect(Potion(eff.type, eff.amplifier, eff.durationTicks))
        }
    }

    if (options.knockbackHorizontal > 0f || options.knockbackVertical > 0f) {
        val dx = target.position.x() - attacker.position.x()
        val dz = target.position.z() - attacker.position.z()
        val dist = sqrt(dx * dx + dz * dz)
        if (dist > 0.0001) {
            val nx = dx / dist
            val nz = dz / dist
            target.velocity = target.velocity.add(
                nx * options.knockbackHorizontal * 20.0,
                options.knockbackVertical * 20.0,
                nz * options.knockbackHorizontal * 20.0,
            )
        }
    }

    if (target.isDead) {
        attacker.memory.set(MemoryKeys.LAST_TARGET_KILLED, target)
        attacker.fire(Triggers.ON_KILLED_TARGET, target)
    }

    return true
}
