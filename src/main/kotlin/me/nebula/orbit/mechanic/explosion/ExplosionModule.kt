package me.nebula.orbit.mechanic.explosion

import me.nebula.orbit.mechanic.food.addExhaustion
import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Explosion
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.timer.TaskSchedule

class ExplosionModule : OrbitModule("explosion") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (block.name() != "minecraft:tnt") return@addListener
            if (event.player.getItemInMainHand().material() != Material.FLINT_AND_STEEL) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            instance.setBlock(pos, Block.AIR)

            val tnt = Entity(EntityType.TNT)
            tnt.setInstance(instance, Vec(pos.x() + 0.5, pos.y().toDouble(), pos.z() + 0.5))
            tnt.setNoGravity(false)

            tnt.scheduler().buildTask {
                val center = tnt.position
                val explosion = TntExplosion(
                    center.x().toFloat(),
                    center.y().toFloat(),
                    center.z().toFloat(),
                    4f,
                )
                explosion.apply(instance)
                damageNearby(instance, center, 4f)
                tnt.remove()
            }.delay(TaskSchedule.tick(80)).schedule()
        }
    }

    private fun damageNearby(instance: Instance, center: Point, strength: Float) {
        val radius = strength * 2.0
        instance.getNearbyEntities(center, radius).forEach { entity ->
            if (entity.entityType == EntityType.TNT) return@forEach
            val distance = entity.position.distance(center)
            if (distance > radius) return@forEach
            val impact = (1.0 - distance / radius).toFloat()
            val damage = (impact * impact + impact) / 2f * 7f * strength + 1f
            if (entity is LivingEntity) {
                entity.damage(DamageType.EXPLOSION, damage)
                if (entity is Player) entity.addExhaustion(0.1f)
            }
            val knockback = Vec(
                entity.position.x() - center.x(),
                entity.position.y() - center.y() + 0.5,
                entity.position.z() - center.z(),
            ).normalize().mul(impact.toDouble() * 1.5)
            entity.velocity = entity.velocity.add(knockback)
        }
    }

    private class TntExplosion(
        centerX: Float, centerY: Float, centerZ: Float, strength: Float,
    ) : Explosion(centerX, centerY, centerZ, strength) {

        override fun prepare(instance: Instance): List<Point> {
            val blocks = mutableListOf<Point>()
            val radius = strength.toInt()
            val center = Vec(centerX.toDouble(), centerY.toDouble(), centerZ.toDouble())
            for (x in -radius..radius) {
                for (y in -radius..radius) {
                    for (z in -radius..radius) {
                        val pos = Vec(centerX.toDouble() + x, centerY.toDouble() + y, centerZ.toDouble() + z)
                        if (pos.distance(center) > strength) continue
                        val block = instance.getBlock(pos)
                        if (block == Block.AIR || block == Block.BEDROCK) continue
                        val hardness = block.registry()?.hardness() ?: continue
                        if (hardness < 0) continue
                        blocks.add(pos)
                    }
                }
            }
            return blocks
        }
    }
}
