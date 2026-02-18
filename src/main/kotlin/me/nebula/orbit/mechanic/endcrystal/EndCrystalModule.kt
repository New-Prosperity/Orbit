package me.nebula.orbit.mechanic.endcrystal

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material

class EndCrystalModule : OrbitModule("end-crystal") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.player.getItemInMainHand().material() != Material.END_CRYSTAL) return@addListener

            val blockName = event.block.name()
            if (blockName != "minecraft:obsidian" && blockName != "minecraft:bedrock") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val spawnPos = Vec(pos.x() + 0.5, pos.y() + 1.0, pos.z() + 0.5)

            val crystal = Entity(EntityType.END_CRYSTAL)
            crystal.setInstance(instance, spawnPos)

            val consumed = event.player.getItemInMainHand().consume(1)
            event.player.setItemInMainHand(consumed)
        }

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val target = event.target
            if (target.entityType != EntityType.END_CRYSTAL) return@addListener

            val instance = target.instance ?: return@addListener
            val center = target.position
            target.remove()

            explodeCrystal(instance, center)
        }
    }

    private fun explodeCrystal(instance: Instance, center: net.minestom.server.coordinate.Point) {
        val radius = 6.0
        instance.getNearbyEntities(center, radius).forEach { entity ->
            if (entity.isRemoved) return@forEach
            val distance = entity.position.distance(center)
            if (distance > radius) return@forEach
            val impact = (1.0 - distance / radius).toFloat()
            val damage = impact * 12f

            if (entity is LivingEntity) {
                entity.damage(DamageType.EXPLOSION, damage)
            }

            val knockback = Vec(
                entity.position.x() - center.x(),
                entity.position.y() - center.y() + 0.5,
                entity.position.z() - center.z(),
            ).normalize().mul(impact.toDouble() * 2.0)
            entity.velocity = entity.velocity.add(knockback)
        }
    }
}
