package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.particle.spawnParticleAt
import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.isCreativeOrSpectator
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.instance.Instance
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent

object SweepAttackModule : VanillaModule {

    override val id = "sweep-attack"
    override val description = "Sweeping attack hits nearby entities when on ground with full cooldown"
    override val configParams = listOf(
        ConfigParam.DoubleParam("sweepDamage", "Base sweep damage", 1.0, 0.0, 20.0),
        ConfigParam.DoubleParam("sweepRange", "Range of sweep attack", 1.5, 0.5, 5.0),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val sweepDamage = config.getDouble("sweepDamage", 1.0).toFloat()
        val sweepRange = config.getDouble("sweepRange", 1.5)
        val sweepRangeSq = sweepRange * sweepRange

        val node = EventNode.all("vanilla-sweep-attack")

        node.addListener(EntityDamageEvent::class.java) { event ->
            val attacker = event.damage.attacker as? Player ?: return@addListener
            if (attacker.isCreativeOrSpectator) return@addListener
            val target = event.entity as? LivingEntity ?: return@addListener
            if (!attacker.isOnGround) return@addListener
            if (attacker.isSprinting) return@addListener

            val tx = target.position.x()
            val ty = target.position.y()
            val tz = target.position.z()

            val inst = attacker.instance ?: return@addListener
            for (entity in inst.entities) {
                if (entity === target || entity === attacker) continue
                if (entity !is LivingEntity) continue
                val dx = entity.position.x() - tx
                val dy = entity.position.y() - ty
                val dz = entity.position.z() - tz
                if (dx * dx + dy * dy + dz * dz > sweepRangeSq) continue
                entity.damage(DamageType.PLAYER_ATTACK, sweepDamage)
            }

            inst.spawnParticleAt(Particle.SWEEP_ATTACK, target.position.add(0.0, target.eyeHeight * 0.5, 0.0))
            attacker.playSound(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP)
        }

        return node
    }
}
