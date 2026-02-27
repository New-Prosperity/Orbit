package me.nebula.orbit.cosmetic

import me.nebula.orbit.utils.customcontent.armor.CustomArmorRegistry
import me.nebula.orbit.utils.customcontent.armor.equipFullSet
import me.nebula.orbit.utils.particle.spawnParticleAt
import me.nebula.orbit.utils.particle.showParticleShape
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.particle.Particle

object CosmeticApplier {

    fun applyArmorSkin(player: Player, cosmeticId: String) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val armorId = definition.data["armorId"] ?: return
        CustomArmorRegistry[armorId]?.equipFullSet(player)
    }

    fun clearArmorSkin(player: Player) {
        player.setEquipment(net.minestom.server.entity.EquipmentSlot.HELMET, net.minestom.server.item.ItemStack.AIR)
        player.setEquipment(net.minestom.server.entity.EquipmentSlot.CHESTPLATE, net.minestom.server.item.ItemStack.AIR)
        player.setEquipment(net.minestom.server.entity.EquipmentSlot.LEGGINGS, net.minestom.server.item.ItemStack.AIR)
        player.setEquipment(net.minestom.server.entity.EquipmentSlot.BOOTS, net.minestom.server.item.ItemStack.AIR)
    }

    fun playKillEffect(instance: Instance, position: Pos, cosmeticId: String) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val particle = resolveParticle(definition.data["particle"]) ?: return
        val shape = definition.data["shape"] ?: "sphere"
        instance.showParticleShape {
            when (shape) {
                "sphere" -> sphere(position, radius = 1.5, density = 12, particle = particle)
                "helix" -> helix(position, radius = 1.0, height = 2.0, turns = 3, particle = particle)
                "circle" -> circle(position, radius = 1.5, points = 24, particle = particle)
                else -> sphere(position, radius = 1.5, density = 12, particle = particle)
            }
        }
    }

    fun playWinEffect(player: Player, cosmeticId: String) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val particle = resolveParticle(definition.data["particle"]) ?: return
        val shape = definition.data["shape"] ?: "helix"
        player.showParticleShape {
            when (shape) {
                "helix" -> helix(player.position, radius = 1.0, height = 3.0, turns = 4, particle = particle)
                "sphere" -> sphere(player.position, radius = 2.0, density = 15, particle = particle)
                "circle" -> circle(player.position, radius = 2.0, points = 30, particle = particle)
                else -> helix(player.position, radius = 1.0, height = 3.0, turns = 4, particle = particle)
            }
        }
    }

    fun spawnTrailParticle(instance: Instance, position: Pos, cosmeticId: String) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val particle = resolveParticle(definition.data["particle"]) ?: return
        instance.spawnParticleAt(particle, position, count = 3, spread = 0.1f, speed = 0.02f)
    }

    fun spawnProjectileTrailParticle(instance: Instance, position: Pos, cosmeticId: String) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val particle = resolveParticle(definition.data["particle"]) ?: return
        instance.spawnParticleAt(particle, position, count = 1, spread = 0.05f, speed = 0.01f)
    }

    private fun resolveParticle(name: String?): Particle? {
        if (name == null) return null
        return runCatching { Particle.fromKey("minecraft:${name.lowercase()}") }.getOrNull()
    }
}
